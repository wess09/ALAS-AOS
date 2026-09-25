package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.privileged.DisplaySizeController
import com.aliothmoon.azurpilot.privileged.DisplaySizeGateway
import com.aliothmoon.azurpilot.privileged.PermissionGateway
import com.aliothmoon.azurpilot.privileged.PermissionManager
import com.aliothmoon.azurpilot.privileged.PrivilegedServicePort
import com.aliothmoon.azurpilot.privileged.RemoteAccessCoordinator
import com.aliothmoon.azurpilot.privileged.RemoteAccessPort
import com.aliothmoon.azurpilot.privileged.RemoteServiceManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val privilegedModule = module {
    single<PrivilegedServicePort> { RemoteServiceManager }
    single<RemoteAccessPort> { RemoteAccessCoordinator }

    single { PermissionManager(androidContext(), get(), get(), get()) }
    single<PermissionGateway> { get<PermissionManager>() }
    single<DisplaySizeGateway> { DisplaySizeController(androidContext(), get()) }
}