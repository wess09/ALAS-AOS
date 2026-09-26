package com.azurpilot.ghio.ui.azurpilot.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.ApValue
import com.azurpilot.ghio.proot.AzurPilotField
import com.azurpilot.ghio.proot.AzurPilotSchema
import com.azurpilot.ghio.proot.prettyText
import com.azurpilot.ghio.proot.sameValueAs
import com.azurpilot.ghio.theme.AppTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 一个参数的保存状态，用来在行尾给一行小字 */
enum class ApFieldStatus { Idle, Saving, Saved, Failed }

/** 参数值的文本形式；嵌套结构给 pretty JSON，避免显示成 `{a=1}` 那种 toString */
fun fieldTextOf(value: ApValue): String = value.prettyText()

/** 本地校验结果：[payload] 为 null 表示这次输入还不能提交 */
data class PreparedField(val payload: ApValue, val error: String?)

/**
 * 把输入框里的文本转成能提交的值
 *
 * 与网关的校验规则对齐（类型、整数性、区间、正则、日期格式），这样**本地就能拦下**明显不合法
 * 的输入，不必等一次往返再显示红字。
 */
fun prepareFieldValue(field: AzurPilotField, text: String): PreparedField {
    val trimmed = text.trim()
    // preserve_empty 的字段空值本身有意义，照提交；其余空值回落默认值
    // （调度时间「立即运行」正是靠这条：清空 → 提交默认的过去时间 → 调度器视为待运行）
    if (trimmed.isEmpty() && !field.preserveEmpty) {
        val fallback = field.value
        if ((field.numeric || field.type == "datetime") && fallback != null) {
            return PreparedField(fallback, null)
        }
        if (!field.numeric) return PreparedField(text, null)
        return PreparedField(null, null)
    }
    if (field.type == "datetime") {
        if (trimmed.isNotEmpty() && !DATETIME.matches(trimmed)) {
            return PreparedField(null, null)
        }
        return PreparedField(text, null)
    }
    if (!field.numeric) {
        field.pattern?.let { pattern ->
            if (trimmed.isNotEmpty() && !Regex(pattern).matches(trimmed)) {
                return PreparedField(null, null)
            }
        }
        return PreparedField(text, null)
    }
    val number = trimmed.toDoubleOrNull() ?: return PreparedField(null, null)
    if (field.value is Int && number != number.toLong().toDouble()) return PreparedField(null, null)
    field.range?.let { (min, max) ->
        if (number < min || number > max) return PreparedField(null, null)
    }
    // 默认值是整数的参数，网关要求 type(value) is int
    val payload: ApValue = if (field.value is Int) number.toLong().toInt() else number
    return PreparedField(payload, null)
}

private val DATETIME = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")

/**
 * 一个参数行
 *
 * 控件按 schema 的定义挑：布尔→开关，有候选→下拉，多选→勾选组，其余→文本。
 * 开关与下拉这类**离散选择**改完立刻提交；文本类**防抖后提交**——手机上逐字符发请求既费流量，
 * 也会让中间态（`1`、`1.`）撞上服务端校验。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApFieldRow(
    schema: AzurPilotSchema,
    group: String,
    argument: String,
    field: AzurPilotField,
    value: ApValue,
    status: ApFieldStatus,
    /** 保存失败时网关给的原文——「参数必须在 0 到 100 之间：Main.Campaign.X」正是用户要的下一步 */
    statusError: String? = null,
    onChange: (ApValue) -> Unit,
    onRetry: () -> Unit,
    onRunNow: (() -> Unit)? = null,
    onCheckScript: (suspend (String) -> String?)? = null,
) {
    val readOnly = field.readOnly
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTokens.Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = schema.fieldLabel(group, argument),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (readOnly) {
                Text(
                    text = stringResource(R.string.ap_field_readonly),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusIndicator(status)
        }
        val help = schema.fieldHelp(group, argument)
        if (help.isNotEmpty() && help != "help" && help != argument) {
            Text(
                text = help.replace(HELP_TAG, ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            // storage 只允许清空（`{}`）；网关对它有单独的放行分支
            field.type == "storage" -> StorageControl(value, enabled = !readOnly && field.clearableStorage) {
                onChange(emptyMap<String, ApValue>())
            }

            field.type == "stored" || field.type == "state" || field.type == "lock" ->
                ReadOnlyValue(fieldTextOf(value))

            field.booleanish -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = value as? Boolean ?: false,
                    enabled = !readOnly,
                    onCheckedChange = { onChange(it) },
                )
            }

            field.type == "multiselect" -> MultiSelect(
                field = field,
                value = value,
                enabled = !readOnly,
                labelOf = { schema.optionLabel(group, argument, it) },
                onChange = onChange,
            )

            !field.option.isNullOrEmpty() -> OptionSelect(
                field = field,
                value = value,
                enabled = !readOnly,
                labelOf = { schema.optionLabel(group, argument, it) },
                onChange = onChange,
            )

            else -> TextControl(
                field = field,
                value = value,
                enabled = !readOnly,
                onCommit = onChange,
                onRunNow = onRunNow,
                onCheckScript = onCheckScript,
            )
        }

        if (status == ApFieldStatus.Failed) FailedNotice(statusError, onRetry)
    }
}

private val HELP_TAG = Regex("<[^>]*>")

@Composable
private fun StatusIndicator(status: ApFieldStatus) {
    when (status) {
        ApFieldStatus.Idle, ApFieldStatus.Failed -> Unit
        ApFieldStatus.Saving -> Text(
            text = stringResource(R.string.ap_field_saving),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ApFieldStatus.Saved -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.ap_field_saved),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppTokens.IconSize.sm),
        )
    }
}

/**
 * 保存失败的原因
 *
 * 只给一个「重试」按钮等于让用户猜哪里错了：网关的校验消息（区间、正则、日期格式、类型）
 * 就是修好这件事所需的全部信息，必须原样显示出来。
 */
@Composable
private fun FailedNotice(error: String?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(AppTokens.IconSize.sm),
        )
        Text(
            text = error?.let { stringResource(R.string.ap_field_failed, it) }
                ?: stringResource(R.string.ap_field_failed_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.ap_field_retry), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 只读值：嵌套结构给等宽 pretty JSON，截断到固定行数 */
@Composable
private fun ReadOnlyValue(text: String) {
    Text(
        text = text.ifEmpty { "—" },
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 存储区：只读展示 + 清空（网关只接受清成 `{}`） */
@Composable
private fun StorageControl(value: ApValue, enabled: Boolean, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = fieldTextOf(value).ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = stringResource(R.string.ap_field_clear),
                )
            }
        }
    }
}

/** 单选下拉：候选取自 schema，显示名走翻译；当前值不在候选里时也照样显示出来 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSelect(
    field: AzurPilotField,
    value: ApValue,
    enabled: Boolean,
    labelOf: (ApValue) -> String,
    onChange: (ApValue) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = field.option.orEmpty()
    val known = options.any { it.sameValueAs(value) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = if (known) labelOf(value) else fieldTextOf(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        expanded = false
                        if (!option.sameValueAs(value)) onChange(option)
                    },
                )
            }
        }
    }
}

/** 多选：勾选即提交整个列表（网关要求元素与候选"类型与值都相同"且不重复） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelect(
    field: AzurPilotField,
    value: ApValue,
    enabled: Boolean,
    labelOf: (ApValue) -> String,
    onChange: (ApValue) -> Unit,
) {
    val selected = value as? List<ApValue> ?: emptyList()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
    ) {
        field.option.orEmpty().forEach { option ->
            val checked = selected.any { it.sameValueAs(option) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { next ->
                        onChange(
                            if (next) selected + option
                            else selected.filterNot { it.sameValueAs(option) },
                        )
                    },
                )
                Text(text = labelOf(option), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 文本类控件：防抖提交 + 本地预校验 */
@Composable
private fun TextControl(
    field: AzurPilotField,
    value: ApValue,
    enabled: Boolean,
    onCommit: (ApValue) -> Unit,
    onRunNow: (() -> Unit)?,
    onCheckScript: (suspend (String) -> String?)?,
) {
    val multiline = field.type == "textarea" || field.type == "task_priority" ||
        field.mode == "yaml" || field.mode == "restricted_lua"
    var text by remember(field) { mutableStateOf(fieldTextOf(value)) }
    var touched by remember(field) { mutableStateOf(false) }
    var invalid by remember(field) { mutableStateOf(false) }
    var scriptError by remember(field) { mutableStateOf<String?>(null) }
    var scriptChecked by remember(field) { mutableStateOf(false) }
    var checking by remember(field) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 值来自服务端（配置刚加载完、或换了实例）时同步进来；用户动过的框不再覆盖
    LaunchedEffect(value) {
        if (!touched) text = fieldTextOf(value)
    }

    LaunchedEffect(text, touched) {
        if (!touched) return@LaunchedEffect
        delay(DEBOUNCE_MS)
        val prepared = prepareFieldValue(field, text)
        invalid = prepared.payload == null
        val payload = prepared.payload
        if (payload != null && !payload.sameValueAs(value)) onCommit(payload)
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            touched = true
            scriptError = null
            scriptChecked = false
            text = it
        },
        enabled = enabled,
        isError = invalid || scriptError != null,
        singleLine = !multiline,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                field.type == "datetime" -> KeyboardType.Text
                field.value is Int -> KeyboardType.Number
                field.numeric -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
        ),
        textStyle = if (multiline || field.type == "datetime") {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        supportingText = {
            when {
                !enabled -> Text(stringResource(R.string.ap_field_readonly))
                invalid -> Text(stringResource(R.string.ap_field_invalid), color = MaterialTheme.colorScheme.error)
                scriptError != null -> Text(scriptError!!, color = MaterialTheme.colorScheme.error)
                scriptChecked -> Text(
                    text = stringResource(R.string.ap_script_valid),
                    color = MaterialTheme.colorScheme.primary,
                )

                field.type == "datetime" -> Text(stringResource(R.string.ap_field_datetime_format))
                field.mode == "yaml" -> Text(stringResource(R.string.ap_field_yaml_hint))
                field.mode == "restricted_lua" -> Text(stringResource(R.string.ap_field_script_hint))
            }
        },
        trailingIcon = {
            if (onRunNow != null && enabled) {
                IconButton(onClick = onRunNow) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.ap_run_now),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (multiline) Modifier.heightIn(min = 140.dp) else Modifier),
    )

    // 受限脚本可以在提交前先求一次诊断，拿到行列号；不点也能靠保存失败发现
    if (onCheckScript != null && enabled && !readOnlyHint(field)) {
        TextButton(
            enabled = !checking,
            onClick = {
                checking = true
                scope.launch {
                    scriptError = onCheckScript(text)
                    scriptChecked = true
                    checking = false
                }
            },
        ) {
            Text(
                text = stringResource(
                    if (checking) R.string.ap_script_checking else R.string.ap_script_check,
                ),
            )
        }
    }
}

private fun readOnlyHint(field: AzurPilotField): Boolean = field.readOnly

private const val DEBOUNCE_MS = 600L

/** 未连接的提示条：网关掉线时说明为什么内容不刷新 */
@Composable
fun ApOfflineNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(AppTokens.IconSize.md),
        )
        Text(
            text = stringResource(R.string.ap_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
