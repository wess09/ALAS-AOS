package com.azurpilot.ghio.di

import com.azurpilot.ghio.log.AzurPilotErrorDetailViewModel
import com.azurpilot.ghio.log.AzurPilotLogViewModel
import com.azurpilot.ghio.log.AppLogViewModel
import com.azurpilot.ghio.log.LogTailViewModel
import com.azurpilot.ghio.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AppLogViewModel)
    viewModelOf(::LogTailViewModel)
    viewModelOf(::AzurPilotLogViewModel)
    viewModelOf(::AzurPilotErrorDetailViewModel)

    viewModel {
        SettingsViewModel(get(), get(), get())
    }
}
