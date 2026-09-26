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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.log.AppLogFileInfo
import com.azurpilot.ghio.log.AppLogIntent
import com.azurpilot.ghio.log.AppLogViewModel
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.components.AppPromptDialog
import org.koin.androidx.compose.koinViewModel

/**
 * 启动器日志的文件列表（二级页面）：`log/` 目录递归（app.log 系列 / session.log / crash）
 *
 * 与运行历史同一形态的两级页；版面对齐 参考实现 的 `ErrorLogView`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLogScreen(
    onBack: () -> Unit,
    onOpen: (fileName: String) -> Unit,
    viewModel: AppLogViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AppPromptDialog(
            title = stringResource(R.string.app_log_clear_title),
            message = stringResource(R.string.app_log_clear_message),
            icon = Icons.Outlined.DeleteOutline,
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                viewModel.onIntent(AppLogIntent.ClearAll)
                confirmClear = false
            },
            onDismissRequest = { confirmClear = false },
            dismissOnOutsideClick = true,
            destructive = true,
        )
    }

    // M3 的顶栏滚动行为：列表滚起来时顶栏换成容器色并抬起，与主 tab 页同一套
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state.files.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(
                                text = stringResource(R.string.app_log_clear),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (state.files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(AppTokens.Spacing.lg),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = stringResource(
                        if (state.loading) R.string.common_loading else R.string.app_log_empty,
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
            // 同一批日志文件是一条列表，不是一堆并列的独立卡片：
            // 逐行套卡会把「它们属于同一处」这层意思抹掉，还多出一圈没有信息量的框
            itemsIndexed(state.files, key = { _, file -> file.name }) { index, file ->
                if (index > 0) {
                    // 分隔线向内缩到文字首列，是 M3 列表分隔线的取法
                    HorizontalDivider(modifier = Modifier.padding(start = AppTokens.Spacing.lg))
                }
                AppLogFileRow(file = file, onClick = { onOpen(file.name) })
            }
        }
    }
}

@Composable
private fun AppLogFileRow(file: AppLogFileInfo, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.app_log_meta,
                    formatFileSize(file.sizeBytes),
                    logTimestamp(file.lastModified),
                ),
            )
        },
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
