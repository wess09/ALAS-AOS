package com.azurpilot.ghio.settings

import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.domain.ThemeMode
import com.azurpilot.ghio.privileged.RemoteAccessState

/**
 * 设置页聚合态
 *
 * 后端的写走 PermissionGateway.setBackend（带 unbind 副作用），不直接落 AppSettings——
 * 跳过 unbind 会连着错的特权进程
 */
data class SettingsUiState(
    val remoteAccess: RemoteAccessState = RemoteAccessState(),
    val themeMode: ThemeMode = ThemeMode.System,
    val autoCleanLogs: Boolean = true,
)

sealed interface SettingsIntent {
    /** 切换 Shizuku / Root 后端；落到 AppSettings.startupBackend 并断开当前特权进程 */
    data class SetBackend(val backend: RemoteBackend) : SettingsIntent

    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent

    /** null 恢复跟随系统；切换后 Activity 重建 */
    data class SetLanguage(val tag: String?) : SettingsIntent

    data class SetAutoCleanLogs(val enabled: Boolean) : SettingsIntent
}
