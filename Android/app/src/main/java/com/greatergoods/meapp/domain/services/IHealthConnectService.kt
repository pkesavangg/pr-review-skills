package com.greatergoods.meapp.domain.services

import android.content.Intent
import androidx.activity.ComponentActivity
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import kotlinx.coroutines.flow.Flow

/**
 * Interface for Health Connect service operations.
 */
interface IHealthConnectService {
    val requestingPermissions: HealthConnectOptions
    val outOfSyncState: Flow<Boolean>

    /**
     * Handles new intents for privacy policy and permissions rationale.
     * @param intent The new intent to handle
     */
    fun handleOnNewIntent(intent: Intent?)

    /**
     * Initializes Health Connect and sets up permission handling.
     * This should be called from an Activity context to register ActivityResultLauncher.
     */
    fun load(activity: androidx.activity.ComponentActivity)

    /**
     * Initializes Health Connect with the given activity context.
     */
    fun initializeHealthConnect(activity: ComponentActivity)

    /**
     * Checks if Health Connect is available on the device.
     */
    suspend fun checkAvailability(): Boolean

    /**
     * Gets the current Health Connect status.
     */
    suspend fun healthConnectStatus(): HealthConnectStatus

    /**
     * Revokes all Health Connect permissions.
     */
    suspend fun revokePermission(): Boolean

    /**
     * Checks if Health Connect is already being used by another account.
     */
    suspend fun checkIfAlreadyUsed(): Boolean

    /**
     * Opens the Health Connect app or settings.
     */
    suspend fun openHealthConnect(): Boolean

    /**
     * Checks the current permission status.
     */
    suspend fun checkPermissionStatus(): HealthConnectPermissionStatus

    /**
     * Requests Health Connect authorization using callback pattern.
     * @param callback Function to call when authorization completes
     */
    suspend fun requestAuthorization(callback: (HealthConnectRequestStatus) -> Unit)

    /**
     * Gets the list of approved permissions from Health Connect.
     * @return List of permission strings that the user has granted
     */
    suspend fun getApprovedPermissionList(): List<String>

    /**
     * Syncs all entries to Health Connect.
     */
    suspend fun syncAllData(fromOutOfSync: Boolean = false): Boolean

    /**
     * Deletes all Health Connect data.
     */
    suspend fun deleteAllData(): Boolean

    /**
     * Turns on Health Connect integration.
     */
    suspend fun turnOnIntegration(fromMultiDevice: Boolean = false, isRequestNeed: Boolean = false)

    /**
     * Removes Health Connect integration.
     */
    suspend fun removeHealthConnectIntegration(): Boolean

    /**
     * Clears all Health Connect data and settings.
     */
    suspend fun clearHealthConnect(): Boolean

    /**
     * Checks for permission changes.
     */
    suspend fun checkPermissionChange()

    /**
     * Checks for multiple device connections.
     */
    suspend fun checkMultiDeviceConnection(isPermissionEnabled: Boolean = false): Boolean

    /**
     * Handles Health Connect out of sync scenarios.
     */
    suspend fun healthConnectOutOfSync(): Boolean
    fun syncWeightHistory()
    suspend fun checkHealthConnectPermissionDisabled()
}
