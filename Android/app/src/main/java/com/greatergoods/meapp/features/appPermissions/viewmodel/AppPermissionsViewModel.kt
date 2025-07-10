package com.greatergoods.meapp.features.appPermissions.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.permission.PermissionState
import com.greatergoods.meapp.domain.model.permission.PermissionStatus
import com.greatergoods.meapp.domain.model.permission.PermissionType
import com.greatergoods.meapp.domain.services.IPermissionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the App Permissions screen.
 * Manages permission states and handles permission-related intents using PermissionService.
 */
@HiltViewModel
class AppPermissionsViewModel @Inject constructor(
    private val permissionService: IPermissionService,
) : ViewModel() {

    companion object {
        private const val TAG = "AppPermissionsViewModel"
    }

    private val _state = MutableStateFlow(AppPermissionsState())
    val state: StateFlow<AppPermissionsState> = _state.asStateFlow()

    init {
        refreshPermissions()
        observePermissionChanges()
    }

    /**
     * Handles intents from the UI.
     */
    fun handleIntent(intent: AppPermissionsIntent) {
        when (intent) {
            is AppPermissionsIntent.RefreshPermissions -> refreshPermissions()
            is AppPermissionsIntent.RequestPermission -> requestPermission(intent.permissionType)
            is AppPermissionsIntent.OpenAppSettings -> openAppSettings()
        }
    }

    /**
     * Observes permission status changes from the service.
     */
    private fun observePermissionChanges() {
        viewModelScope.launch {
            try {
                permissionService.observePermissionStatusChanges().collect { permissionStatus ->
                    _state.value = _state.value.copy(
                        bluetoothPermission = mapPermissionState(permissionStatus.bluetoothSwitch),
                        nearbyDevicePermission = mapPermissionState(permissionStatus.nearbyDevice),
                        locationPermission = mapPermissionState(permissionStatus.location),
                        notificationsPermission = mapPermissionState(permissionStatus.notification),
                        cameraPermission = mapPermissionState(permissionStatus.camera),
                        isLoading = false
                    )
                    AppLog.d(TAG, "Permission status updated from service")
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error observing permission changes", e.toString())
                _state.value = _state.value.copy(isLoading = false, error = "Failed to observe permissions")
            }
        }
    }

    /**
     * Refreshes all permission statuses using the permission service.
     */
    private fun refreshPermissions() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val permissionStatus = permissionService.getAllPermissionStatus()

                _state.value = _state.value.copy(
                    healthConnectPermission = checkHealthConnectPermission(),
                    bluetoothPermission = mapPermissionState(permissionStatus.bluetoothSwitch),
                    nearbyDevicePermission = mapPermissionState(permissionStatus.nearbyDevice),
                    locationPermission = mapPermissionState(permissionStatus.location),
                    notificationsPermission = mapPermissionState(permissionStatus.notification),
                    cameraPermission = mapPermissionState(permissionStatus.camera),
                    isLoading = false
                )
                AppLog.i(TAG, "Permissions refreshed successfully")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error refreshing permissions", e.toString())
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to refresh permissions"
                )
            }
        }
    }

    /**
     * Requests a specific permission using the permission service.
     */
    private fun requestPermission(permissionType: PermissionType) {
        viewModelScope.launch {
            try {
                AppLog.d(TAG, "Requesting permission: ${permissionType.value}")
                _state.value = _state.value.copy(isLoading = true, error = null)

                val result = permissionService.requestPermission(permissionType)
                AppLog.i(TAG, "Permission request result for ${permissionType.value}: $result")

                // Permission status will be updated through the observer
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error requesting permission ${permissionType.value}", e.toString())
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to request ${permissionType.value} permission"
                )
            }
        }
    }

    /**
     * Opens the app settings page for manual permission management.
     */
    private fun openAppSettings() {
        try {
            AppLog.d(TAG, "Opening app settings")
            permissionService.openAppSettings()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error opening app settings", e.toString())
            _state.value = _state.value.copy(error = "Failed to open settings")
        }
    }

    /**
     * Checks Health Connect permission status.
     * Note: Health Connect permissions are handled differently from regular Android permissions.
     */
    private fun checkHealthConnectPermission(): AppPermissionStatus {
        // Health Connect permissions are checked differently
        // For now, we'll assume it's available if the app is installed
        // TODO: Implement proper Health Connect permission checking
        return AppPermissionStatus.NotRequested
    }

    /**
     * Maps PermissionState from the service to the ViewModel's AppPermissionStatus.
     */
    private fun mapPermissionState(permissionState: String): AppPermissionStatus {
        return when (permissionState) {
            PermissionState.ENABLED -> AppPermissionStatus.Granted
            PermissionState.DISABLED,
            PermissionState.PERMANENTLY_DENIED -> AppPermissionStatus.Denied
            PermissionState.NOT_DETERMINED,
            PermissionState.NOT_REQUESTED,
            PermissionState.APPROX_LOCATION -> AppPermissionStatus.NotRequested
            else -> AppPermissionStatus.NotRequested
        }
    }
}

/**
 * Represents the different states a permission can be in for the UI.
 */
enum class AppPermissionStatus {
    Granted,
    Denied,
    NotRequested
}

/**
 * State class for the App Permissions screen.
 */
data class AppPermissionsState(
    val healthConnectPermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val bluetoothPermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val nearbyDevicePermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val locationPermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val notificationsPermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val cameraPermission: AppPermissionStatus = AppPermissionStatus.NotRequested,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Checks if essential Bluetooth permissions are granted.
     */
    fun isBluetoothPermissionGranted(): Boolean {
        return bluetoothPermission == AppPermissionStatus.Granted &&
               (nearbyDevicePermission == AppPermissionStatus.Granted ||
                locationPermission == AppPermissionStatus.Granted)
    }
}

/**
 * Intent class for the App Permissions screen.
 */
sealed class AppPermissionsIntent {
    data object RefreshPermissions : AppPermissionsIntent()
    data class RequestPermission(val permissionType: PermissionType) : AppPermissionsIntent()
    data object OpenAppSettings : AppPermissionsIntent()
}
