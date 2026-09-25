package com.azurpilot.ghio.di

import com.azurpilot.ghio.service.HostState
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val hostModule = module {
    single { HostState(androidContext(), get(), get(), get(named<AppCoroutineScope>())) }
}
