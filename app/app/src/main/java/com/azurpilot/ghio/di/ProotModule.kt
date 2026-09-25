package com.azurpilot.ghio.di

import com.azurpilot.ghio.proot.AzurPilotApi
import com.azurpilot.ghio.proot.AzurPilotGateway
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.proot.ProotHost
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

val prootModule = module {
    single { ProotHost(androidApplication(), get(named<AppCoroutineScope>()), get()) }
    single { AzurPilotRunController(androidApplication(), get(named<AppCoroutineScope>()), get()) }
    // /api/v1/ws 富接口：总览、自启、实例与配置（与上面那套 /android/* 薄接口并存）
    single { AzurPilotGateway(get(named<AppCoroutineScope>())) }
    single { AzurPilotApi(get(named<AppCoroutineScope>()), get()) }
}
