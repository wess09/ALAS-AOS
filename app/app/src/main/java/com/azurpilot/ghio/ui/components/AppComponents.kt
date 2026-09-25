package com.azurpilot.ghio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.azurpilot.ghio.theme.AppTokens

/**
 * 内容分组卡：M3 的 filled card（`surfaceContainerHighest` 底色 + 0 elevation），
 * 颜色、圆角、层级全走主题 token，调用点不再各写一遍
 *
 * [collapsible] 要求有 [title]：折叠靠点标题行，没标题就没有可点的表头
 * 展开态只活在本次会话（[rememberSaveable]）——收起是临时整理视线，不是配置，不该进 DataStore
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsible: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(AppTokens.Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    // 不要给 rememberSaveable 传 key：运行时已废弃它，理由正是会绕开位置作用域造成状态串卡
    var expanded by rememberSaveable { mutableStateOf(true) }
    val canCollapse = collapsible && title != null

    Card(
        modifier = modifier.fillMaxWidth(),
        // 卡内是正文而非次要说明，内容色钉在 onSurface：filled card 默认取 onSurfaceVariant，
        // 标题会偏淡
        colors = CardDefaults.cardColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        ) {
            if (title != null) {
                AppLabeledControlRow(
                    label = title,
                    labelStyle = MaterialTheme.typography.titleMedium,
                    modifier = if (canCollapse) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                    trailing = {
                        if (canCollapse) {
                            val rotation by animateFloatAsState(
                                targetValue = if (expanded) 180f else 0f,
                                label = "chevron",
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(AppTokens.IconSize.sm)
                                    .rotate(rotation),
                            )
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = !canCollapse || expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
                    content = content,
                )
            }
        }
    }
}

/** 尾控件固宽，标签占剩余并换行，避免长 label 挤掉 Switch/Icon */
@Composable
fun AppLabeledControlRow(
    label: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    labelColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.wrapContentWidth(),
            contentAlignment = Alignment.Center,
        ) {
            trailing()
        }
    }
}

/**
 * 点进二级页面的一行：标题 + 说明 + 右侧箭头
 *
 * 与 [AppLabeledControlRow] 分开：那个的尾部是控件、点的是控件本身；这个整行可点，
 * 语义是「离开当前页」
 */
@Composable
fun AppNavigationRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppTokens.IconSize.md),
        )
    }
}

/**
 * 列表行/缩略图的卡片配方：M3 的 outlined card（`surface` 底 + `outlineVariant` 描边），
 * 与内容分组用的 [AppCard] 拉开层级
 */
@Composable
fun AppCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier = modifier) { content() }
}

/** 卡片内一组控件的小标题；一张卡装多组时靠它区分 */
@Composable
fun AppFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun AppInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}
