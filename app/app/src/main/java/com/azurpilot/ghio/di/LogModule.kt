package com.azurpilot.ghio.di

import com.azurpilot.ghio.constant.AppPaths
import com.azurpilot.ghio.log.AzurPilotLogSource
import com.azurpilot.ghio.log.AppLogWriter
import com.azurpilot.ghio.log.LogCleaner
import com.azurpilot.ghio.log.LogExportService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val logModule = module {
    single { AppLogWriter() }
    single { AzurPilotLogSource(androidContext()) }
    single { LogCleaner(get()) }
    single {
        LogExportService(
            context = androidContext(),
            baseDir = { AppPaths.ROOT },
            launcherRoots = { listOf(AppPaths.LOG_DIR, AppPaths.DEBUG_DIR) },
            logDir = { get<AzurPilotLogSource>().logDir() },
        )
    }
}
