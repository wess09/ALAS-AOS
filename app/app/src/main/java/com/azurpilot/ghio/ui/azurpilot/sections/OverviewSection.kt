package com.azurpilot.ghio.ui.azurpilot.sections



import androidx.compose.foundation.Image
import com.azurpilot.ghio.ui.azurpilot.ApMotion
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotResource
import com.azurpilot.ghio.proot.AzurPilotStatus
import com.azurpilot.ghio.proot.AzurPilotTask
import com.azurpilot.ghio.proot.AzurPilotTaskState
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.apEnter
import com.azurpilot.ghio.ui.azurpilot.ApEmptyState
import com.azurpilot.ghio.ui.azurpilot.ApKeyValueRow
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.azurpilot.ApStatusPill
import com.azurpilot.ghio.ui.azurpilot.formatResource
import com.azurpilot.ghio.ui.azurpilot.instanceStatusColor
import com.azurpilot.ghio.ui.azurpilot.instanceStatusText
import com.azurpilot.ghio.ui.azurpilot.isRecorded
import com.azurpilot.ghio.ui.azurpilot.resourceLabel
import com.azurpilot.ghio.ui.azurpilot.taskStateColor
import com.azurpilot.ghio.ui.azurpilot.taskStateText
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.AppLabeledControlRow

/** 总览：调度器状态、资源、实时画面、任务计划 */
@Composable
fun OverviewSection(repository: AzurPilotRepository) {
    val overview by repository.overview.collectAsStateWithLifecycle()
    val startup by repository.startup.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()

    ApSectionColumn {
        if (selected == null) {
            AppCard {
                ApEmptyState(
                    icon = Icons.Filled.Schedule,
                    title = stringResource(R.string.ap_overview_no_instance),
                    hint = stringResource(R.string.ap_instance_none),
                )
            }
            return@ApSectionColumn
        }

        val data = overview
        AppCard(
            title = stringResource(R.string.ap_overview_scheduler),
            modifier = Modifier.apEnter(0),
        ) {
            if (data == null) {
                ApEmptyState(Icons.Filled.Schedule, stringResource(R.string.ap_waiting_data))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 状态是离散的几档，换档时交叉淡入而不是硬切，眼睛才跟得上「刚刚变了」
                    AnimatedContent(
                        targetState = data.status,
                        transitionSpec = {
                            fadeIn(ApMotion.effects(ApMotion.Medium2, ApMotion.EmphasizedDecelerate)) togetherWith
                                fadeOut(ApMotion.effects(ApMotion.Short2, ApMotion.StandardAccelerate))
                        },
                        label = "schedulerStatus",
                    ) { status ->
                        ApStatusPill(
                            text = instanceStatusText(status),
                            container = instanceStatusColor(status).copy(alpha = 0.16f),
                            content = instanceStatusColor(status),
                        )
                    }
                    TaskCounts(data.tasks)
                }
                ApKeyValueRow(stringResource(R.string.ap_overview_revision), data.revision.take(12))
            }
            AppLabeledControlRow(
                label = stringResource(R.string.ap_overview_startup_auto),
                trailing = {
                    Switch(
                        checked = startup?.enabled == true,
                        onCheckedChange = { repository.setStartup(enabled = it) },
                    )
                },
            )
            SchedulerButton(repository, running = data?.status == AzurPilotStatus.Running)
        }

        if (data != null && data.resources.isNotEmpty()) {
            AppCard(
                title = stringResource(R.string.ap_overview_resources),
                modifier = Modifier.apEnter(1),
            ) {
                ResourceGrid(data.resources)
            }
        }

        PreviewCard(repository, Modifier.apEnter(2))

        if (data != null) {
            AppCard(
                title = stringResource(R.string.ap_overview_tasks),
                modifier = Modifier.apEnter(3),
            ) {
                if (data.tasks.isEmpty()) {
                    ApEmptyState(
                        icon = Icons.Filled.TaskAlt,
                        title = stringResource(R.string.ap_overview_no_tasks),
                        hint = stringResource(R.string.ap_overview_no_tasks_hint),
                    )
                } else {
                    TaskPlan(data.tasks, onRunNow = repository::runTask)
                }
            }
        }
    }
}

/**
 * 调度器启停
 *
 * 总览是「一眼看状态并动手」的那一页，启停按钮必须在它上面。状态是 error 时按钮回到「启动」
 * ——那正是重试的入口；进行中的那一次会禁用按钮，stop 在服务端最长要等 30s 才回话。
 */
@Composable
private fun SchedulerButton(repository: AzurPilotRepository, running: Boolean) {
    val busy by repository.schedulerBusy.collectAsStateWithLifecycle()
    Button(
        onClick = { repository.setSchedulerRunning(!running) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(AppTokens.IconSize.md),
        )
        // 文案与图标一起交叉淡入：同一颗按钮的三种态，硬切会闪一下「换了个按钮」
        AnimatedContent(
            targetState = when {
                busy -> R.string.ap_scheduler_processing
                running -> R.string.ap_scheduler_stop
                else -> R.string.ap_scheduler_start
            },
            transitionSpec = {
                fadeIn(ApMotion.effects(ApMotion.Short4, ApMotion.EmphasizedDecelerate)) togetherWith
                    fadeOut(ApMotion.effects(ApMotion.Short2, ApMotion.StandardAccelerate))
            },
            label = "schedulerAction",
        ) { label ->
            Text(
                text = stringResource(label),
                modifier = Modifier.padding(start = AppTokens.Spacing.sm),
            )
        }
    }
}

@Composable
private fun TaskCounts(tasks: List<AzurPilotTask>) {
    Text(
        text = stringResource(
            R.string.ap_overview_counts,
            tasks.count { it.state == AzurPilotTaskState.Running },
            tasks.count { it.state == AzurPilotTaskState.Pending },
            tasks.count { it.state == AzurPilotTaskState.Waiting },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
}

/** 资源格：两列铺开，零值显示占位符（服务端用 0 + 哨兵时间戳表达「从未同步」） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceGrid(resources: List<AzurPilotResource>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        maxItemsInEachRow = 2,
    ) {
        resources.forEach { resource ->
            val recorded = isRecorded(resource.record)
            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(AppTokens.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs),
                ) {
                    Text(
                        text = resourceLabel(resource.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatResource(resource.value, recorded),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val limit = resource.limit
                    if (recorded && limit != null && limit > 0) {
                        Text(
                            text = stringResource(
                                R.string.ap_overview_resource_limit,
                                formatResource(limit, true),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 实时画面：网关只在订阅期间推帧，这里不做抓拍 */
@Composable
private fun PreviewCard(repository: AzurPilotRepository, modifier: Modifier = Modifier) {
    val preview by repository.preview.collectAsStateWithLifecycle()
    AppCard(title = stringResource(R.string.ap_overview_preview), modifier = modifier) {
        // 逐帧硬换会闪；交叉淡入让「画面在动」这件事看起来是连续的
        Crossfade(
            targetState = preview?.image,
            animationSpec = ApMotion.effects(ApMotion.Medium2, ApMotion.EmphasizedDecelerate),
            label = "previewFrame",
        ) { image ->
            if (image == null) {
                ApEmptyState(
                    icon = Icons.Filled.Image,
                    title = stringResource(R.string.ap_preview_waiting),
                    hint = stringResource(R.string.ap_preview_waiting_hint),
                )
            } else {
                // 虚拟屏是横屏 16:9，按同一比例留位，换帧时不会跳高度
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = stringResource(R.string.ap_overview_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        preview?.capturedAt?.let {
            Text(
                text = stringResource(R.string.ap_preview_captured_at, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 任务计划：顺序沿用网关的排序（运行中 → 待运行 → 等待） */
@Composable
private fun TaskPlan(tasks: List<AzurPilotTask>, onRunNow: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        tasks.forEachIndexed { index, task ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = taskStateColor(task.state),
                    modifier = Modifier.size(AppTokens.IconSize.md),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = task.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${taskStateText(task.state)} · ${task.nextRun}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRunNow(task.name) }) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.ap_run_now),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
