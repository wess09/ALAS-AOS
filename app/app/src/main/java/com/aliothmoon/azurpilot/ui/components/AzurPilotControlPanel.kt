package com.aliothmoon.azurpilot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.constant.DefaultDisplayConfig
import com.aliothmoon.azurpilot.proot.AzurPilotRunState
import com.aliothmoon.azurpilot.proot.ProotHost
import com.aliothmoon.azurpilot.proot.ProotPhase
import com.aliothmoon.azurpilot.service.HostSnapshot
import com.aliothmoon.azurpilot.theme.AppTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

/**
 * AzurPilot 控制面板（共享组合件）：环境/调度器状态行 + 日志板 + 调度器启停
 *
 * 悬浮窗（OverlayPanel）与挂机页（HangarScreen）共用同一份。
 * 调度器控制面只此一处（wrapper 薄 HTTP）；WebUI 里的启停按钮已被锁定补丁封死，
 * 双头同用会抢设备——别用（见 AzurPilotRunController 头注）
 * [showTools]：悬浮窗要工具区；挂机页的工具按钮已并进运行配置行（ConfigToolRow），传 false
 */
@Composable
fun AzurPilotControlPanel(
    snapshot: HostSnapshot,
    run: AzurPilotRunState,
    onRunStart: () -> Unit,
    onRunStop: () -> Unit,
    onToolStart: (String) -> Unit,
    onToolStop: () -> Unit,
    modifier: Modifier = Modifier,
    showTools: Boolean = true,
) {
    // wrapper 不可达时区分「环境准备中（带阶段明细）」与真正的「未就绪」——
    // 准备链全程 2~5 分钟且 release 日志静默，状态行是唯一可见的进度面
    val prootHost: ProotHost = koinInject()
    val proot by prootHost.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
            AzurPilotStatusRow(
                labelRes = R.string.overlay_host_privileged,
                value = stringResource(
                    if (snapshot.privilegedConnected) {
                        R.string.host_state_connected
                    } else {
                        R.string.host_state_disconnected
                    }
                ),
            )
            AzurPilotStatusRow(
                labelRes = R.string.overlay_host_bridge,
                value = stringResource(
                    if (snapshot.bridgeReachable) {
                        R.string.host_state_ok
                    } else {
                        R.string.host_state_unreachable
                    }
                ),
            )
            AzurPilotStatusRow(
                labelRes = R.string.overlay_host_display,
                value = if (snapshot.vdDisplayId != DefaultDisplayConfig.DISPLAY_NONE) {
                    "#${snapshot.vdDisplayId}"
                } else {
                    stringResource(R.string.host_state_display_none)
                },
            )
            AzurPilotStatusRow(
                labelRes = R.string.overlay_azurpilot_status,
                value = when {
                    !run.reachable && proot.phase == ProotPhase.FAILED ->
                        stringResource(R.string.overlay_azurpilot_start_failed, proot.detail)
                    !run.reachable && proot.sessionActive && proot.detail.isNotEmpty() ->
                        stringResource(R.string.overlay_azurpilot_preparing, proot.detail)
                    !run.reachable && proot.sessionActive ->
                        stringResource(R.string.overlay_azurpilot_preparing_generic)
                    !run.reachable -> stringResource(R.string.overlay_azurpilot_unreachable)
                    run.runnerAlive -> stringResource(R.string.overlay_azurpilot_running, run.pid ?: 0)
                    else -> stringResource(R.string.overlay_azurpilot_stopped)
                },
            )
        }
        AzurPilotLogBoard(
            lines = run.logTail,
            linesCount = run.logLines,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Button(
            onClick = if (run.runnerAlive) onRunStop else onRunStart,
            enabled = run.reachable && !run.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (run.runnerAlive) {
                        R.string.overlay_azurpilot_stop
                    } else {
                        R.string.overlay_azurpilot_start
                    }
                )
            )
        }
        if (showTools) {
            AzurPilotToolSection(
                run = run,
                onToolStart = onToolStart,
                onToolStop = onToolStop,
            )
        }
        Text(
            text = stringResource(R.string.overlay_azurpilot_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 半透明黑底日志板：新日志自动沉底；无内容时给占位提示 */
@Composable
private fun AzurPilotLogBoard(lines: List<String>, linesCount: Int, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(linesCount, lines.size) { scrollState.scrollTo(scrollState.maxValue) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(AppTokens.Spacing.sm),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.overlay_azurpilot_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                )
            } else {
                Text(
                    text = lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    ),
                    color = Color(0xFFDDDDDD),
                )
            }
        }
    }
}

@Composable
private fun AzurPilotStatusRow(labelRes: Int, value: String) {
    Text(
        text = stringResource(labelRes, value),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 工具区：半自动点击 / 活动剧情——与挂机页同款实心槽位按钮（[ToolSlotButton]）
 *
 * 某工具在跑时对应槽位变「停止」（槽位即归属），另一槽保持可点=换工具；
 * 与调度器的互斥（启工具自动停 runner、启 runner 自动停工具）由 wrapper 集中执行，
 * 可用性只沿用面板既有的 wrapper 可达/忙碌判断，不拿 runnerAlive/toolAlive 互相禁用
 */
@Composable
private fun AzurPilotToolSection(
    run: AzurPilotRunState,
    onToolStart: (String) -> Unit,
    onToolStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)) {
        Text(
            text = stringResource(R.string.hangar_tool_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
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

/**
 * 工具槽位按钮（共享）：与开始挂机同款实心形制；本槽工具在跑时变「停止」（槽位即归属）。
 * 挂机页（ConfigToolRow 右列）与悬浮窗工具区（[AzurPilotToolSection]）共用
 */
@Composable
fun ToolSlotButton(
    labelRes: Int,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = if (running) onStop else onStart,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (running) R.string.hangar_tool_stop else labelRes))
    }
}
