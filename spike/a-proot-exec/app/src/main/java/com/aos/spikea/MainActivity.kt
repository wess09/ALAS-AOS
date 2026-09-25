package com.aos.spikea

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SpikeA"

class MainActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var logFile: File
    private lateinit var rootfsDir: File
    private lateinit var tmpDir: File
    private lateinit var nativeLibDir: String
    private val busy = AtomicBoolean(false)

    /** "ladder" (Spike A) or "phantom" (Spike C: spawn N long-lived guest processes). */
    private fun mode(): String = intent?.getStringExtra("mode") ?: "ladder"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nativeLibDir = applicationInfo.nativeLibraryDir
        logFile = File(filesDir, "spikea.log")
        rootfsDir = File(filesDir, "rootfs")
        tmpDir = File(filesDir, "tmp")

        val header = TextView(this).apply { textSize = 11f }
        val runButton = Button(this).apply { text = "RUN LADDER AGAIN" }
        val scroll = ScrollView(this)
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 8.5f
        }
        scroll.addView(logView)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(runButton)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(scroll, lp)
        }
        setContentView(root)
        header.text = "SpikeA targetSdk=${applicationInfo.targetSdkVersion} mode=${mode()}\n" +
            "nld=$nativeLibDir\nlog=${logFile.absolutePath}"

        runButton.setOnClickListener {
            if (mode() == "phantom") launchPhantom() else launchLadder()
        }
        if (mode() == "phantom") launchPhantom() else launchLadder()
    }

    private fun launchLadder() {
        if (!busy.compareAndSet(false, true)) return
        Thread {
            try {
                runLadder()
            } catch (t: Throwable) {
                log("FATAL ${t.javaClass.name}: ${t.message}")
            } finally {
                busy.set(false)
            }
        }.start()
    }

    // ---------------------------------------------------------------- logging

    private fun log(line: String) {
        Log.i(TAG, line)
        try {
            FileOutputStream(logFile, true).use {
                it.write((line + "\n").toByteArray())
                it.flush()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "log file write failed", t)
        }
        runOnUiThread {
            logView.append(line + "\n")
            (logView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun readFirstLine(path: String): String = try {
        File(path).readLines().firstOrNull() ?: "(empty)"
    } catch (t: Throwable) {
        "ERR ${t.javaClass.simpleName}: ${t.message}"
    }

    private fun nld(name: String) = File(nativeLibDir, name).absolutePath

    private fun guest(path: String) = File(rootfsDir, path).absolutePath

    // ------------------------------------------------------------ exec plumbing

    private class ExecResult(
        val exit: Int?,
        val out: String,
        val err: String,
        val exception: String?,
        val ms: Long,
        val timedOut: Boolean,
    )

    private val baseEnv: Map<String, String>
        get() = mapOf(
            "LD_LIBRARY_PATH" to nativeLibDir,
            "PROOT_TMP_DIR" to tmpDir.absolutePath,
            "PROOT_LOADER" to File(nativeLibDir, "libproot-loader.so").absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "HOME" to filesDir.absolutePath,
        )

    private fun exec(id: String, cmd: List<String>, extraEnv: Map<String, String> = emptyMap(), timeoutSec: Long = 30): ExecResult {
        log("[$id] CMD: ${cmd.joinToString(" ")}")
        if (extraEnv.isNotEmpty()) {
            log("[$id] ENV_EXTRA: ${extraEnv.entries.joinToString(" ") { "${it.key}=${it.value}" }}")
        }
        val pb = ProcessBuilder(cmd)
        pb.directory(rootfsDir)
        pb.environment().remove("LD_PRELOAD")
        pb.environment().putAll(baseEnv)
        pb.environment().putAll(extraEnv)
        val start = SystemClock.elapsedRealtime()
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            val ms = SystemClock.elapsedRealtime() - start
            val ex = "${e.javaClass.name}: ${e.message}"
            log("[$id] EXEC-FAILED after ${ms}ms: $ex")
            return ExecResult(null, "", "", ex, ms, false)
        }
        val outBuf = StringBuilder()
        val errBuf = StringBuilder()
        val tOut = Thread { proc.inputStream.bufferedReader().forEachLine { outBuf.append(it).append('\n') } }
        val tErr = Thread { proc.errorStream.bufferedReader().forEachLine { errBuf.append(it).append('\n') } }
        tOut.start()
        tErr.start()
        val finished = try {
            proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }
        if (!finished) proc.destroyForcibly()
        tOut.join(2000)
        tErr.join(2000)
        val ms = SystemClock.elapsedRealtime() - start
        return ExecResult(if (finished) proc.exitValue() else null, outBuf.toString(), errBuf.toString(), null, ms, !finished)
    }

    private fun step(id: String, cmd: List<String>, extraEnv: Map<String, String> = emptyMap()): Boolean {
        val r = exec(id, cmd, extraEnv)
        val status = when {
            r.exception != null -> "EXEC-FAILED"
            r.timedOut -> "TIMEOUT"
            r.exit == 0 -> "PASS"
            else -> "FAIL"
        }
        log("[$id] EXIT: ${r.exit ?: "n/a"}  DURATION: ${r.ms}ms  TIMEOUT: ${r.timedOut}")
        r.out.trimEnd().split('\n').forEach { if (it.isNotEmpty()) log("[$id] out| $it") }
        r.err.trimEnd().split('\n').forEach { if (it.isNotEmpty()) log("[$id] err| $it") }
        if (r.exception != null) log("[$id] EXCEPTION: ${r.exception}")
        val overall = if (status == "PASS") "PASS" else "$status(exit=${r.exit ?: "-"})"
        log("[$id] RESULT: $overall")
        results[id] = overall
        return status == "PASS"
    }

    private val results = LinkedHashMap<String, String>()

    // ------------------------------------------------------- phantom mode (Spike C)

    /**
     * Spike C: spawn [count] long-lived guest processes through proot and report
     * liveness periodically, so the host can watch PhantomProcessKiller trim them.
     *
     * Uses run-as-free self-observation: the guest writes its PIDs to
     * <rootfs>/tmp/phantoms.pids, which this process reads back and probes with
     * `Os.kill(pid, 0)` (same-uid, no extra permission needed).
     */
    private fun launchPhantom() {
        if (!busy.compareAndSet(false, true)) return
        Thread {
            try {
                runPhantom()
            } catch (t: Throwable) {
                log("FATAL ${t.javaClass.name}: ${t.message}")
            }
        }.start()
    }

    private fun guestPhantomScript(n: Int): String = """
        echo "GUEST_START pid=${'$'}${'$'}"
        : > /tmp/phantoms.pids
        i=0
        while [ "${'$'}i" -lt $n ]; do
          /bin/busybox sleep 3600 &
          echo "${'$'}!" >> /tmp/phantoms.pids
          i=${'$'}((i+1))
        done
        echo "GUEST_SPAWNED=${'$'}(/bin/busybox wc -l < /tmp/phantoms.pids)"
        echo "GUEST_PIDS=${'$'}(/bin/busybox cat /tmp/phantoms.pids | /bin/busybox tr '\n' ',')"
        while true; do
          alive=0
          for p in ${'$'}(/bin/busybox cat /tmp/phantoms.pids); do
            /bin/busybox kill -0 "${'$'}p" 2>/dev/null && alive=${'$'}((alive+1))
          done
          echo "GUEST_ALIVE=${'$'}alive/$n ts=${'$'}(/bin/busybox date +%s)"
          /bin/busybox sleep 15
        done
    """.trimIndent()

    private fun guestPidFile() = File(rootfsDir, "tmp/phantoms.pids")

    /** Count guest PIDs still alive, from this (app) process' point of view. */
    private fun countGuestAlive(): String {
        val f = guestPidFile()
        if (!f.exists()) return "n/a"
        val pids = try {
            f.readLines().mapNotNull { it.trim().toIntOrNull() }
        } catch (t: Throwable) {
            return "read-err"
        }
        var alive = 0
        for (p in pids) {
            try {
                Os.kill(p, 0)
                alive++
            } catch (t: Throwable) {
                // ESRCH -> dead & reaped
            }
        }
        return "$alive/${pids.size}"
    }

    private fun runPhantom() {
        val n = intent?.getIntExtra("count", 48) ?: 48
        val durationSec = intent?.getIntExtra("durationSec", 0) ?: 0
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val myPid = android.os.Process.myPid()
        log("")
        log("================================================================================")
        log("=== PHANTOM RUN $stamp  count=$n durationSec=$durationSec appPid=$myPid")
        log("================================================================================")
        provisionRootfs()
        val script = guestPhantomScript(n)
        // -b /dev:/dev is required: busybox ash redirects each background job's stdin
        // from /dev/null, and a freshly provisioned rootfs has no /dev node for it.
        val cmd = listOf(
            nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "-b", "/dev:/dev",
            "/bin/busybox", "sh", "-c", script,
        )
        log("[PHANTOM] CMD: ${cmd.joinToString(" ")}")
        val pb = ProcessBuilder(cmd)
        pb.directory(rootfsDir)
        pb.environment().remove("LD_PRELOAD")
        pb.environment().putAll(baseEnv)
        val start = SystemClock.elapsedRealtime()
        val proc = try {
            pb.start()
        } catch (e: IOException) {
            log("[PHANTOM] EXEC-FAILED: ${e.javaClass.name}: ${e.message}")
            busy.set(false)
            return
        }
        log("[PHANTOM] proot started (appPid=$myPid)")
        Thread { proc.inputStream.bufferedReader().forEachLine { log("[GUEST] $it") } }.start()
        Thread { proc.errorStream.bufferedReader().forEachLine { log("[GUEST-ERR] $it") } }.start()

        val stop = AtomicBoolean(false)
        Thread {
            while (!stop.get()) {
                try {
                    Thread.sleep(30_000)
                } catch (t: InterruptedException) {
                    return@Thread
                }
                if (stop.get()) return@Thread
                val up = (SystemClock.elapsedRealtime() - start) / 1000
                log(
                    "[HEARTBEAT] appPid=$myPid uptimeSec=$up prootAlive=${proc.isAlive} " +
                        "guestAlive=${countGuestAlive()}"
                )
            }
        }.start()

        if (durationSec > 0) {
            Thread {
                try {
                    Thread.sleep(durationSec * 1000L)
                } catch (t: InterruptedException) {
                    return@Thread
                }
                log("[PHANTOM] duration ${durationSec}s elapsed -> destroying proot")
                proc.destroyForcibly()
            }.start()
        }

        val exit = try {
            proc.waitFor()
        } catch (t: InterruptedException) {
            -1
        }
        stop.set(true)
        log("[PHANTOM] PROOT_EXIT=$exit after ${(SystemClock.elapsedRealtime() - start) / 1000}s")
    }

    // ------------------------------------------------------------------ ladder

    private fun runLadder() {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        results.clear()
        log("")
        log("================================================================================")
        log("=== RUN $stamp  targetSdk=${applicationInfo.targetSdkVersion}  sdkInt=${Build.VERSION.SDK_INT}")
        log("================================================================================")
        logDeviceFacts()
        provisionRootfs()
        runSteps()
        log("")
        log("=== RESULT TABLE ===")
        results.forEach { (k, v) -> log("RESULT $k = $v") }
        log("=== LADDER DONE ===")
    }

    private fun logDeviceFacts() {
        log("--- A0 DEVICE FACTS ---")
        log("model=${Build.MODEL} manufacturer=${Build.MANUFACTURER} device=${Build.DEVICE} hardware=${Build.HARDWARE}")
        log("androidRelease=${Build.VERSION.RELEASE} sdkInt=${Build.VERSION.SDK_INT} securityPatch=${Build.VERSION.SECURITY_PATCH}")
        log("abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        log("targetSdk=${applicationInfo.targetSdkVersion} minSdk=${applicationInfo.minSdkVersion} versionName=${packageManager.getPackageInfo(packageName, 0).versionName}")
        val flags = applicationInfo.flags
        val flagExtractNativeLibs = 1 shl 28 // ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS (hidden)
        log(
            "applicationFlags=0x${Integer.toHexString(flags)} " +
                "debuggable=${flags and ApplicationInfo.FLAG_DEBUGGABLE != 0} " +
                "extractNativeLibs=${flags and flagExtractNativeLibs != 0} " +
                "largeHeap=${flags and ApplicationInfo.FLAG_LARGE_HEAP != 0}"
        )
        log("nativeLibraryDir=$nativeLibDir")
        log("logFile=${logFile.absolutePath}")
        try {
            log("pageSize=${Os.sysconf(OsConstants._SC_PAGESIZE)}")
        } catch (t: Throwable) {
            log("pageSize=ERR ${t.javaClass.simpleName}: ${t.message}")
        }
        log("processSelinuxContext=${readFirstLine("/proc/self/attr/current")}")
        log("selinuxEnforce=${readFirstLine("/sys/fs/selinux/enforce")} (1=enforcing 0=permissive)")
        log("nld listing (name size canExecute):")
        File(nativeLibDir).listFiles()?.sortedBy { it.name }?.forEach {
            log("  nld| ${it.name} ${it.length()} ${it.canExecute()}")
        }
        val g = exec("A0-getenforce", listOf("/system/bin/getenforce"))
        log("A0-getenforce out=${g.out.trim()} err=${g.err.trim()} exit=${g.exit}")
    }

    private fun provisionRootfs() {
        log("--- PROVISION ROOTFS ---")
        tmpDir.mkdirs()
        for (d in listOf("bin", "usr/bin", "system/bin", "system/lib64", "tmp", "root", "dev", "proc")) {
            File(rootfsDir, d).mkdirs()
        }
        val assetFiles = listOf(
            "rootfs/bin/busybox",
            "rootfs/usr/bin/hello_static",
            "rootfs/usr/bin/hello_dynamic",
            "rootfs/system/lib64/libbusybox_app.so",
            "rootfs/system/lib64/libandroid-selinux.so",
            "rootfs/system/lib64/libpcre2-8.so",
            "rootfs/system/lib64/libtalloc.so",
            "rootfs/system/lib64/libandroid-shmem.so",
        )
        for (a in assetFiles) {
            val dst = File(filesDir, a)
            dst.parentFile?.mkdirs()
            try {
                assets.open(a).use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
                dst.setExecutable(true, false)
                log("provisioned asset $a -> ${dst.absolutePath} size=${dst.length()} canExec=${dst.canExecute()}")
            } catch (t: Throwable) {
                log("provision asset FAILED $a: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        val hostMap = mapOf(
            "/system/bin/linker64" to "system/bin/linker64",
            "/system/lib64/ld-android.so" to "system/lib64/ld-android.so",
            "/system/lib64/libc.so" to "system/lib64/libc.so",
            "/system/lib64/libm.so" to "system/lib64/libm.so",
            "/system/lib64/libdl.so" to "system/lib64/libdl.so",
            "/system/lib64/liblog.so" to "system/lib64/liblog.so",
            "/system/lib64/libc++.so" to "system/lib64/libc++.so",
        )
        for ((src, rel) in hostMap) {
            val s = File(src)
            if (!s.exists()) {
                log("host file missing, skipped: $src")
                continue
            }
            try {
                val dst = File(rootfsDir, rel)
                s.copyTo(dst, overwrite = true)
                dst.setExecutable(true, false)
                log("provisioned host $src -> $rel size=${s.length()} canExec=${dst.canExecute()}")
            } catch (t: Throwable) {
                log("provision host FAILED $src: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        File(rootfsDir, "usr/bin/hello_static").setExecutable(true, false)
        File(rootfsDir, "usr/bin/hello_dynamic").setExecutable(true, false)
        log("rootfs at ${rootfsDir.absolutePath}")
    }

    private fun runSteps() {
        log("--- LADDER ---")
        step("C1-hostsh", listOf("/system/bin/sh", "-c", "echo HOST_SH_OK; id; uname -m"))
        step(
            "P0-rootfs-modes",
            listOf("/system/bin/sh", "-c", "ls -l ${rootfsDir.absolutePath}/bin ${rootfsDir.absolutePath}/usr/bin ${rootfsDir.absolutePath}/system/bin ${rootfsDir.absolutePath}/system/lib64"),
        )
        step("A1-busybox-exec", listOf(nld("libbusybox.so"), "uname", "-m"))
        step("A1b-busybox-id", listOf(nld("libbusybox.so"), "id"))
        step("A1c-shim-busybox", listOf(nld("libspike_shim.so"), "busybox", nld("libbusybox.so"), "uname", "-m"))
        step("A2-proot-version", listOf(nld("libproot.so"), "--version"))

        val a3ok = step("A3-proot-ptrace", listOf(nld("libproot.so"), "-0", "/system/bin/sh", "-c", "echo PROOT_PTRACE_OK; uname -m"))
        if (!a3ok) {
            step(
                "A3r-proot-ptrace-noseccomp",
                listOf(nld("libproot.so"), "-0", "/system/bin/sh", "-c", "echo PROOT_PTRACE_OK; uname -m"),
                mapOf("PROOT_NO_SECCOMP" to "1"),
            )
        }

        step("P1-direct-static-exec", listOf(guest("usr/bin/hello_static")))
        step(
            "P1b-direct-busybox-exec",
            listOf(guest("bin/busybox"), "uname", "-m"),
            mapOf("LD_LIBRARY_PATH" to guest("system/lib64")),
        )
        step("P1c-direct-dynamic-exec", listOf(guest("usr/bin/hello_dynamic")))

        val a4ok = step("A4-proot-rootfs-busybox", listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "/bin/busybox", "uname", "-m"))
        if (!a4ok) {
            step(
                "A4r-proot-rootfs-busybox-verbose",
                listOf(nld("libproot.so"), "-v", "1", "-w", "/", "-r", rootfsDir.absolutePath, "/bin/busybox", "uname", "-m"),
                mapOf("PROOT_NO_SECCOMP" to "1", "PROOT_VERBOSE" to "1"),
            )
            step(
                "A4c-proot-rootfs-busybox-bind",
                listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "-b", "/system:/system", "/bin/busybox", "uname", "-m"),
                mapOf("PROOT_NO_SECCOMP" to "1"),
            )
        }

        step("A4b-proot-rootfs-static", listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "/usr/bin/hello_static"))
        step("A4e-proot-rootfs-dynamic", listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "/usr/bin/hello_dynamic"))
        step(
            "A5a-nested-exec-static",
            listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "/usr/bin/hello_dynamic", "--exec", "/usr/bin/hello_static"),
        )
        step(
            "A5b-nested-exec-busybox",
            listOf(nld("libproot.so"), "-w", "/", "-r", rootfsDir.absolutePath, "/usr/bin/hello_dynamic", "--exec", "/bin/busybox", "uname", "-m"),
        )
    }
}
