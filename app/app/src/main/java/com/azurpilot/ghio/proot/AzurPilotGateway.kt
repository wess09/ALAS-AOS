package com.azurpilot.ghio.proot

import com.azurpilot.ghio.AppDispatchers
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
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * AzurPilot `/api/v1/ws` 网关客户端
 *
 * 与 `/android/…` 那套 HTTP 薄接口互补：这里是 WebUI 前端同款的全量接口
 * （总览、单任务运行、自启、实例与配置读写、运行时热更新、公告…），
 * 走一条长连接 + 请求/响应信封 + 主题推送。
 *
 * **鉴权靠「本机直连免密」**：网关的 `is_local_client()` 只要求来源地址与 Host 头都是回环，
 * 且**没有 Origin 头**——OkHttp 的 WebSocket 天然不带 Origin，因此直接放行，无需 WebUI 密码。
 * 若哪天网关收紧该策略，把 [login] 接到连接建立之后即可（信封与错误码已按协议留好）。
 *
 * 信封（`module/api/protocol.py`）：
 * - 请求 `{"v":1,"type":"request","id":..,"method":..,"params":{..}}`
 * - 响应 `{"v":1,"type":"response","id":..,"ok":true,"result":{..}}`，失败为 `ok:false` + `error{code,message}`
 * - 事件 `{"v":1,"type":"event","topic":"overview|instances|logs|preview","data":{..}}`
 */
class AzurPilotGateway(private val scope: CoroutineScope) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接：读超时交给心跳与重连
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<JSONObject>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<JSONObject>>()

    @Volatile
    private var socket: WebSocket? = null

    private var loop: Job? = null

    /** 当前订阅的实例；重连后用它恢复订阅 */
    @Volatile
    private var subscribedInstance: String? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch(AppDispatchers.IO) {
            var backoff = RECONNECT_MIN_MS
            while (true) {
                val established = runCatching { connectOnce() }.getOrElse {
                    Timber.d(it, "azurpilot gateway connect failed")
                    false
                }
                _connected.value = false
                failAllPending()
                backoff = if (established) RECONNECT_MIN_MS else (backoff * 2).coerceAtMost(RECONNECT_MAX_MS)
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

    /** 订阅某个实例的 `overview` 主题；实例变了就重订，网关会推最新总览 */
    suspend fun subscribeOverview(instance: String) {
        subscribedInstance = instance
        request("events.subscribe", JSONObject().put("instance", instance).put("topics", listOf("overview")))
    }

    /**
     * 发一次请求并等它的响应；未连接、超时、或网关回了 `ok:false` 都返回 null
     * （错误码只记日志：调用方关心的是「拿到没有」）
     */
    suspend fun request(method: String, params: JSONObject = JSONObject()): JSONObject? {
        val ws = socket
        if (ws == null || !_connected.value) return null
        val id = UUID.randomUUID().toString()
        val deferred = kotlinx.coroutines.CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val payload = JSONObject()
            .put("v", 1)
            .put("type", "request")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!ws.send(payload.toString())) {
            pending.remove(id)
            return null
        }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
            ?.also { pending.remove(id) }
            ?: run { pending.remove(id); null }
    }

    /** 供将来网关收紧免密策略时使用：本机直连当前不需要 */
    suspend fun login(password: String): Boolean =
        request("auth.login", JSONObject().put("password", password)) != null

    private suspend fun connectOnce(): Boolean = suspendCancellableCoroutine { cont ->
        val request = Request.Builder()
            .url(WS_URL)
            .build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                _connected.value = true
                Timber.d("azurpilot gateway connected")
                if (cont.isActive) cont.resume(true)
                subscribedInstance?.let { instance ->
                    scope.launch(AppDispatchers.IO) { subscribeOverview(instance) }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                _connected.value = false
                if (cont.isActive) cont.resume(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                _connected.value = false
                Timber.d(t, "azurpilot gateway socket failure")
                if (cont.isActive) cont.resume(false)
            }
        }
        socket = client.newWebSocket(request, listener)
        cont.invokeOnCancellation { socket?.cancel() }
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "response" -> {
                val id = json.optString("id")
                val deferred = id.takeIf { it.isNotEmpty() }?.let { pending.remove(it) } ?: return
                if (json.optBoolean("ok")) {
                    deferred.complete(json.optJSONObject("result") ?: JSONObject())
                } else {
                    val error = json.optJSONObject("error")
                    Timber.w(
                        "azurpilot gateway %s: %s",
                        error?.optString("code").orEmpty(),
                        error?.optString("message").orEmpty(),
                    )
                    deferred.completeExceptionally(IllegalStateException(error?.optString("code")))
                }
            }

            "event" -> {
                // 事件不阻塞读取循环：满了丢最旧（过期总览没有价值）
                _events.tryEmit(json)
            }
        }
    }

    private fun failAllPending() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    private companion object {
        const val WS_URL = "ws://127.0.0.1:${ProotHost.WEBUI_PORT}/api/v1/ws"
        const val NORMAL_CLOSE = 1000
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val PING_INTERVAL_MS = 20_000L
        const val RECONNECT_MIN_MS = 2_000L
        const val RECONNECT_MAX_MS = 30_000L
    }
}
