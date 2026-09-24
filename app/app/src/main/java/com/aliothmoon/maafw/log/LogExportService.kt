package com.aliothmoon.maafw.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.aliothmoon.maafw.MaaDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 两类导出各自打包、各自管理旧 zip；底部 sheet 按这个类型决定标题与产物 */
enum class LogExportKind(val filePrefix: String) {
    /** ALAS 日志（官方 issue 格式：zip 条目镜像 `log/xxx` 相对路径，只收近 7 天） */
    ALAS("azurpilot_logs_"),

    /** 启动器日志（log/ + debug/ 全量收集，常驻附 getprop 的 properties.txt） */
    LAUNCHER("launcher_logs_"),
}

/**
 * 把日志打成 zip 交出去
 *
 * 反馈问题时最费劲的一步是「把日志弄出来」——设备上没有文件管理入口，adb 又不是人人都有。
 * 打一个包直接分享出去，这一步就没了
 *
 * 不删源：导完还留在设备上，用户可以再导一次；旧 zip 按 [LogExportKind] 各自先删再打
 */
class LogExportService(
    private val context: Context,
    /** 启动器日志的根；zip 里的路径相对 [baseDir] */
    private val baseDir: () -> File,
    private val launcherRoots: () -> List<File>,
    /** ALAS 日志目录（`rootfs/opt/alas/log`）；zip 条目镜像成官方 issue 的 `log/xxx` */
    private val alasLogDir: () -> File,
) {

    /** 返回 null = 没有可导出的日志，或打包失败 */
    suspend fun exportZip(kind: LogExportKind): File? = withContext(MaaDispatchers.IO) {
        val now = System.currentTimeMillis()
        val files = when (kind) {
            LogExportKind.ALAS -> LogExportCollector.collectAlas(alasLogDir(), now)
            LogExportKind.LAUNCHER -> LogExportCollector.collect(launcherRoots(), now)
        }
        if (files.isEmpty()) {
            Timber.w("没有可导出的日志：%s", kind)
            return@withContext null
        }
        runCatching {
            val dir = File(baseDir(), "${LOG_DIR_NAME}/${LogExportCollector.EXPORT_DIR_NAME}")
                .apply { mkdirs() }
            // 只留最新一份：旧包对用户没用，留着纯占空间；两类导出各自管自己的前缀
            dir.listFiles()
                ?.filter { it.name.startsWith(kind.filePrefix) || it.name.startsWith(LEGACY_PREFIX) }
                ?.forEach { it.delete() }
            val zip = File(dir, "${kind.filePrefix}${STAMP.format(Date())}.zip")
            writeZip(kind, zip, files)
            zip
        }.onFailure { Timber.w(it, "导出日志失败：%s", kind) }.getOrNull()
    }

    suspend fun shareIntent(kind: LogExportKind): Intent? = exportZip(kind)?.let(::createShareIntent)

    /** 写进用户经 SAF 选的位置；成功返回显示名 */
    suspend fun exportTo(kind: LogExportKind, target: Uri): String? = withContext(MaaDispatchers.IO) {
        val zip = exportZip(kind) ?: return@withContext null
        runCatching {
            context.contentResolver.openOutputStream(target)?.use { out ->
                zip.inputStream().use { it.copyTo(out) }
            } ?: return@runCatching null
            displayName(target) ?: zip.name
        }.onFailure { Timber.w(it, "写入导出目标失败：%s", target) }.getOrNull()
    }

    fun suggestedFileName(kind: LogExportKind): String = "${kind.filePrefix}${STAMP.format(Date())}.zip"

    private fun writeZip(kind: LogExportKind, zip: File, files: List<File>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
            // getprop 常驻附在启动器包：ROM 差异是排障时最先要问的；ALAS 包走官方 issue 格式不附
            if (kind == LogExportKind.LAUNCHER) appendDeviceProperties(out)
            files.forEach { file ->
                val entry = ZipEntry(entryName(kind, file))
                entry.time = file.lastModified()
                out.putNextEntry(entry)
                file.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
                out.closeEntry()
            }
        }
    }

    private fun entryName(kind: LogExportKind, file: File): String = when (kind) {
        // 官方 issue 格式：相对 ALAS 日志目录的路径前面补 `log/`（error/ 子目录原样带上）
        LogExportKind.ALAS -> "log/" + file.relativeTo(alasLogDir()).invariantSeparatorsPath
        LogExportKind.LAUNCHER -> file.relativeTo(baseDir()).invariantSeparatorsPath
    }

    /** 取不到就跳过：少一份设备属性不该让整个导出失败 */
    private fun appendDeviceProperties(out: ZipOutputStream) {
        runCatching {
            val process = Runtime.getRuntime().exec("getprop")
            out.putNextEntry(ZipEntry(PROPERTIES_ENTRY))
            process.inputStream.use { it.copyTo(out, BUFFER_SIZE) }
            out.closeEntry()
            process.waitFor()
        }.onFailure { Timber.w(it, "收集设备属性失败") }
    }

    /** 走 FileProvider 而非 file://：API 24 起后者直接抛 FileUriExposedException */
    private fun createShareIntent(zip: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_ZIP
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(com.aliothmoon.maafw.R.string.log_export_subject))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()

    private companion object {
        const val LOG_DIR_NAME = "log"
        const val PROPERTIES_ENTRY = "properties.txt"
        const val MIME_ZIP = "application/zip"
        const val BUFFER_SIZE = 8 * 1024

        /** v0.1.3 之前的旧命名，导出启动器日志时一并清掉，不留两份 */
        const val LEGACY_PREFIX = "maafw_logs_"

        val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
