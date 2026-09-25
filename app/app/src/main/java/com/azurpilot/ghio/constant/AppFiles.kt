package com.azurpilot.ghio.constant

import android.content.Context
import java.io.File

/**
 * 外部私有目录（getExternalFilesDir(null)）下的固定子路径
 * 特权进程是 shell 身份，只有这里既写得进又和 app 看到同一份
 *
 * 两棵子树：[LOG_DIR] 运行日志、[DEBUG_DIR] 启动诊断
 */
object AppFiles {
    /** 运行日志树：Timber、定时触发记录、缓存帧 */
    const val LOG_DIR = "log"

    /** 启动诊断树：服务绑定/启动 trace、root launcher 输出 */
    const val DEBUG_DIR = "debug"

    /** [LOG_DIR] 下：未捕获异常现场 */
    const val CRASH_DIR = "crash"
}

