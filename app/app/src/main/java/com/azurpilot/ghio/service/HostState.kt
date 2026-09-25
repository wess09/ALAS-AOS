package com.azurpilot.ghio.service

import android.content.Context
import android.view.Surface
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.constant.DefaultDisplayConfig
import com.azurpilot.ghio.privileged.PrivilegedServicePort
import com.azurpilot.ghio.privileged.PrivilegedServiceState
import com.azurpilot.ghio.privileged.PermissionGateway
import com.azurpilot.ghio.privileged.ServiceBindResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * 外壳真实状态的唯一来源：特权进程连接态 + 桥可达性 + 虚拟屏 displayId
 *
 * 桥活在特权进程里（BridgeServer 随 RemoteServiceImpl 自起），app 侧够不到它的
 * isRunning()，可达性只能自己周期 ping——协议与 m0 桥同款（行分隔 JSON），
 * 与 AzurPilot 客户端打的是同一个端口
 *
 * displayId 只是「最近一次 startVirtualDisplay 的返回值」：特权进程一断，
 * 屏与桥随之作废，快照整份清零，等下次连接/探测重建
 */
class HostState(
    private val context: Context,
    private val servicePort: PrivilegedServicePort,
    private val permissionGateway: PermissionGateway,
    private val scope: CoroutineScope,
) {

    private val _snapshot = MutableStateFlow(HostSnapshot())
    val snapshot: StateFlow<HostSnapshot> = _snapshot.asStateFlow()

    /** 周期与按需探测共用一把锁，防并发探测挤在同一端口上 */
    private val probeMutex = Mutex()
    private val envMutex = Mutex()
    private val pingSeq = AtomicInteger(0)

    fun start() {
        scope.launch {
            servicePort.serviceState.collect { state ->
                _snapshot.update {
                    if (state == PrivilegedServiceState.Connected) {
                        it.copy(privilegedConnected = true)
                    } else {
                        HostSnapshot()
                    }
                }
            }
        }
        scope.launch(AppDispatchers.IO) {
            while (true) {
                probeBridgeNow()
                delay(BRIDGE_PROBE_INTERVAL_MS)
            }
        }
    }

    suspend fun probeBridgeNow(): Boolean = probeMutex.withLock {
        val reachable = runCatching { pingBridge() }
            .onFailure { Timber.d("bridge probe failed: %s", it.message) }
            .getOrDefault(false)
        _snapshot.update { it.copy(bridgeReachable = reachable) }
        reachable
    }

    /**
     * 「开始」链路：确保特权连接就绪 → setup() → startVirtualDisplay()
     * 桥随特权进程自起，无需显式操作
     *
     * 幂等：屏已存在直接成功；断线重连后（快照已清零）可再次触发
     */
    suspend fun ensureEnvironmentStarted(): Boolean {
        envMutex.withLock {
            if (_snapshot.value.vdDisplayId != DefaultDisplayConfig.DISPLAY_NONE) return@withLock
            val service = servicePort.serviceOrNull() ?: run {
                // 用户可能关闭过首启引导。启动环境必须走统一权限入口，未授权时
                // 直接弹出 Shizuku 授权，不能只 bind 后静默落成 Error。
                when (val result = permissionGateway.bindService()) {
                    ServiceBindResult.AlreadyConnected,
                    ServiceBindResult.Started -> Unit
                    else -> {
                        Timber.w("ensureEnvironmentStarted: privileged bind rejected: %s", result)
                        return@withLock
                    }
                }
                runCatching {
                    withTimeout(CONNECT_WAIT_MS) {
                        servicePort.serviceState.first { it != PrivilegedServiceState.Connecting }
                    }
                }
                servicePort.serviceOrNull() ?: run {
                    Timber.w("ensureEnvironmentStarted: privileged not connected")
                    return@withLock
                }
            }
            runCatching { service.setup(null, null, BuildConfig.DEBUG) }
                .onFailure { Timber.w(it, "setup failed") }
            val displayId = runCatching { service.startVirtualDisplay() }
                .getOrElse {
                    Timber.e(it, "startVirtualDisplay failed")
                    return@withLock
                }
            if (displayId == DefaultDisplayConfig.DISPLAY_NONE) {
                Timber.w("startVirtualDisplay returned DISPLAY_NONE")
                return@withLock
            }
            Timber.i("Environment started, displayId=%s", displayId)
            _snapshot.update { it.copy(vdDisplayId = displayId) }
            RunForegroundService.start(context)
        }
        // 建完屏（或本来就有屏）顺手刷一次桥态，UI 不必干等下个探测周期
        probeBridgeNow()
        return _snapshot.value.vdDisplayId != DefaultDisplayConfig.DISPLAY_NONE
    }

    /** 「停止」链路：只停虚拟屏；特权进程与桥留着，下次开始不用重连 */
    suspend fun stopEnvironment() {
        envMutex.withLock {
            servicePort.serviceOrNull()?.let { service ->
                runCatching { service.stopVirtualDisplay() }
                    .onFailure { Timber.w(it, "stopVirtualDisplay failed") }
            }
            _snapshot.update { it.copy(vdDisplayId = DefaultDisplayConfig.DISPLAY_NONE) }
        }
    }

    /**
     * 预览面挂载/摘除：挂机页 SurfaceView 的 Surface 交给特权进程渲染虚拟屏画面
     * （native bridge_preview 通道，零拷贝）。特权断线时静默失败——
     * 页面侧显示占位，连接恢复后随页面 active 翻转会重挂
     */
    fun attachPreviewSurface(surface: Surface) {
        runCatching { servicePort.serviceOrNull()?.setMonitorSurface(surface) }
            .onFailure { Timber.w(it, "attachPreviewSurface failed") }
    }

    fun detachPreviewSurface() {
        runCatching { servicePort.serviceOrNull()?.setMonitorSurface(null) }
            .onFailure { Timber.w(it, "detachPreviewSurface failed") }
    }

    /**
     * 全屏预览上的手动操作：坐标由 UI 换算到虚拟屏坐标系后传入，
     * 直通 AIDL 同名方法（oneway，内部带虚拟屏 displayId 注入，见 RemoteServiceImpl）。
     * 高频（一次滑动几十条），失败静默——特权断线时快照清零，注入也随之失去目标
     */
    fun touchDown(x: Int, y: Int) {
        runCatching { servicePort.serviceOrNull()?.touchDown(x, y) }
            .onFailure { Timber.w(it, "touchDown failed") }
    }

    fun touchMove(x: Int, y: Int) {
        runCatching { servicePort.serviceOrNull()?.touchMove(x, y) }
            .onFailure { Timber.w(it, "touchMove failed") }
    }

    fun touchUp(x: Int, y: Int) {
        runCatching { servicePort.serviceOrNull()?.touchUp(x, y) }
            .onFailure { Timber.w(it, "touchUp failed") }
    }

    /** m0 桥协议最小客户端：一行请求一行响应，判 "pong":true */
    private fun pingBridge(): Boolean {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(InetAddress.getByName(BRIDGE_HOST), BRIDGE_PORT),
                BRIDGE_CONNECT_TIMEOUT_MS,
            )
            socket.soTimeout = BRIDGE_READ_TIMEOUT_MS
            val request = """{"id":${pingSeq.incrementAndGet()},"method":"ping"}"""
            socket.getOutputStream().write((request + "\n").toByteArray(Charsets.UTF_8))
            val line = readLine(BufferedInputStream(socket.getInputStream()))
            return JSONObject(line).optBoolean("pong", false)
        }
    }

    private fun readLine(input: BufferedInputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            check(b >= 0) { "bridge closed connection" }
            if (b == '\n'.code) break
            line.write(b)
            check(line.size() <= MAX_REPLY_LINE) { "reply line too long" }
        }
        return line.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val BRIDGE_HOST = "127.0.0.1"
        const val BRIDGE_PORT = 22301
        const val BRIDGE_PROBE_INTERVAL_MS = 4_000L
        const val BRIDGE_CONNECT_TIMEOUT_MS = 1_500
        const val BRIDGE_READ_TIMEOUT_MS = 2_000
        const val CONNECT_WAIT_MS = 12_000L
        const val MAX_REPLY_LINE = 4 * 1024
    }
}

data class HostSnapshot(
    val privilegedConnected: Boolean = false,
    val bridgeReachable: Boolean = false,
    val vdDisplayId: Int = DefaultDisplayConfig.DISPLAY_NONE,
) {
    /** 环境整体活着：屏在且桥通；悬浮球与 FGS 的「活着」判据 */
    val environmentUp: Boolean
        get() = bridgeReachable && vdDisplayId != DefaultDisplayConfig.DISPLAY_NONE
}
