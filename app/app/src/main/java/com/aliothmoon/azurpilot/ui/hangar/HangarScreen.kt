package com.aliothmoon.azurpilot.ui.hangar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.constant.DefaultDisplayConfig
import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.proot.AzurPilotRunState
import com.aliothmoon.azurpilot.service.HostState
import com.aliothmoon.azurpilot.theme.AppTokens
import com.aliothmoon.azurpilot.ui.components.AzurPilotControlPanel
import com.aliothmoon.azurpilot.ui.components.AppCard
import com.aliothmoon.azurpilot.ui.components.ToolSlotButton
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 挂机 tab：虚拟屏实时画面 + 运行配置选择 + AzurPilot 控制面板
 *
 * 画面走 native bridge_preview 通道（AIDL setMonitorSurface，零拷贝）；
 * 预览面本体是 AppRoot 持有的 movableContent（见 HangarPreview.kt），点卡片进全屏时搬走，
 * 页面不可见即摘面，不给看不到的画面白烧帧。
 * 配置面（改任务参数）仍在 AzurPilot WebUI tab，本页只选「跑哪个配置」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(
    active: Boolean,
    previewContent: (@Composable () -> Unit)?,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    hostState: HostState = koinInject(),
    runController: AzurPilotRunController = koinInject(),
) {
    val snapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val run by runController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 本页可见且特权连接就绪时自动补一次「开始」链路建虚拟屏（HostState 内幂等）
    LaunchedEffect(active, snapshot.privilegedConnected) {
        if (active && snapshot.privilegedConnected) {
            hostState.ensureEnvironmentStarted()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_hangar)) },
            // AppRoot 的 Scaffold 已吃掉状态栏顶部 inset，这里不能再加一次
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    start = AppTokens.Spacing.lg,
                    end = AppTokens.Spacing.lg,
                    bottom = AppTokens.Spacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        ) {
            VdPreview(
                envUp = snapshot.environmentUp,
                onStartEnv = { scope.launch { hostState.ensureEnvironmentStarted() } },
                content = previewContent,
                onEnterFullscreen = onEnterFullscreen,
                modifier = Modifier.fillMaxWidth(),
            )
            ConfigToolRow(
                run = run,
                onSelect = runController::selectConfig,
                onToolStart = { runController.startTool(it) },
                onToolStop = { runController.stopTool() },
            )
            AzurPilotControlPanel(
                snapshot = snapshot,
                run = run,
                onRunStart = { runController.startRunner() },
                onRunStop = { runController.stopRunner() },
                onToolStart = { runController.startTool(it) },
                onToolStop = { runController.stopTool() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                showTools = false,
            )
        }
    }
}

/**
 * 虚拟屏画面卡：环境在跑显示实时预览，没跑给占位 + 一键拉起
 *
 * 内嵌画面只响应「单击进全屏」，不转发触摸（防误触）；
 * [content] 为 null 表示画面已搬去全屏宿主，显示占位
 */
@Composable
private fun VdPreview(
    envUp: Boolean,
    onStartEnv: () -> Unit,
    content: (@Composable () -> Unit)?,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(DefaultDisplayConfig.WIDTH.toFloat() / DefaultDisplayConfig.HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            envUp && content != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onEnterFullscreen),
                ) {
                    content()
                }
            }

            envUp -> PreviewPlaceholder(
                textRes = R.string.hangar_preview_moved,
            )

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                ) {
                    PreviewPlaceholder(textRes = R.string.hangar_env_down)
                    Button(onClick = onStartEnv) {
                        Text(stringResource(R.string.hangar_env_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPlaceholder(textRes: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.OndemandVideo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = AppTokens.Alpha.disabledContent),
            modifier = Modifier.size(AppTokens.IconSize.lg),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = AppTokens.Alpha.disabledContent),
        )
    }
}

/**
 * 运行配置 + 工具 双模块行：左卡上「运行配置」下拉，右列两个工具槽位按钮上下排。
 * 行高取左右最大固有高（IntrinsicSize），右列两按钮均分填满，大小随模块自适应。
 * 某工具在跑时对应槽位变「停止」（槽位即归属）；启停互斥归 wrapper，可用性只看可达/忙碌。
 * 调度器在跑时锁配置切换——生效配置以 /status 回报的 runningConfig 为准，选择下次启动生效
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigToolRow(
    run: AzurPilotRunState,
    onSelect: (String) -> Unit,
    onToolStart: (String) -> Unit,
    onToolStop: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val switchable = !run.runnerAlive && run.configs.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
    ) {
        AppCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (switchable) expanded = it },
            ) {
                OutlinedTextField(
                    value = if (run.runnerAlive) {
                        run.runningConfig ?: run.selectedConfig
                    } else {
                        run.selectedConfig
                    },
                    onValueChange = {},
                    readOnly = true,
                    enabled = switchable,
                    label = { Text(stringResource(R.string.hangar_config_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    run.configs.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onSelect(name)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
        ) {
            ToolSlotButton(
                labelRes = R.string.hangar_tool_semi_auto,
                running = run.toolAlive && run.toolName == AzurPilotRunState.TOOL_SEMI_AUTO,
                onStart = { onToolStart(AzurPilotRunState.TOOL_SEMI_AUTO) },
                onStop = onToolStop,
                enabled = run.reachable && !run.busy,
                modifier = Modifier.weight(1f),
            )
            ToolSlotButton(
                labelRes = R.string.hangar_tool_event_story,
                running = run.toolAlive && run.toolName == AzurPilotRunState.TOOL_EVENT_STORY,
                onStart = { onToolStart(AzurPilotRunState.TOOL_EVENT_STORY) },
                onStop = onToolStop,
                enabled = run.reachable && !run.busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
