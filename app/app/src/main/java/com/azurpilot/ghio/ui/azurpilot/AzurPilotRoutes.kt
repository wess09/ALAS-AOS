package com.azurpilot.ghio.ui.azurpilot

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.azurpilot.ghio.R

/**
 * AzurPilot 页内的一级分区
 *
 * 用**标签行**而不是第二条底部导航栏：AzurPilot 本身已经是外层的一个底部目的地，
 * 再叠一条底部栏就分不清哪条管哪个层级了。分区之间是平级的视图切换，正是标签的语义。
 */
enum class AzurPilotSection(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Overview("ap/overview", R.string.ap_section_overview, Icons.Filled.Dashboard),
    Config("ap/config", R.string.ap_section_config, Icons.Filled.Widgets),
    Logs("ap/logs", R.string.ap_section_logs, Icons.AutoMirrored.Filled.Article),
    Statistics("ap/stats", R.string.ap_section_stats, Icons.Filled.BarChart),
    Settings("ap/settings", R.string.ap_section_settings, Icons.Filled.Tune),
    ;

    companion object {
        fun ofRoute(route: String?): AzurPilotSection? =
            entries.firstOrNull { it.route == route }

        /** 详情页归属哪个分区，用来决定标签行停在哪儿 */
        fun parentOfRoute(route: String?): AzurPilotSection = when {
            route == null -> Overview
            route.startsWith(TASK_PREFIX) -> Config
            route in DETAIL_SETTINGS -> Settings
            else -> ofRoute(route) ?: Overview
        }

        const val TASK_ARG = "task"
        const val TASK = "ap/task/{$TASK_ARG}"
        fun task(name: String) = "ap/task/$name"
        private const val TASK_PREFIX = "ap/task/"

        const val INSTANCES = "ap/instances"
        const val ANNOUNCEMENT = "ap/announcement"
        const val MEOWFFICER = "ap/meowfficer"
        const val UPDATER = "ap/updater"
        const val DEPLOY = "ap/deploy"

        val DETAIL_SETTINGS = setOf(INSTANCES, ANNOUNCEMENT, MEOWFFICER, UPDATER, DEPLOY)
        val DETAILS = DETAIL_SETTINGS + setOf(TASK)
    }
}
