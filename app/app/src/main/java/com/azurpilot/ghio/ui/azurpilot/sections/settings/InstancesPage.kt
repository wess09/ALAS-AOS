package com.azurpilot.ghio.ui.azurpilot.sections.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.ApEmptyState
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.azurpilot.ApStatusPill
import com.azurpilot.ghio.ui.azurpilot.instanceStatusColor
import com.azurpilot.ghio.ui.azurpilot.instanceStatusText
import com.azurpilot.ghio.ui.components.AppCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 实例管理：建、删、导出、导入
 *
 * 删除走「先读 revision 再删」——网关拿它挡住「读到旧快照后删掉别人刚改的配置」，
 * 所以这里必须先取一次再带上去，不能只传名字。
 */
@Composable
fun InstancesPage(repository: AzurPilotRepository) {
    val instances by repository.instances.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 声明必须排在使用它的 launcher 之前：SAF 回调里要拿这个值
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val name = pendingExport
        pendingExport = null
        if (uri != null && name != null) {
            scope.launch {
                val payload = repository.exportConfig(name)?.values?.toJsonString().orEmpty()
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
                    }
                }
            }
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.ap_instances_delete_title, name)) },
            text = { Text(stringResource(R.string.ap_instances_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteInstance(name)
                    pendingDelete = null
                }) { Text(stringResource(R.string.ap_instances_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.ap_cancel))
                }
            },
        )
    }

    ApSectionColumn {
        if (creating) {
            CreateInstanceCard(
                sources = instances.map { it.name },
                onCreate = { name, source ->
                    repository.createInstance(name, source)
                    creating = false
                },
                onCancel = { creating = false },
            )
        } else {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AppTokens.IconSize.md))
                Text(
                    text = stringResource(R.string.ap_instances_create),
                    modifier = Modifier.padding(start = AppTokens.Spacing.sm),
                )
            }
        }

        AppCard(title = stringResource(R.string.ap_settings_instances_count, instances.size)) {
            if (instances.isEmpty()) {
                ApEmptyState(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.ap_instance_none),
                    hint = stringResource(R.string.ap_instances_empty_hint),
                )
            } else {
                instances.forEachIndexed { index, instance ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                            ) {
                                Text(instance.name, style = MaterialTheme.typography.bodyLarge)
                                ApStatusPill(
                                    text = instanceStatusText(instance.status),
                                    container = instanceStatusColor(instance.status).copy(alpha = 0.16f),
                                    content = instanceStatusColor(instance.status),
                                )
                            }
                            Text(
                                text = buildString {
                                    append(instance.server)
                                    if (instance.serial.isNotEmpty()) append(" · ${instance.serial}")
                                    instance.currentTask?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            pendingExport = instance.name
                            exporter.launch("${instance.name}.json")
                        }) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = stringResource(R.string.ap_instances_export),
                            )
                        }
                        IconButton(
                            enabled = instance.name != selected || instances.size > 1,
                            onClick = { pendingDelete = instance.name },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.ap_instances_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 建档：可以复制一个已有实例，也可以从模板起 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInstanceCard(
    sources: List<String>,
    onCreate: (String, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AppCard(title = stringResource(R.string.ap_instances_create)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.ap_instances_name)) },
            supportingText = { Text(stringResource(R.string.ap_instances_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = source ?: stringResource(R.string.ap_instances_source_template),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.ap_instances_source)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ap_instances_source_template)) },
                    onClick = {
                        source = null
                        expanded = false
                    },
                )
                sources.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ap_instances_source_copy, option)) },
                        onClick = {
                            source = option
                            expanded = false
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), source) },
            ) { Text(stringResource(R.string.ap_instances_create_confirm)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ap_cancel)) }
        }
    }
}

/** 整份配置 → 带缩进的 JSON 文本，导出用 */
private fun com.azurpilot.ghio.proot.ApConfigValues.toJsonString(): String =
    runCatching { (JSONObject.wrap(this) as? JSONObject)?.toString(2) }.getOrNull() ?: "{}"
