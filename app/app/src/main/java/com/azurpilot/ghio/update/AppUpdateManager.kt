package com.azurpilot.ghio.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.settings.AppSettingsManager
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

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
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

/**
 * 固定开发通道的 APK 更新器；系统安装确认仍由 Android Package Installer 展示。
 *
 * 检查与下载都走 Release 清单（[ReleaseUrls]）；下载源支持镜像，大小与 SHA-256
 * 在落盘后校验，安装交给系统安装器。
 *
 * Updater for the fixed dev channel's APK; the system Package Installer still
 * owns the install confirmation.
 *
 * Both check and download go through the release manifest ([ReleaseUrls]).
 * Downloads support mirrors; size and SHA-256 are verified after the file
 * lands, and installation is handed to the system installer.
 */
class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettingsManager,
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()

    fun check() {
        if (_state.value.checking || _state.value.downloading) return
        scope.launch(AppDispatchers.IO) {
            _state.update { it.copy(checking = true, error = null) }
            runCatching {
                val indexUrl = ReleaseUrls.selected(ReleaseUrls.INDEX, mirrorPrefix())
                val body = requestText("$indexUrl?t=${System.currentTimeMillis()}")
                val json = JSONObject(body)
                // Latest 可先只发布 rootfs；正式签名尚未配置时没有可安装的 APK。
                if (!json.has("apkUrl")) return@runCatching null
                AppUpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    apkSha256 = json.getString("apkSha256"),
                    apkSize = json.getLong("apkSize"),
                ).also {
                    require(it.apkUrl.startsWith(ReleaseUrls.BASE))
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
                val dir = File(context.cacheDir, "updates").apply { check(mkdirs() || isDirectory) }
                // 安装器可能在 startActivity 返回后才读取文件。按 SHA 命名并保持内容不变，
                // 避免再次点击更新时覆盖它，导致安装阶段的 APK v2 内容摘要不匹配。
                val target = File(dir, "${info.apkSha256}.apk")
                if (target.length() != info.apkSize || sha256Hex(target) != info.apkSha256) {
                    val partial = File(dir, "${info.apkSha256}.apk.part")
                    partial.delete()
                    try {
                        val downloadUrl = ReleaseUrls.selected(info.apkUrl, mirrorPrefix())
                        ReleaseDownloader.download(downloadUrl, partial)
                        require(partial.length() == info.apkSize) { "APK 大小校验失败" }
                        require(sha256Hex(partial) == info.apkSha256) { "APK 校验失败" }
                        require(partial.renameTo(target)) { "无法保存更新安装包" }
                    } finally {
                        partial.delete()
                    }
                }
                launchInstaller(target)
            }.onSuccess {
                _state.update { it.copy(downloading = false) }
            }.onFailure { error ->
                Timber.w(error, "App update download failed")
                _state.update { it.copy(downloading = false, error = error.message ?: "下载更新失败") }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(available = null, error = null) }

    /** 当前生效的镜像前缀（与 Runtime 下载共用同一个源选择） */
    private fun mirrorPrefix() =
        ReleaseUrls.mirrorPrefix(settings.githubMirror.value, settings.githubMirrorCustom.value)

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

}
