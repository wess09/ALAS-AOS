package com.azurpilot.ghio.remote

import com.azurpilot.ghio.IRunnerCallback
import com.azurpilot.ghio.ITouchEventCallback
import com.azurpilot.ghio.RemoteService
import com.azurpilot.ghio.bridge.InputControlUtils
import com.azurpilot.ghio.bridge.NativeBridgeLib
import com.azurpilot.ghio.constant.DefaultDisplayConfig
import com.azurpilot.ghio.constant.DisplayMode
import com.azurpilot.ghio.remote.internal.ActivityUtils
import com.azurpilot.ghio.remote.internal.AppWatchdog
import com.azurpilot.ghio.remote.internal.BridgeServer
import com.azurpilot.ghio.remote.internal.PermissionGrantHelper
import com.azurpilot.ghio.service.AccessibilityHelperService
import com.azurpilot.ghio.remote.internal.PowerController
import com.azurpilot.ghio.remote.internal.PrimaryDisplayManager
import com.azurpilot.ghio.remote.internal.ScreenManager
import com.azurpilot.ghio.constant.PrivilegedGrant
import com.azurpilot.ghio.remote.internal.VirtualDisplayManager
import com.azurpilot.ghio.remote.internal.WakeUnlockController
import com.azurpilot.ghio.third.FakeContext
import com.azurpilot.ghio.third.Ln
import com.azurpilot.ghio.third.wrappers.ServiceManager
import com.azurpilot.ghio.third.Workarounds
import android.view.Surface
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * 特权进程的入口对象：由 Shizuku 或 root starter 反射实例化，实例化即完成进程内初始化
 * 构造函数不能抛：抛了 binder 回不去，app 侧只看得到连接超时
 */
class RemoteServiceImpl : RemoteService.Stub() {

    private val virtualDisplayMode = AtomicInteger(DisplayMode.BACKGROUND)
    private val appPid = AtomicInteger(0)
    private val destroyed = AtomicBoolean(false)

    init {
        RemoteBootTrace.mark("CTOR_START")
        Workarounds.apply()
        runCatching { BridgeServer.start() }.onFailure { Ln.e("$TAG: BridgeServer start failed", it) }
        Runtime.getRuntime().addShutdownHook(
            Thread { runCatching(::cleanup) }.apply { name = "remote-shutdown-hook" }
        )
        startHeartbeatWatchdog()
        RemoteBootTrace.mark("CTOR_DONE")
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        Ln.i("$TAG: destroy()")
        AppWatchdog.stopWatching()
        InputControlUtils.setTouchCallback(null)
        cleanup()
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun version(): String = buildString {
        append("bridge=").append(if (NativeBridgeLib.LOADED) NativeBridgeLib.ping() else "not loaded")
        append(" uid=").append(Process.myUid())
        append(" pid=").append(Process.myPid())
    }

    override fun pid(): Int = Process.myPid()

    override fun watchdogState(): Int = AppWatchdog.state.value

    override fun watchdogTargetPackage(): String = AppWatchdog.targetPackage.orEmpty()

    // ── 亮屏与解锁 ──

    override fun unlock(credential: String?): Int =
        WakeUnlockController.unlock(credential.orEmpty())

    override fun testUnlock(credential: String?): Int =
        WakeUnlockController.testUnlock(credential.orEmpty())

    override fun lockAndSleep(): Int = WakeUnlockController.lockAndSleep()

    override fun isScreenOn(): Boolean =
        runCatching { ServiceManager.getPowerManager().isScreenOn(0) }.getOrDefault(true)

    override fun stopTargetApp(): Boolean {
        val target = AppWatchdog.targetPackage ?: run {
            Ln.i("$TAG: stopTargetApp skipped, watchdog never acquired a target")
            return false
        }
        return runCatching {
            ServiceManager.getActivityManager().forceStopPackage(target)
            Ln.i("$TAG: force-stopped $target")
            true
        }.getOrElse {
            Ln.w("$TAG: stopTargetApp failed: ${'$'}it")
            false
        }
    }

    override fun heartbeat(pid: Int) {
        appPid.set(pid)
    }

    override fun setup(piRoot: String?, logDir: String?, isDebug: Boolean): Boolean {
        // Android 12 起子进程会被 phantom process killer 收割，先关掉
        PermissionGrantHelper.disablePhantomProcessKiller()
        Ln.i("$TAG: setup, piRoot=$piRoot logDir=$logDir isDebug=$isDebug")
        return true
    }

    // ── 显示 ──

    override fun setVirtualDisplayMode(mode: Int): Boolean = when (mode) {
        DisplayMode.PRIMARY -> {
            VirtualDisplayManager.stop()
            virtualDisplayMode.set(mode)
            true
        }

        DisplayMode.BACKGROUND -> {
            PrimaryDisplayManager.stop()
            virtualDisplayMode.set(mode)
            true
        }

        else -> false
    }

    override fun setVirtualDisplayResolution(width: Int, height: Int, dpi: Int) {
        VirtualDisplayManager.setResolution(width, height, dpi)
    }

    override fun startVirtualDisplay(): Int = when (virtualDisplayMode.get()) {
        DisplayMode.PRIMARY -> PrimaryDisplayManager.start()
        DisplayMode.BACKGROUND -> VirtualDisplayManager.start().also { displayId ->
            if (displayId != DefaultDisplayConfig.DISPLAY_NONE) {
                PowerController.startUserActivityKeepAlive(displayId)
            }
        }

        else -> DefaultDisplayConfig.DISPLAY_NONE
    }

    override fun stopVirtualDisplay() {
        AppWatchdog.stopWatching()
        when (virtualDisplayMode.get()) {
            DisplayMode.PRIMARY -> PrimaryDisplayManager.stop()
            DisplayMode.BACKGROUND -> {
                PowerController.stopUserActivityKeepAlive()
                VirtualDisplayManager.stop()
            }
        }
    }

    /** 没有虚拟屏时返回 true：调用方据此判断「是否需要拉回」，无屏可拉即无需处理 */
    override fun isAppOnVirtualDisplay(packageName: String): Boolean {
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) return true
        return ActivityUtils.isAppOnDisplay(packageName, displayId)
    }

    override fun moveAppToVirtualDisplay(packageName: String): Boolean {
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) {
            Ln.w("$TAG: moveAppToVirtualDisplay: no active virtual display")
            return false
        }
        return ActivityUtils.repinAppToDisplay(packageName, displayId)
    }

    override fun setForceFullscreenOnVirtualDisplay(enabled: Boolean) {
        ActivityUtils.forceFullscreenOnVirtualDisplay = enabled
    }

    override fun setDisplayPower(on: Boolean) {
        PowerController.setDisplayPower(on)
    }

    /**
     * 改主屏分辨率会把整个系统的 UI 重排一遍，失败要报出去而不是吞掉——
     * 用户看到「已修改」却什么都没变，只会以为是自己屏幕不支持
     */
    override fun setForcedDisplaySize(width: Int, height: Int): Boolean {
        Ln.i("$TAG: setForcedDisplaySize(${width}x$height)")
        return runCatching { ScreenManager.setForcedDisplaySize(width, height) }
            .onFailure { Ln.e("$TAG: setForcedDisplaySize failed: ${it.message}") }
            .getOrDefault(false)
    }

    override fun clearForcedDisplaySize(): Boolean {
        Ln.i("$TAG: clearForcedDisplaySize")
        return runCatching { ScreenManager.clearForcedDisplaySize() }
            .onFailure { Ln.e("$TAG: clearForcedDisplaySize failed: ${it.message}") }
            .getOrDefault(false)
    }

    // ── 预览 ──

    override fun setMonitorSurface(surface: Surface?) {
        Ln.i("$TAG: setMonitorSurface(${surface != null})")
        VirtualDisplayManager.setMonitorSurface(surface)
        NativeBridgeLib.setPreviewSurface(surface)
    }

    override fun setTouchCallback(callback: ITouchEventCallback?) {
        InputControlUtils.setTouchCallback(callback)
    }

    // ── 预览上的手动操作；主屏模式下不接管输入 ──

    override fun touchDown(x: Int, y: Int) = withVirtualDisplay { InputControlUtils.down(x, y, 0, it) }

    override fun touchMove(x: Int, y: Int) = withVirtualDisplay { InputControlUtils.move(x, y, 0, it) }

    override fun touchUp(x: Int, y: Int) = withVirtualDisplay { InputControlUtils.up(x, y, 0, it) }

    private inline fun withVirtualDisplay(action: (Int) -> Unit) {
        if (virtualDisplayMode.get() == DisplayMode.PRIMARY) return
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId != DefaultDisplayConfig.DISPLAY_NONE) action(displayId)
    }

    // ── 执行 ──

    override fun setRunnerCallback(callback: IRunnerCallback?) = Unit

    override fun startRun(runPlanJson: String?): Boolean = false

    override fun stopRun(): Boolean = false

    override fun isRunning(): Boolean = false

    override fun saveCachedImage(path: String?): Boolean = false

    override fun nativeVersion(): String? = null

    /**
     * 逐项独立执行：一项失败不影响其余，返回实际授到的位
     * 失败不抛——app 侧据返回值决定要不要再引导用户手点
     */
    override fun grantPermissions(packageName: String?, uid: Int, permissions: Int): Int {
        if (packageName.isNullOrBlank()) return 0
        var granted = 0
        if (permissions and PrivilegedGrant.NOTIFICATION != 0 &&
            PermissionGrantHelper.grantNotificationPermission(packageName, uid)
        ) {
            granted = granted or PrivilegedGrant.NOTIFICATION
        }
        if (permissions and PrivilegedGrant.BATTERY != 0 &&
            PermissionGrantHelper.grantBatteryOptimizationExemption(packageName)
        ) {
            granted = granted or PrivilegedGrant.BATTERY
        }
        if (permissions and PrivilegedGrant.BACKGROUND != 0 &&
            PermissionGrantHelper.grantBackgroundUnrestricted(packageName, uid)
        ) {
            granted = granted or PrivilegedGrant.BACKGROUND
        }
        if (permissions and PrivilegedGrant.OVERLAY != 0 &&
            PermissionGrantHelper.grantFloatingWindowPermission(packageName, uid)
        ) {
            granted = granted or PrivilegedGrant.OVERLAY
        }
        // 服务 id 不用过 binder 传：特权进程跑的就是这个 APK，直接引用常量即可
        if (permissions and PrivilegedGrant.ACCESSIBILITY != 0 &&
            PermissionGrantHelper.grantAccessibilityService(AccessibilityHelperService.SERVICE_ID)
        ) {
            granted = granted or PrivilegedGrant.ACCESSIBILITY
        }
        if (permissions and PrivilegedGrant.STORAGE != 0 &&
            PermissionGrantHelper.grantStoragePermission(packageName, uid)
        ) {
            granted = granted or PrivilegedGrant.STORAGE
        }
        Ln.i("$TAG: grantPermissions($packageName) requested=$permissions granted=$granted")
        return granted
    }

    override fun isPackageInstalled(packageName: String): Boolean = try {
        FakeContext.get().packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        Ln.w("$TAG: isPackageInstalled: $packageName not found", e)
        false
    }

    /**
     * 逐项隔离，不共用一个 runCatching：原先四项串在一个块里，头一项抛了后面全跳过
     *
     * [ScreenManager.destroy] 尤其漏不得——它撤的是**物理主屏**的强改尺寸，
     * 漏掉的话用户会留在一块被改小的屏幕上，而且只能靠再拉一次特权进程才撤得回来。
     * 它自己按 flag 文件判要不要动手，没改过时是空操作
     */
    private fun cleanup() {
        step("bridge server") { BridgeServer.stop() }
        step("screen size") { ScreenManager.destroy() }
        step("power") { PowerController.destroy() }
        step("primary display") { PrimaryDisplayManager.stop() }
        step("virtual display") { VirtualDisplayManager.stop() }
    }

    private inline fun step(name: String, action: () -> Unit) {
        runCatching(action).onFailure { Ln.e("$TAG: cleanup $name failed: ${it.message}") }
    }

    /**
     * app 进程消失后特权进程必须自杀
     * linkToDeath 是主路径，这里兜住「binder 还没建立就崩了」的窗口
     */
    private fun startHeartbeatWatchdog() {
        Thread {
            while (!destroyed.get()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val pid = appPid.get()
                if (pid <= 0) continue
                if (!File("/proc/$pid").exists()) {
                    Ln.w("$TAG: app process (pid=$pid) gone, destroying remote service")
                    destroy()
                    return@Thread
                }
            }
        }.apply {
            name = "remote-heartbeat-watchdog"
            isDaemon = true
        }.start()
    }

    private companion object {
        const val TAG = "RemoteService"
        const val HEARTBEAT_INTERVAL_MS = 5_000L
    }
}
