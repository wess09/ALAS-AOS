package com.aliothmoon.azurpilot.ui.i18n

import com.aliothmoon.azurpilot.R
import com.aliothmoon.azurpilot.domain.DiagnosticSeverity
import com.aliothmoon.azurpilot.domain.TaskCatalogGroup
import com.aliothmoon.azurpilot.i18n.UiText
import com.aliothmoon.azurpilot.i18n.uiTextFromProject
import com.aliothmoon.azurpilot.i18n.uiTextOf
import com.aliothmoon.azurpilot.i18n.uiTextPlural

/**
 * 领域值 -> 文案
 *
 * 一律返回 [UiText] 而不是 `@Composable fun …: String`：后者把文案锁死在组合里，
 * 通知栏、日志导出这些同样要展示同一句话的地方就得再写一遍
 */

fun DiagnosticSeverity.asUiText(): UiText = when (this) {
    DiagnosticSeverity.Warning -> uiTextOf(R.string.diagnostic_severity_warning)
    DiagnosticSeverity.Error -> uiTextOf(R.string.diagnostic_severity_error)
}

/** 合成「未分组」组的名字走资源；真实分组 label 是 PI 数据，原样展示 */
fun TaskCatalogGroup.asUiText(): UiText =
    if (isUngrouped) uiTextOf(R.string.tasks_ungrouped) else uiTextFromProject(label)

/** 有错误时才切到带错误数的那条；两条的选形数不同，不能合并 */
fun diagnosticsSummaryUiText(total: Int, errors: Int): UiText =
    if (errors > 0) {
        uiTextPlural(R.plurals.home_diagnostics_summary_with_errors, errors, total, errors)
    } else {
        uiTextOf(R.string.home_diagnostics_summary, total)
    }
