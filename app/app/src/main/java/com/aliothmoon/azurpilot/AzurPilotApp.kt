package com.aliothmoon.azurpilot

import android.app.Application
import com.aliothmoon.azurpilot.constant.AppPaths
import com.aliothmoon.azurpilot.di.AppCoroutineScope
import com.aliothmoon.azurpilot.di.coreModule
import com.aliothmoon.azurpilot.di.hostModule
import com.aliothmoon.azurpilot.di.logModule
import com.aliothmoon.azurpilot.di.overlayModule
import com.aliothmoon.azurpilot.di.privilegedModule
import com.aliothmoon.azurpilot.di.prootModule
import com.aliothmoon.azurpilot.di.provisionModule
import com.aliothmoon.azurpilot.di.viewModelModule
import com.aliothmoon.azurpilot.log.AppLogWriter
import com.aliothmoon.azurpilot.log.CrashHandler
import com.aliothmoon.azurpilot.log.LogCleaner
import com.aliothmoon.azurpilot.log.LogTreeHolder
import com.aliothmoon.azurpilot.overlay.OverlayController
import com.aliothmoon.azurpilot.overlay.screensaver.ScreenSaverOverlayManager
import com.aliothmoon.azurpilot.privileged.PermissionManager
import com.aliothmoon.azurpilot.privileged.RemoteServiceManager
import com.aliothmoon.azurpilot.proot.AzurPilotApi
import com.aliothmoon.azurpilot.proot.AzurPilotGateway
import com.aliothmoon.azurpilot.proot.AzurPilotRunController
import com.aliothmoon.azurpilot.service.HostState
import com.aliothmoon.azurpilot.settings.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.qualifier.named
import timber.log.Timber

class AzurPilotApp : Application() {

    private val writer by inject<AppLogWriter>()
    private val settings by inject<AppSettingsManager>()

    override fun onCreate() {
        super.onCreate()
        AppPaths.init(this)
        CrashHandler().install()
        val app = this
        val koin = startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(app)
            modules(
                coreModule,
                privilegedModule,
                hostModule,
                logModule,
                overlayModule,
                provisionModule,
                prootModule,
                viewModelModule,
            )
        }.koin
        writer.setup()
        LogTreeHolder(writer).setup()
        koin.get<CoroutineScope>(named<AppCoroutineScope>()).launch {
            settings.loaded.first { it }
            // 自动清理门控：静默执行，失败不挡启动；汇总行由 LogCleaner 自己 Timber.w
            launch(AppDispatchers.IO) {
                runCatching {
                    if (settings.autoCleanLogs.value) koin.get<LogCleaner>().cleanOutdated()
                }.onFailure { Timber.w(it, "LogCleaner 执行失败") }
            }
            withContext(Dispatchers.Main) { postCreate(koin) }
        }
    }

    fun postCreate(koin: Koin) {
        koin.get<PermissionManager>()
        val provider = koin.get<AppSettingsManager>().startupBackend::value
        RemoteServiceManager.initialize(this, provider)
        koin.get<HostState>().start()
        val runController = koin.get<AzurPilotRunController>()
        runController.start()
        // 富接口：与薄接口并存。实例选择由运行控制器持有，这里把它转给网关做订阅
        koin.get<AzurPilotGateway>().start()
        val api = koin.get<AzurPilotApi>()
        api.start()
        koin.get<CoroutineScope>(named<AppCoroutineScope>()).launch {
            runController.state
                .map { it.selectedConfig }
                .distinctUntilChanged()
                .collect { api.onInstanceSelected(it) }
        }
        koin.get<OverlayController>().setup()
        koin.get<ScreenSaverOverlayManager>().setup()
    }
}
