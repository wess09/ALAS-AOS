package com.aliothmoon.azurpilot.log

import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 冷启动自动清理：7 天前的 AzurPilot 日志静默删除（session.log 截尾在 `ProotHost.cleanupStale`）
 *
 * 固定 7 天无配置项（开关是 `AppSettings.autoCleanLogs`，调用方判）：
 * - 按天日志 txt：按文件名 `yyyy-MM-dd` 前缀定日期，名字对不上的不动；
 * - `log/error/<毫秒时间戳>/`：按时间戳定日期，过期整文件夹删。
 *
 * crash/（10 份上限）与 app.log（4MB×5 滚动）自带天花板，不归这里管
 */
class LogCleaner(
    private val logSource: AzurPilotLogSource,
) {

    /** 入口只许包 runCatching 的地方调；汇总必须 Timber.w 才在 release 落盘 */
    fun cleanOutdated() {
        val cutoffDay = LocalDate.now().minusDays(KEEP_DAYS)
        val cutoffMillis = cutoffDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var deleted = 0
        var freedBytes = 0L

        logSource.dailyLogs().forEach { file ->
            val date = datePrefixOf(file.name) ?: return@forEach
            if (date.isBefore(cutoffDay)) {
                val size = file.length()
                if (file.delete()) {
                    deleted++
                    freedBytes += size
                }
            }
        }

        logSource.errorDirs().forEach { dir ->
            val ts = dir.name.toLongOrNull() ?: return@forEach
            if (ts < cutoffMillis) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (dir.deleteRecursively()) {
                    deleted++
                    freedBytes += size
                }
            }
        }

        Timber.w("LogCleaner: 过期日志清理完成，删除 %d 项，释放 %s", deleted, formatBytes(freedBytes))
    }

    private fun datePrefixOf(name: String): LocalDate? = runCatching {
        if (name.length < DATE_PREFIX_LENGTH) return null
        LocalDate.parse(name.substring(0, DATE_PREFIX_LENGTH), DATE_FORMAT)
    }.getOrNull()

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / 1024 / 1024} MB"
    }

    private companion object {
        const val KEEP_DAYS = 7L
        const val DATE_PREFIX_LENGTH = 10
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
