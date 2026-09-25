package com.aliothmoon.azurpilot.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.aliothmoon.azurpilot.AppDispatchers
import com.aliothmoon.azurpilot.config.DataStoreUserConfigurationStore
import com.aliothmoon.azurpilot.config.UserConfigurationSerializer
import com.aliothmoon.azurpilot.config.UserConfigurationStore
import com.aliothmoon.azurpilot.constant.DataStoreFile
import com.aliothmoon.azurpilot.domain.UserConfiguration
import com.aliothmoon.azurpilot.i18n.LocalizedTextRenderer
import com.aliothmoon.azurpilot.settings.AppSettingsGateway
import com.aliothmoon.azurpilot.settings.AppSettingsManager
import com.aliothmoon.azurpilot.update.AppUpdateManager
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
    single { AppUpdateManager(androidContext(), get(named<AppCoroutineScope>())) }
}
