package com.azurpilot.ghio.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.domain.OverlayControlMode
import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.domain.RunMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * app 设置的唯一读写入口
 *
 * 各项以 StateFlow 暴露。读盘是异步的，[loaded] 置位之前 `.value` 还是 schema 默认值——
 * 同步 `.value` 是 [com.azurpilot.ghio.privileged.RemoteServiceManager] 那条链要的
 * （它收的是 `() -> RemoteBackend`，没有挂起点），所以读盘不能省，只能挪到构造之外
 *
 * **凡是在启动早期同步读 `.value` 的调用方都必须先等 [loaded]**：早读一步拿到的是
 * 默认值，Root 用户会被当成 Shizuku。启动首屏与 `AzurPilotApp.postCreate` 都挂在这上面
 */
class AppSettingsManager(private val context: Context) : AppSettingsGateway {

    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.IO)

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
    }

    val settings: Flow<AppSettings> = with(AppSettingsSchema) { context.dataStore.flow }

    private val defaults = AppSettings()

    private val _loaded = MutableStateFlow(false)

    /**
     * 首次读盘是否已落到下面各 StateFlow 上；置位后 `.value` 才是盘上的值
     *
     * 等待点：启动首屏（`MainActivity`）与 `AzurPilotApp.postCreate`（`RemoteServiceManager`
     * 一初始化就同步读 startupBackend）
     */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _startupBackend = MutableStateFlow(parseBackend(defaults.startupBackend))
    val startupBackend: StateFlow<RemoteBackend> = _startupBackend.asStateFlow()

    private val _skipShizukuCheck = MutableStateFlow(defaults.skipShizukuCheck.toBoolean())
    val skipShizukuCheck: StateFlow<Boolean> = _skipShizukuCheck.asStateFlow()

    private val _shizukuLaunchPackage = MutableStateFlow(defaults.shizukuLaunchPackage)
    val shizukuLaunchPackage: StateFlow<String> = _shizukuLaunchPackage.asStateFlow()

    private val _runMode = MutableStateFlow(parseRunMode(defaults.runMode))
    override val runMode: StateFlow<RunMode> = _runMode.asStateFlow()

    private val _overlayControlMode = MutableStateFlow(parseOverlayMode(defaults.overlayControlMode))
    override val overlayControlMode: StateFlow<OverlayControlMode> = _overlayControlMode.asStateFlow()

    private val _screenSaverEnabled = MutableStateFlow(defaults.screenSaverEnabled.toBoolean())
    override val screenSaverEnabled: StateFlow<Boolean> = _screenSaverEnabled.asStateFlow()

    private val _autoCleanLogs = MutableStateFlow(defaults.autoCleanLogs.toBoolean())
    override val autoCleanLogs: StateFlow<Boolean> = _autoCleanLogs.asStateFlow()

    private val _useGithubMirror = MutableStateFlow(defaults.useGithubMirror.toBoolean())
    val useGithubMirror: StateFlow<Boolean> = _useGithubMirror.asStateFlow()

    init {
        // 一处 collect 铺开到各字段，而不是每个字段各起一条 stateIn：
        // 那样 loaded 置位与各字段拿到首值是两件并发的事，早读的人仍可能读到默认值
        scope.launch {
            settings.collect { s ->
                _startupBackend.value = parseBackend(s.startupBackend)
                _skipShizukuCheck.value = s.skipShizukuCheck.toBoolean()
                _shizukuLaunchPackage.value = s.shizukuLaunchPackage
                _runMode.value = parseRunMode(s.runMode)
                _overlayControlMode.value = parseOverlayMode(s.overlayControlMode)
                _screenSaverEnabled.value = s.screenSaverEnabled.toBoolean()
                _autoCleanLogs.value = s.autoCleanLogs.toBoolean()
                _useGithubMirror.value = s.useGithubMirror.toBoolean()
                // 必须是最后一行：置位即宣告上面全部就位
                _loaded.value = true
            }
        }
    }

    suspend fun setStartupBackend(backend: RemoteBackend) = with(AppSettingsSchema) {
        context.dataStore.edit { it[startupBackend] = backend.name }
    }

    suspend fun setSkipShizukuCheck(skip: Boolean) = with(AppSettingsSchema) {
        context.dataStore.edit { it[skipShizukuCheck] = skip.toString() }
    }

    suspend fun setShizukuLaunchPackage(packageName: String) = with(AppSettingsSchema) {
        context.dataStore.edit { it[shizukuLaunchPackage] = packageName }
    }

    override suspend fun setRunMode(mode: RunMode): Unit = with(AppSettingsSchema) {
        context.dataStore.edit { it[runMode] = mode.name }
    }

    override suspend fun setOverlayControlMode(mode: OverlayControlMode): Unit = with(AppSettingsSchema) {
        context.dataStore.edit { it[overlayControlMode] = mode.name }
    }

    override suspend fun setScreenSaverEnabled(enabled: Boolean): Unit = with(AppSettingsSchema) {
        context.dataStore.edit { it[screenSaverEnabled] = enabled.toString() }
    }

    override suspend fun setAutoCleanLogs(enabled: Boolean): Unit = with(AppSettingsSchema) {
        context.dataStore.edit { it[autoCleanLogs] = enabled.toString() }
    }

    suspend fun setUseGithubMirror(enabled: Boolean): Unit = with(AppSettingsSchema) {
        context.dataStore.edit { it[useGithubMirror] = enabled.toString() }
    }

    /** 盘上是历史遗留或手改的非法值时回落默认，不让设置读取本身抛异常 */
    private fun parseBackend(raw: String): RemoteBackend =
        runCatching { RemoteBackend.valueOf(raw) }.getOrDefault(RemoteBackend.SHIZUKU)

    private fun parseRunMode(raw: String): RunMode =
        runCatching { RunMode.valueOf(raw) }.getOrDefault(RunMode.BACKGROUND)

    private fun parseOverlayMode(raw: String): OverlayControlMode =
        runCatching { OverlayControlMode.valueOf(raw) }.getOrDefault(OverlayControlMode.FLOAT_BALL)
}
