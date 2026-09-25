package com.azurpilot.ghio.domain

import com.azurpilot.ghio.constant.DisplayMode

enum class RunMode(val displayMode: Int) {
    FOREGROUND(DisplayMode.PRIMARY),
    BACKGROUND(DisplayMode.BACKGROUND),
}
