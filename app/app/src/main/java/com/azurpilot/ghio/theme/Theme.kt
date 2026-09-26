package com.azurpilot.ghio.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * MD3 的 colorScheme 只定义 primary / secondary / tertiary / error 四族，没有 success、warning
 * 这类业务语义角色。按 MD3 的自定义颜色角色（custom color role）约定补上，随明暗切换。
 *
 * 与内置角色一样成对给出：色是"容器"，[onSuccess] 是压在它上面的前景——
 * 少了这一半，深色档下的浅绿底就只能配上纯白前景，对比度掉到读不出
 */
data class AppPalette(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
)

private val LightPalette = AppPalette(
    success = Color(0xFF146C2E),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF8F4C00),
)

private val DarkPalette = AppPalette(
    success = Color(0xFF7FDA95),
    onSuccess = Color(0xFF00391A),
    warning = Color(0xFFFFB86B),
)

val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** 主题扩展读取入口；Screen 不直接碰 CompositionLocal */
object AzurPilotTheme {
    val palette: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}

/**
 * 明暗两套色板
 *
 * Android 12 起默认取系统动态色（Material You），其余版本回落到 MD3 基线色板——
 * 两条路都是 MD3 规范内的方案，不再维护自定义色盘
 */
@Composable
private fun colorSchemeOf(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
}

/**
 * 全 App 唯一的主题入口
 *
 * 色板、形状、字阶、动效一律走 MD3 默认 token（`MaterialTheme` 的默认 `Shapes` / `Typography` /
 * 内置动效方案），组件不再按自定义圆角与字阶覆写——需要不同观感时换 M3 的组件变体
 */
@Composable
fun AzurPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppPalette provides if (darkTheme) DarkPalette else LightPalette,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeOf(darkTheme, dynamicColor),
            shapes = Shapes(),
            typography = Typography(),
            content = content,
        )
    }
}
