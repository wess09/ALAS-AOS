package com.aliothmoon.azurpilot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aliothmoon.azurpilot.theme.AppTokens

/**
 * 内容宽度单选组
 *
 * 用 M3 的 [FilterChip] 平铺：选项多、标签长短不一时 FlowRow 能逐颗换行，
 * 换成等分的 SegmentedButton 长标签会把整行挤变形
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AppSingleChoiceFlow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(label) },
            )
        }
    }
}
