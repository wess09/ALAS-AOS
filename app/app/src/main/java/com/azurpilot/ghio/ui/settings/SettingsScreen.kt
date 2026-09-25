package com.azurpilot.ghio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.R
import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.domain.ThemeMode
import com.azurpilot.ghio.i18n.AppLocales
import com.azurpilot.ghio.settings.SettingsIntent
import com.azurpilot.ghio.settings.SettingsUiState
import com.azurpilot.ghio.theme.AppTokens
import androidx.compose.material3.Button
import com.azurpilot.ghio.proot.AzurPilotApi
import com.azurpilot.ghio.provision.RootfsProvisioner
import com.azurpilot.ghio.settings.AppSettingsManager
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.AppFieldLabel
import com.azurpilot.ghio.ui.components.AppInfoRow
import com.azurpilot.ghio.ui.components.AppLabeledControlRow
import com.azurpilot.ghio.ui.components.AppNavigationRow
import com.azurpilot.ghio.ui.components.AppSingleChoiceFlow
import com.azurpilot.ghio.update.AppUpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    // 二级页面与 SAF 都需要 Activity 宿主，导航与弹窗归 AppRoot 那一层
    onOpenAppLog: () -> Unit,
    onOpenRunnerLog: () -> Unit,
    onExportRunnerLogs: () -> Unit,
    onExportLauncherLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_settings)) },
            // AppRoot 的 Scaffold 已吃掉状态栏顶部 inset，这里不能再加一次
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AppTokens.Spacing.lg,
                    end = AppTokens.Spacing.lg,
                    top = AppTokens.Spacing.sm,
                    bottom = AppTokens.Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.lg),
        ) {
            DisplayCard(state, onIntent)
            LogCard(state, onIntent, onOpenAppLog, onOpenRunnerLog, onExportRunnerLogs, onExportLauncherLogs)
            OtherCard(state, onIntent)
            RuntimeCard()
            AboutCard()
        }
    }
}

/** 主题与语言：都只改观感，合成一张卡 */
@Composable
private fun DisplayCard(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    AppCard(title = stringResource(R.string.settings_section_display), collapsible = true) {
        AppFieldLabel(stringResource(R.string.settings_theme))
        val modes = listOf(
            ThemeMode.System to stringResource(R.string.settings_follow_system),
            ThemeMode.Light to stringResource(R.string.settings_theme_light),
            ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
        )
        AppSingleChoiceFlow(
            options = modes,
            selected = state.themeMode,
            onSelect = { onIntent(SettingsIntent.SetThemeMode(it)) },
        )
        AppFieldLabel(stringResource(R.string.settings_language))
        LanguageChoice(onIntent)
    }
}

@Composable
private fun ColumnScope.LanguageChoice(onIntent: (SettingsIntent) -> Unit) {
    // 事实来源在平台侧 per-app locale（AppLocales），不进 UserConfiguration；
    // 切换后 Activity 重建，本处在新组合中重新读取，无需观察流
    // 语言名按惯例保持本族语原文，不随界面语言翻译
    val options = listOf<Pair<String?, String>>(
        null to stringResource(R.string.settings_follow_system),
        "zh-CN" to "简体中文",
        "en" to "English",
    )
    // 选中态用本地 state 立即回显：切到效果相同的档位（如 跟随系统(中文) ↔ 简体中文）
    // 不触发 Activity 重建，重新读 AppLocales 的时机不会到来
    var selectedTag by remember {
        mutableStateOf(
            when (val tag = AppLocales.currentTag()) {
                null -> null
                else -> if (tag.startsWith("zh")) "zh-CN" else "en"
            },
        )
    }
    AppSingleChoiceFlow(
        options = options,
        selected = selectedTag,
        // 重复点选当前档位不发 Intent：避免无意义的 Activity 重建闪屏
        onSelect = { tag ->
            if (tag != selectedTag) {
                selectedTag = tag
                onIntent(SettingsIntent.SetLanguage(tag))
            }
        },
    )
    Text(
        text = stringResource(R.string.settings_language_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 日志区：两个查看入口 + 两条导出 + 自动清理开关
 *
 * 前四项都是「离开这一页」，只有自动清理是就地开关；关闭走确认弹窗（占空间警告）
 */
@Composable
private fun LogCard(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onOpenAppLog: () -> Unit,
    onOpenRunnerLog: () -> Unit,
    onExportRunnerLogs: () -> Unit,
    onExportLauncherLogs: () -> Unit,
) {
    var showDisableConfirm by remember { mutableStateOf(false) }
    AppCard(title = stringResource(R.string.settings_section_log), collapsible = true) {
        AppNavigationRow(
            label = stringResource(R.string.app_log_title),
            description = stringResource(R.string.settings_log_launcher_desc),
            onClick = onOpenAppLog,
        )
        AppNavigationRow(
            label = stringResource(R.string.azurpilot_log_title),
            description = stringResource(R.string.settings_log_azurpilot_desc),
            onClick = onOpenRunnerLog,
        )
        AppNavigationRow(
            label = stringResource(R.string.log_export_azurpilot_title),
            description = stringResource(R.string.settings_log_export_azurpilot_desc),
            onClick = onExportRunnerLogs,
        )
        AppNavigationRow(
            label = stringResource(R.string.log_export_launcher_title),
            description = stringResource(R.string.settings_log_export_launcher_desc),
            onClick = onExportLauncherLogs,
        )
        // 开启直接落盘；关闭先弹确认：关掉之后过期日志只增不减
        AppLabeledControlRow(
            label = stringResource(R.string.settings_auto_clean_logs),
            trailing = {
                Switch(
                    checked = state.autoCleanLogs,
                    onCheckedChange = { enabled ->
                        if (enabled) onIntent(SettingsIntent.SetAutoCleanLogs(true))
                        else showDisableConfirm = true
                    },
                )
            },
        )
        Text(
            text = stringResource(R.string.settings_auto_clean_logs_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            title = { Text(stringResource(R.string.dialog_disable_auto_clean_title)) },
            text = { Text(stringResource(R.string.dialog_disable_auto_clean_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisableConfirm = false
                    onIntent(SettingsIntent.SetAutoCleanLogs(false))
                }) { Text(stringResource(R.string.dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

/** 启动模式（特权后端）：「跑起来之前得先定」的环境选项 */
@Composable
private fun OtherCard(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    AppCard(title = stringResource(R.string.settings_section_other), collapsible = true) {
        AppFieldLabel(stringResource(R.string.permission_backend))
        AppSingleChoiceFlow(
            // 只列后端名，不展示「可用/不可用」——选哪个都行，可用性交给连接流程判
            options = RemoteBackend.entries.map { it to it.display },
            selected = state.remoteAccess.configuredBackend,
            onSelect = { onIntent(SettingsIntent.SetBackend(it)) },
        )
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    AppCard(title = stringResource(R.string.settings_about), collapsible = true) {
        Text(
            text = stringResource(R.string.settings_about_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        AppInfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
        AppInfoRow(stringResource(R.string.settings_build), BuildConfig.VERSION_CODE.toString())
        AppNavigationRow(
            label = stringResource(R.string.settings_about_license),
            description = "AGPL-3.0",
            onClick = { uriHandler.openUri("https://github.com/wess09/AzurPilot-for-Android/blob/main/LICENSE") },
        )
        AppNavigationRow(
            label = stringResource(R.string.settings_about_repository),
            description = "github.com/wess09/AzurPilot-for-Android",
            onClick = { uriHandler.openUri("https://github.com/wess09/AzurPilot-for-Android") },
        )
        AppFieldLabel(stringResource(R.string.settings_about_components))
        val components = listOf(
            Triple("AzurPilot", "GPL-3.0", "https://github.com/wess09/AzurPilot"),
            Triple("PRoot", "GPL-2.0", "https://github.com/proot-me/proot"),
            Triple("Shizuku", "Apache-2.0", "https://github.com/RikkaApps/Shizuku"),
            Triple("libsu", "Apache-2.0", "https://github.com/topjohnwu/libsu"),
            Triple("Koin", "Apache-2.0", "https://github.com/InsertKoinIO/koin"),
            Triple("Timber", "Apache-2.0", "https://github.com/JakeWharton/timber"),
            Triple("Apache Commons Compress", "Apache-2.0", "https://github.com/apache/commons-compress"),
        )
        components.forEach { (name, license, url) ->
            AppNavigationRow(label = name, description = license, onClick = { uriHandler.openUri(url) })
        }
        AppNavigationRow(
            label = stringResource(R.string.settings_about_all_dependencies),
            onClick = { uriHandler.openUri("https://github.com/wess09/AzurPilot-for-Android/blob/main/app/gradle/libs.versions.toml") },
        )
    }
}

/**
 * 运行时卡：启动时由宿主更新完整 rootfs，上游 git 热更仍由 Android 关闭。
 */
@Composable
private fun RuntimeCard(
    api: AzurPilotApi = koinInject(),
    provisioner: RootfsProvisioner = koinInject(),
    updateManager: AppUpdateManager = koinInject(),
    settings: AppSettingsManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val apiState by api.state.collectAsStateWithLifecycle()
    val provisionState by provisioner.state.collectAsStateWithLifecycle()
    val runtimeCheck by provisioner.updateCheck.collectAsStateWithLifecycle()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val useGithubMirror by settings.useGithubMirror.collectAsStateWithLifecycle()
    val installedVersion = provisioner.installedVersion()
    AppCard(title = stringResource(R.string.settings_runtime), collapsible = true) {
        AppInfoRow(
            stringResource(R.string.settings_runtime_commit),
            apiState.runtimeCommit?.take(12)
                ?: installedVersion?.substringBefore('-')
                ?: stringResource(R.string.settings_runtime_unknown),
        )
        if (installedVersion != null) {
            AppInfoRow(stringResource(R.string.settings_runtime_installed), installedVersion)
        }
        runtimeCheck.latestVersion?.let { latest ->
            AppInfoRow(stringResource(R.string.settings_runtime_latest), latest)
        }
        if (runtimeCheck.checked) {
            val status = when {
                runtimeCheck.error != null -> stringResource(R.string.settings_runtime_check_failed, runtimeCheck.error!!)
                runtimeCheck.latestVersion == installedVersion -> stringResource(R.string.settings_runtime_current)
                runtimeCheck.latestVersion != null -> stringResource(R.string.settings_runtime_new_version)
                else -> stringResource(R.string.settings_runtime_unknown)
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.settings_runtime_managed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppLabeledControlRow(
            label = stringResource(R.string.settings_github_mirror),
            trailing = {
                Switch(
                    checked = useGithubMirror,
                    onCheckedChange = { enabled -> scope.launch { settings.setUseGithubMirror(enabled) } },
                )
            },
        )
        Text(
            text = stringResource(R.string.settings_github_mirror_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = provisioner::checkForUpdates,
            enabled = !runtimeCheck.checking && provisionState is com.azurpilot.ghio.provision.ProvisionState.Ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (runtimeCheck.checking) R.string.settings_runtime_checking else R.string.settings_runtime_check))
        }
        Button(
            onClick = updateManager::check,
            enabled = !updateState.downloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_app_check))
        }
    }
}
