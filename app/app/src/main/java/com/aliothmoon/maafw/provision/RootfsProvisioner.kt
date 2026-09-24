package com.aliothmoon.maafw.provision

import android.app.Application
import android.system.Os
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
import java.util.concurrent.atomic.AtomicBoolean

/** 首启 rootfs 部署状态机 */
sealed interface ProvisionState {
    /** 刚启动，正在比对内置版本与已装版本 */
    data object Checking : ProvisionState

    /** 安装包未内置 rootfs.tar.xz（开发构建走 M1-d 的 /data/local/tmp 通道时可跳过） */
    data object NotBundled : ProvisionState

    /** 磁盘余量不足（roadmap 硬校验：≥2GB） */
    data class LowDisk(val freeBytes: Long) : ProvisionState

    /** 解压中；进度按压缩字节读数 / 资产总长（流式解压拿不到的解压后总量不用） */
    data class Extracting(val doneBytes: Long, val totalBytes: Long) : ProvisionState

    data object Ready : ProvisionState

    data class Failed(val reason: String) : ProvisionState
}

/**
 * 首启解压流水线：assets 的 rootfs.tar.xz → 内部存储 files/rootfs
 *
 * - **必须内部 filesDir**：/sdcard 模拟存储不支持符号链接（ubuntu-base 有 740 个），
 *   且 noexec；内部 filesDir 是 Spike A 实证 proot 可用的位置（targetSdk 35）。
 * - 版本闸门：assets 侧 `rootfs/BUILD_MANIFEST` 与 marker `files/rootfs/.provisioned`
 *   对版本号；不一致（或 python3  sanity 不过）就重解。升级=换新包重解，不做增量。
 * - 落盘走 `rootfs.tmp` 解完再换名，半途失败不留半拉子正式目录。
 * - busybox tar 解 ubuntu-base 硬链接前向引用必炸（M1-d 坑②），故用纯 Java
 *   commons-compress + tukaani xz 流式解；硬链接物化成副本，符号链接走 [Os.symlink]。
 */
class RootfsProvisioner(
    private val app: Application,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<ProvisionState>(ProvisionState.Checking)
    val state: StateFlow<ProvisionState> = _state.asStateFlow()

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
            val bundled = bundledVersion()
            if (bundled == null) {
                Timber.w("rootfs archive not bundled in this build")
                _state.value = ProvisionState.NotBundled
                return@withContext
            }
            if (isInstalled(bundled)) {
                Timber.i("rootfs $bundled already provisioned")
                _state.value = ProvisionState.Ready
                return@withContext
            }

            val free = app.filesDir.let { it.mkdirs(); it.usableSpace }
            if (free < MIN_FREE_BYTES) {
                Timber.w("low disk: ${free / 1_000_000}MB free, need ${MIN_FREE_BYTES / 1_000_000}MB")
                _state.value = ProvisionState.LowDisk(free)
                return@withContext
            }

            extract()

            // 解完 sanity：解释器在且可执行，版本以镜像内清单为准
            val python = File(rootDir, PYTHON_REL)
            check(python.isFile || java.nio.file.Files.isSymbolicLink(python.toPath())) {
                "$PYTHON_REL missing"
            }
            val installed = readExtractedVersion() ?: bundled
            markerFile.writeText(installed)
            Timber.i("rootfs $installed provisioned")
            _state.value = ProvisionState.Ready
        } catch (e: Exception) {
            Timber.e(e, "rootfs provision failed")
            tmpDir.deleteRecursively()
            _state.value = ProvisionState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            running.set(false)
        }
    }

    private fun isInstalled(bundled: String): Boolean =
        installedVersion() == bundled &&
                File(rootDir, PYTHON_REL).let {
                    it.isFile || java.nio.file.Files.isSymbolicLink(it.toPath())
                }

    /** 内置包版本；资产缺任一件都视为未内置 */
    private fun bundledVersion(): String? = runCatching {
        parseVersion(app.assets.open(ASSET_MANIFEST).bufferedReader().use { it.readText() })
    }.getOrNull().also { version ->
        if (version != null) app.assets.open(ASSET_ARCHIVE).use { }
    }

    private fun readExtractedVersion(): String? = runCatching {
        parseVersion(File(rootDir, MANIFEST_REL).readText())
    }.getOrNull()

    private fun parseVersion(manifestJson: String): String? =
        VERSION_KEY.find(manifestJson)?.groupValues?.get(1)

    // ── 解压 ──

    private fun extract() {
        tmpDir.deleteRecursively()
        check(tmpDir.mkdirs()) { "cannot create $tmpDir" }

        // openFd 拿未压缩资产的真实长度做进度分母（noCompress "xz" 已配）
        val afd = app.assets.openFd(ASSET_ARCHIVE)
        val total = afd.length
        val counting = CountingInputStream(afd.createInputStream().buffered(BUFFER_SIZE)) { read ->
            _state.value = ProvisionState.Extracting(read, total)
        }
        val extracted = mutableMapOf<String, File>()
        val deferredLinks = mutableListOf<Pair<String, String>>()

        TarArchiveInputStream(XZInputStream(counting, -1)).use { tar ->
            generateSequence { tar.nextEntry }.forEach { entry ->
                extractEntry(tar, entry, extracted, deferredLinks)
            }
        }
        afd.close()

        // 硬链接前向引用兜底：解完全量后目标必然在（否则包本身坏）
        for ((name, linkName) in deferredLinks) {
            val src = File(tmpDir, linkName)
            check(src.isFile) { "hard link target missing: $linkName (for $name)" }
            src.copyTo(File(tmpDir, name), overwrite = true)
        }

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
    }
}
