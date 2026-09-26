package com.azurpilot.ghio.ui.azurpilot.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.ApValue
import com.azurpilot.ghio.proot.AzurPilotEditorState
import com.azurpilot.ghio.proot.AzurPilotField
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.toValueMap
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.ApEmptyState
import com.azurpilot.ghio.ui.azurpilot.ApErrorState
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.AppLabeledControlRow
import com.azurpilot.ghio.ui.components.AppNavigationRow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** FleetInfo 是唯一不走通用表单的任务：它的结果是一个隐藏的 JSON 舰队快照 */
private const val FLEET_INFO = "FleetInfo"

/** 自启开关挂在系统设置任务页上——它描述的是「整机启动时怎么办」，不属于任何一个任务参数 */
private const val STARTUP_TASK = "Alas"

private val FLEET_ROLES = listOf("vanguard", "main", "submarine")
private val FLEET_NUMBERS = listOf(1, 2, 3, 4, 5, 6)

/**
 * 配置：任务目录
 *
 * 目录结构与名称全部取自 `schema.get`——菜单分组由 `menu.json` 定，任务名与分组标题走运行时翻译，
 * 所以上游加了任务这里自动就有了。
 */
@Composable
fun ConfigSection(repository: AzurPilotRepository, onOpenTask: (String) -> Unit) {
    val schema by repository.schema.collectAsStateWithLifecycle()
    val schemaError by repository.schemaError.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()

    LaunchedEffect(selected) { selected?.let { repository.configEditor.load(it) } }

    ApSectionColumn {
        when {
            selected == null -> AppCard {
                ApEmptyState(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.ap_overview_no_instance),
                    hint = stringResource(R.string.ap_instance_none),
                )
            }

            schema == null && schemaError != null -> AppCard {
                ApErrorState(schemaError!!) { repository.ensureSchema(force = true) }
            }

            schema == null -> AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            else -> {
                val data = schema!!
                data.menu.forEach { group ->
                    AppCard(
                        title = data.menuTitle(group.key),
                        collapsible = true,
                    ) {
                        group.tasks.forEachIndexed { index, task ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            AppNavigationRow(
                                label = data.taskTitle(task),
                                description = if (group.isTool) {
                                    stringResource(R.string.ap_config_tool_hint)
                                } else {
                                    task
                                },
                                onClick = { onOpenTask(task) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一个任务的配置页
 *
 * 参数、分组、控件的形状全部由 schema 决定；界面只负责「渲染 + 提交」，
 * 所以 97 个任务、2500 多个参数不需要各写一遍。
 *
 * 布局：搜索框与分组跳转**钉在顶部**，下面才是可滚动的参数卡。一个任务动辄八九组、上百个参数，
 * 跳转栏跟着一起滚走就等于没有。
 */
@Composable
fun TaskConfigPage(repository: AzurPilotRepository, task: String) {
    val schema by repository.schema.collectAsStateWithLifecycle()
    val editor by repository.configEditor.state.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()
    val logs by repository.logs.collectAsStateWithLifecycle()
    val startup by repository.startup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var pendingRun by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) { selected?.let { repository.configEditor.load(it) } }

    val data = schema ?: return
    // 这一层的键是**分组名**，再往里才是参数名；取参数值必须走 group/argument 两级
    val taskValues = editor.values[task].orEmpty()
    val isTool = data.toolTasks.contains(task)
    val isFleetInfo = task == FLEET_INFO

    // 搜索按 分组名 / 参数显示名 / 原始 `Group.Arg` 匹配——中文翻译想不起来时可以直接敲参数名
    val groups = remember(data, taskValues, query, task, isFleetInfo) {
        if (isFleetInfo) emptyList() else {
            data.groupsOf(task).mapNotNull { (group, fields) ->
                val visible = fields.filter { (argument, field) ->
                    if (!isFieldVisible(argument, field, taskValues[group]?.get(argument))) {
                        return@filter false
                    }
                    if (query.isBlank()) return@filter true
                    val label = data.fieldLabel(group, argument)
                    label.contains(query, ignoreCase = true) ||
                        "$group.$argument".contains(query, ignoreCase = true) ||
                        argument.contains(query, ignoreCase = true)
                }
                if (visible.isEmpty()) null else group to visible
            }
        }
    }

    // 运行会真的接管游戏，先确认；确认后还要等编辑队列落盘——调度器读的是磁盘上的配置，
    // 手上还有没保存的改动就直接起，跑的是上一版参数
    fun run(what: String) {
        scope.launch {
            repository.configEditor.settle()
            repository.runTask(what)
        }
    }

    pendingRun?.let { what ->
        AlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text(stringResource(R.string.ap_run_title, data.taskTitle(what))) },
            text = { Text(stringResource(R.string.ap_run_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRun = null
                    run(what)
                }) { Text(stringResource(R.string.ap_run_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRun = null }) { Text(stringResource(R.string.ap_cancel)) }
            },
        )
    }

    val listState = rememberLazyListState()
    val startupCard = task == STARTUP_TASK && query.isBlank()
    // 跳转目标的列表下标：前面可能垫了自启卡
    val groupIndex = remember(groups, startupCard) {
        groups.mapIndexed { index, (group, _) -> group to index + if (startupCard) 1 else 0 }.toMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing.lg, vertical = AppTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(AppTokens.IconSize.md),
                    )
                },
                placeholder = { Text(stringResource(R.string.ap_config_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                ) {
                    groups.forEach { (group, _) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                groupIndex[group]?.let { target ->
                                    scope.launch { listState.animateScrollToItem(target) }
                                }
                            },
                            label = { Text(data.groupTitle(group), maxLines = 1) },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(AppTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
        ) {
            if (editor.loading) {
                item {
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
            editor.error?.let { message ->
                item {
                    AppCard {
                        ApErrorState(message) {
                            selected?.let { repository.configEditor.load(it, force = true) }
                        }
                    }
                }
            }

            if (isFleetInfo) {
                item { FleetInfoCard(taskValues["FleetInfo"]?.get("Result")) }
                return@LazyColumn
            }

            if (startupCard) {
                item { StartupCard(repository, startup) }
            }

            if (groups.isEmpty()) {
                item {
                    AppCard {
                        // 「搜索没命中」与「这个任务本来就没有参数」是两件事，别用同一句话打发
                        if (query.isNotBlank()) {
                            ApEmptyState(
                                icon = Icons.Filled.Search,
                                title = stringResource(R.string.ap_config_no_match),
                                hint = stringResource(R.string.ap_config_no_match_hint),
                            )
                        } else {
                            ApEmptyState(
                                icon = Icons.Filled.Tune,
                                title = stringResource(R.string.ap_config_no_config),
                                hint = stringResource(R.string.ap_config_no_config_hint),
                            )
                        }
                    }
                }
            }

            itemsIndexed(groups, key = { _, item -> item.first }) { _, (group, fields) ->
                AppCard(title = data.groupTitle(group), collapsible = true) {
                    fields.entries.forEachIndexed { index, (argument, field) ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        val path = "$task.$group.$argument"
                        ApFieldRow(
                            schema = data,
                            group = group,
                            argument = argument,
                            field = field,
                            value = taskValues[group]?.get(argument) ?: field.value,
                            status = fieldStatus(path, editor),
                            statusError = editor.failed[path],
                            onChange = { repository.configEditor.update(task, group, argument, it) },
                            onRetry = { repository.configEditor.retry(path) },
                            // 「立刻运行」＝把调度时间清空回落默认的过去时间；这是 WebUI 的做法
                            onRunNow = if (group == "Scheduler" && argument == "NextRun") {
                                { repository.configEditor.update(task, group, argument, "") }
                            } else {
                                null
                            },
                            onCheckScript = if (field.mode == "restricted_lua") {
                                { script -> checkScript(repository, task, script) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            if (isTool) {
                item {
                    AppCard(title = stringResource(R.string.ap_tool_logs)) {
                        Button(
                            onClick = { pendingRun = task },
                            enabled = !editor.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.IconSize.md),
                            )
                            Text(
                                text = stringResource(R.string.ap_config_run_tool),
                                modifier = Modifier.padding(start = AppTokens.Spacing.sm),
                            )
                        }
                        ApLogBoard(
                            entries = logs.takeLast(TOOL_LOG_LINES),
                            modifier = Modifier.height(220.dp),
                            emptyHint = stringResource(R.string.ap_tool_logs_empty),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自启开关
 *
 * 「启动时自动运行」与「启动时记忆运行」是两个不同的策略，WebUI 把它们并排放在系统设置任务页上；
 * 这里照做——只在别处给一个「自动运行」会让人以为记忆功能不存在。
 */
@Composable
private fun StartupCard(
    repository: AzurPilotRepository,
    startup: com.azurpilot.ghio.proot.AzurPilotStartup?,
) {
    val ready = startup != null
    AppCard(title = stringResource(R.string.ap_startup_title)) {
        AppLabeledControlRow(
            label = stringResource(R.string.ap_startup_autorun),
            trailing = {
                Switch(
                    checked = startup?.enabled == true,
                    enabled = ready,
                    onCheckedChange = { repository.setStartup(enabled = it) },
                )
            },
        )
        Text(
            text = stringResource(R.string.ap_startup_autorun_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AppLabeledControlRow(
            label = stringResource(R.string.ap_startup_remember),
            trailing = {
                Switch(
                    checked = startup?.remember == true,
                    enabled = ready,
                    onCheckedChange = { repository.setStartup(remember = it) },
                )
            },
        )
        Text(
            text = stringResource(R.string.ap_startup_remember_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 舰队扫描结果
 *
 * `FleetInfo.Result` 是 `display: hide` 的 `stored` 字段，通用表单看不见它，所以这个任务页
 * 必须单独渲染——否则点进去只有一句「没有可配置项」。
 */
@Composable
private fun FleetInfoCard(value: ApValue) {
    val snapshot = remember(value) { parseFleetInfo(value) }
    when {
        snapshot == null -> AppCard {
            ApEmptyState(
                icon = Icons.Filled.DirectionsBoat,
                title = stringResource(R.string.ap_fleet_invalid),
                hint = stringResource(R.string.ap_fleet_invalid_hint),
            )
        }

        snapshot.roles.isEmpty() -> AppCard {
            ApEmptyState(
                icon = Icons.Filled.DirectionsBoat,
                title = stringResource(R.string.ap_fleet_empty),
                hint = stringResource(R.string.ap_fleet_empty_hint),
            )
        }

        else -> FLEET_NUMBERS.forEach { number ->
            AppCard(title = stringResource(R.string.ap_fleet_title, number)) {
                val roles = snapshot.roles.mapNotNull { (role, byFleet) ->
                    byFleet[number]?.let { role to it }
                }
                if (roles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ap_fleet_no_record),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    roles.forEach { (role, ships) ->
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                            AppFieldLabelText(stringResource(fleetRoleLabel(role)))
                            ships.forEach { ship ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                                ) {
                                    Text(
                                        text = ship.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ship.level?.let {
                                        Text(
                                            text = stringResource(R.string.ap_fleet_level, it),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 小队标题：比正文重一点、比卡标题轻一点 */
@Composable
private fun AppFieldLabelText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun fleetRoleLabel(role: String): Int = when (role) {
    "vanguard" -> R.string.ap_fleet_vanguard
    "main" -> R.string.ap_fleet_main
    else -> R.string.ap_fleet_submarine
}

private data class FleetShip(val name: String, val level: Int?)

private data class FleetSnapshot(val roles: Map<String, Map<Int, List<FleetShip>>>)

/**
 * 解析舰队快照
 *
 * 服务端存的是 `{vanguard: {"1": [{name, level}]}, main: …, submarine: …}`，但历史上也存过
 * JSON 字符串。两种都收；返回 null 表示内容根本不像舰队记录（与「记录为空」区分开）。
 */
private fun parseFleetInfo(value: ApValue): FleetSnapshot? {
    val map: Map<*, *> = when (value) {
        null -> return FleetSnapshot(emptyMap())
        is Map<*, *> -> value
        is String -> {
            if (value.isBlank()) return FleetSnapshot(emptyMap())
            runCatching { JSONObject(value).toValueMap() }.getOrNull() ?: return null
        }

        else -> return null
    }
    if (map.isEmpty()) return FleetSnapshot(emptyMap())
    val roles = LinkedHashMap<String, Map<Int, List<FleetShip>>>()
    FLEET_ROLES.forEach { role ->
        val byFleet = map[role] as? Map<*, *> ?: return@forEach
        val parsed = LinkedHashMap<Int, List<FleetShip>>()
        byFleet.forEach { (key, ships) ->
            val number = key?.toString()?.toIntOrNull() ?: return@forEach
            val list = (ships as? List<*>)?.mapNotNull { item ->
                when (item) {
                    is String -> FleetShip(item, null)
                    is Map<*, *> -> (item["name"] as? String)?.let {
                        FleetShip(it, (item["level"] as? Number)?.toInt())
                    }

                    else -> null
                }
            }.orEmpty()
            if (list.isNotEmpty()) parsed[number] = list
        }
        if (parsed.isNotEmpty()) roles[role] = parsed
    }
    return FleetSnapshot(roles)
}

/** 受限 Lua 脚本的检查：返回 null 表示通过，否则是可直接显示的诊断文案 */
private suspend fun checkScript(
    repository: AzurPilotRepository,
    task: String,
    script: String,
): String? {
    val instance = repository.selectedInstance.value ?: return null
    val result = repository.validateShopStrategy(instance, task, script) ?: return null
    if (result.valid) return null
    val first = result.diagnostics.firstOrNull() ?: return null
    val where = buildString {
        first.line?.let { append(" 第 $it 行") }
        first.column?.let { append(" 第 $it 列") }
    }
    return first.message + where
}

private fun fieldStatus(path: String, editor: AzurPilotEditorState): ApFieldStatus = when {
    editor.failed.containsKey(path) -> ApFieldStatus.Failed
    path in editor.saving -> ApFieldStatus.Saving
    path in editor.saved -> ApFieldStatus.Saved
    else -> ApFieldStatus.Idle
}

/**
 * 一个参数要不要显示
 *
 * 与 WebUI 的 `isFieldVisible` 同一套规则：`display: hide` 不显示；空的 `storage`
 * 既不生成字段也不生成分组——旧版 `put_arg_storage` 就是这么做的，空存储区显示出来只会是噪音。
 */
private fun isFieldVisible(argument: String, field: AzurPilotField, value: ApValue): Boolean {
    if (argument == "_info" || field.hidden) return false
    if (field.type == "storage") {
        val map = value as? Map<*, *> ?: return true
        if (map.isEmpty()) return false
    }
    return true
}

private const val TOOL_LOG_LINES = 60
