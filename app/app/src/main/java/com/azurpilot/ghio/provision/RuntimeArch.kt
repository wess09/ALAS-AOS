package com.azurpilot.ghio.provision

import android.os.Build

/**
 * Runtime（rootfs）支持的设备 ABI 清单与设备侧解析。
 *
 * 命名与 rootfs 构建脚本写进 BUILD_MANIFEST 的 `rootfs_arch`、CI 矩阵的 job 名、
 * Release 的 `runtimes` 键完全一致：新增架构要同时补 `rootfs/build/build-azurpilot.sh`
 * 的映射表与 `.github/workflows/rootfs.yml` 的矩阵。
 */
object RuntimeArch {
    const val ARM64 = "arm64-v8a"
    const val X86_64 = "x86_64"

    /** proot 不做指令翻译，只支持这些架构的原生执行 */
    val SUPPORTED = listOf(ARM64, X86_64)

    /** 设备首选的受支持 ABI（SUPPORTED_ABIS 自带偏好序）；全不支持时返回 null */
    fun deviceAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED }
}
