package com.azurpilot.ghio.ui.azurpilot.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.ApValue
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotStatCategory
import com.azurpilot.ghio.proot.AzurPilotStatSeries
import com.azurpilot.ghio.proot.AzurPilotStatTable
import com.azurpilot.ghio.proot.AzurPilotStatisticsReport
import com.azurpilot.ghio.proot.AzurPilotStatsQuery
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.ApEmptyState
import com.azurpilot.ghio.ui.azurpilot.ApErrorState
import com.azurpilot.ghio.ui.azurpilot.ApMetricCard
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.azurpilot.prettyCell
import com.azurpilot.ghio.ui.components.AppCard

private val DAY_CHOICES = listOf(1, 7, 30, 90, 365)
private val PERIOD_CHOICES = listOf("day", "week", "month")

/**
 * 统计：7 个分类，口径与 WebUI 一致（同一套 `statistics.report` 参数）
 *
 * 图表用 Canvas 自绘：项目里没有图表库，而这里只需要折线——为一条折线引入 ECharts 级别的
 * 依赖不划算。表格给横向滚动，列多的时候不挤成一团。
 */
@Composable
fun StatisticsSection(repository: AzurPilotRepository) {
    var category by remember { mutableStateOf(AzurPilotStatCategory.Resources) }
    var days by remember { mutableStateOf(7) }
    var period by remember { mutableStateOf("month") }
    var scope by remember { mutableStateOf("series") }
    var series by remember { mutableStateOf(0) }
    var task by remember { mutableStateOf<String?>(null) }

    var report by remember { mutableStateOf<AzurPilotStatisticsReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selected by repository.selectedInstance.collectAsStateWithLifecycle()
    val tick by repository.statisticsTick.collectAsStateWithLifecycle()

    val query = remember(category, days, period, scope, series, task) {
        AzurPilotStatsQuery(
            category = category,
            days = days,
            period = period,
            scope = scope,
            series = series,
            task = task,
        )
    }

    LaunchedEffect(query, selected, tick) {
        if (selected == null) {
            report = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        repository.statisticsReport(query)
            .onSuccess { report = it }
            .onFailure {
                report = null
                error = it.message
            }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = category.ordinal,
            edgePadding = AppTokens.Spacing.lg,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AzurPilotStatCategory.entries.forEach { entry ->
                Tab(
                    selected = entry == category,
                    onClick = { category = entry },
                    text = { Text(stringResource(statCategoryLabel(entry)), maxLines = 1) },
                )
            }
        }

        ApSectionColumn {
            CategoryControls(
                category = category,
                days = days,
                onDays = { days = it },
                period = period,
                onPeriod = { period = it },
                scope = scope,
                onScope = { scope = it },
                series = series,
                onSeries = { series = it },
                task = task,
                taskOptions = report?.taskOptions.orEmpty(),
                onTask = { task = it },
                onRefresh = if (category == AzurPilotStatCategory.Loot) {
                    { repository.refreshLoot() }
                } else {
                    null
                },
            )

            when {
                selected == null -> AppCard {
                    Text(stringResource(R.string.ap_stats_no_instance))
                }

                loading && report == null -> AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }

                report == null -> AppCard { ApErrorState(error ?: stringResource(R.string.ap_waiting_data)) }

                else -> {
                    val data = report!!
                    val charted = data.series.filter { it.points.isNotEmpty() }
                    // 指标、曲线、表格全空时要说清「这一类还没有数据」：
                    // 留一片空白会让人以为是界面坏了，而实际上是任务还没跑过
                    if (data.metrics.isEmpty() && charted.isEmpty() && data.tables.isEmpty()) {
                        AppCard {
                            ApEmptyState(
                                icon = Icons.Filled.BarChart,
                                title = stringResource(R.string.ap_stats_no_data),
                                hint = stringResource(R.string.ap_stats_no_data_hint),
                            )
                        }
                    }
                    if (data.metrics.isNotEmpty()) {
                        MetricsGrid(data)
                    }
                    if (charted.isNotEmpty()) {
                        AppCard(title = stringResource(R.string.ap_stats_trend)) {
                            ApLineChart(charted)
                        }
                    }
                    data.tables.forEach { table -> StatTableCard(table) }
                    data.notes.forEach { note ->
                        AppCard { Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricsGrid(report: AzurPilotStatisticsReport) {
    AppCard(title = stringResource(R.string.ap_stats_summary)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
            maxItemsInEachRow = 2,
        ) {
            report.metrics.forEach { metric ->
                ApMetricCard(
                    label = metric.label,
                    value = metric.value?.let { formatNumber(it) } ?: "—",
                    unit = metric.unit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 每个分类各自的控制项；没有可调项的分类就不显示这一行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryControls(
    category: AzurPilotStatCategory,
    days: Int,
    onDays: (Int) -> Unit,
    period: String,
    onPeriod: (String) -> Unit,
    scope: String,
    onScope: (String) -> Unit,
    series: Int,
    onSeries: (Int) -> Unit,
    task: String?,
    taskOptions: List<com.azurpilot.ghio.proot.AzurPilotTaskOption>,
    onTask: (String?) -> Unit,
    onRefresh: (() -> Unit)?,
) {
    val showsDays = category == AzurPilotStatCategory.Resources || category == AzurPilotStatCategory.Action
    val showsPeriod = category == AzurPilotStatCategory.Commission || category == AzurPilotStatCategory.Ships
    val showsScope = category == AzurPilotStatCategory.Research
    val showsTask = category == AzurPilotStatCategory.Loot && taskOptions.isNotEmpty()
    if (!showsDays && !showsPeriod && !showsScope && !showsTask && onRefresh == null) return

    AppCard {
        if (showsDays) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                DAY_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = days == choice,
                        onClick = { onDays(choice) },
                        label = { Text(stringResource(R.string.ap_stats_days, choice)) },
                    )
                }
            }
        }
        if (showsPeriod) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                PERIOD_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = period == choice,
                        onClick = { onPeriod(choice) },
                        label = {
                            Text(
                                stringResource(
                                    when (choice) {
                                        "day" -> R.string.ap_stats_period_day
                                        "week" -> R.string.ap_stats_period_week
                                        else -> R.string.ap_stats_period_month
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (showsScope) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                FilterChip(
                    selected = scope == "series",
                    onClick = { onScope("series") },
                    label = { Text(stringResource(R.string.ap_stats_scope_series)) },
                )
                FilterChip(
                    selected = scope == "consumable",
                    onClick = { onScope("consumable") },
                    label = { Text(stringResource(R.string.ap_stats_scope_consumable)) },
                )
            }
            // 期数是 0..20 的整数，0 表示「最新记录的那一期」
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
            ) {
                (0..9).forEach { index ->
                    FilterChip(
                        selected = series == index,
                        onClick = { onSeries(index) },
                        label = {
                            Text(
                                if (index == 0) {
                                    stringResource(R.string.ap_stats_series_latest)
                                } else {
                                    stringResource(R.string.ap_stats_series_n, index)
                                },
                            )
                        },
                    )
                }
            }
        }
        if (showsTask) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                FilterChip(
                    selected = task == null,
                    onClick = { onTask(null) },
                    label = { Text(stringResource(R.string.ap_stats_task_all)) },
                )
                taskOptions.filter { it.count > 0 }.forEach { option ->
                    FilterChip(
                        selected = task == option.key,
                        onClick = { onTask(if (task == option.key) null else option.key) },
                        label = { Text("${option.label} (${option.count})") },
                    )
                }
            }
        }
        if (onRefresh != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ap_stats_refresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.ap_stats_refresh))
                }
            }
        }
    }
}

/** 折线图：多条序列共用一套纵轴，颜色按固定次序取（同一份数据每次画出来一致） */
@Composable
private fun ApLineChart(series: List<AzurPilotStatSeries>) {
    val colors = chartPalette()
    val allValues = series.flatMap { line -> line.points.map { it.value } }
    val minValue = allValues.minOrNull() ?: 0.0
    val maxValue = allValues.maxOrNull() ?: 1.0
    val span = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            series.forEachIndexed { index, line ->
                val points = line.points
                if (points.size < 2) {
                    // 单点画不出折线，给一个圆点，否则这条序列看起来像不存在
                    if (points.size == 1) {
                        val y = size.height * (1f - ((points[0].value - minValue) / span).toFloat())
                        drawCircle(colors[index % colors.size], radius = 4f, center = Offset(size.width / 2f, y))
                    }
                    return@forEachIndexed
                }
                val path = Path()
                points.forEachIndexed { pointIndex, point ->
                    val x = size.width * pointIndex / (points.size - 1).toFloat()
                    val y = size.height * (1f - ((point.value - minValue) / span).toFloat())
                    if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = colors[index % colors.size],
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
            Text(
                text = formatNumber(minValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatNumber(maxValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRowChips(
            series = series,
            colors = colors,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(series: List<AzurPilotStatSeries>, colors: List<Color>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
    ) {
        series.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(AppTokens.Indicator.dot)
                        .background(colors[index % colors.size], androidx.compose.foundation.shape.CircleShape),
                )
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = AppTokens.Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun chartPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.error,
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.tertiaryContainer,
)

/**
 * 明细表：横向滚动 + 竖向滚动
 *
 * 列数由服务端定（最多 9 列），手机宽度放不下，横滚比压成小字可读。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatTableCard(table: AzurPilotStatTable) {
    AppCard(title = table.title) {
        if (table.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.ap_stats_no_rows),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val horizontal = rememberScrollState()
            val vertical = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal),
            ) {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    table.columns.forEach { column ->
                        Text(
                            text = column,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .padding(AppTokens.Spacing.xs),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                table.rows.forEach { row ->
                    Row {
                        table.columns.indices.forEach { index ->
                            Text(
                                text = row.getOrNull(index).prettyCell(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(CELL_WIDTH)
                                    .padding(AppTokens.Spacing.xs),
                            )
                        }
                    }
                }
            }
        }
        if (table.note.isNotEmpty()) {
            Text(
                text = table.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CELL_WIDTH = 108.dp

private fun statCategoryLabel(category: AzurPilotStatCategory): Int = when (category) {
    AzurPilotStatCategory.Resources -> R.string.ap_stats_cat_resources
    AzurPilotStatCategory.Action -> R.string.ap_stats_cat_action
    AzurPilotStatCategory.Opsi -> R.string.ap_stats_cat_opsi
    AzurPilotStatCategory.Commission -> R.string.ap_stats_cat_commission
    AzurPilotStatCategory.Ships -> R.string.ap_stats_cat_ships
    AzurPilotStatCategory.Loot -> R.string.ap_stats_cat_loot
    AzurPilotStatCategory.Research -> R.string.ap_stats_cat_research
}

/** 统计数字统一不给小数尾巴；小数只在真的存在时保留一位 */
internal fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value)
    }
