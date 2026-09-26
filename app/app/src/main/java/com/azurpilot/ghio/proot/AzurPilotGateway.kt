package com.azurpilot.ghio.proot

import com.azurpilot.ghio.AppDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * AzurPilot `/api/v1/ws` 网关客户端
 *
 * WebUI 前端用的就是这一条长连接：请求/响应信封 + 主题推送 + `seq` 序号。
 * 原生界面不碰 HTTP 静态资源，但业务能力全部来自这里——所以这个类要能表达协议的全部语义，
 * 而不只是「拿到没有」。
 *
 * **鉴权靠「本机直连免密」**：网关的 `is_local_client()` 只要求来源地址与 Host 头都是回环，
 * 且**没有 Origin 头**——OkHttp 的 WebSocket 天然不带 Origin，因此直接放行，无需 WebUI 密码。
 * 收到 `session` 事件时若 `authRequired` 为真（网关收紧了策略），登录入口由 [login] 补。
 *
 * 信封（`module/api/protocol.py`）：
 * - 请求 `{"v":1,"type":"request","id":..,"method":..,"params":{..}}`
 * - 响应 `{"v":1,"type":"response","id":..,"ok":true,"result":{..}}`，失败为 `ok:false` + `error{code,message}`
 * - 事件 `{"v":1,"type":"event","topic":"overview|instances|logs|preview|statistics|session","seq":n,"data":{..}}`
 */
class AzurPilotGateway(private val scope: CoroutineScope) {

    /**
     * 一次调用的结果。`Offline` 与 `Err` 分开：前者可以重试，后者重试也是同一个错
     *
     * `raw` 不一定是对象——`instances.list` / `instances.importable` 按协议返回的是**裸数组**，
     * 只认对象的话这两个接口的返回值会被整段丢掉。所以原样留着，由调用方按 [obj] / [array] 取。
     */
    sealed interface Reply {
        data class Ok(val raw: ApValue) : Reply {
            val obj: JSONObject get() = raw as? JSONObject ?: EMPTY_OBJECT
            val array: JSONArray? get() = raw as? JSONArray
        }

        data class Err(val code: String, val message: String) : Reply
        object Offline : Reply

        companion object {
            private val EMPTY_OBJECT = JSONObject()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接：读超时交给心跳与重连
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 网关要求先登录；本机直连时为 false */
    private val _authRequired = MutableStateFlow(false)
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    private val _events = MutableSharedFlow<JSONObject>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Reply.Ok>>()

    @Volatile
    private var socket: WebSocket? = null

    private var loop: Job? = null

    /** 当前订阅；断线重连后用它原样恢复，界面不需要感知重连 */
    @Volatile
    private var subscription: Pair<String?, List<String>> = null to emptyList()

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch(AppDispatchers.IO) {
            var backoff = RECONNECT_MIN_MS
            while (true) {
                // awaitSession 要挂到这条连接**结束**才返回；只在 onOpen 就返回会让循环
                // 每 2 秒另开一条，旧连接一直挂着，直到撞上网关的连接数上限被踢
                val opened = runCatching { awaitSession() }.getOrElse {
                    Timber.d(it, "azurpilot gateway connect failed")
                    false
                }
                _connected.value = false
                failAllPending()
                backoff = if (opened) RECONNECT_MIN_MS else (backoff * 2).coerceAtMost(RECONNECT_MAX_MS)
                delay(backoff)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(NORMAL_CLOSE, null)
        socket = null
        _connected.value = false
        failAllPending()
    }

    /**
     * 订阅主题；`instance` 为 null 时只订 `instances` 这类与实例无关的主题
     *
     * 网关侧订阅会被整体替换（不是叠加），所以这里保存完整的一份用于重连恢复。
     */
    suspend fun subscribe(instance: String?, topics: List<String>): Reply {
        subscription = instance to topics
        // 必须是 JSONArray：org.json 的 put 收到裸 List 会当成普通对象，
        // 序列化时走 toString() 变成 `"instances, overview"` 这样一个字符串，网关直接 INVALID_PARAMS
        val params = JSONObject().put("topics", JSONArray(topics))
        if (instance != null) params.put("instance", instance)
        return call("events.subscribe", params)
    }

    /** 供网关收紧免密策略时使用：本机直连当前不需要 */
    suspend fun login(password: String): Reply = call("auth.login", JSONObject().put("password", password))

    /**
     * 发一次请求并等它的响应
     *
     * [timeoutMs] 默认取协议的 45s——`scheduler.stop` 要等最多 30s 才敢回话，超时设小了会
     * 把「正在停」误判成失败。
     */
    suspend fun call(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): Reply {
        val ws = socket
        if (ws == null || !_connected.value) return Reply.Offline
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Reply.Ok>()
        pending[id] = deferred
        val payload = JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("type", "request")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!ws.send(payload.toString())) {
            pending.remove(id)
            return Reply.Offline
        }
        // 只吞业务错误：CancellationException 要放出去，否则外层取消会被这里悄悄吃掉
        val result = withTimeoutOrNull(timeoutMs) {
            try {
                deferred.await()
            } catch (_: AzurPilotException) {
                null
            }
        }
        pending.remove(id)
        return when {
            result != null -> result
            !_connected.value -> Reply.Offline
            else -> Reply.Err("TIMEOUT", "请求超时（$method）")
        }
    }

    /** 只关心「拿到没有」的调用点用这个；错误码与数组结果都折成 null */
    suspend fun request(method: String, params: JSONObject = JSONObject()): JSONObject? =
        (call(method, params) as? Reply.Ok)?.obj

    /** 结果本身是值的调用（裸数组）走这个 */
    suspend fun callRaw(method: String, params: JSONObject = JSONObject()): ApValue =
        (call(method, params) as? Reply.Ok)?.raw

    /**
     * 建连，并**挂起直到这条连接结束**；返回期间是否真的建立过会话
     *
     * 返回 true 表示「连上过」（用来重置退避），false 表示连都没连上（用来加倍退避）。
     * 连接存续期间这个挂起就是「保持」——`_connected` 是唯一的连接态来源。
     */
    private suspend fun awaitSession(): Boolean = suspendCancellableCoroutine { cont ->
        var opened = false
        var resumed = false

        fun finish() {
            if (resumed) return
            resumed = true
            if (cont.isActive) cont.resume(opened)
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                opened = true
                _connected.value = true
                Timber.d("azurpilot gateway connected")
                // 重连后把订阅恢复回来：网关不保存会话状态，不重订就没有推送
                val (instance, topics) = subscription
                if (topics.isNotEmpty()) {
                    scope.launch(AppDispatchers.IO) { subscribe(instance, topics) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // 对端先发起：回一个正常关闭，让 onClosed 走完
                webSocket.close(NORMAL_CLOSE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                finish()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                Timber.d(t, "azurpilot gateway socket failure")
                finish()
            }
        }
        val request = Request.Builder().url(WS_URL).build()
        socket = client.newWebSocket(request, listener)
        cont.invokeOnCancellation {
            socket?.cancel()
            socket = null
        }
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "response" -> {
                val id = json.optString("id")
                val deferred = id.takeIf { it.isNotEmpty() }?.let { pending.remove(it) } ?: return
                if (json.optBoolean("ok")) {
                    // 缺席与显式 null 都收成 null；JSONObject.NULL 不能当值用
                    val raw = json.opt("result").takeIf { it !== JSONObject.NULL }
                    deferred.complete(Reply.Ok(raw))
                } else {
                    val error = json.optJSONObject("error")
                    val code = error?.optString("code").orEmpty()
                    val message = error?.optString("message").orEmpty()
                    Timber.w("azurpilot gateway %s: %s", code, message)
                    deferred.completeExceptionally(AzurPilotException(code, message))
                }
            }

            "event" -> {
                // 连接建立时网关先发一条 session：本机直连 authRequired=false，无需登录
                if (json.optString("topic") == "session") {
                    _authRequired.value = json.optJSONObject("data")?.optBoolean("authRequired") ?: false
                    return
                }
                // 事件不阻塞读取循环：满了丢最旧（过期总览没有价值）
                _events.tryEmit(json)
            }
        }
    }

    private fun failAllPending() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    /** `CompletableDeferred` 不接受 null 作为完成值，所以成功态本身带上结果一起传 */
    companion object {
        const val WS_URL = "ws://127.0.0.1:${ProotHost.WEBUI_PORT}/api/v1/ws"
        private const val NORMAL_CLOSE = 1000
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val PING_INTERVAL_MS = 20_000L
        private const val RECONNECT_MIN_MS = 2_000L
        private const val RECONNECT_MAX_MS = 30_000L
        private const val EVENT_BUFFER = 256
        private const val PROTOCOL_VERSION = 1
    }
}

/** 网关回的业务错误；[code] 是协议里的错误码，[message] 是可直接展示的中文文案 */
class AzurPilotException(val code: String, override val message: String) : Exception(message)

/**
 * 失败态折成 `Result`
 *
 * 走 `Result` 的调用点需要「错误文案能显示出来」，而 [AzurPilotGateway.Reply.Err] 里已经带着
 * 可直接展示的中文了——这里只做转换，不再拼文案。
 */
fun AzurPilotGateway.Reply.asFailure(): Result<Nothing> = when (this) {
    is AzurPilotGateway.Reply.Err -> Result.failure(AzurPilotException(code, message))
    AzurPilotGateway.Reply.Offline ->
        Result.failure(AzurPilotException("DISCONNECTED", "未连接到 AzurPilot"))

    is AzurPilotGateway.Reply.Ok ->
        Result.failure(AzurPilotException("INTERNAL_ERROR", "结果格式不正确"))
}
