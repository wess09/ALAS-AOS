package com.azurpilot.ghio.provision

import android.app.Application
import android.os.Build
import android.system.Os
import com.azurpilot.ghio.settings.AppSettingsManager
import com.azurpilot.ghio.update.DownloadAborted
import com.azurpilot.ghio.update.ReleaseDownloader
import com.azurpilot.ghio.update.ReleaseUrls
import com.azurpilot.ghio.update.sha256Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import timber.log.Timber
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** 首启 rootfs 部署状态机 */
sealed interface ProvisionState {
    /** 刚启动，正在比对内置版本与已装版本 */
    data object Checking : ProvisionState

    /** 未内置 rootfs.tar.xz（或架构不符），且 Release 清单也没有可部署的 Runtime */
    data object NotBundled : ProvisionState

    /** 磁盘余量不足（roadmap 硬校验：≥2GB） */
    data class LowDisk(val freeBytes: Long) : ProvisionState

    /** 解压中；进度按压缩字节读数 / 资产总长（流式解压拿不到的解压后总量不用） */
    data class Extracting(val doneBytes: Long, val totalBytes: Long) : ProvisionState

    data class Downloading(val doneBytes: Long, val totalBytes: Long) : ProvisionState

    data object Ready : ProvisionState

    data class Failed(val reason: String) : ProvisionState
}

data class RuntimeUpdateCheck(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val latestVersion: String? = null,
    val error: String? = null,
)

/**
 * 首启解压流水线：assets 的 rootfs.tar.xz → 内部存储 files/rootfs
 *
 * - **必须内部 filesDir**：/sdcard 模拟存储不支持符号链接（ubuntu-base 有 740 个），
 *   且 noexec；内部 filesDir 是 Spike A 实证 proot 可用的位置（targetSdk 35）。
 * - **按架构选 Runtime**：rootfs 按设备 ABI（[RuntimeArch]，proot 不做指令翻译）构建发布；
 *   内置包的 `BUILD_MANIFEST.rootfs_arch` 与设备不符时跳过内置改走 Release，
 *   Release 按 `latest.json.runtimes[abi]` 取对应架构的包。
 * - 版本闸门：assets 侧 `rootfs/BUILD_MANIFEST` 与 marker `files/rootfs/.provisioned`
 *   对版本号；不一致（或 python3 sanity 不过）就重解。升级=换新包重解，不做增量。
 * - 安装包未内置 rootfs（轻量 APK）时从 Release 拉 `rootfs-<abi>.tar.xz` 自动部署；
 *   两者共用同一条解压流水线。
 * - 落盘走 `rootfs.tmp` 解完再换名，半途失败不留半拉子正式目录。
 * - busybox tar 解 ubuntu-base 硬链接前向引用必炸（M1-d 坑②），故用纯 Java
 *   commons-compress + tukaani xz 流式解；硬链接物化成副本，符号链接走 [Os.symlink]。
 */
class RootfsProvisioner(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settings: AppSettingsManager,
) {

    private val _state = MutableStateFlow<ProvisionState>(ProvisionState.Checking)
    val state: StateFlow<ProvisionState> = _state.asStateFlow()
    private val _updateCheck = MutableStateFlow(RuntimeUpdateCheck())
    val updateCheck: StateFlow<RuntimeUpdateCheck> = _updateCheck.asStateFlow()

    /** 只查询 Latest，不下载或替换运行时。 */
    fun checkForUpdates() {
        if (_updateCheck.value.checking) return
        _updateCheck.value = RuntimeUpdateCheck(checking = true)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val abi = RuntimeArch.deviceAbi() ?: throw IOException("设备架构不受支持")
                parseRuntime(fetchIndex(), abi)?.version
            }.onSuccess { version ->
                _updateCheck.value = RuntimeUpdateCheck(checked = true, latestVersion = version)
            }.onFailure { error ->
                _updateCheck.value = RuntimeUpdateCheck(checked = true, error = error.message ?: "检查失败")
            }
        }
    }

    /** 当前生效的镜像前缀；「换源」判断以 (镜像, 自定义前缀) 二元组整体比较 */
    private fun mirrorPrefix() = ReleaseUrls.mirrorPrefix(settings.githubMirror.value, settings.githubMirrorCustom.value)

    private fun sourceSwitched(prefix: String) = mirrorPrefix() != prefix

    /** Latest 清单；镜像前缀与缓存绕过集中在这里。 */
    private fun fetchIndex(): JSONObject {
        val indexUrl = ReleaseUrls.selected(ReleaseUrls.INDEX, mirrorPrefix())
        val connection = URL("$indexUrl?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private data class ReleaseRuntime(val version: String, val url: String, val sha256: String, val size: Long)

    /**
     * 按 ABI 解析 Release 清单。新式清单是 `runtimes: { <abi>: {version,url,sha256,size} }`；
     * 旧式扁平字段（rootfsVersion 等）描述的一直是 arm64 包，仅对 arm64 生效。
     * 该架构没有可用 Runtime 时返回 null。
     */
    private fun parseRuntime(info: JSONObject, abi: String): ReleaseRuntime? {
        info.optJSONObject("runtimes")?.let { runtimes ->
            val entry = runtimes.optJSONObject(abi) ?: return null
            val version = entry.optString("version")
            val url = entry.optString("url")
            val sha = entry.optString("sha256")
            val size = entry.optLong("size", 0)
            require(version.isNotBlank()) { "运行时版本缺失" }
            require(url.startsWith(ReleaseUrls.BASE)) { "运行时下载地址无效" }
            require(sha.matches(SHA256)) { "运行时校验值无效" }
            require(size > 0) { "运行时大小无效" }
            return ReleaseRuntime(version, url, sha, size)
        }
        if (abi != RuntimeArch.ARM64 || !info.has("rootfsVersion")) return null
        val version = info.getString("rootfsVersion")
        require(version.isNotBlank()) { "运行时版本缺失" }
        val url = info.getString("rootfsUrl")
        require(url.startsWith(ReleaseUrls.BASE)) { "运行时下载地址无效" }
        val sha = info.getString("rootfsSha256")
        require(sha.matches(SHA256)) { "运行时校验值无效" }
        val size = info.getLong("rootfsSize")
        require(size > 0) { "运行时大小无效" }
        return ReleaseRuntime(version, url, sha, size)
    }

    /** 用户确认后调用；AppRoot 在结果出来前不会启动 proot。 */
    fun applyUpdate() {
        if (!running.compareAndSet(false, true)) return
        _state.value = ProvisionState.Checking
        scope.launch(Dispatchers.IO) {
            try {
                installFromRelease()
                _updateCheck.value = RuntimeUpdateCheck(checked = true, latestVersion = installedVersion())
            } catch (error: Exception) {
                Timber.w(error, "rootfs update failed")
                _updateCheck.value = _updateCheck.value.copy(error = error.message ?: "更新失败")
            } finally {
                tmpDir.deleteRecursively()
                _state.value = ProvisionState.Ready
                running.set(false)
            }
        }
    }

    private val running = AtomicBoolean(false)

    private val rootDir: File get() = File(app.filesDir, "rootfs")
    private val tmpDir: File get() = File(app.filesDir, "rootfs.tmp")
    private val markerFile: File get() = File(rootDir, MARKER_NAME)

    fun start() {
        if (running.compareAndSet(false, true)) {
            scope.launch { run() }
        }
    }

    /** 失败/低磁盘/版本过期后手动重跑 */
    fun retry() = start()

    /** 已装版本（marker 内容），设置页/诊断展示用 */
    fun installedVersion(): String? =
        runCatching { markerFile.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()

    private suspend fun run() = withContext(Dispatchers.IO) {
        _state.value = ProvisionState.Checking
        try {
            val previous = File(app.filesDir, "rootfs.previous")
            if (!rootDir.exists() && previous.exists()) {
                check(previous.renameTo(rootDir)) { "cannot restore previous rootfs" }
            }
            if (!isInstalled()) {
                val deviceAbi = RuntimeArch.deviceAbi()
                val bundled = deviceAbi?.let { bundledRuntime() }?.takeIf { it.arch == deviceAbi }
                if (bundled != null) {
                    checkDisk()
                    extract({ app.assets.open(ASSET_ARCHIVE) }, app.assets.openFd(ASSET_ARCHIVE).use { it.length }, bundled.version)
                } else if (!installFromRelease()) {
                    // 轻量包没内置 Runtime（或内置的是别的架构），Release 也没有本架构可用的：
                    // 只能等下一次发布或换完整版 APK。
                    Timber.w("no usable runtime: bundled arch=%s device=%s", bundled?.arch, deviceAbi)
                    _state.value = ProvisionState.NotBundled
                    return@withContext
                }
            }
            // Runtime 已在位时只查询并提示；用户确认前不替换。
            checkForUpdates()
            _state.value = ProvisionState.Ready
        } catch (e: Exception) {
            Timber.e(e, "rootfs provision failed")
            tmpDir.deleteRecursively()
            _state.value = ProvisionState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            running.set(false)
        }
    }

    private fun isInstalled(): Boolean {
        val deviceAbi = RuntimeArch.deviceAbi() ?: return false
        val manifest = readManifest { it } ?: return false
        val version = parseVersion(manifest) ?: return false
        // 旧包没写 rootfs_arch，视为 arm64
        if ((parseArch(manifest) ?: RuntimeArch.ARM64) != deviceAbi) return false
        return installedVersion() == version &&
                File(rootDir, PYTHON_REL).let {
                    it.isFile || java.nio.file.Files.isSymbolicLink(it.toPath())
                }
    }

    private data class BundledRuntime(val version: String, val arch: String)

    /** 内置包版本与架构；资产缺任一件都视为未内置。旧包没写 rootfs_arch，视为 arm64 */
    private fun bundledRuntime(): BundledRuntime? {
        val manifest = readManifest(fromAssets = true) { it } ?: return null
        val version = parseVersion(manifest) ?: return null
        app.assets.open(ASSET_ARCHIVE).use { }
        return BundledRuntime(version, parseArch(manifest) ?: RuntimeArch.ARM64)
    }

    /**
     * 读 BUILD_MANIFEST 文本：[fromAssets]=true 读 assets 侧，否则读已解包目录。
     * 任何 IO 异常都以 null 收场（版本闸门视为不过）。
     */
    private inline fun <T> readManifest(fromAssets: Boolean = false, block: (String) -> T): T? = runCatching {
        val text = if (fromAssets) {
            app.assets.open(ASSET_MANIFEST).bufferedReader().use { it.readText() }
        } else {
            File(rootDir, MANIFEST_REL).readText()
        }
        block(text)
    }.getOrNull()

    private fun parseVersion(manifestJson: String): String? =
        VERSION_KEY.find(manifestJson)?.groupValues?.get(1)

    private fun parseArch(manifestJson: String): String? =
        ARCH_KEY.find(manifestJson)?.groupValues?.get(1)

    private fun checkDisk() {
        val free = app.filesDir.let { it.mkdirs(); it.usableSpace }
        if (free < MIN_FREE_BYTES) throw IOException("磁盘空间不足：剩余 ${free / 1_000_000} MB，需要至少 2 GB")
    }

    /**
     * 从 Release 下载并部署 Runtime。
     *
     * 清单里还没有 Runtime 字段（旧版 latest.json 只发布 APK）时返回 false；版本已与已装
     * 一致时也返回 false。设备架构不受支持、清单里没有该架构的包时抛异常，调用方把
     * 消息带进 Failed/错误态。
     */
    private suspend fun installFromRelease(): Boolean {
        val info = fetchIndex()
        if (!info.has("rootfsVersion") && !info.has("runtimes")) return false
        val abi = RuntimeArch.deviceAbi()
            ?: throw IOException("设备架构不受支持：${Build.SUPPORTED_ABIS.firstOrNull()}，需要 ${RuntimeArch.SUPPORTED.joinToString()}")
        val runtime = parseRuntime(info, abi)
            ?: throw IOException("发布渠道暂无 $abi 的 Runtime")
        if (runtime.version == installedVersion()) return false
        checkDisk()
        val archive = File(app.filesDir, "rootfs-update.tar.xz")
        try {
            // 下载途中换源就从头再来：不同镜像的断点续传对不上号，删掉重下没有额外风险。
            while (true) {
                val prefix = mirrorPrefix()
                try {
                    downloadArchive(runtime, prefix, archive)
                    break
                } catch (changed: DownloadAborted) {
                    if (!sourceSwitched(prefix)) throw IOException("下载被中止", changed)
                    Timber.i("runtime download source changed, restarting")
                    archive.delete()
                    _state.value = ProvisionState.Downloading(0, runtime.size)
                }
            }
            check(sha256Hex(archive) == runtime.sha256) { "rootfs 下载校验失败" }
            extract({ archive.inputStream() }, runtime.size, runtime.version)
            Timber.i("rootfs installed from release: ${runtime.version}")
        } finally { archive.delete() }
        return true
    }

    /** okdownload 多连接下载，进度直推状态机；SHA-256 由调用方在完成后统一校验 */
    private suspend fun downloadArchive(runtime: ReleaseRuntime, prefix: String, target: File) {
        val downloadUrl = ReleaseUrls.selected(runtime.url, prefix)
        ReleaseDownloader.download(
            url = downloadUrl,
            target = target,
            shouldAbort = { sourceSwitched(prefix) },
        ) { done, total ->
            if (total > 0) _state.value = ProvisionState.Downloading(done, total)
        }
    }

    // ── 解压 ──

    private fun extract(openArchive: () -> InputStream, total: Long, expectedVersion: String) {
        tmpDir.deleteRecursively()
        check(tmpDir.mkdirs()) { "cannot create $tmpDir" }

        val counting = CountingInputStream(openArchive().buffered(BUFFER_SIZE)) { read ->
            _state.value = ProvisionState.Extracting(read, total)
        }
        val extracted = mutableMapOf<String, File>()
        val deferredLinks = mutableListOf<Pair<String, String>>()

        TarArchiveInputStream(XZInputStream(counting, -1)).use { tar ->
            generateSequence { tar.nextEntry }.forEach { entry ->
                extractEntry(tar, entry, extracted, deferredLinks)
            }
        }

        // 硬链接前向引用兜底：解完全量后目标必然在（否则包本身坏）
        for ((name, linkName) in deferredLinks) {
            val src = File(tmpDir, linkName)
            check(src.isFile) { "hard link target missing: $linkName (for $name)" }
            src.copyTo(File(tmpDir, name), overwrite = true)
        }

        val extractedVersion = parseVersion(File(tmpDir, MANIFEST_REL).readText())
        check(extractedVersion == expectedVersion) { "rootfs 清单版本不符" }
        val python = File(tmpDir, PYTHON_REL)
        check(python.isFile || java.nio.file.Files.isSymbolicLink(python.toPath())) { "$PYTHON_REL missing" }
        File(tmpDir, MARKER_NAME).writeText(expectedVersion)

        // APK 升级重铺 rootfs 时保留用户实例与日志。
        val oldPilot = File(rootDir, "opt/azurpilot")
        val newPilot = File(tmpDir, "opt/azurpilot")
        oldPilot.resolve("config").listFiles()
            ?.filter { it.isFile && it.extension == "json" &&
                !it.name.startsWith("template") && !it.name.startsWith("deploy") }
            ?.forEach { file -> file.copyTo(newPilot.resolve("config/${file.name}"), overwrite = true) }
        oldPilot.resolve("log").takeIf { it.isDirectory }
            ?.copyRecursively(newPilot.resolve("log"), overwrite = true)

        val previous = File(app.filesDir, "rootfs.previous")
        previous.deleteRecursively()
        val hadRoot = rootDir.exists()
        if (hadRoot) check(rootDir.renameTo(previous)) { "cannot preserve previous rootfs" }
        if (!tmpDir.renameTo(rootDir)) {
            if (hadRoot) previous.renameTo(rootDir)
            throw IOException("rename $tmpDir -> $rootDir failed")
        }
        previous.deleteRecursively()
    }

    private fun extractEntry(
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
        extracted: MutableMap<String, File>,
        deferredLinks: MutableList<Pair<String, String>>,
    ) {
        val name = entry.name.removePrefix("./").trimEnd('/')
        if (name.isEmpty()) return
        val root = tmpDir.absoluteFile.toPath().normalize()
        val path = root.resolve(name).normalize()
        // 绝对 guest symlink 在 Android 宿主视角可能悬空；使用词法路径检查，并拒绝穿过已解出的链接。
        if (!path.startsWith(root) || path == root) {
            throw IOException("illegal entry path: $name")
        }
        val target = path.toFile()
        var parent = path.parent
        while (parent != null && parent != root) {
            if (java.nio.file.Files.isSymbolicLink(parent)) {
                throw IOException("entry traverses symlink: $name")
            }
            parent = parent.parent
        }
        if (!entry.isSymbolicLink && java.nio.file.Files.isSymbolicLink(path)) {
            throw IOException("entry replaces symlink: $name")
        }

        when {
            entry.isDirectory -> target.mkdirs()

            entry.isSymbolicLink -> {
                target.parentFile?.mkdirs()
                target.delete()
                Os.symlink(entry.linkName, target.absolutePath)
            }

            entry.isLink -> {
                // 硬链接物化成副本：目标已解出直接拷，前向引用登记后补
                val linkName = entry.linkName.removePrefix("./")
                val src = extracted[linkName]
                if (src != null && src.isFile) {
                    target.parentFile?.mkdirs()
                    src.copyTo(target, overwrite = true)
                } else {
                    deferredLinks += name to linkName
                }
            }

            entry.isFile -> {
                target.parentFile?.mkdirs()
                target.outputStream().buffered(BUFFER_SIZE).use { out ->
                    tar.copyTo(out, BUFFER_SIZE)
                }
                // 可执行位必须保：proot/python/busybox 全靠它（Spike A：缺 +x 报 Permission denied 极易误判）
                if (entry.mode and 0b001_001_001 != 0) target.setExecutable(true, false)
                extracted[name] = target
            }
        }
    }

    /** 每 256KB 才推一次状态，StateFlow 合流前刷太勤纯属白跑重组 */
    private class CountingInputStream(
        input: InputStream,
        private val onProgress: (Long) -> Unit,
    ) : FilterInputStream(input) {
        private var read = 0L
        private var sinceEmit = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

        private fun add(n: Long) {
            read += n
            sinceEmit += n
            if (sinceEmit >= 256 * 1024) {
                sinceEmit = 0
                onProgress(read)
            }
        }
    }

    private companion object {
        const val ASSET_ARCHIVE = "rootfs/rootfs.tar.xz"
        const val ASSET_MANIFEST = "rootfs/BUILD_MANIFEST"
        const val MANIFEST_REL = "opt/azurpilot/BUILD_MANIFEST"
        const val PYTHON_REL = "opt/azurpilot/.venv/bin/python"
        const val MARKER_NAME = ".provisioned"
        const val MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024
        const val BUFFER_SIZE = 256 * 1024
        val VERSION_KEY = Regex(""""rootfs_version"\s*:\s*"([^"]+)"""")
        val ARCH_KEY = Regex(""""rootfs_arch"\s*:\s*"([^"]+)"""")
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
