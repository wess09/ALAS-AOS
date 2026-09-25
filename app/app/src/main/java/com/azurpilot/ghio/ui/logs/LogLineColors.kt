package com.azurpilot.ghio.ui.logs

import androidx.compose.ui.graphics.Color

/**
 * 日志行的级别上色，查看器共用一个判据，兼容两种落盘格式：
 *
 * - app.log（`AppLogWriter`）：`[时间] E tag: 正文`——单字母级别。
 *   不照抄 参考实现 的 `contains("[ERROR]")`：它的写入器打全词，我们打单字母
 * - AzurPilot 按天日志：`YYYY-MM-DD HH:MM:SS.mmm | LEVEL | msg`——全词级别
 *
 * 堆栈续行与 session.log 的 `[host]`/`[proot-out]` 行都取不到级别，跟默认色
 */
internal fun logLineColor(line: String, error: Color, warning: Color): Color {
    appLogLevelColor(line, error, warning).takeIf { it != Color.Unspecified }?.let { return it }
    return when {
        line.contains(ERROR_MARKER) -> error
        line.contains(WARNING_MARKER) -> warning
        else -> Color.Unspecified
    }
}

private fun appLogLevelColor(line: String, error: Color, warning: Color): Color {
    val marker = line.indexOf(APP_LEVEL_MARKER)
    if (marker < 0 || line.length <= marker + APP_LEVEL_MARKER.length + 1) return Color.Unspecified
    if (line[marker + APP_LEVEL_MARKER.length + 1] != ' ') return Color.Unspecified
    return when (line[marker + APP_LEVEL_MARKER.length]) {
        'E', 'A' -> error
        'W' -> warning
        else -> Color.Unspecified
    }
}

private const val APP_LEVEL_MARKER = "] "
private const val ERROR_MARKER = "| ERROR |"
private const val WARNING_MARKER = "| WARNING |"
