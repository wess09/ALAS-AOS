package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.proot.ProotHost
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

val prootModule = module {
    single { ProotHost(androidApplication(), get(named<AppCoroutineScope>()), get()) }
    single { AzurPilotRunController(androidApplication(), get(named<AppCoroutineScope>()), get()) }
}
