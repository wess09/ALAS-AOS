package com.azurpilot.ghio.di

import com.azurpilot.ghio.provision.RootfsProvisioner
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

val provisionModule = module {
    single { RootfsProvisioner(androidApplication(), get(named<AppCoroutineScope>())) }
}
