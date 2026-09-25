package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.overlay.OverlayController
import com.aliothmoon.azurpilot.overlay.OverlayViewModelOwner
import com.aliothmoon.azurpilot.overlay.border.BorderOverlayManager
import com.aliothmoon.azurpilot.overlay.screensaver.ScreenSaverOverlayManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val overlayModule = module {
    single { BorderOverlayManager(androidContext()) }
    single { OverlayViewModelOwner() }
    single {
        OverlayController(
            context = androidContext() as android.app.Application,
            hostState = get(),
            appSettings = get(),
            borderOverlayManager = get(),
            viewModelOwner = get(),
            runController = get(),
        )
    }

    single {
        ScreenSaverOverlayManager(
            context = androidContext(),
            hostState = get(),
            appSettings = get(),
        )
    }
}
