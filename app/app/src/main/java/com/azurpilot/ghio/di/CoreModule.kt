package com.azurpilot.ghio.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.azurpilot.ghio.AppDispatchers
import com.azurpilot.ghio.config.DataStoreUserConfigurationStore
import com.azurpilot.ghio.config.UserConfigurationSerializer
import com.azurpilot.ghio.config.UserConfigurationStore
import com.azurpilot.ghio.constant.DataStoreFile
import com.azurpilot.ghio.domain.UserConfiguration
import com.azurpilot.ghio.i18n.LocalizedTextRenderer
import com.azurpilot.ghio.settings.AppSettingsGateway
import com.azurpilot.ghio.settings.AppSettingsManager
import com.azurpilot.ghio.update.AppUpdateManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

/** 进程级 scope 限定符，避免与其它 CoroutineScope 绑定冲突 */
object AppCoroutineScope

val coreModule = module {
    single(named<AppCoroutineScope>()) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "AppCoroutineScope uncaught")
        }
        CoroutineScope(SupervisorJob() + AppDispatchers.Default + handler)
    }

    single<DataStore<UserConfiguration>> {
        DataStoreFactory.create(
            serializer = UserConfigurationSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserConfiguration() },
            produceFile = {
                androidContext().dataStoreFile(DataStoreFile.USER_CONFIGRATION)
            },
        )
    }
    single<UserConfigurationStore> { DataStoreUserConfigurationStore(get()) }

    single { AppSettingsManager(androidContext()) }
    single<AppSettingsGateway> { get<AppSettingsManager>() }

    single { LocalizedTextRenderer(androidContext()) }
    single { AppUpdateManager(androidContext(), get(named<AppCoroutineScope>()), get<AppSettingsManager>()) }
}
