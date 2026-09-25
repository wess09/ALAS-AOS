package com.azurpilot.ghio.di

import com.azurpilot.ghio.overlay.OverlayController
import com.azurpilot.ghio.overlay.OverlayViewModelOwner
import com.azurpilot.ghio.overlay.border.BorderOverlayManager
import com.azurpilot.ghio.overlay.screensaver.ScreenSaverOverlayManager
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
