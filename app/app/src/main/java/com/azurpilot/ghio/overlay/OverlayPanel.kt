package com.azurpilot.ghio.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRunState
import com.azurpilot.ghio.service.HostSnapshot
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AzurPilotControlPanel

/**
 * 悬浮控制面板：面板壳（标题/锁定/关闭 + 回 App/环境启停），
 * 状态行、日志板与调度器启停复用共享组合件 [AzurPilotControlPanel]
 *
 * 底色取 surfaceContainerHighest 而非 surface + 1dp tonal：这面板浮在别的应用画面上，
 * 得先跟背后的内容分开；原来那档 1dp tonal + 1dp shadow 几乎看不出是个浮层。
 * 取到卡片同一档还有个副作用——内层的状态卡与日志板不再"同色压同色"，
 * 整块面板读成一个面，而不是若干发灰的嵌套块
 */
@Composable
fun OverlayPanel(
    snapshot: HostSnapshot,
    run: AzurPilotRunState,
    isLocked: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRunStart: () -> Unit,
    onRunStop: () -> Unit,
    onToolStart: (String) -> Unit,
    onToolStop: () -> Unit,
    onBackToApp: () -> Unit,
    onLockToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        // M3 的浮层档位；tonalElevation 留给"同底色分层级"，这里已经有独立 container 角色了
        shadowElevation = PanelElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        ) {
            PanelHeader(isLocked, onLockToggle, onClose)
            // 中段可滚、底部按钮钉住：面板只占屏高 60%，状态卡 + 日志 + 工具全塞进来
            // 会超；不给中段滚动，日志板会被挤成一条线，而两颗按钮滚走了就找不着了
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
            ) {
                AzurPilotControlPanel(
                    snapshot = snapshot,
                    run = run,
                    onRunStart = onRunStart,
                    onRunStop = onRunStop,
                    onToolStart = onToolStart,
                    onToolStop = onToolStop,
                    modifier = Modifier.fillMaxWidth(),
                    logBoardHeight = OverlayLogHeight,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
            ) {
                // 回 App 是"离开这一层"，环境启停是"改变这一层的状态"：
                // 两件都上实心 filled 时并列摆着谁也不是主操作，降一件到 tonal
                FilledTonalButton(
                    onClick = onBackToApp,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Home,
                        contentDescription = null,
                        Modifier.size(AppTokens.IconSize.sm),
                    )
                    Text(
                        text = stringResource(R.string.overlay_back_to_app),
                        modifier = Modifier.padding(start = AppTokens.Spacing.xs),
                    )
                }
                Button(
                    onClick = if (snapshot.environmentUp) onStop else onStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (snapshot.environmentUp) {
                                R.string.overlay_host_stop
                            } else {
                                R.string.overlay_host_start
                            }
                        )
                    )
                }
            }
        }
    }
}

/** M3 elevation level 3：浮在他人画面之上的容器 */
private val PanelElevation = 3.dp

/** 面板里日志板的固定高：中段可滚之后日志必须有自己的一档，否则会被挤成一条线 */
private val OverlayLogHeight = 120.dp

@Composable
private fun PanelHeader(
    isLocked: Boolean,
    onLockToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.overlay_panel_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        // 锁住即禁止拖拽：面板压在目标应用上，误拖会把它拽出可视区
        IconButton(onClick = { onLockToggle(!isLocked) }) {
            Icon(
                imageVector = if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = stringResource(
                    if (isLocked) R.string.overlay_unlock else R.string.overlay_lock,
                ),
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close))
        }
    }
}
