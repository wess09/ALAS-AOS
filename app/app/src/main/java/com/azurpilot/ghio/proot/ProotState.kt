package com.azurpilot.ghio.proot

/** proot 会话（rootfs 内 wrapper + WebUI）的托管状态 */
enum class ProotPhase {
    /** 未启动或已停止 */
    IDLE,

    /** 启动前准备：清残留/写 DNS/铺 overlay/播种实例配置 */
    PREPARING,

    /** 热更新中（git.lyoko.io；断网超时降级不阻塞） */
    UPDATING,

    /** 拉起 proot 会话 / 等待 wrapper 就绪 / 崩溃重拉中 */
    STARTING,

    /** 会话存活（wrapper 22400 可达，WebUI 由 wrapper 监管） */
    RUNNING,

    /** 启动失败（detail 为人可读原因；ensureStarted 可重试） */
    FAILED,
}

data class ProotSnapshot(
    val phase: ProotPhase = ProotPhase.IDLE,

    /** 当前步骤或失败原因（人可读） */
    val detail: String = "",

    /** 最近一次热更新结果摘要（UPDATED/UNCHANGED/SKIPPED + 说明），未跑过为 null */
    val updateResult: String? = null,
) {
    /** FGS 保活判据：会话在任一活跃阶段都需要 app 进程钉前台 */
    val sessionActive: Boolean
        get() = phase == ProotPhase.PREPARING || phase == ProotPhase.UPDATING ||
                phase == ProotPhase.STARTING || phase == ProotPhase.RUNNING
}
