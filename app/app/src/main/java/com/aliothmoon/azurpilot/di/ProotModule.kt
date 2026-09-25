package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.proot.AzurPilotApi
import com.aliothmoon.azurpilot.proot.AzurPilotGateway
import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.proot.ProotHost
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
