package com.aliothmoon.azurpilot.log

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 挑出要打进 zip 的文件；不删源、不写盘，纯函数好测
 *
 * 我们的日志分在两棵目录下（`log/` 与 `debug/`），有意不归拢成一棵：`debug/` 那份的路径
 * 在特权进程侧是硬解析的（见 `RemoteBootTrace`），挪了要连着改两边
 */
object LogExportCollector {

    const val EXPORT_DIR_NAME = "export"

    /** 会无限长的那几个目录只留近 7 天；其余（app.log、触发日志）本身就有上限，全带 */
    const val ROLLING_KEEP_DAYS = 7L

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    /**
     * 按次或按轮堆文件的目录
     *
     * 加新目录时记得往这里补一条，否则一年后的导出包会有上千个文件
     */
    private val ROLLING_MARKERS = listOf("/run/", "/focus/", "/logcat/", "/crash/")

    fun collect(roots: List<File>, now: Long): List<File> =
        roots.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile }
            .filter { shouldExport(it, now - ROLLING_KEEP_DAYS * MS_PER_DAY) }
            .toList()

    private fun shouldExport(file: File, rollingCutoff: Long): Boolean {
        val path = file.invariantSeparatorsPath
        // 上一次导出的 zip 不能再打进这一次，否则每导一次体积翻一倍
        if (path.contains("/$EXPORT_DIR_NAME/")) return false
        if (ROLLING_MARKERS.none { path.contains(it) }) return true
        return file.lastModified() >= rollingCutoff
    }

    /**
     * AzurPilot 日志目录（`rootfs/opt/run/log`）的近 7 天收集，对应「导出AzurPilot日志」
     *
     * 判定基准与 `LogCleaner` 一致：txt 按文件名 `yyyy-MM-dd` 前缀（对不上前缀的保留——
     * 导出多带一份无伤，漏掉现场才误事）；error/<毫秒时间戳>/ 按时间戳，留着的整目录全收
     */
    fun collectAzurPilot(logDir: File, now: Long): List<File> {
        val cutoffMillis = now - ROLLING_KEEP_DAYS * MS_PER_DAY
        val cutoffDay = LocalDate.now().minusDays(ROLLING_KEEP_DAYS)
        val dated = logDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.filter { file ->
                val date = datePrefixOf(file.name) ?: return@filter true
                !date.isBefore(cutoffDay)
            }
            .orEmpty()
        val errors = File(logDir, "error").listFiles()
            ?.filter { it.isDirectory && it.name.all(Char::isDigit) }
            ?.filter { (it.name.toLongOrNull() ?: 0L) >= cutoffMillis }
            ?.flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
            .orEmpty()
        return dated + errors
    }

    private fun datePrefixOf(name: String): LocalDate? = runCatching {
        if (name.length < DATE_PREFIX_LENGTH) return null
        LocalDate.parse(name.substring(0, DATE_PREFIX_LENGTH), DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()

    private const val DATE_PREFIX_LENGTH = 10
}
