package com.azurpilot.ghio.log

import android.util.Log
import java.io.File

/**
 * 启动器日志目录（`log/`）的递归扫描：app 滚动日志、`proot/session.log`、crash 下的 txt
 *
 * 与 `AppLogWriter.listFiles()` 是两回事：那个只认 app.log 滚动系列（purge 的范围），
 * 这个管「启动器日志」列表页要展示的全部；export/ 下的 zip 不匹配任何一条，天然排除
 */
object LauncherLogScanner {

    /** mtime 倒序；返回的 File 都挂在 [logDir] 下，展示用相对路径 */
    fun scan(logDir: File): List<File> = runCatching {
        logDir.walkTopDown()
            .filter { it.isFile && include(it.relativeTo(logDir).invariantSeparatorsPath) }
            .sortedByDescending { it.lastModified() }
            .toList()
    }.getOrElse {
        Log.w(TAG, "Failed to scan launcher logs", it)
        emptyList()
    }

    private fun include(relPath: String): Boolean = when {
        APP_LOG_PATTERN.matches(relPath) -> true
        relPath == SESSION_LOG -> true
        relPath.startsWith(CRASH_PREFIX) && relPath.endsWith(".txt") -> true
        else -> false
    }

    private const val TAG = "LauncherLogScanner"
    private const val SESSION_LOG = "proot/session.log"
    private const val CRASH_PREFIX = "crash/"

    /** 与 `AppLogWriter.FILE_PATTERN` 同一条：滚动系列改名要两边同改 */
    private val APP_LOG_PATTERN = Regex("""app(\.\d+)?\.log""")
}
