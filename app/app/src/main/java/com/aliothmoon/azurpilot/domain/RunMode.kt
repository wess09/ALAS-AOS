package com.aliothmoon.azurpilot.domain

import com.aliothmoon.azurpilot.constant.DisplayMode

enum class RunMode(val displayMode: Int) {
    FOREGROUND(DisplayMode.PRIMARY),
    BACKGROUND(DisplayMode.BACKGROUND),
}
