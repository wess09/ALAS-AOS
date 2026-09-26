package com.azurpilot.ghio.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App 当前解析出的明暗档，供 Activity 之外的地方取用（悬浮窗、悬浮球）
 *
 * 这些地方不该自己去问系统：服务进程的 Configuration 可能停在服务创建那一刻，
 * 系统换深浅色时它不跟 Activity 一起刷新，照它取色就会出现"App 已经浅色、
 * 悬浮窗还是深色"这种对不上的情况。由 Activity 把最终结果播出来，别处只读
 *
 * [darkTheme] 为 null = 本次进程里还没播过（App 尚未启动过），此时调用方按自己的
 * 兜底判断；一旦播过就以它为准
 */
object AppThemeState {

    private val _darkTheme = MutableStateFlow<Boolean?>(null)

    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()

    fun publish(dark: Boolean) {
        _darkTheme.value = dark
    }
}
