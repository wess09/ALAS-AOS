package com.azurpilot.ghio.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azurpilot.ghio.config.UserConfigurationStore
import com.azurpilot.ghio.i18n.AppLocales
import com.azurpilot.ghio.privileged.PermissionGateway
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页的 Activity 作用域会话
 *
 * 后端选择是 app 设置而非运行配置；主题/语言/日志清理都是只改观感与维护行为的外壳设置
 */
class SettingsViewModel(
    private val permissionGateway: PermissionGateway,
    private val appSettings: AppSettingsGateway,
    private val userConfigurationStore: UserConfigurationStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        permissionGateway.state,
        userConfigurationStore.data,
        appSettings.autoCleanLogs,
    ) { remoteAccess, userConfig, autoCleanLogs ->
        SettingsUiState(
            remoteAccess = remoteAccess,
            themeMode = userConfig.themeMode,
            autoCleanLogs = autoCleanLogs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetBackend -> viewModelScope.launch {
                permissionGateway.setBackend(intent.backend)
            }

            is SettingsIntent.SetThemeMode -> viewModelScope.launch {
                userConfigurationStore.update { it.copy(themeMode = intent.mode) }
            }

            is SettingsIntent.SetLanguage -> AppLocales.apply(intent.tag)

            is SettingsIntent.SetAutoCleanLogs -> viewModelScope.launch {
                appSettings.setAutoCleanLogs(intent.enabled)
            }
        }
    }
}
