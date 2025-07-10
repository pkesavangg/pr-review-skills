package com.greatergoods.meapp.core.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.permission.PermissionState
import com.greatergoods.meapp.domain.model.permission.PermissionStatus
import com.greatergoods.meapp.domain.model.permission.PermissionType
import com.greatergoods.meapp.domain.services.IPermissionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing app permissions.
 * Handles checking permission status, requesting permissions, and monitoring permission changes.
 * TODO: Integrate with bluetoothconnect plugin when module dependencies are resolved.
 */
@Singleton
class PermissionService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService
) : BaseService(connectivityObserver, dialogQueueService), IPermissionService {

    companion object {
        private const val TAG = "PermissionService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isMonitoring = false

    // StateFlow for permission status changes
    private val _permissionStatusFlow = MutableStateFlow(PermissionStatus())
    private val permissionStatusFlow: StateFlow<PermissionStatus> = _permissionStatusFlow.asStateFlow()

    /**
     * Initializes the permission service with the current activity context.
     * @param activity The current activity for permission handling
     */
    fun initialize(activity: Activity) {
        try {
            AppLog.d(TAG, "Initializing PermissionService with activity: ${activity.javaClass.simpleName}")
            AppLog.i(TAG, "PermissionService initialized successfully")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to initialize PermissionService", e.toString())
            throw e
        }
    }

        override suspend fun getAllPermissionStatus(): PermissionStatus {
        return try {
            AppLog.d(TAG, "Getting all permission status")

            val permissionStatus = PermissionStatus(
                bluetooth = checkAndroidPermission(Manifest.permission.BLUETOOTH),
                bluetoothSwitch = checkBluetoothEnabled(),
                nearbyDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    checkAndroidPermission(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    PermissionState.NOT_DETERMINED
                },
                location = checkAndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION),
                locationSwitch = PermissionState.NOT_DETERMINED, // TODO: Check location services enabled
                notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkAndroidPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionState.ENABLED
                },
                camera = checkAndroidPermission(Manifest.permission.CAMERA)
            )

            _permissionStatusFlow.value = permissionStatus
            AppLog.d(TAG, "Retrieved permission status: $permissionStatus")

            permissionStatus
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get all permission status", e.toString())
            PermissionStatus()
        }
    }

    override suspend fun getPermissionStatus(permissionType: PermissionType): String {
        return try {
            AppLog.d(TAG, "Getting permission status for: ${permissionType.value}")

            val status = when (permissionType) {
                PermissionType.BLUETOOTH -> checkAndroidPermission(Manifest.permission.BLUETOOTH)
                PermissionType.BLUETOOTH_SWITCH -> checkBluetoothEnabled()
                PermissionType.NEARBY_DEVICE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    checkAndroidPermission(Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    PermissionState.NOT_DETERMINED
                }
                PermissionType.LOCATION -> checkAndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                PermissionType.LOCATION_SWITCH -> PermissionState.NOT_DETERMINED // TODO: Check location services
                PermissionType.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkAndroidPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionState.ENABLED
                }
                PermissionType.CAMERA -> checkAndroidPermission(Manifest.permission.CAMERA)
                PermissionType.ALL -> PermissionState.NOT_DETERMINED
            }

            AppLog.d(TAG, "Permission status for ${permissionType.value}: $status")
            status
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get permission status for ${permissionType.value}", e.toString())
            PermissionState.NOT_DETERMINED
        }
    }

        override suspend fun requestPermission(permissionType: PermissionType): String {
        return try {
            AppLog.d(TAG, "Requesting permission: ${permissionType.value}")

            // For now, just return the current status
            // TODO: Implement actual permission requesting when bluetooth plugin is integrated
            val status = getPermissionStatus(permissionType)
            AppLog.i(TAG, "Permission request completed for ${permissionType.value}: $status")

            status
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to request permission ${permissionType.value}", e.toString())
            PermissionState.NOT_DETERMINED
        }
    }

    override suspend fun requestMultiplePermissions(permissionTypes: List<PermissionType>): PermissionStatus {
        return try {
            AppLog.d(TAG, "Requesting multiple permissions: ${permissionTypes.map { it.value }}")

            // Request permissions sequentially to avoid conflicts
            for (permissionType in permissionTypes) {
                requestPermission(permissionType)
            }

            // Return the updated status
            getAllPermissionStatus()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to request multiple permissions", e.toString())
            PermissionStatus()
        }
    }

        override fun observeScanPermissionStatus(): Flow<Boolean> {
        // TODO: Implement proper scan permission monitoring
        return flowOf(false)
    }

    override fun observePermissionStatusChanges(): Flow<PermissionStatus> {
        return permissionStatusFlow
    }

    override fun isBluetoothScanAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    override fun openAppSettings() {
        try {
            AppLog.d(TAG, "Opening app settings")
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to open app settings", e.toString())
        }
    }

    override fun stopPermissionMonitoring() {
        try {
            AppLog.d(TAG, "Stopping permission monitoring")
            isMonitoring = false
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to stop permission monitoring", e.toString())
        }
    }

    override fun startPermissionMonitoring() {
        try {
            AppLog.d(TAG, "Starting permission monitoring")
            isMonitoring = true
            // TODO: Implement permission monitoring when bluetooth plugin is integrated
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to start permission monitoring", e.toString())
        }
    }

    /**
     * Checks standard Android permission status.
     */
    private fun checkAndroidPermission(permission: String): String {
        return try {
            when (ContextCompat.checkSelfPermission(context, permission)) {
                PackageManager.PERMISSION_GRANTED -> PermissionState.ENABLED
                PackageManager.PERMISSION_DENIED -> PermissionState.DISABLED
                else -> PermissionState.NOT_DETERMINED
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error checking permission $permission", e.toString())
            PermissionState.NOT_DETERMINED
        }
    }

    /**
     * Checks if Bluetooth is enabled on the device.
     */
    private fun checkBluetoothEnabled(): String {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            when {
                bluetoothAdapter == null -> PermissionState.NOT_DETERMINED
                bluetoothAdapter.isEnabled -> PermissionState.ENABLED
                else -> PermissionState.DISABLED
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error checking Bluetooth status", e.toString())
            PermissionState.NOT_DETERMINED
        }
    }
}
