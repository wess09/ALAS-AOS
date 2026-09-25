package com.aliothmoon.azurpilot.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
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
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.proot.AzurPilotRunState
import com.aliothmoon.azurpilot.service.HostSnapshot
import com.aliothmoon.azurpilot.theme.AppTokens
import com.aliothmoon.azurpilot.ui.components.AzurPilotControlPanel

/**
 * 悬浮控制面板：面板壳（标题/锁定/关闭 + 回 App/环境启停），
 * 状态行、日志板与调度器启停复用共享组合件 [AzurPilotControlPanel]
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        ) {
            PanelHeader(isLocked, onLockToggle, onClose)
            AzurPilotControlPanel(
                snapshot = snapshot,
                run = run,
                onRunStart = onRunStart,
                onRunStop = onRunStop,
                onToolStart = onToolStart,
                onToolStop = onToolStop,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
            ) {
                Button(
                    onClick = onBackToApp,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Home,
                        contentDescription = null,
                        Modifier.size(AppTokens.IconSize.sm)
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
