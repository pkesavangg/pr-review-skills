package com.greatergoods.libs.healthconnect.interfaces

import androidx.health.connect.client.records.Record
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import kotlin.reflect.KClass
import android.content.Intent

/**
 * Interface for interacting with Health Connect APIs.
 */
interface IHealthConnect {

    /**
     * Handles new intents, specifically for privacy policy links from Health Connect.
     * This is called automatically by the library when intents are received.
     * You should not call this method directly - use HealthConnect.handleActivityIntent() instead.
     * @param intent The new intent received by the Activity.
     */
    fun handleOnNewIntent(intent: Intent?)

    /**
     * Requests authorization following the Capacitor plugin pattern.
     * This method will:
     * 1. Check current permissions
     * 2. If not granted, launch permission request
     * 3. Call the callback with the result
     *
     * @param options The HealthConnectOptions specifying which permissions to request
     * @param callback Callback to receive the authorization result
     */
    suspend fun requestAuthorization(
        options: HealthConnectOptions,
        callback: (HealthConnectRequestStatus) -> Unit
    )

    /**
     * Checks if Health Connect is available on the device.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Gets the current Health Connect status.
     */
    suspend fun getStatus(): HealthConnectStatus

    /**
     * Checks if the Health Connect app is installed.
     */
    fun isAppInstalled(): Boolean

    /**
     * Checks if a specific Health Connect package is installed.
     */
    fun isHealthConnectInstalled(packageName: String): Boolean

    /**
     * Gets the permission status for the given options.
     */
    suspend fun getPermissionStatus(options: HealthConnectOptions): HealthConnectPermissionStatus

    /**
     * Gets the list of currently approved Health Connect permissions.
     */
    suspend fun getApprovedPermissionList(): Set<String>

    /**
     * Returns the set of permission strings for given options.
     */
    fun getRequestedPermissions(options: HealthConnectOptions): Set<String>

    /**
     * Checks if any permission in a set is granted.
     */
    suspend fun hasAnyPermissions(permissions: Set<String>): Boolean

    /**
     * Checks if all permissions in a set are granted.
     */
    suspend fun hasAllPermissions(permissions: Set<String>): Boolean

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
        clientRecordIdsList: List<String>
    )

    /**
     * Revokes all Health Connect permissions.
     */
    suspend fun revokeAllPermissions(): HealthConnectResult<Unit>

    /**
     * Saves a list of health data entries.
     */
    suspend fun saveData(data: List<HealthConnectData>): HealthConnectResult<Unit>

    /**
     * Launches the Health Connect app or settings screen.
     */
    fun launchHealthConnect(activity: android.app.Activity, forcePlayStore: Boolean = false): Boolean
}
