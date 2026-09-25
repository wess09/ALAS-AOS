package com.azurpilot.ghio.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.log.AzurPilotDailyLogInfo
import com.azurpilot.ghio.log.AzurPilotErrorDirInfo
import com.azurpilot.ghio.log.AzurPilotLogViewModel
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AppCardSurface
import org.koin.androidx.compose.koinViewModel

/**
 * AzurPilot 日志列表（二级页面）：直读内部存储的 `rootfs/opt/run/log`，不走 wrapper HTTP
 *
 * 两个分区：「错误记录」是 AzurPilot 出错时落的时间戳现场（log.txt + 截图），
 * 「按天日志」是整天 append 的 txt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzurPilotLogScreen(
    onBack: () -> Unit,
    onOpenDaily: (fileName: String) -> Unit,
    onOpenError: (dirName: String) -> Unit,
    viewModel: AzurPilotLogViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.azurpilot_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val empty = state.errorDirs.isEmpty() && state.dailyLogs.isEmpty()
        if (empty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(AppTokens.Spacing.lg),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = stringResource(
                        if (state.loading) R.string.common_loading else R.string.azurpilot_log_empty,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(AppTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        ) {
            if (state.errorDirs.isNotEmpty()) {
                item(key = "header_errors") {
                    AzurPilotLogSectionHeader(stringResource(R.string.azurpilot_log_section_errors))
                }
                items(state.errorDirs, key = { "error_${it.name}" }) { dir ->
                    AzurPilotErrorDirRow(dir = dir, onClick = { onOpenError(dir.name) })
                }
            }
            if (state.dailyLogs.isNotEmpty()) {
                item(key = "header_daily") {
                    AzurPilotLogSectionHeader(stringResource(R.string.azurpilot_log_section_daily))
                }
                items(state.dailyLogs, key = { "daily_${it.name}" }) { file ->
                    AzurPilotDailyLogRow(file = file, onClick = { onOpenDaily(file.name) })
                }
            }
        }
    }
}

@Composable
private fun AzurPilotLogSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppTokens.Spacing.sm),
    )
}

@Composable
private fun AzurPilotErrorDirRow(dir: AzurPilotErrorDirInfo, onClick: () -> Unit) {
    AzurPilotLogRow(
        title = logTimestamp(dir.timestamp),
        subtitle = stringResource(R.string.azurpilot_log_error_meta, dir.fileCount),
        onClick = onClick,
    )
}

@Composable
private fun AzurPilotDailyLogRow(file: AzurPilotDailyLogInfo, onClick: () -> Unit) {
    AzurPilotLogRow(
        title = file.name,
        subtitle = stringResource(
            R.string.app_log_meta,
            formatFileSize(file.sizeBytes),
            logTimestamp(file.lastModified),
        ),
        onClick = onClick,
    )
}

@Composable
private fun AzurPilotLogRow(title: String, subtitle: String, onClick: () -> Unit) {
    AppCardSurface(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
