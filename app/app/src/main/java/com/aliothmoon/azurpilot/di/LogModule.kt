package com.aliothmoon.azurpilot.di

import com.aliothmoon.azurpilot.constant.AppPaths
import com.aliothmoon.azurpilot.log.AzurPilotLogSource
import com.aliothmoon.azurpilot.log.AppLogWriter
import com.aliothmoon.azurpilot.log.LogCleaner
import com.aliothmoon.azurpilot.log.LogExportService
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
