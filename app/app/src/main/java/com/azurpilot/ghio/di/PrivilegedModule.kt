package com.azurpilot.ghio.di

import com.azurpilot.ghio.privileged.DisplaySizeController
import com.azurpilot.ghio.privileged.DisplaySizeGateway
import com.azurpilot.ghio.privileged.PermissionGateway
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.privileged.PrivilegedServicePort
import com.azurpilot.ghio.privileged.RemoteAccessCoordinator
import com.azurpilot.ghio.privileged.RemoteAccessPort
import com.azurpilot.ghio.privileged.RemoteServiceManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val privilegedModule = module {
    single<PrivilegedServicePort> { RemoteServiceManager }
    single<RemoteAccessPort> { RemoteAccessCoordinator }

    single { PermissionManager(androidContext(), get(), get(), get()) }
    single<PermissionGateway> { get<PermissionManager>() }
    single<DisplaySizeGateway> { DisplaySizeController(androidContext(), get()) }
}