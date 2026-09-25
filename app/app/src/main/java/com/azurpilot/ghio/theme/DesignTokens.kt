package com.azurpilot.ghio.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MD3 token 之外、本项目自用的静态尺寸：间距、图标、描边、透明度
 *
 * 圆角、elevation、字阶、动效一律取 M3 主题 token（[androidx.compose.material3.MaterialTheme]），
 * 不在这里再放一套
 */
object AppTokens {

    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
    }

    object Separator {
        val thickness: Dp = 0.5.dp
    }

    /** 描边宽度；分隔线用 [Separator] */
    object Border {
        /** 转圈的线宽 */
        val marker: Dp = 2.dp
    }

    /**
     * 图标绘制尺寸；不要在 `Modifier.size(N.dp)` 上拍裸数
     * 新场景对不上现有档时，先在这里加档并写清用途
     */
    object IconSize {
        /** 行内装饰图标，与 bodyLarge / labelLarge 并排 */
        val sm: Dp = 16.dp

        /** IconButton 与按钮前置图标的标准档 */
        val md: Dp = 20.dp

        /** 卡片内的占位插画 */
        val lg: Dp = 32.dp
    }

    object Alpha {
        /** 锁定时的文字与图标 */
        const val disabledContent = 0.4f
    }
}
