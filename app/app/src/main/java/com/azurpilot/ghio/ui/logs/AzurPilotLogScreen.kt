package com.azurpilot.ghio.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                scrollBehavior = scrollBehavior,
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
            contentPadding = PaddingValues(vertical = AppTokens.Spacing.sm),
        ) {
            if (state.errorDirs.isNotEmpty()) {
                item(key = "header_errors") {
                    AzurPilotLogSectionHeader(stringResource(R.string.azurpilot_log_section_errors))
                }
                // 同一批记录是一条列表，不是一堆并列的独立卡片：逐行套卡会把
                // 「它们属于同一处」这层意思抹掉，还多出一圈没有信息量的框
                itemsIndexed(state.errorDirs, key = { _, dir -> "error_${dir.name}" }) { index, dir ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = AppTokens.Spacing.lg))
                    }
                    AzurPilotErrorDirRow(dir = dir, onClick = { onOpenError(dir.name) })
                }
            }
            if (state.dailyLogs.isNotEmpty()) {
                item(key = "header_daily") {
                    AzurPilotLogSectionHeader(stringResource(R.string.azurpilot_log_section_daily))
                }
                itemsIndexed(state.dailyLogs, key = { _, file -> "daily_${file.name}" }) { index, file ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = AppTokens.Spacing.lg))
                    }
                    AzurPilotDailyLogRow(file = file, onClick = { onOpenDaily(file.name) })
                }
            }
        }
    }
}

/** 分区标题：与列表行的首列文字对齐，读的人才能把标题和它下面那批连起来 */
@Composable
private fun AzurPilotLogSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = AppTokens.Spacing.lg,
            end = AppTokens.Spacing.lg,
            top = AppTokens.Spacing.md,
            bottom = AppTokens.Spacing.xs,
        ),
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        // 尾箭头：这一行通向别处，与设置页的导航行同一套提示
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppTokens.IconSize.md),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
