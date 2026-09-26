package com.azurpilot.ghio

import android.app.Application
import com.azurpilot.ghio.constant.AppPaths
import com.azurpilot.ghio.di.AppCoroutineScope
import com.azurpilot.ghio.di.coreModule
import com.azurpilot.ghio.di.hostModule
import com.azurpilot.ghio.di.logModule
import com.azurpilot.ghio.di.overlayModule
import com.azurpilot.ghio.di.privilegedModule
import com.azurpilot.ghio.di.prootModule
import com.azurpilot.ghio.di.provisionModule
import com.azurpilot.ghio.di.viewModelModule
import com.azurpilot.ghio.log.AppLogWriter
import com.azurpilot.ghio.log.CrashHandler
import com.azurpilot.ghio.log.LogCleaner
import com.azurpilot.ghio.log.LogTreeHolder
import com.azurpilot.ghio.overlay.OverlayController
import com.azurpilot.ghio.overlay.screensaver.ScreenSaverOverlayManager
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.privileged.RemoteServiceManager
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotGateway
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.settings.AppSettingsManager
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
        // 富接口：与 /android/* 薄接口并存。实例选择归 repository 自己管，
        // 首次进入时对齐运行控制器选的那个配置——否则原生界面一打开就是空实例。
        koin.get<AzurPilotGateway>().start()
        val repository = koin.get<AzurPilotRepository>()
        repository.start()
        koin.get<CoroutineScope>(named<AppCoroutineScope>()).launch {
            runController.state
                .map { it.selectedConfig }
                .distinctUntilChanged()
                .collect { config ->
                    if (repository.selectedInstance.value == null) {
                        repository.selectInstance(config)
                    }
                }
        }
        koin.get<OverlayController>().setup()
        koin.get<ScreenSaverOverlayManager>().setup()
    }
}
