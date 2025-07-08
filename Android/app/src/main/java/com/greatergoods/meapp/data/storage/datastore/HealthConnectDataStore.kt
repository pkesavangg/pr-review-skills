package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

/**
 * Extension property to provide HealthConnectDataMap DataStore instance from Context.
 */
val Context.healthConnectDataStore: DataStore<HealthConnectDataMap> by dataStore(
    fileName = "health_connect_data.pb",
    serializer = HealthConnectDataMapSerializer,
)

/**
 * DataStore for managing Health Connect integration and sync status.
 */
class HealthConnectDataStore(context: Context) : BaseProtoDataStore<HealthConnectDataMap>(
    dataStore = context.healthConnectDataStore,
) {
    /**
     * Emits a Flow of all HealthConnectData entries keyed by accountId.
     */
    val healthConnectDataFlow: Flow<Map<String, HealthConnectData>> = dataFlow.map { it.dataMap }

    /**
     * Returns a Flow of the current active account ID.
     */
    val activeAccountIdFlow: Flow<String?> = dataFlow.map {
        it.dataMap.entries.firstOrNull { entry -> entry.value.integrated }?.key
    }

    /**
     * Returns a Flow of HealthConnectData for a specific account.
     */
    fun getHealthConnectDataFlow(accountId: String): Flow<HealthConnectData?> =
        dataFlow.map { it.dataMap[accountId] }

    /**
     * Gets the current map of HealthConnectData.
     */
    suspend fun healthConnectData(): Map<String, HealthConnectData> = getData().dataMap

    /**
     * Sets or updates a HealthConnectData entry for the given accountId.
     */
    suspend fun setHealthConnectData(accountId: String, data: HealthConnectData) {
        updateData { current ->
            current.toBuilder().putData(accountId, data).build()
        }
    }

    /**
     * Removes a HealthConnectData entry for the given accountId.
     */
    suspend fun removeHealthConnectData(accountId: String) {
        updateData { current ->
            current.toBuilder().removeData(accountId).build()
        }
    }

    /**
     * Gets a HealthConnectData entry by its accountId.
     */
    suspend fun getHealthConnectData(accountId: String): HealthConnectData? = getData().dataMap[accountId]

    /**
     * Checks if a HealthConnectData entry exists for the given accountId.
     */
    suspend fun hasHealthConnectData(accountId: String): Boolean = getData().dataMap.containsKey(accountId)
    override fun getDefaultInstance(): HealthConnectDataMap = HealthConnectDataMap.getDefaultInstance()

    /**
     * Clears all data.
     */
    override suspend fun clearData() {
       super.clearData()
    }

    /**
     * Updates the integration status for an account.
     */
    suspend fun updateIntegrationStatus(accountId: String, integrated: Boolean) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setIntegrated(integrated)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setIntegrated(integrated)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the alert seen status for an account.
     */
    suspend fun updateAlertSeen(accountId: String, seen: Boolean) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setAlertSeen(seen)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setAlertSeen(seen)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the out of sync status for an account.
     */
    suspend fun updateOutOfSync(accountId: String, outOfSync: Boolean) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setOutOfSync(outOfSync)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setOutOfSync(outOfSync)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the modal state for an account.
     */
    suspend fun updateModalState(accountId: String, state: Boolean) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setModalState(state)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setModalState(state)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the update timestamp for an account.
     */
    suspend fun updateTimestamp(accountId: String, timestamp: String) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setUpdatedAt(timestamp)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setUpdatedAt(timestamp)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the integration timestamp for an account.
     */
    suspend fun updateIntegrationTimestamp(accountId: String, timestamp: String) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setIntegratedAt(timestamp)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setIntegratedAt(timestamp)
                        .build(),
            ).build()
        }
    }

    /**
     * Updates the granted permissions for an account.
     */
    suspend fun updatePermissions(accountId: String, permissions: List<String>) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.clearGrantedPermission()
                    ?.addAllGrantedPermission(permissions)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .addAllGrantedPermission(permissions)
                        .build(),
            ).build()
        }
    }
}

/**
 * Serializer for HealthConnectDataMap proto.
 */
object HealthConnectDataMapSerializer : Serializer<HealthConnectDataMap> {
    override val defaultValue: HealthConnectDataMap = HealthConnectDataMap.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): HealthConnectDataMap =
        HealthConnectDataMap.parseFrom(input)

    override suspend fun writeTo(
        t: HealthConnectDataMap,
        output: OutputStream,
    ) = t.writeTo(output)
}
