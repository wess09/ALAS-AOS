package com.aliothmoon.maafw.proot

import android.content.Context
import com.aliothmoon.maafw.MaaDispatchers
import com.aliothmoon.maafw.service.HostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ALAS 调度器运行态：悬浮窗「开始/停止挂机」与日志板的数据源
 *
 * 数据全部来自 wrapper 薄 HTTP（127.0.0.1:22400，rootfs wrapper.py）：
 * - GET /status → runner_alive/pid/config/gui_alive/log_lines
 * - POST /start?config=N、POST /stop → 调度器启停（幂等；/stop 内部 SIGTERM→3s→SIGKILL，响应偏慢）
 * - GET /logs?tail=N → 纯文本日志尾。语义：按 mtime 取 log/ 下最新 *.txt——
 *   调度器在跑时是它的 {date}_alas.txt，没跑时多半是 gui 启动日志，均够悬浮窗一瞥
 * - GET /configs → config/ 下的实例配置名列表（运行配置下拉的数据源）
 *
 * 4s 轮询；wrapper 不可达不视为错误（proot 会话没起/正在起，reachable=false 即可）。
 * 运行配置选择持久化在 SharedPreferences，/start 时透传给 runner；
 * 调度器在跑时 /status 回报的 config 才是生效配置，下拉选择下次启动生效。
 * 双头管理注意：WebUI 的启停按钮已被锁定补丁封死（只记 warning），本通道是唯一控制面。
 */
data class AlasRunState(
    val reachable: Boolean = false,
    val runnerAlive: Boolean = false,
    val pid: Int? = null,
    val guiAlive: Boolean = false,
    val logLines: Int = 0,
    val logTail: List<String> = emptyList(),
    val busy: Boolean = false,
    val configs: List<String> = emptyList(),
    val selectedConfig: String = DEFAULT_CONFIG,
    /** 正在跑的实例名（/status 回报）；没在跑为 null */
    val runningConfig: String? = null,
    /** ALAS 工具（半自动点击/活动剧情）是否在跑（/status 回报） */
    val toolAlive: Boolean = false,
    /** 在跑的工具名（TOOL_* 常量）；没在跑为 null */
    val toolName: String? = null,
) {
    companion object {
        const val DEFAULT_CONFIG = "alas"

        /** wrapper /tool/start 认识的工具名：半自动点击、活动剧情 */
        const val TOOL_SEMI_AUTO = "daemon"
        const val TOOL_EVENT_STORY = "event_story"
    }
}

class AlasRunController(
    context: Context,
    private val scope: CoroutineScope,
    private val hostState: HostState,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val controlToken = AndroidControlAuth.get(context)

    private val _state = MutableStateFlow(
        AlasRunState(
            selectedConfig = prefs.getString(KEY_SELECTED_CONFIG, AlasRunState.DEFAULT_CONFIG)
                ?: AlasRunState.DEFAULT_CONFIG,
        )
    )
    val state = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val refreshMutex = Mutex()

    /** 幂等：挂到 MaaFwApp.postCreate，轮询整个 App 生命周期 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(MaaDispatchers.IO) {
            while (true) {
                refreshMutex.withLock { refreshLocked() }
                delay(POLL_MS)
            }
        }
    }

    /** 选择运行配置：持久化，下次 /start 生效；调度器在跑时不拦，但生效要等下次启动 */
    fun selectConfig(name: String) {
        prefs.edit().putString(KEY_SELECTED_CONFIG, name).apply()
        _state.update { it.copy(selectedConfig = name) }
    }

    fun startAlas() {
        val config = URLEncoder.encode(_state.value.selectedConfig, "UTF-8")
        startAfterEnvironmentReady("$BASE/start?config=$config")
    }

    fun stopAlas() = postThenRefresh("$BASE/stop")

    /**
     * 工具启停：与调度器同一条 postThenRefresh 通道。
     * 互斥（启工具先停 runner、启 runner 先停工具）由 wrapper 集中执行，这里不做门控
     */
    fun startTool(name: String) {
        val tool = URLEncoder.encode(name, "UTF-8")
        val config = URLEncoder.encode(_state.value.selectedConfig, "UTF-8")
        startAfterEnvironmentReady("$BASE/tool/start?name=$tool&config=$config")
    }

    fun stopTool() = postThenRefresh("$BASE/tool/stop")

    private fun startAfterEnvironmentReady(url: String) {
        scope.launch {
            _state.update { it.copy(busy = true) }
            if (!hostState.ensureEnvironmentStarted()) {
                Timber.w("refusing AzurPilot start: Android environment is unavailable")
                _state.update { it.copy(busy = false) }
                return@launch
            }
            postThenRefresh(url)
        }
    }

    private fun postThenRefresh(url: String) {
        scope.launch(MaaDispatchers.IO) {
            _state.update { it.copy(busy = true) }
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-AzurPilot-Android-Token", controlToken)
                conn.connectTimeout = HTTP_TIMEOUT_MS
                // /stop 要等进程组死掉（SIGTERM→3s→SIGKILL），读超时给足
                conn.readTimeout = POST_READ_TIMEOUT_MS
                conn.inputStream.use { it.readBytes() }
            }.onFailure { Timber.w(it, "alas POST %s failed", url) }
            refreshMutex.withLock { refreshLocked() }
            _state.update { it.copy(busy = false) }
        }
    }

    /** 可达即拉日志尾：loopback 读文件尾部开销可忽略，空闲时 gui 启动日志恰是排障现场 */
    private fun refreshLocked() {
        val body = get("$BASE/status", HTTP_TIMEOUT_MS)
        if (body == null) {
            _state.update {
                it.copy(
                    reachable = false, runnerAlive = false, pid = null,
                    guiAlive = false, logLines = 0, logTail = emptyList(),
                    configs = emptyList(), runningConfig = null,
                    toolAlive = false, toolName = null,
                )
            }
            return
        }
        val j = runCatching { JSONObject(body) }.getOrNull() ?: return
        val runnerAlive = j.optBoolean("runner_alive")
        val pid = if (j.isNull("pid")) null else j.optInt("pid")
        val runningConfig = if (j.isNull("config")) null else j.optString("config")
        val toolAlive = j.optBoolean("tool_alive")
        val toolName = if (j.isNull("tool_name")) null else j.optString("tool_name")
        val guiAlive = j.optBoolean("gui_alive")
        val logLines = j.optInt("log_lines")
        val configs = runCatching {
            val arr = JSONObject(get("$BASE/configs", HTTP_TIMEOUT_MS) ?: return@runCatching null)
                .getJSONArray("configs")
            List(arr.length()) { arr.getString(it) }
        }.getOrNull() ?: _state.value.configs
        // 持久化的选择可能已被 WebUI 删掉；列表非空时自愈回第一项
        val selected = _state.value.selectedConfig
        if (configs.isNotEmpty() && selected !in configs) {
            selectConfig(configs.first())
        }
        val tail = get("$BASE/logs?tail=$LOG_TAIL", HTTP_TIMEOUT_MS)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: _state.value.logTail
        _state.update {
            it.copy(
                reachable = true, runnerAlive = runnerAlive, pid = pid,
                guiAlive = guiAlive, logLines = logLines, logTail = tail,
                configs = configs, runningConfig = runningConfig,
                toolAlive = toolAlive, toolName = toolName,
            )
        }
    }

    private fun get(url: String, timeoutMs: Int): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("X-AzurPilot-Android-Token", controlToken)
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        if (conn.responseCode != 200) return null
        conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }.onFailure { Timber.d(it, "alas GET %s failed", url) }.getOrNull()

    private companion object {
        const val BASE = "http://127.0.0.1:${ProotHost.WEBUI_PORT}/android"
        const val POLL_MS = 4_000L
        const val HTTP_TIMEOUT_MS = 1_500
        const val POST_READ_TIMEOUT_MS = 12_000
        const val LOG_TAIL = 80
        const val PREFS_NAME = "azurpilot_android"
        const val KEY_SELECTED_CONFIG = "selected_config"
    }
}
