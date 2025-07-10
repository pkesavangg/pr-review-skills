package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.permission.PermissionStatus
import com.greatergoods.meapp.domain.model.permission.PermissionType
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing app permissions using the bluetooth connect plugin.
 * Handles checking permission status, requesting permissions, and monitoring permission changes.
 */
interface IPermissionService {

    /**
     * Gets the current status of all permissions.
     * @return PermissionStatus object containing the status of all permission types
     */
    suspend fun getAllPermissionStatus(): PermissionStatus

    /**
     * Gets the current status of a specific permission.
     * @param permissionType The type of permission to check
     * @return Current permission state as a String
     */
    suspend fun getPermissionStatus(permissionType: PermissionType): String

    /**
     * Requests permission for a specific permission type.
     * @param permissionType The type of permission to request
     * @return The result of the permission request
     */
    suspend fun requestPermission(permissionType: PermissionType): String

    /**
     * Requests multiple permissions at once.
     * @param permissionTypes List of permission types to request
     * @return PermissionStatus object containing the updated status of all permissions
     */
    suspend fun requestMultiplePermissions(permissionTypes: List<PermissionType>): PermissionStatus

    /**
     * Observes permission status changes for scan-related permissions.
     * @return Flow that emits true when scan permissions are granted, false otherwise
     */
    fun observeScanPermissionStatus(): Flow<Boolean>

    /**
     * Observes all permission status changes.
     * @return Flow that emits PermissionStatus updates
     */
    fun observePermissionStatusChanges(): Flow<PermissionStatus>

    /**
     * Checks if Bluetooth scan is available for the current device.
     * @return true if Bluetooth scan is available (Android 12+), false otherwise
     */
    fun isBluetoothScanAvailable(): Boolean

    /**
     * Opens the app settings page for manual permission management.
     */
    fun openAppSettings()

    /**
     * Stops permission monitoring and cleans up resources.
     */
    fun stopPermissionMonitoring()

    /**
     * Starts permission monitoring for reactive updates.
     */
    fun startPermissionMonitoring()
}
