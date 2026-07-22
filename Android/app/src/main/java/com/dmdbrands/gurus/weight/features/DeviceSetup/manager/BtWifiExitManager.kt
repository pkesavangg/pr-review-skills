package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Owns the exit / teardown slice of [BtWifiScaleSetupViewModel] (MOB-1501).
 * Holds the [isExiting] flag (read by onNext to freeze navigation) and the
 * [fetchUserListJob] prefetch. Behaviour-preserving verbatim move.
 */
class BtWifiExitManager(
    private val ggDeviceService: GGDeviceService,
    private val deviceService: IDeviceService,
    private val scope: CoroutineScope,
    private val initialStep: BtWifiSetupStep,
    private val cleanupTimeoutMs: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val getIsScaleSaved: () -> Boolean,
    private val clearAllTimeouts: () -> Unit,
    private val enqueueDialog: (DialogModel) -> Unit,
    private val showLoader: (String) -> Unit,
    private val dismissLoader: () -> Unit,
    private val navigateBack: suspend () -> Unit,
    private val switchActiveProductAfterSetup: suspend () -> Unit,
) {

    private val TAG = "BtWifiExitManager"

    var isExiting: Boolean = false
        private set

    private var fetchUserListJob: Job? = null

    fun onExitSetup(isSetupFinished: Boolean) {
        deviceService.setSetupInProgress(false)
        if (isSetupFinished) {
            onExit()
        } else {
            isExiting = true
            fetchUserListForExit()
            enqueueDialog(
                DialogModel.Confirm(
                    title = DeviceSetupStrings.ExitSetupAlert.Title,
                    message = DeviceSetupStrings.ExitSetupAlert.Message(getDiscoveredScale()?.connectionStatus == BLEStatus.CONNECTED),
                    confirmText = DeviceSetupStrings.ExitSetupAlert.Exit,
                    cancelText = DeviceSetupStrings.ExitSetupAlert.GoBack,
                    onConfirm = { onExit() },
                    onCancel = { cancelExitFetch() },
                    onDismiss = { cancelExitFetch() },
                ),
            )
        }
    }

    private fun cancelExitFetch() {
        fetchUserListJob?.cancel()
        fetchUserListJob = null
        isExiting = false
    }

    private fun fetchUserListForExit() {
        val scale = getDiscoveredScale() ?: return
        fetchUserListJob = scope.launch {
            try {
                ggDeviceService.getUsers(scale.toGGBTDevice()) { response ->
                    onIntent(BtWifiScaleSetupIntent.SetUserList(response.user))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error fetching user list for exit", e)
            }
        }
    }

    private fun onExit() {
        isExiting = true
        clearAllTimeouts()
        scope.launch {
            try {
                ggDeviceService.resumeScan(false)
                getDiscoveredScale()?.let { scale ->
                    ggDeviceService.cancelWifi(scale.toGGBTDevice()) {}
                    if (!getIsScaleSaved() && initialStep != BtWifiSetupStep.GATHERING_NETWORK) {
                        if (getState().currentStep.ordinal >= BtWifiSetupStep.CONNECTING_BLUETOOTH.ordinal) {
                            showLoader("Exiting..")
                            val scaleToken = getState().userList
                                .find { user -> user.name == scale.preferences?.displayName }
                                ?.token
                                ?: scale.token
                            val deleteResult = withTimeoutOrNull(cleanupTimeoutMs) {
                                suspendCancellableCoroutine { continuation ->
                                    ggDeviceService.deleteAccount(scale.toGGBTDevice().copy(token = scaleToken)) { result ->
                                        AppLog.d(TAG, "deleteAccount completed with result: $result")
                                        if (continuation.isActive) continuation.resume(result)
                                    }
                                }
                            }
                            if (deleteResult == null) {
                                AppLog.w(TAG, "deleteAccount timed out")
                            }
                            ggDeviceService.disconnectDevice(scale.toGGBTDevice())
                        }
                    }
                }
                loadPluginData()
                if (getIsScaleSaved()) {
                    // Auto-switch the dashboard header to the newly added scale (MOB-422).
                    switchActiveProductAfterSetup()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during Bluetooth cleanup", e)
            } finally {
                dismissLoader()
            }
            try {
                navigateBack()
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate back from scale setup", e)
            }
        }
    }

    private suspend fun loadPluginData() {
        try {
            val device = getDiscoveredScale()
            var connectedDeviceBroadcastID: String? = null
            ggDeviceService.localSkipDevices.value.forEach {
                if (device?.device?.broadcastId == it && device.connectionStatus == BLEStatus.CONNECTED) {
                    connectedDeviceBroadcastID = it
                } else {
                    ggDeviceService.skipDevice(it, considerForSession = true)
                }
            }
            if (connectedDeviceBroadcastID != null) {
                ggDeviceService.removeSkipDeviceBroadcastID(connectedDeviceBroadcastID)
            }
            val pairedDevices = deviceService.pairedScales.first().map { it.toGGBTDevice() }
            AppLog.d(TAG, "Syncing ${pairedDevices.size} paired devices")
            ggDeviceService.syncDevices(pairedDevices)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during Bluetooth cleanup", e)
        }
    }
}
