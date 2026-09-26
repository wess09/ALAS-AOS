package com.azurpilot.ghio.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 下载被主动中止（换源）时抛出；调用方据此把「换源重来」与「真失败」区分开。
 *
 * Thrown when a download is aborted on purpose (source switch); callers use it
 * to tell a deliberate restart apart from a genuine failure.
 */
class DownloadAborted : Exception("download aborted")

/**
 * 整文件 SHA-256；下载完成后由调用方统一校验。
 *
 * Whole-file SHA-256, verified by the caller once the download completes.
 */
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
 * Release 下载器：单连接 HttpURLConnection 直下。
 *
 * 曾试过 okdownload 多连接分段：真机收益不稳（部分镜像对并发 Range 行为参差），
 * 排障面也大，按产品决定回退单连接；大小与 SHA-256 由调用方在完成后统一校验。
 *
 * Downloads release assets over a single `HttpURLConnection`.
 *
 * Segmented multi-connection downloads (okdownload) were tried and rolled
 * back: real-device gains were unstable (mirrors handle concurrent `Range`
 * requests inconsistently) and the troubleshooting surface grew. Size and
 * SHA-256 are verified by the caller once the download completes.
 */
object ReleaseDownloader {

    /**
     * 阻塞下载 url 到 target（覆盖写）。
     *
     * - [shouldAbort] 在每个读块前检查（换源）→ [DownloadAborted]；连接阶段
     *   挂死时最长 [CONNECT_TIMEOUT_MS]、读阶段最长 [READ_TIMEOUT_MS] 能脱身
     * - [totalBytes] 传清单里的大小供进度显示；未知传 -1，[onProgress] 收到的
     *   total 即该值
     *
     * Downloads [url] into [target] (overwriting), blocking the caller.
     *
     * - [shouldAbort] is polled before every read chunk (source switch) and
     *   trips [DownloadAborted]; a stalled connect gives up within
     *   [CONNECT_TIMEOUT_MS], a stalled read within [READ_TIMEOUT_MS]
     * - Pass the manifest size as [totalBytes] for progress display, or -1
     *   when unknown; [onProgress] reports that same value as its total
     */
    fun download(
        url: String,
        target: File,
        shouldAbort: () -> Boolean = { false },
        totalBytes: Long = -1L,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            check(connection.responseCode in 200..299) { "下载失败（HTTP ${connection.responseCode}）" }
            connection.inputStream.use { input ->
                target.outputStream().buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var done = 0L
                    while (true) {
                        // 连接卡住时 read 会阻塞到超时，这里保证一换源就尽快放弃当前连接
                        // read blocks until the timeouts elapse when stalled; this check
                        // abandons the connection as soon as the user switches source
                        if (shouldAbort()) throw DownloadAborted()
                        val count = input.read(buffer)
                        if (count < 0) break
                        done += count
                        output.write(buffer, 0, count)
                        onProgress(done, totalBytes)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val BUFFER_SIZE = 256 * 1024
}
