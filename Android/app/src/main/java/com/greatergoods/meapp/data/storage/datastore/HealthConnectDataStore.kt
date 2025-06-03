package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.content.Context

/**
 * Extension property to provide HealthConnectDataMap DataStore instance from Context.
 */
val Context.healthConnectDataStore: DataStore<HealthConnectDataMap> by dataStore(
    fileName = "health_connect_data.pb",
    serializer = HealthConnectDataMapSerializer,
)

/**
 * DataStore for persisting a map of HealthConnectData.
 *
 * @constructor Creates a HealthConnectDataStore with the given context.
 * @param context The application context.
 */
class HealthConnectDataStore(context: Context) : BaseProtoDataStore<HealthConnectDataMap>(
    dataStore = context.healthConnectDataStore,
) {
    /**
     * Returns a [Flow] of the current map of HealthConnectData.
     */
    val dataMapFlow: Flow<Map<String, HealthConnectData>> = dataFlow.map { it.dataMap }

    /**
     * Gets the current map of HealthConnectData.
     */
    suspend fun getDataMap(): Map<String, HealthConnectData> = getData().dataMap

    /**
     * Sets or updates a HealthConnectData entry for the given key.
     */
    suspend fun setEntry(key: String, value: HealthConnectData) {
        updateData { current ->
            current.toBuilder().putData(key, value).build()
        }
    }

    /**
     * Removes a HealthConnectData entry for the given key.
     */
    suspend fun removeEntry(key: String) {
        updateData { current ->
            val builder = current.toBuilder()
            builder.removeData(key)
            builder.build()
        }
    }

    /**
     * Clears all HealthConnectData entries.
     */
    override suspend fun clearData() {
        updateData { it.toBuilder().clearData().build() }
    }

    /**
     * Gets a HealthConnectData entry by its account id (key).
     * @param accountId The key for the HealthConnectData entry.
     * @return The HealthConnectData if present, or null.
     */
    suspend fun getByAccountId(accountId: String): HealthConnectData? = getData().dataMap[accountId]
}

/**
 * Serializer for HealthConnectDataMap proto.
 */
object HealthConnectDataMapSerializer : Serializer<HealthConnectDataMap> {
    override val defaultValue: HealthConnectDataMap = HealthConnectDataMap.getDefaultInstance()

    override suspend fun readFrom(input: java.io.InputStream): HealthConnectDataMap =
        HealthConnectDataMap.parseFrom(input)

    override suspend fun writeTo(t: HealthConnectDataMap, output: java.io.OutputStream) =
        t.writeTo(output)
}
