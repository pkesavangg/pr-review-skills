package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Owns the permission + network observation slice of [BtWifiScaleSetupViewModel].
 *
 * Extracted from the ViewModel (MOB-1501) to keep it under detekt's LargeClass limit.
 * Behaviour-preserving: the logic is a verbatim move; cross-slice actions (settings/measurement
 * error states, pairing-timeout cancel) are reached through the injected callbacks so the
 * relevant step owners keep their own timeout state.
 */
class BtWifiPermissionsManager(
    private val ggDeviceService: GGDeviceService,
    private val permissionService: GGPermissionService,
    private val connectivityObserver: IConnectivityObserver,
    private val deviceService: IDeviceService,
    private val dialogUtility: IDialogUtility,
    private val scope: CoroutineScope,
    private val permissionCheckTimeOut: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val getStateFlow: () -> kotlinx.coroutines.flow.StateFlow<BtWifiScaleSetupState>,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val onUpdateSettingsError: () -> Unit,
    private val onMeasurementFailed: () -> Unit,
    private val cancelPairingTimeout: () -> Unit,
) {

    private val TAG = "BtWifiPermissionsManager"

    fun fallbackToErrorsIfPermissionIsDisabled() {
        scope.launch {
            getStateFlow().map { it.currentStep }.collect {
                delay(permissionCheckTimeOut)
                handlePermissionBasedErrors()
            }
        }
    }
    // NOTE: getStateFlow() provides the observable state; getState() the latest snapshot.

    fun initializePermissionsImmediately() {
        scope.launch {
            try {
                val currentPermissions = permissionService.permissionCallBackFlow.value
                val networkState = try {
                    connectivityObserver.observe().first()
                } catch (e: Exception) {
                    AppLog.d(TAG, "Network state unavailable during initialization (offline mode): ${e.message}")
                    NetworkState(available = false, unAvailable = true)
                }
                updatePermissionsState(currentPermissions, networkState.available)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error initializing permissions immediately", e)
            }
        }
    }

    private fun updatePermissionsState(permissions: GGPermissionStatusMap, isNetworkAvailable: Boolean) {
        val networkStatus = if (isNetworkAvailable) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED
        val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
            wifiSwitchStatus == GGPermissionState.ENABLED
        ) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val updatedPermissions = permissions.toMutableMap().apply {
            put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
        }
        onIntent(BtWifiScaleSetupIntent.SetPermissions(updatedPermissions))
    }

    fun observePermissions() {
        scope.launch {
            val defaultNetworkState = NetworkState(available = false, unAvailable = true)
            val networkStateFlow = merge(
                flowOf(defaultNetworkState),
                connectivityObserver.observe().catch { e ->
                    AppLog.d(TAG, "Network state unavailable (offline mode): ${e.message}")
                    emit(defaultNetworkState)
                },
            )
            combine(
                permissionService.permissionCallBackFlow.onStart { AppLog.d(TAG, "Starting permission observation") },
                networkStateFlow,
            ) { permissions, networkState ->
                updatePermissionsState(permissions, networkState.available)
                val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
                    getState().permissions, setupType = DeviceSetupType.BtWifiR4,
                )
                if (!areRequiredPermissionsEnabled) {
                    handlePermissionBasedErrors()
                } else {
                    val currentStep = getState().currentStep
                    if (currentStep == BtWifiSetupStep.CUSTOMIZE_SETTINGS || currentStep == BtWifiSetupStep.UPDATE_SETTINGS) {
                        scope.launch { syncForSetupBleReconnection() }
                    }
                }
            }.catch { e ->
                AppLog.e(TAG, "Error in permission observation flow", e)
                try {
                    val currentPermissions = permissionService.permissionCallBackFlow.value
                    updatePermissionsState(currentPermissions, false)
                } catch (updateError: Exception) {
                    AppLog.e(TAG, "Error updating permissions in offline mode", updateError)
                }
            }.collect { }
        }
    }

    fun handlePermissionBasedErrors() {
        val areRequiredPermissionsEnabled = AppPermissionsHelper.areRequiredPermissionsEnabled(
            getState().permissions, setupType = DeviceSetupType.BtWifiR4,
        )
        if (!areRequiredPermissionsEnabled) {
            val disabledPermissions = AppPermissionsHelper.getDisabledPermissionsForSetupType(
                permissionMap = getState().permissions, setupType = DeviceSetupType.BtWifiR4,
            )
            val isOnlyNetworkPermissionMissing = disabledPermissions.size == 1 &&
                disabledPermissions.contains(GGPermissionType.WIFI_SWITCH)
            when (getState().currentStep) {
                BtWifiSetupStep.WAKEUP -> {
                    goToPermissionSlide()
                    onIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
                }
                BtWifiSetupStep.GATHERING_NETWORK -> setGatheringNetworkFailed()
                BtWifiSetupStep.UPDATE_SETTINGS -> {
                    if (!isOnlyNetworkPermissionMissing) onUpdateSettingsError()
                }
                BtWifiSetupStep.STEP_ON,
                BtWifiSetupStep.MEASUREMENT -> {
                    // Bluetooth (or nearby-device / location) switched off during "One Last Step" or
                    // while the reading is being collected must surface the "Error Collecting
                    // Measurement" screen — mirroring the on-entry guard in observeStepChanges —
                    // instead of leaving the user stranded on "One Last Step" (MOB-871). A missing
                    // *network* permission alone doesn't block the BLE reading, so it's ignored here.
                    if (!isOnlyNetworkPermissionMissing) onMeasurementFailed()
                }
                BtWifiSetupStep.CONNECTING_BLUETOOTH -> {
                    // Scale is already paired/CONNECTED, so goToPermissionSlide() is a no-op
                    // here (its guard blocks navigation once connected). If Bluetooth itself
                    // is switched off in this window the screen would otherwise freeze, so
                    // surface a retryable error instead. A missing network-only permission
                    // must NOT fail BLE pairing, which doesn't need the network. (MOB-248)
                    if (!isOnlyNetworkPermissionMissing) setConnectingBluetoothFailed()
                }
                BtWifiSetupStep.DUPLICATES_FOUND,
                BtWifiSetupStep.USER_LIMIT_REACHED -> goToPermissionSlide()
                else -> {}
            }
        }
    }

    private fun goToPermissionSlide() {
        if (getDiscoveredScale()?.connectionStatus != BLEStatus.CONNECTED) {
            onIntent(SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        }
    }

    fun requestPermission(permissionType: String, isDuringStepOn: Boolean = false) {
        if (permissionType == GGPermissionType.WIFI_SWITCH) {
            permissionService.requestPermission(permissionType)
            return
        }
        scope.launch {
            try {
                dialogUtility.permissionAlert(
                    permissionType = permissionType,
                    isScaleSetupRequest = isDuringStepOn,
                    onRequest = { permissionService.requestPermission(permissionType) },
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Error requesting permission $permissionType", e.toString())
            }
        }
    }

    fun permissionAccess() {
        val currentPermissions = getState().permissions
        if (currentPermissions[GGPermissionType.BLUETOOTH_SWITCH] != GGPermissionState.ENABLED) {
            onIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.BLUETOOTH_SWITCH))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (currentPermissions[GGPermissionType.NEARBY_DEVICE] != GGPermissionState.ENABLED) {
                onIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.NEARBY_DEVICE))
                return
            }
        } else {
            if (currentPermissions[GGPermissionType.LOCATION_SWITCH] != GGPermissionState.ENABLED) {
                onIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION_SWITCH))
            }
            if (currentPermissions[GGPermissionType.LOCATION] != GGPermissionState.ENABLED) {
                onIntent(BtWifiScaleSetupIntent.RequestPermission(GGPermissionType.LOCATION))
            }
        }
    }

    fun setGatheringNetworkFailed() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.GATHERING_NETWORK, ConnectionState.Failed.Error,
            ),
        )
    }

    private fun setConnectingBluetoothFailed() {
        // Stop the pending pairing timeout so it can't re-fire over the failed state.
        cancelPairingTimeout()
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH, ConnectionState.Failed.Error,
            ),
        )
    }

    private suspend fun syncForSetupBleReconnection() {
        try {
            val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
            AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices for setup BLE reconnection")
            ggDeviceService.syncDevices(pairedDevices)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during setup BLE reconnection sync", e)
        }
    }
}
