package com.aliothmoon.azurpilot.config

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.aliothmoon.azurpilot.domain.UserConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** App 私有设置的数据层；版本不兼容时使用当前默认值。 */
interface UserConfigurationStore {
    val data: Flow<UserConfiguration>

    suspend fun update(transform: (UserConfiguration) -> UserConfiguration)
}

class DataStoreUserConfigurationStore(
    private val store: DataStore<UserConfiguration>,
) : UserConfigurationStore {
    override val data: Flow<UserConfiguration> = store.data

    override suspend fun update(transform: (UserConfiguration) -> UserConfiguration) {
        store.updateData(transform)
    }
}

@Serializable
private data class ConfigurationEnvelope(
    val schemaVersion: Int,
    val configuration: UserConfiguration,
)

object UserConfigurationSerializer : Serializer<UserConfiguration> {
    private const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: UserConfiguration = UserConfiguration()

    override suspend fun readFrom(input: InputStream): UserConfiguration {
        val content = input.readBytes().decodeToString()
        if (content.isBlank()) return defaultValue
        try {
            val envelope = json.decodeFromString<ConfigurationEnvelope>(content)
            return if (envelope.schemaVersion == SCHEMA_VERSION) envelope.configuration else defaultValue
        } catch (exc: SerializationException) {
            throw CorruptionException("Invalid user configuration", exc)
        }
    }

    override suspend fun writeTo(t: UserConfiguration, output: OutputStream) {
        output.write(json.encodeToString(ConfigurationEnvelope(SCHEMA_VERSION, t)).encodeToByteArray())
    }
}
