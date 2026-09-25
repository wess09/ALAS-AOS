package com.azurpilot.ghio.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val azurPilotCommit: String,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long,
)

data class AppUpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val available: AppUpdateInfo? = null,
    val error: String? = null,
)

/** 固定开发通道的 APK 更新器；系统安装确认仍由 Android Package Installer 展示。 */
class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()

    fun check() {
        if (_state.value.checking || _state.value.downloading) return
        scope.launch(AppDispatchers.IO) {
            _state.update { it.copy(checking = true, error = null) }
            runCatching {
                val body = requestText("$INDEX_URL?t=${System.currentTimeMillis()}")
                val json = JSONObject(body)
                // Latest 可先只发布 rootfs；正式签名尚未配置时没有可安装的 APK。
                if (!json.has("apkUrl")) return@runCatching null
                AppUpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    azurPilotCommit = json.getString("azurpilotCommit"),
                    apkUrl = json.getString("apkUrl"),
                    apkSha256 = json.getString("apkSha256"),
                    apkSize = json.getLong("apkSize"),
                ).also {
                    require(it.apkUrl.startsWith("https://"))
                    require(it.apkSha256.matches(Regex("[0-9a-f]{64}")))
                    require(it.apkSize > 0)
                }
            }.onSuccess { info ->
                _state.update {
                    it.copy(checking = false, available = info?.takeIf { candidate ->
                        candidate.versionCode > BuildConfig.VERSION_CODE
                    })
                }
            }.onFailure { error ->
                Timber.w(error, "App update check failed")
                _state.update { it.copy(checking = false, error = error.message ?: "检查更新失败") }
            }
        }
    }

    fun downloadAndInstall() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        scope.launch(AppDispatchers.IO) {
            _state.update { it.copy(downloading = true, error = null) }
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "azurpilot-update.apk")
                target.delete()
                val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 120_000
                    require(connection.responseCode in 200..299) { "APK 下载失败（HTTP ${connection.responseCode}）" }
                    val responseSize = connection.contentLengthLong
                    require(responseSize <= 0 || responseSize == info.apkSize) { "APK 响应大小与更新清单不一致" }
                    connection.inputStream.use { input -> target.outputStream().use(input::copyTo) }
                } finally {
                    connection.disconnect()
                }
                require(target.length() == info.apkSize) { "APK 大小校验失败" }
                require(sha256(target) == info.apkSha256) { "APK 校验失败" }
                launchInstaller(target)
            }.onSuccess {
                _state.update { it.copy(downloading = false) }
            }.onFailure { error ->
                File(context.cacheDir, "updates/azurpilot-update.apk").delete()
                Timber.w(error, "App update download failed")
                _state.update { it.copy(downloading = false, error = error.message ?: "下载更新失败") }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(available = null, error = null) }

    private fun requestText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            require(connection.responseCode in 200..299) { "更新检查失败（HTTP ${connection.responseCode}）" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
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

    private companion object {
        const val INDEX_URL = "https://github.com/wess09/AzurPilot-for-Android/releases/latest/download/latest.json"
    }
}
