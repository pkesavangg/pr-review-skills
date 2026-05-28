package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

/*
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
    suspend fun setHcIntegrationStatus(accountId: String, integrated: Boolean) {
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
     * Updates the open status for an account.
     */
    suspend fun setOpen(accountId: String, open: Boolean) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setOpen(open)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setOpen(open)
                        .build(),
            ).build()
        }
    }

    /**
     * Gets the open status for an account.
     */
    suspend fun getOpen(accountId: String): Boolean {
        return getData().dataMap[accountId]?.open ?: false
    }

    /**
     * Sets the assignedTo field for an account.
     */
    suspend fun setAssignedTo(accountId: String, assignedTo: String) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.setAssignedTo(assignedTo)
                    ?.build()
                    ?: HealthConnectData.newBuilder()
                        .setAssignedTo(assignedTo)
                        .build(),
            ).build()
        }
    }

    /**
     * Gets the assignedTo field for an account.
     */
    suspend fun getAssignedTo(accountId: String): String? {
        return getData().dataMap[accountId]?.assignedTo
    }

    /**
     * Clears the assignedTo field for an account.
     */
    suspend fun clearAssignedTo(accountId: String) {
        updateData { current ->
            current.toBuilder().putData(
                accountId,
                current.dataMap[accountId]?.toBuilder()
                    ?.clearAssignedTo()
                    ?.build()
                    ?: HealthConnectData.newBuilder()
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
     * Updates the granted permissions for an account.
     */
    suspend fun setPermissions(accountId: String, permissions: List<String>) {
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

    /**
     * Updates the integration info for an account.
     * Converts domain IntegratedDeviceInfo to proto ProtoIntegratedDeviceInfo.
     */
    suspend fun setIntegrationInfo(accountId: String, integrationInfo: com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo?) {
        if (integrationInfo == null) {
            updateData { current ->
                val currentDataBuilder = current.dataMap[accountId]?.toBuilder()
                    ?: HealthConnectData.newBuilder()
                currentDataBuilder.clearIntegrationInfo()
                current.toBuilder().putData(accountId, currentDataBuilder.build()).build()
            }
            return
        }
        val protoInfo = integrationInfo.toProtoOrNull(accountId)
        if (protoInfo == null) {
            AppLog.w(
                "HealthConnectDataStore",
                "Skipping integration DataStore write for account=$accountId — unknown operationType='${integrationInfo.operationType}'"
            )
            return
        }
        updateData { current ->
            val currentDataBuilder = current.dataMap[accountId]?.toBuilder()
                ?: HealthConnectData.newBuilder()
            currentDataBuilder.setIntegrationInfo(protoInfo)
            current.toBuilder().putData(accountId, currentDataBuilder.build()).build()
        }
    }

    /**
     * Extension function to convert domain IntegratedDeviceInfo to proto ProtoIntegratedDeviceInfo.
     * Returns null when [operationType] does not map to a known proto value, so callers can
     * skip the write instead of corrupting the DataStore with a null enum (which crashes at
     * serialization time with `ProtoIntegrationOperationType.getNumber()` NPE).
     */
    private fun com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo.toProtoOrNull(accountId: String): ProtoIntegratedDeviceInfo? {
        val protoOp = when {
            operationType.equals(com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType.SAVE.value, ignoreCase = true) ->
                ProtoIntegrationOperationType.PROTO_SAVE
            operationType.equals(com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType.REMOVE.value, ignoreCase = true) ->
                ProtoIntegrationOperationType.PROTO_REMOVE
            else -> return null
        }
        return ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(protoOp)
            .setScopes(
                ProtoIntegrationData.newBuilder()
                    .setAccountId(accountId)
                    .setDeviceId(scopes.deviceId)
                    .addAllScopes(scopes.preferences?.scopes ?: emptyList())
                    .build()
            )
            .build()
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
