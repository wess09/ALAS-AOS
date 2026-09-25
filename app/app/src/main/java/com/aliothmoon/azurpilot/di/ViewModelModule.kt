package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.log.AzurPilotErrorDetailViewModel
import com.aliothmoon.azurpilot.log.AzurPilotLogViewModel
import com.aliothmoon.azurpilot.log.AppLogViewModel
import com.aliothmoon.azurpilot.log.LogTailViewModel
import com.aliothmoon.azurpilot.settings.SettingsViewModel
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
