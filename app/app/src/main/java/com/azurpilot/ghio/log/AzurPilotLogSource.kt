package com.azurpilot.ghio.log

import android.content.Context
import java.io.File

/**
 * AzurPilot 侧日志目录（proot 内 `/opt/azurpilot/log`，实体在内部存储 rootfs 下）的唯一访问口
 *
 * App 进程直接可读，不走 wrapper HTTP：wrapper 的 /logs 只服务 mtime 最新的一个 txt，
 * 历史 txt 与 error 现场根本拿不到；路径拼法与 `ProotHost` 的 installDir 一致
 *
 * adb 读不到内部存储（release 无 run-as），所以查看/导出都得在 App 进程内做
 */
class AzurPilotLogSource(context: Context) {

    private val logDir: File = File(context.filesDir, "rootfs/opt/azurpilot/log")

    fun logDir(): File = logDir

    /** 按天日志 `yyyy-MM-dd_{config}.txt`，mtime 倒序 */
    fun dailyLogs(): List<File> = runCatching {
        logDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** 错误现场 `error/<毫秒时间戳>/`，时间戳倒序；名字非纯数字的目录不收 */
    fun errorDirs(): List<File> = runCatching {
        File(logDir, "error").listFiles()
            ?.filter { it.isDirectory && it.name.all(Char::isDigit) }
            ?.sortedByDescending { it.name.toLongOrNull() ?: 0L }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** 按名单取按天日志；拒绝路径段，防路由参数越出日志目录 */
    fun dailyFile(name: String): File? =
        name.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains("..") }
            ?.let { File(logDir, it) }
            ?.takeIf { it.isFile }

    /** 按目录名取错误现场；同样是路由参数，先验纯数字 */
    fun errorDir(name: String): File? =
        name.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?.let { File(File(logDir, "error"), it) }
            ?.takeIf { it.isDirectory }
}
