package com.aliothmoon.azurpilot.ui.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aliothmoon.azurpilot.log.AzurPilotLogSource
import org.koin.compose.koinInject

/**
 * 一份 AzurPilot 按天日志的正文（二级页面），与启动器日志共用尾部加载查看器
 */
@Composable
fun AzurPilotLogDetailScreen(
    fileName: String,
    onBack: () -> Unit,
    source: AzurPilotLogSource = koinInject(),
) {
    val file = remember(fileName) { source.dailyFile(fileName) }
    LogTailScreen(title = fileName, file = file, onBack = onBack)
}
