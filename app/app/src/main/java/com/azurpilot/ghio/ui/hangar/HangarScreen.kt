package com.azurpilot.ghio.ui.hangar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.proot.AzurPilotRunState
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AzurPilotControlPanel
import com.azurpilot.ghio.ui.components.AzurPilotLogBoard
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.ToolSlotButton
import org.koin.compose.koinInject

/** 日志区固定高度：约 21 行可见，其余靠板内滚动；再往下由整页滚动接手 */
private val LogBoardHeight = 280.dp

/**
 * 主页 tab：运行配置选择 + 工具槽 + AzurPilot 控制面板 + 运行日志
 *
 * 整页可滚（矮屏 / 分屏 / 横屏下按钮不会被裁）。虚屏实时画面已从本页移除；
 * 环境仍由 [HostState.ensureEnvironmentStarted] 在本页可见时自动拉起，与画面无关。
 * 配置面（改任务参数）仍在 AzurPilot WebUI tab，本页只选「跑哪个配置」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarScreen(
    active: Boolean,
    modifier: Modifier = Modifier,
    hostState: HostState = koinInject(),
    runController: AzurPilotRunController = koinInject(),
) {
    val snapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val run by runController.state.collectAsStateWithLifecycle()

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
                // 内容进了 scrollable Column 就再用不得 weight——日志区因此取固定高，
                // 两者合起来保证矮屏能滚到底、开始按钮不会被裁
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AppTokens.Spacing.lg,
                    end = AppTokens.Spacing.lg,
                    bottom = AppTokens.Spacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        ) {
            ConfigToolRow(
                run = run,
                onSelect = runController::selectConfig,
                onToolStart = { runController.startTool(it) },
                onToolStop = { runController.stopTool() },
            )
            // 面板本身带上「开始 / 停止」，日志改由本页在按钮下方单独渲染
            AzurPilotControlPanel(
                snapshot = snapshot,
                run = run,
                onRunStart = { runController.startRunner() },
                onRunStop = { runController.stopRunner() },
                onToolStart = { runController.startTool(it) },
                onToolStop = { runController.stopTool() },
                modifier = Modifier.fillMaxWidth(),
                showTools = false,
                // 主页带上 /api/v1/ws 的调度总览与自启开关
                showGateway = true,
                showLog = false,
            )
            AzurPilotLogBoard(
                lines = run.logTail,
                linesCount = run.logLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LogBoardHeight),
            )
        }
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
