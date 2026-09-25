package com.aliothmoon.azurpilot.proot

import com.aliothmoon.azurpilot.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber

/** 总览里的一条任务：名字、下次运行时间、状态（running/pending/waiting） */
data class AzurPilotTask(
    val name: String,
    val nextRun: String,
    val state: String,
)

/**
 * `/api/v1/ws` 网关给 App 的那部分状态
 *
 * 只放「App 侧真正要用」的字段，不把网关的原始 JSON 透进 UI 层。
 */
data class AzurPilotApiState(
    val connected: Boolean = false,
    val instance: String? = null,
    /** 调度器状态：running / stopped / error / updating */
    val schedulerStatus: String? = null,
    val tasks: List<AzurPilotTask> = emptyList(),
    /** 启动时自动运行（对应 alas-launcher 的 startup.query/set 里的 enabled） */
    val startupEnabled: Boolean = false,
    /** 记住上次运行状态 */
    val startupRemember: Boolean = false,
    val busy: Boolean = false,
    /**
     * 内置运行时的上游提交。取自上层的 `updater.status`
     *
     * 注意：Android 下上游**有意关闭**了运行时热更（`if self.android: state='android',
     * available=False, managedByAndroid=True`）——运行时随 App 整包更新，这里只用来展示版本。
     */
    val runtimeCommit: String? = null,
    /** 运行时是否由 App 整包管理（Android 下恒 true） */
    val managedByAndroid: Boolean = false,
)

/**
 * App 侧对 `/api/v1/ws` 的封装：连上之后订阅总览、读自启开关，并把它们暴露成 Compose 能收的 StateFlow
 *
 * 与 [AzurPilotRunController] 的分工：那个走 `/android/…` 薄接口管启停与日志尾（悬浮窗也要用），
 * 这个走富接口补上「总览 / 自启 / 实例与配置」——两者互不替代。
 */
class AzurPilotApi(
    private val scope: CoroutineScope,
    private val gateway: AzurPilotGateway,
) {

    private val _state = MutableStateFlow(AzurPilotApiState())
    val state: StateFlow<AzurPilotApiState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var eventsJob: Job? = null
    private var loopJob: Job? = null

    /** 幂等：接上网关的连接状态与事件流，并按需重订当前实例 */
    fun start() {
        if (eventsJob == null) {
            eventsJob = scope.launch(AppDispatchers.IO) {
                gateway.events.collect { event ->
                    if (event.optString("topic") == "overview") {
                        applyOverview(event.optJSONObject("data"))
                    }
                }
            }
        }
        if (loopJob == null) {
            loopJob = scope.launch(AppDispatchers.IO) {
                while (true) {
                    val connected = gateway.connected.value
                    _state.update { it.copy(connected = connected) }
                    if (connected) refreshMutex.withLock { refreshLocked() }
                    delay(POLL_MS)
                }
            }
        }
    }

    /** 运行配置下拉变了：重订总览并重读该实例的自启开关 */
    fun onInstanceSelected(instance: String) {
        scope.launch(AppDispatchers.IO) {
            refreshMutex.withLock {
                _state.update { it.copy(instance = instance, tasks = emptyList(), schedulerStatus = null) }
                if (!gateway.connected.value) return@withLock
                runCatching { gateway.subscribeOverview(instance) }
                loadStartupLocked(instance)
            }
        }
    }

    /** 开/关「启动后自动运行挂机」 */
    fun setStartupEnabled(enabled: Boolean) {
        scope.launch(AppDispatchers.IO) {
            _state.update { it.copy(busy = true) }
            val instance = _state.value.instance
            if (instance != null && gateway.connected.value) {
                gateway.request("startup.set", JSONObject().put("instance", instance).put("enabled", enabled))
                loadStartupLocked(instance)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun refreshLocked() {
        val instance = _state.value.instance ?: return
        if (!gateway.connected.value) return
        // 订阅可能因重连而丢失：每次刷新补一次 overview.get，事件只做增量
        gateway.request("overview.get", JSONObject().put("instance", instance))
            ?.let(::applyOverview)
        loadStartupLocked(instance)
        // 运行时提交不会变，取到一次就够
        if (_state.value.runtimeCommit == null) loadRuntimeLocked()
    }

    private suspend fun loadRuntimeLocked() {
        val status = gateway.request("updater.status") ?: return
        _state.update {
            it.copy(
                runtimeCommit = status.optString("localHead").ifEmpty { null },
                managedByAndroid = status.optBoolean("managedByAndroid"),
            )
        }
    }

    private suspend fun loadStartupLocked(instance: String) {
        val result = gateway.request("startup.get", JSONObject().put("instance", instance)) ?: return
        _state.update {
            it.copy(
                startupEnabled = result.optBoolean("enabled"),
                startupRemember = result.optBoolean("remember"),
            )
        }
    }

    private fun applyOverview(overview: JSONObject?) {
        if (overview == null) return
        val tasks = runCatching {
            val arr = overview.optJSONArray("tasks") ?: return@runCatching emptyList()
            List(arr.length()) { index ->
                val item = arr.getJSONObject(index)
                AzurPilotTask(
                    name = item.optString("name"),
                    nextRun = item.optString("nextRun"),
                    state = item.optString("state"),
                )
            }
        }.getOrElse {
            Timber.d(it, "azurpilot overview parse failed")
            emptyList()
        }
        _state.update {
            it.copy(
                schedulerStatus = overview.optString("status").ifEmpty { null },
                tasks = tasks,
            )
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
    }
}
