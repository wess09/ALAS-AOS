package com.azurpilot.ghio.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.R
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.theme.AzurPilotTheme

/**
 * 常驻悬浮球：环境活着期间的唯一入口
 *
 * 尺寸压到 32dp：它盖在其他应用画面上，再大就开始碍事了
 * 环境活着时做呼吸动画——静止的小圆点在满屏画面里根本注意不到
 */
@Composable
fun FloatBall(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AzurPilotTheme.palette
    val target = if (running) palette.success else MaterialTheme.colorScheme.primary
    val color by animateColorAsState(target.copy(alpha = 0.85f), tween(300))

    val breathing by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
    )

    val description = stringResource(
        if (running) R.string.overlay_ball_running else R.string.overlay_ball_idle,
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(BALL_SIZE)
            .clip(CircleShape)
            .border(AppTokens.Separator.thickness, Color.White.copy(alpha = 0.15f), CircleShape)
            .then(if (running) Modifier.alpha(breathing) else Modifier)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = color,
        // 球盖在其他应用上需要体量感；不跟 Semi 卡片的 0 elevation
        shadowElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (running) Icons.Outlined.PlayArrow else Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(AppTokens.IconSize.sm),
            )
        }
    }
}

/** 悬浮球的直径；它盖在别人画面上，属一次性视觉尺寸，不进 Spacing */
private val BALL_SIZE = 32.dp
