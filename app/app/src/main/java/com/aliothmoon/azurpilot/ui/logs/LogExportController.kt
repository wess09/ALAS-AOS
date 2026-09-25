package com.aliothmoon.azurpilot.ui.logs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.log.LogExportKind
import com.aliothmoon.azurpilot.log.LogExportService
import com.aliothmoon.azurpilot.theme.AppTokens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 导出日志：sheet 显隐、SAF 选位置、分享 Intent、重复点击保护
 *
 * [kind] 为空 = 不显示。两类导出（AzurPilot / 启动器）共用这一个 sheet，标题与产物按类型走
 *
 * 必须无条件挂在调用方的组合顶层——`rememberLauncherForActivityResult` 的注册要稳定，
 * 跟着 sheet 的显隐一起装卸的话，回调回来时注册已经没了
 *
 * 反馈经 [onMessage] 交给调用方：提示该显示在哪（snackbar / toast）由承载它的那一层决定
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogExportController(
    kind: LogExportKind?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    service: LogExportService = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // SAF 回调回来时 sheet 已关、kind 已置空，发起那刻的类型得另存一份
    var pendingKind by remember { mutableStateOf(LogExportKind.LAUNCHER) }

    val runningText = stringResource(R.string.log_export_running)
    val failedText = stringResource(R.string.log_export_failed)

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_ZIP),
    ) { uri ->
        if (uri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        val targetKind = pendingKind
        scope.launch {
            val name = service.exportTo(targetKind, uri)
            busy = false
            onMessage(name?.let { context.getString(R.string.log_export_saved, it) } ?: failedText)
        }
    }

    if (kind == null) return

    val title = stringResource(
        when (kind) {
            LogExportKind.AZURPILOT -> R.string.log_export_azurpilot_title
            LogExportKind.LAUNCHER -> R.string.log_export_launcher_title
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = AppTokens.Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = AppTokens.Spacing.lg,
                    end = AppTokens.Spacing.lg,
                    bottom = AppTokens.Spacing.sm,
                ),
            )
            ExportAction(
                label = stringResource(R.string.log_export_share),
                icon = Icons.Outlined.Share,
                onClick = {
                    onDismiss()
                    if (busy) return@ExportAction
                    busy = true
                    onMessage(runningText)
                    scope.launch {
                        val intent = service.shareIntent(kind)
                        busy = false
                        if (intent == null) onMessage(failedText)
                        else context.startActivity(Intent.createChooser(intent, title))
                    }
                },
            )
            ExportAction(
                label = stringResource(R.string.log_export_save),
                icon = Icons.Outlined.Save,
                onClick = {
                    onDismiss()
                    if (busy) return@ExportAction
                    busy = true
                    onMessage(runningText)
                    pendingKind = kind
                    // 打包推迟到用户选完位置：选一半退出去就白打了
                    saveLauncher.launch(service.suggestedFileName(kind))
                },
            )
        }
    }
}

@Composable
private fun ExportAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private const val MIME_ZIP = "application/zip"
