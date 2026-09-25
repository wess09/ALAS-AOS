package com.aliothmoon.azurpilot.constant

import com.aliothmoon.azurpilot.BuildConfig
import java.io.File

/**
 * /data/local/tmp 下的固定路径
 *
 * shell 与 root 身份都属主可写可执行；外部私有目录挂 noexec、app 私有目录 shell 进不去，
 * 跨进程标志文件只能落这里
 */
object ShellDirs {
    private const val BASE = "/data/local/tmp"
    private const val PKG = BuildConfig.APPLICATION_ID

    /** 屏幕电源状态标志，PowerController 跨进程读写 */
    val POWER_OFF_FLAG = File("$BASE/azurpilot_power_off_flag_$PKG")

    /** 强制分辨率状态标志，ScreenManager 跨进程读写 */
    val SCREEN_FLAG = File("$BASE/azurpilot_screen_flag_$PKG")
}
