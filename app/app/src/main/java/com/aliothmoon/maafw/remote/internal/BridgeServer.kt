package com.aliothmoon.maafw.remote.internal

import android.os.SystemClock
import com.aliothmoon.maafw.bridge.InputControlUtils
import com.aliothmoon.maafw.bridge.NativeBridgeLib
import com.aliothmoon.maafw.third.Ln
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

/**
 * ALAS 桥服务：m0 Python 代理（m0-archive/spike/m0/agent/main.py）的特权进程内 Kotlin 重写。
 *
 * 监听 127.0.0.1:22301，协议为行分隔 JSON 请求/响应 + screencap 响应行后紧跟裸字节帧，
 * 端点 ping/screencap/click/swipe/shell。协议形状与 ALAS 侧冻结客户端
 * rootfs/patches/module/device/method/alasaos.py 逐字节兼容：每回复（含错误帧）echo 请求 id。
 */
object BridgeServer {

    private const val TAG = "BridgeServer"
    private const val LISTEN_HOST = "127.0.0.1"
    private const val LISTEN_PORT = 22301

    /** 请求行上限，防呆（m0 MAX_LINE 同款） */
    private const val MAX_LINE = 64 * 1024

    /** shell 端点 stdout/stderr 单向上限：响应是单行 JSON，转义后膨胀，客户端行上限 256KB */
    private const val SHELL_OUT_CAP = 64 * 1024
    private const val SHELL_PATH_PREFIX = "/system/bin:/system_ext/bin:/vendor/bin:"
    private const val DEFAULT_SHELL_TIMEOUT_SEC = 30.0
    private const val CLICK_HOLD_MS = 50L
    private const val SWIPE_STEP_MS = 16L

    /**
     * down 注入重试预算：游戏被 am start 拉上 VD 后 SurfaceFlinger 已出帧（screencap 可见），
     * 但 input 窗注册滞后 ~1s，此间 WAIT_FOR_FINISH 注入原生返 false（debug.md 同款）。
     * ALAS 单击失败即 ScriptError 死调度器，故在有界预算内重试把瞬态竞态对客户端隐身；
     * 真空 VD（游戏崩溃/未启动）仍报错，只是晚 ~3s。
     */
    private const val DOWN_RETRY_BUDGET_MS = 3000L
    private const val DOWN_RETRY_INTERVAL_MS = 200L
    private const val DEFAULT_SWIPE_MS = 500L
    private const val JOIN_AFTER_KILL_MS = 1000L

    /** 帧读取与触摸注入的全局串行锁：m0 实测并发抢设备通道会堵死，shell 不进这把锁 */
    val DEVICE_LOCK = ReentrantLock()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var startedAtMs = 0L
    private val clientCounter = AtomicInteger(0)

    /** 幂等；bind 失败记日志不抛——构造期调用方是 RemoteServiceImpl.init，抛了 binder 回不去 */
    @Synchronized
    fun start() {
        if (serverSocket != null) return
        // 原调用在被删的 MaaRunner.prepare 里；保持 native 输入路径确定性单 contact 语义
        runCatching { NativeBridgeLib.setContactSupport(false) }
            .onFailure { Ln.w("$TAG: setContactSupport(false) failed: ${it.message}") }
        val socket = ServerSocket()
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(LISTEN_HOST), LISTEN_PORT), 8)
        } catch (e: IOException) {
            Ln.e("$TAG: bind $LISTEN_HOST:$LISTEN_PORT failed", e)
            runCatching { socket.close() }
            return
        }
        serverSocket = socket
        startedAtMs = SystemClock.elapsedRealtime()
        thread(isDaemon = true, name = "alasaos-bridge-accept") { acceptLoop(socket) }
        Ln.i("$TAG: listening on $LISTEN_HOST:$LISTEN_PORT")
    }

    @Synchronized
    fun stop() {
        val socket = serverSocket ?: return
        serverSocket = null
        runCatching { socket.close() }
            .onFailure { Ln.w("$TAG: close server socket failed: ${it.message}") }
        Ln.i("$TAG: stopped")
    }

    fun isRunning(): Boolean = serverSocket != null

    private fun acceptLoop(socket: ServerSocket) {
        while (serverSocket === socket) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                // stop() 关 socket 唤醒 accept 是正常退出；引用比较防 stop→start 快速重开误杀新循环
                if (serverSocket !== socket) break
                Ln.w("$TAG: accept failed: ${e.message}")
                Thread.sleep(1000)
                continue
            }
            val no = clientCounter.incrementAndGet()
            thread(isDaemon = true, name = "alasaos-client-$no") { serveClient(client, no) }
        }
    }

    private fun serveClient(socket: Socket, clientNo: Int) {
        val conn = Conn(socket)
        Ln.i("$TAG: client #$clientNo connected from ${socket.remoteSocketAddress}")
        try {
            while (true) {
                val line = conn.readLine() ?: break
                if (line.isBlank()) continue
                val request = try {
                    JSONObject(line)
                } catch (e: JSONException) {
                    // 协议错误：回错误帧再断开，避免坏数据拖死 accept 循环
                    runCatching {
                        conn.sendLine(
                            JSONObject()
                                .put("id", JSONObject.NULL)
                                .put("ok", false)
                                .put("error", "bad request: ${e.message}")
                        )
                    }
                    break
                }
                handleRequest(conn, request)
            }
        } catch (e: IOException) {
            Ln.w("$TAG: client #$clientNo error: ${e.message}")
        } catch (e: Exception) {
            // 特权进程里线程未捕获异常会杀整个进程，兜底不能不写
            Ln.e("$TAG: client #$clientNo unexpected error", e)
        } finally {
            Ln.i("$TAG: client #$clientNo disconnected")
            conn.close()
        }
    }

    private fun handleRequest(conn: Conn, request: JSONObject) {
        val reqId: Any = request.opt("id") ?: JSONObject.NULL
        val method = if (request.isNull("method")) null else request.optString("method")

        fun reply(payload: JSONObject) {
            payload.put("id", reqId)
            conn.sendLine(payload)
        }

        try {
            when (method) {
                "ping" -> reply(
                    JSONObject()
                        .put("ok", true)
                        .put("pong", true)
                        .put("displayId", VirtualDisplayManager.getDisplayId())
                        .put("uptime", (SystemClock.elapsedRealtime() - startedAtMs) / 1000.0)
                )

                "screencap" -> handleScreencap(conn, ::reply)
                "click" -> handleClick(request, ::reply)
                "swipe" -> handleSwipe(request, ::reply)
                "shell" -> handleShell(request, ::reply)
                else -> reply(err(if (method == null) "unknown method: None" else "unknown method: '$method'"))
            }
        } catch (e: Exception) {
            // 单请求失败不能拖垮服务：记日志 + 错误帧，连接保持
            Ln.e("$TAG: request '$method' failed", e)
            runCatching { reply(err(e.message ?: e.toString())) }
        }
    }

    private fun handleScreencap(conn: Conn, reply: (JSONObject) -> Unit) {
        DEVICE_LOCK.withLock {
            if (!NativeBridgeLib.LOADED) {
                reply(err("native bridge not loaded"))
                return@withLock
            }
            val bytes = NativeBridgeLib.getFrameBufferBytes()
            if (bytes == null) {
                reply(err("no frame available"))
                return@withLock
            }
            val cfg = VirtualDisplayManager.getConfig()
            if (bytes.size != cfg.width * cfg.height * 3) {
                // VD 重启半途：native 帧缓冲还是旧尺寸，直接发给客户端会 reshape 炸掉
                reply(err("frame size mismatch: got ${bytes.size}, expected ${cfg.width}x${cfg.height}x3"))
                return@withLock
            }
            reply(
                JSONObject()
                    .put("ok", true)
                    .put("width", cfg.width)
                    .put("height", cfg.height)
                    .put("channels", 3)
                    .put("length", bytes.size)
            )
            conn.sendRaw(bytes)
        }
    }

    /** 有界重试的 down：见 DOWN_RETRY_BUDGET_MS 注释。失败事件未投递无悬挂状态，可安全重试。 */
    private fun downWithRetry(x: Int, y: Int, displayId: Int): Boolean {
        val deadline = SystemClock.uptimeMillis() + DOWN_RETRY_BUDGET_MS
        var attempts = 0
        while (true) {
            attempts++
            if (InputControlUtils.down(x, y, 0, displayId)) {
                if (attempts > 1) Ln.w("$TAG: touch down ok after $attempts attempts (window-register race absorbed)")
                return true
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining <= 0) {
                Ln.e("$TAG: touch down failed after $attempts attempts / ${DOWN_RETRY_BUDGET_MS}ms on display $displayId")
                return false
            }
            Thread.sleep(minOf(DOWN_RETRY_INTERVAL_MS, remaining))
        }
    }

    private fun handleClick(request: JSONObject, reply: (JSONObject) -> Unit) {
        val x = request.getInt("x")
        val y = request.getInt("y")
        DEVICE_LOCK.withLock {
            val displayId = VirtualDisplayManager.getDisplayId()
            if (displayId == VirtualDisplayManager.DISPLAY_NONE) {
                reply(err("no active virtual display"))
                return@withLock
            }
            if (!downWithRetry(x, y, displayId)) {
                reply(err("touch down failed (no touchable window on display $displayId within ${DOWN_RETRY_BUDGET_MS}ms)"))
                return@withLock
            }
            Thread.sleep(CLICK_HOLD_MS)
            if (!InputControlUtils.up(x, y, 0, displayId)) {
                reply(err("touch up failed"))
                return@withLock
            }
            reply(ok())
        }
    }

    private fun handleSwipe(request: JSONObject, reply: (JSONObject) -> Unit) {
        val x1 = request.getInt("x1")
        val y1 = request.getInt("y1")
        val x2 = request.getInt("x2")
        val y2 = request.getInt("y2")
        val durationMs = request.optLong("duration", DEFAULT_SWIPE_MS).coerceAtLeast(0)
        DEVICE_LOCK.withLock {
            val displayId = VirtualDisplayManager.getDisplayId()
            if (displayId == VirtualDisplayManager.DISPLAY_NONE) {
                reply(err("no active virtual display"))
                return@withLock
            }
            if (!downWithRetry(x1, y1, displayId)) {
                reply(err("touch down failed (no touchable window on display $displayId within ${DOWN_RETRY_BUDGET_MS}ms)"))
                return@withLock
            }
            val steps = (durationMs / SWIPE_STEP_MS).coerceAtLeast(1)
            val startMs = SystemClock.uptimeMillis()
            var lastX = x1
            var lastY = y1
            var moveFailed = false
            for (i in 1..steps) {
                val t = i.toDouble() / steps
                lastX = (x1 + (x2 - x1) * t).roundToInt()
                lastY = (y1 + (y2 - y1) * t).roundToInt()
                // 绝对时间轴控节奏：单步注入耗时不吃后续预算，整体时长仍 ≈ duration
                val sleepMs = startMs + durationMs * i / steps - SystemClock.uptimeMillis()
                if (sleepMs > 0) Thread.sleep(sleepMs)
                if (!InputControlUtils.move(lastX, lastY, 0, displayId)) {
                    moveFailed = true
                    break
                }
            }
            // 失败也要抬手：悬着的 ACTION_DOWN 会劫持虚拟屏触摸输入直到 VD 重启
            val upOk = InputControlUtils.up(lastX, lastY, 0, displayId)
            if (moveFailed || !upOk) {
                reply(err("swipe injection failed"))
                return@withLock
            }
            reply(ok())
        }
    }

    private fun handleShell(request: JSONObject, reply: (JSONObject) -> Unit) {
        val cmd = request.getString("cmd")
        val timeoutSec = request.optDouble("timeout", DEFAULT_SHELL_TIMEOUT_SEC)
        val process = ProcessBuilder("sh", "-c", cmd)
            .apply {
                val env = environment()
                // 剥掉 LD_LIBRARY_PATH：系统二进制被桥自身的库污染会起不来（m0 同款教训）
                env.remove("LD_LIBRARY_PATH")
                env["PATH"] = SHELL_PATH_PREFIX + (env["PATH"] ?: "")
            }
            .start()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        // stdout/stderr 分线程读全：单线程顺序读会在管道 buffer 撑满时与子进程互等死锁
        val outReader =
            thread(isDaemon = true, name = "alasaos-shell-out") { drain(process.inputStream, stdout) }
        val errReader =
            thread(isDaemon = true, name = "alasaos-shell-err") { drain(process.errorStream, stderr) }
        val timeoutMs = (timeoutSec * 1000).toLong().coerceAtLeast(1)
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
            outReader.join(JOIN_AFTER_KILL_MS)
            errReader.join(JOIN_AFTER_KILL_MS)
            reply(err("shell timeout ${timeoutSec}s"))
            return
        }
        outReader.join()
        errReader.join()
        reply(
            JSONObject()
                .put("ok", process.exitValue() == 0)
                .put("code", process.exitValue())
                .put("stdout", stdout.toString(Charsets.UTF_8.name()))
                .put("stderr", stderr.toString(Charsets.UTF_8.name()))
        )
    }

    private fun drain(input: InputStream, out: ByteArrayOutputStream) {
        val tmp = ByteArray(8192)
        while (true) {
            val n = try {
                input.read(tmp)
            } catch (e: IOException) {
                break
            }
            if (n < 0) break
            // 超限后照读照丢：不抽干管道，子进程写满 buffer 就憋死了
            val room = SHELL_OUT_CAP - out.size()
            if (room > 0) out.write(tmp, 0, minOf(n, room))
        }
    }

    private fun ok(): JSONObject = JSONObject().put("ok", true)

    private fun err(message: String): JSONObject =
        JSONObject().put("ok", false).put("error", message)

    /**
     * socket 之上的缓冲读：行 + 裸字节写在同一条流上混用不丢数据（m0 Conn 语义）。
     * 5 端点里没有上行裸帧（ocr 已剔除），故只需行读；BufferedInputStream 垫底，
     * 后续若加读帧端点，字节流位置天然衔接。
     */
    private class Conn(private val socket: Socket) {

        private val input = BufferedInputStream(socket.getInputStream(), BUFFER_SIZE)
        private val output = BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE)

        /** 读一行（不含 \n）；EOF 返回 null；超上限抛 IOException 由连接层断开 */
        fun readLine(): String? {
            val line = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return null
                if (b == '\n'.code) break
                line.write(b)
                if (line.size() > MAX_LINE) throw IOException("request line too long")
            }
            return line.toString(Charsets.UTF_8.name())
        }

        fun sendLine(payload: JSONObject) {
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
        }

        fun sendRaw(data: ByteArray) {
            output.write(data)
            output.flush()
        }

        fun close() {
            runCatching { socket.close() }
        }

        private companion object {
            const val BUFFER_SIZE = 8192
        }
    }
}
