package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.service.WifiDeviceService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enum.CustomPermissionType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGPermissionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Owns the permission-observation / alert slice of [WifiScaleSetupViewModel] (MOB-1501).
 * Behaviour-preserving verbatim move; network refresh and location-permission checks are
 * reached through the injected callbacks so the network state stays with [WifiNetworkManager].
 */
class WifiPermissionsManager(
    private val permissionService: GGPermissionService,
    private val dialogUtility: IDialogUtility,
    private val wifiScaleService: WifiDeviceService,
    private val scope: CoroutineScope,
    private val getState: () -> WifiScaleSetupState,
    private val onIntent: (WifiScaleSetupIntent) -> Unit,
    private val subscribePermissions: () -> Flow<GGPermissionStatusMap>,
    private val onUpdateNetworkStatus: () -> Unit,
    private val isAllLocationPermissionGranted: () -> Boolean,
    private val enqueueDialog: (DialogModel) -> Unit,
) {

    private val TAG = "WifiPermissionsManager"

    fun observePermissions() {
        scope.launch {
            subscribePermissions().collect { permissions ->
                onIntent(WifiScaleSetupIntent.SetPermissions(permissions))
                AppPermissionsHelper.areRequiredPermissionsEnabled(permissions, setupType = DeviceSetupType.Wifi)
                // Refresh WiFi information when permissions change to ensure WiFi name is current
                onUpdateNetworkStatus()
            }
        }
    }

    fun handlePermissionsStep() {
        val areRequiredPermissionsEnabled = AppPermissionsHelper
            .areRequiredPermissionsEnabled(getState().permissions, setupType = DeviceSetupType.Wifi)
        onIntent(WifiScaleSetupIntent.SetCanProceedToNext(areRequiredPermissionsEnabled))
        if (areRequiredPermissionsEnabled) {
            onIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.WIFI_PASSWORD))
        }
        onUpdateNetworkStatus()
    }

    /**
     * Requests a specific permission using the PermissionService.
     */
    fun requestPermission(permissionType: String) {
        if (permissionType == CustomPermissionType.WIFI_SWITCH_LOCATION.value) {
            // Check if location permissions are granted before allowing WiFi switch request
            val hasLocationPermissions = isAllLocationPermissionGranted()
            if (!hasLocationPermissions) {
                AppLog.w(TAG, "Location permissions not granted")
                return
            }
            permissionService.requestPermission(GGPermissionType.WIFI_SWITCH)
            return
        }
        scope.launch {
            try {
                dialogUtility.permissionAlert(
                    permissionType = permissionType,
                    onRequest = {
                        permissionService.requestPermission(permissionType)
                    },
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error requesting permission ${permissionType}", e)
            }
        }
    }

    /**
     * Shows permission revoked alert.
     * Equivalent to TypeScript showPermissionRevokedAlert()
     */
    fun showPermissionRevokedAlert() {
        scope.launch {
            try {
                val currentState = getState()
                val permissions = currentState.permissions

                // Check location switch permission
                val isLocationSwitchEnabled = permissions[GGPermissionType.LOCATION_SWITCH] == GGPermissionState.ENABLED
                val isLocationAuthorized = permissions[GGPermissionType.LOCATION] == GGPermissionState.ENABLED

                AppLog.d(
                    TAG,
                    "showPermissionRevokedAlert - Location switch: $isLocationSwitchEnabled, Location: $isLocationAuthorized",
                )

                if (!isLocationSwitchEnabled) {
                    // Show location disabled error
                    enqueueDialog(
                        DialogModel.Alert(
                            title = DeviceSetupStrings.PermissionAlerts.LocationDisabled.Title,
                            message = DeviceSetupStrings.PermissionAlerts.LocationDisabled.Message,
                            dismissText = DeviceSetupStrings.PermissionAlerts.LocationDisabled.Enable,
                            onDismiss = {
                                // Open location settings
                                try {
                                    wifiScaleService.openWifiSettings()
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "Failed to open location settings", e)
                                }
                            },
                        ),
                    )
                } else if (!isLocationAuthorized) {
                    // Show location access disabled error
                    enqueueDialog(
                        DialogModel.Alert(
                            title = DeviceSetupStrings.PermissionAlerts.LocationAccessDisabled.Title,
                            message = DeviceSetupStrings.PermissionAlerts.LocationAccessDisabled.Message,
                            dismissText = DeviceSetupStrings.PermissionAlerts.LocationAccessDisabled.Enable,
                            onDismiss = {
                                requestPermission(GGPermissionType.LOCATION)
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error showing permission revoked alert", e)
            }
        }
    }

    /**
     * Handles the "Get Scale MAC Address" button click
     */
    fun onGetScaleMacAddress() {
        AppLog.d(TAG, "MAC address setup requested")
        onIntent(WifiScaleSetupIntent.SetShouldGetMacAddress(true))
        val currentState = getState()
        val arePermissionsCurrentlyEnabled = AppPermissionsHelper
            .areRequiredPermissionsEnabled(currentState.permissions, setupType = DeviceSetupType.Wifi)
        if (arePermissionsCurrentlyEnabled) {
            onIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.ACTIVATE_SCALE))
        } else {
            onIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.PERMISSIONS))
        }
        // The intent is already handled by the reducer to set MAC setup flags
    }
}
