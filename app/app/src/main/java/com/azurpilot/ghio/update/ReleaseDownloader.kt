package com.azurpilot.ghio.update

import android.content.Context
import com.liulishuo.okdownload.DownloadListener
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.cause.ResumeFailedCause
import com.liulishuo.okdownload.core.connection.DownloadOkHttp3Connection
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 下载被主动中止（换源）时抛出；调用方据此把「换源重来」与「真失败」区分开 */
class DownloadAborted : Exception("download aborted")

/** 整文件 SHA-256；下载校验在落盘完成后统一做 */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Release 资产下载器：okdownload 引擎的薄封装（自己拼 Range 分段属于重复造轮子）。
 *
 * - okdownload 按文件大小自动用 1~5 条连接并行分段（<1MB 单连接，>100MB 五连接），
 *   断点信息由 okdownload-sqlite 落盘，同一文件的续传/重试都归引擎管
 * - 连接层走 OkHttp（okdownload 的 okhttp 组件），超时按慢速镜像场景放宽
 * - 中止：[DownloadTask.cancel] 会让任务以 CANCELED 收尾，shouldAbort 轮询挂在
 *   进度回调上（约 200ms 一拍），换源后当前连接很快放弃
 */
object ReleaseDownloader {

    /** Application 创建时调一次；显式装配 OkHttp 连接层，不依赖反射默认值 */
    fun init(context: Context) {
        val factory = DownloadOkHttp3Connection.Factory().setBuilder(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
        )
        OkDownload.setSingletonInstance(
            OkDownload.Builder(context).connectionFactory(factory).build(),
        )
    }

    /**
     * 挂起下载 url 到 target。同名旧文件沿用 okdownload 的断点续传；
     * 不想续传（如换源）由调用方先删文件。
     * onProgress 以约 200ms 节流回调 (doneBytes, totalBytes)。
     */
    suspend fun download(
        url: String,
        target: File,
        shouldAbort: () -> Boolean = { false },
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) = suspendCancellableCoroutine { cont ->
        val task = DownloadTask.Builder(url, target.parentFile ?: File("."))
            .setFilename(target.name)
            .setConnectionCount(CONNECTION_COUNT)
            .setPreAllocateLength(true)
            // 回调节流交给引擎；autoCallbackToUIThread 保持 false，协程恢复无需主循环
            .setAutoCallbackToUIThread(false)
            .setMinIntervalMillisCallbackProcess(PROGRESS_INTERVAL_MS)
            .build()
        // 外层协程被取消（用户离开/状态复位）时同样停掉引擎任务
        cont.invokeOnCancellation { task.cancel() }
        task.enqueue(object : DownloadListener {
            override fun taskStart(task: DownloadTask) = Unit
            override fun connectTrialStart(
                task: DownloadTask,
                requestHeaderFields: Map<String, List<String>>,
            ) = Unit
            override fun connectTrialEnd(
                task: DownloadTask,
                responseCode: Int,
                responseHeaderFields: Map<String, List<String>>,
            ) = Unit
            override fun downloadFromBeginning(task: DownloadTask, info: BreakpointInfo, cause: ResumeFailedCause) = Unit
            override fun downloadFromBreakpoint(task: DownloadTask, info: BreakpointInfo) = Unit
            override fun connectStart(task: DownloadTask, blockIndex: Int, requestHeaderFields: Map<String, List<String>>) = Unit
            override fun connectEnd(task: DownloadTask, blockIndex: Int, responseCode: Int, responseHeaderFields: Map<String, List<String>>) = Unit
            override fun fetchStart(task: DownloadTask, blockIndex: Int, contentLength: Long) = Unit
            override fun fetchEnd(task: DownloadTask, blockIndex: Int, contentLength: Long) = Unit

            override fun fetchProgress(task: DownloadTask, blockIndex: Int, increaseBytes: Long) {
                val info = task.getInfo()
                onProgress(info?.totalOffset ?: 0L, info?.totalLength ?: 0L)
                // 换源等中止诉求在下一拍回调上生效；cancel 让引擎尽快收尾
                if (shouldAbort()) task.cancel()
            }

            override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?) {
                when {
                    cause == EndCause.COMPLETED -> cont.resume(Unit)
                    cause == EndCause.CANCELED && shouldAbort() -> cont.resumeWithException(DownloadAborted())
                    cause == EndCause.CANCELED -> cont.resumeWithException(IOException("下载被取消"))
                    else -> cont.resumeWithException(realCause ?: IOException("下载失败（$cause）"))
                }
            }
        })
    }

    private const val CONNECTION_COUNT = 4
    private const val PROGRESS_INTERVAL_MS = 200
}
