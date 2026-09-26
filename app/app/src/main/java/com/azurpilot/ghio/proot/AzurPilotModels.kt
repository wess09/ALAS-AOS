package com.azurpilot.ghio.proot

import android.graphics.Bitmap

/** 实例状态；网关的 `STATES` 1..4 */
enum class AzurPilotStatus {
    Running, Stopped, Error, Updating;

    companion object {
        fun of(raw: String?): AzurPilotStatus = when (raw) {
            "running" -> Running
            "error" -> Error
            "updating" -> Updating
            else -> Stopped
        }
    }
}

/** 调度器里一条任务的状态 */
enum class AzurPilotTaskState { Running, Pending, Waiting;
    companion object {
        fun of(raw: String?): AzurPilotTaskState = when (raw) {
            "running" -> Running
            "pending" -> Pending
            else -> Waiting
        }
    }
}

data class AzurPilotInstance(
    val name: String,
    val status: AzurPilotStatus,
    val currentTask: String?,
    val serial: String,
    val server: String,
)

data class AzurPilotTask(
    val name: String,
    val nextRun: String,
    val pending: Boolean,
    val state: AzurPilotTaskState,
)

/** 总览里的资源卡：`limit`/`total`/`record` 只有配置里声明了的资源才有 */
data class AzurPilotResource(
    val name: String,
    val label: String,
    val value: Double?,
    val limit: Double?,
    val total: Double?,
    val record: String?,
)

data class AzurPilotOverview(
    val instance: String,
    val revision: String,
    val status: AzurPilotStatus,
    val tasks: List<AzurPilotTask>,
    val resources: List<AzurPilotResource>,
)

data class AzurPilotConfig(
    val instance: String,
    val revision: String,
    val values: ApConfigValues,
)

/** 一次 `config.patch`：路径恒为 `Task.Group.Argument` */
data class AzurPilotChange(val path: String, val value: ApValue)

data class AzurPilotLogEntry(val id: Long, val level: String, val text: String)

data class AzurPilotPreview(
    val capturedAt: String?,
    val runId: String?,
    val image: Bitmap?,
)

data class AzurPilotStartup(
    val enabled: Boolean,
    val remember: Boolean,
    val run: List<String>,
)

data class AzurPilotDeployField(
    val key: String,
    val type: String,
    val label: String,
    val help: String,
    val value: ApValue,
    val options: List<ApValue>,
)

data class AzurPilotDeployGroup(val key: String, val label: String, val fields: List<AzurPilotDeployField>)

data class AzurPilotRemoteAccess(
    val enabled: Boolean,
    val state: String,
    val address: String,
    val error: String,
)

data class AzurPilotDeploySettings(
    val groups: List<AzurPilotDeployGroup>,
    val notice: String,
    val demo: Boolean,
    val remote: AzurPilotRemoteAccess?,
)

data class AzurPilotUpdateStatus(
    val state: String,
    val localHead: String?,
    val upstreamHead: String?,
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val available: Boolean,
    val busy: Boolean,
    val canApply: Boolean,
    val canCancel: Boolean,
    val error: String,
    val managedByAndroid: Boolean,
) {
    val hasUpdate: Boolean get() = available || behind > 0
}

data class AzurPilotCommit(val sha: String, val author: String, val date: String, val message: String)

data class AzurPilotCommitHistory(
    val entries: List<AzurPilotCommit>,
    val total: Int,
    val hasMore: Boolean,
    val localHead: String?,
    val upstreamHead: String?,
)

data class AzurPilotAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val url: String?,
)

data class AzurPilotStatPoint(val time: String, val value: Double, val source: String?)

data class AzurPilotStatSeries(val key: String, val label: String, val points: List<AzurPilotStatPoint>)

data class AzurPilotStatTable(
    val title: String,
    val columns: List<String>,
    val rows: List<List<ApValue>>,
    val note: String,
    val defaultSortIndex: Int?,
    val defaultSortDescending: Boolean,
)

data class AzurPilotMetric(val label: String, val value: Double?, val unit: String, val icon: String?)

data class AzurPilotTaskOption(val key: String, val label: String, val count: Int)

data class AzurPilotStatisticsReport(
    val instance: String,
    val category: String,
    val month: String,
    val metrics: List<AzurPilotMetric>,
    val series: List<AzurPilotStatSeries>,
    val tables: List<AzurPilotStatTable>,
    val notes: List<String>,
    val taskOptions: List<AzurPilotTaskOption>,
)

/** 统计分类；顺序与 WebUI 的分段控件一致 */
enum class AzurPilotStatCategory(val key: String) {
    Resources("resources"),
    Action("action"),
    Opsi("opsi"),
    Commission("commission"),
    Ships("ships"),
    Loot("loot"),
    Research("research"),
}

/** `statistics.report` 的可选参数，界面控件直接绑到它 */
data class AzurPilotStatsQuery(
    val category: AzurPilotStatCategory,
    val days: Int = 7,
    val month: String? = null,
    val period: String = "month",
    val series: Int = 0,
    val scope: String = "series",
    val task: String? = null,
)

data class AzurPilotStatistics(
    val instance: String,
    val resource: String,
    val points: List<AzurPilotStatPoint>,
    val truncated: Boolean,
)

data class AzurPilotTalent(val name: String, val level: Int?, val inferred: Boolean)

data class AzurPilotCat(
    val cat: String,
    val level: Int?,
    val maxed: Boolean,
    val fixed: Boolean,
    val source: String?,
    val note: String?,
    val talents: List<AzurPilotTalent>,
    val score: Double?,
    val tier: String?,
    val verdict: String?,
    val headline: String?,
    val reason: String?,
    val adviceReason: String?,
    val costText: String?,
    val pointsSpent: Int?,
    val targets: List<String>,
    val tags: List<String>,
)

data class AzurPilotMeowfficerReport(
    val generatedAt: String,
    val count: Int,
    val cats: List<AzurPilotCat>,
)

data class AzurPilotDiagnostic(
    val code: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)

data class AzurPilotShopValidation(val valid: Boolean, val diagnostics: List<AzurPilotDiagnostic>)

/** 可导入的配置文件（`config/import` 下的 json） */
data class AzurPilotImportable(val name: String, val modified: Double)

/** 网关上跑着的一个工具任务 */
data class AzurPilotToolInfo(val name: String?, val alive: Boolean)
