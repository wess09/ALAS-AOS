package com.azurpilot.ghio.ui.azurpilot.sections.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.ApValue
import com.azurpilot.ghio.proot.AzurPilotDeployField
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.sameValueAs
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.ApErrorState
import com.azurpilot.ghio.ui.azurpilot.ApSectionColumn
import com.azurpilot.ghio.ui.components.AppCard

/**
 * 部署设置（`config/deploy.yaml`）
 *
 * 与任务参数不同，这些是**运行环境**的配置：改完大多要重启 AzurPilot 才生效，
 * 所以这里不逐字段自动保存，而是攒到一份草稿里，由底部的「保存」一次提交——
 * 半套应用的设置比没保存更难排查。
 */
@Composable
fun DeploySettingsPage(repository: AzurPilotRepository) {
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val draft = remember { mutableStateMapOf<String, ApValue>() }

    val loadFailed = stringResource(R.string.ap_deploy_load_failed)
    LaunchedEffect(Unit) {
        val settings = repository.loadDeploySettings()
        error = if (settings == null) loadFailed else null
        loading = false
    }

    val deploy by repository.deploySettings.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        ApSectionColumn {
            if (loading) {
                AppCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@ApSectionColumn
            }
            val data = deploy ?: run {
                AppCard { ApErrorState(error ?: stringResource(R.string.ap_waiting_data)) }
                return@ApSectionColumn
            }
            if (data.notice.isNotEmpty()) {
                AppCard {
                    Text(
                        text = data.notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (data.demo) {
                AppCard {
                    Text(
                        text = stringResource(R.string.ap_deploy_demo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            data.groups.forEach { group ->
                AppCard(title = group.label, collapsible = true) {
                    group.fields.forEach { field ->
                        DeployFieldRow(
                            field = field,
                            draft = draft[field.key],
                            enabled = !data.demo,
                            onDraft = { draft[field.key] = it },
                        )
                    }
                }
            }
        }

        if (draft.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppTokens.Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.ap_deploy_pending, draft.size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            repository.patchDeploySettings(HashMap(draft)) { ok, _ ->
                                saving = false
                                if (ok) draft.clear()
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.ap_deploy_saving else R.string.ap_deploy_save,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeployFieldRow(
    field: AzurPilotDeployField,
    // ApValue 本身就是可空类型，不要再叠一层 ?
    draft: ApValue,
    enabled: Boolean,
    onDraft: (ApValue) -> Unit,
) {
    val current = draft ?: field.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTokens.Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
    ) {
        Text(text = field.label, style = MaterialTheme.typography.bodyLarge)
        if (field.help.isNotEmpty()) {
            Text(
                text = field.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            field.type == "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = current as? Boolean ?: false,
                    enabled = enabled,
                    onCheckedChange = { onDraft(it) },
                )
            }

            field.options.isNotEmpty() -> {
                var expanded by remember { mutableStateOf(false) }
                val known = field.options.any { it.sameValueAs(current) }
                ExposedDropdownMenuBox(
                    expanded = expanded && enabled,
                    onExpandedChange = { if (enabled) expanded = it },
                ) {
                    OutlinedTextField(
                        value = if (known) optionText(current) else optionText(field.value),
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                        field.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(optionText(option)) },
                                onClick = {
                                    expanded = false
                                    onDraft(option)
                                },
                            )
                        }
                    }
                }
            }

            field.type == "password" -> {
                var visible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = current as? String ?: "",
                    onValueChange = { onDraft(it) },
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    placeholder = { Text(stringResource(R.string.ap_deploy_password_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(AppTokens.IconSize.md),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            field.type == "int" -> OutlinedTextField(
                value = (current as? Number)?.toLong()?.toString() ?: "",
                onValueChange = { text -> text.toIntOrNull()?.let(onDraft) },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            else -> OutlinedTextField(
                value = current as? String ?: "",
                onValueChange = { onDraft(it) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun optionText(value: ApValue): String = when (value) {
    null -> ""
    is String -> value.ifEmpty { "—" }
    else -> value.toString()
}
