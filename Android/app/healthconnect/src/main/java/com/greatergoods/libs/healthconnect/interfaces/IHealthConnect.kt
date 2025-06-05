package com.greatergoods.libs.healthconnect.interfaces

import androidx.health.connect.client.records.Record
import com.greatergoods.libs.healthconnect.enum.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enum.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enum.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import kotlin.reflect.KClass

/**
 * Main interface for interacting with Health Connect APIs.
 * All functions are suspend or Flow-based for coroutine support.
 */
interface IHealthConnect {
    /**
     * Deletes all health data for the given options.
     */
    suspend fun deleteAllData(options: HealthConnectOptions): HealthConnectResult<Unit>

    /**
     * Deletes specific health data entries.
     */
    suspend fun deleteEntry(data: List<HealthConnectData>): HealthConnectResult<Unit>

    /**
     * Deletes records by unique client record IDs for the given types.
     */
    suspend fun deleteEntryByUniqueIdentifier(
        recordTypes: Set<KClass<out Record>>,
        clientRecordIdsList: List<String>,
    )

    /**
     * Gets the list of currently approved Health Connect permissions.
     */
    suspend fun getApprovedPermissionList(): Set<String>

    /**
     * Returns the set of permission strings for given options.
     */
    fun getPermissions(options: HealthConnectOptions): Set<String>

    /**
     * Gets the permission status for the given options.
     */
    suspend fun getPermissionStatus(options: HealthConnectOptions): HealthConnectPermissionStatus

    /**
     * Gets the current Health Connect status on the device.
     */
    suspend fun getStatus(): HealthConnectStatus

    /**
     * Checks if any permission in a set is granted.
     */
    suspend fun hasAnyPermissions(permissions: Set<String>): Boolean

    /**
     * Checks if all permissions in a set are granted.
     */
    suspend fun hasPermissions(permissions: Set<String>): Boolean

    /**
     * Checks if Health Connect is installed (SDK_AVAILABLE).
     */
    fun isAppInstalled(): Boolean

    /**
     * Checks if Health Connect is available on the device.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Checks if a specific package is installed (by package name).
     */
    fun isHealthConnectInstalled(packageName: String): Boolean

    /**
     * Requests authorization for the given options.
     */
    suspend fun requestAuthorization(options: HealthConnectOptions): HealthConnectRequestStatus

    /**
     * Revokes all Health Connect permissions.
     */
    suspend fun revokeAllPermissions(): HealthConnectResult<Unit>

    /**
     * Saves a list of health data entries.
     */
    suspend fun saveData(data: List<HealthConnectData>): HealthConnectResult<Unit>

    // Optionally, add Flow-based APIs for background sync or live updates
    // fun observeSyncStatus(): Flow<SyncStatus>
}
