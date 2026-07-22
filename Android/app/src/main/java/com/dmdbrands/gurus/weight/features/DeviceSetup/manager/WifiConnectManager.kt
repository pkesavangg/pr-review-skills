package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.service.WifiDeviceService
import com.dmdbrands.gurus.weight.core.service.WifiSetupInfo
import com.dmdbrands.gurus.weight.core.service.WifiSetupType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.DeviceSetupConstants
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DEVICES
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the scale-token / connect / save / exit-navigation slice of [WifiScaleSetupViewModel]
 * (MOB-1501). Holds the scale token and the connected-network identifiers used across the
 * smart-connect and AP-mode flows. Behaviour-preserving verbatim move.
 */
class WifiConnectManager(
    private val wifiScaleService: WifiDeviceService,
    private val deviceService: IDeviceService,
    private val scope: CoroutineScope,
    private val scaleInfo: DeviceModelInfo?,
    private val getState: () -> WifiScaleSetupState,
    private val onIntent: (WifiScaleSetupIntent) -> Unit,
    private val enqueueDialog: (DialogModel) -> Unit,
    private val showLoader: (String) -> Unit,
    private val dismissLoader: () -> Unit,
    private val showToast: (Toast) -> Unit,
    private val navigateBackNav: suspend () -> Unit,
    private val switchActiveProductAfterSetup: suspend () -> Unit,
    private val onRequestNotificationPermission: () -> Unit,
) {

    private val TAG = "WifiConnectManager"

    var scaleToken: String? = null
        private set
    private var connectedSsid: String? = null
    private var connectedBssid: String? = null

    /**
     * Gets the scale token from the API.
     * Equivalent to TypeScript getScaleToken()
     */
    fun getScaleToken() {
        scope.launch {
            try {
                val token = wifiScaleService.getScaleToken()
                scaleToken = token
                AppLog.d(TAG, "getScaleToken - token retrieved successfully")
            } catch (e: Exception) {
                AppLog.e(TAG, "getScaleToken - Error getting scale token", e)
            }
        }
    }

    /**
     * Gets the setup information based on the setup type.
     * Equivalent to TypeScript getSetupInfo()
     */
    private fun getSetupInfo(setupType: WifiSetupType): WifiSetupInfo {
        val currentState = getState()
        val hasPassword = !currentState.wifiPasswordForm.noPasswordNetwork.value
        val currentUserNumber = currentState.selectedUser

        return when (setupType) {
            WifiSetupType.JOIN -> {
                WifiSetupInfo(
                    userNumber = currentUserNumber,
                    token = scaleToken,
                )
            }

            WifiSetupType.CHANGE -> {
                WifiSetupInfo(
                    ssid = currentState.wifiPasswordForm.ssid.value,
                    password = if (hasPassword) currentState.wifiPasswordForm.password.value else "",
                    userNumber = currentUserNumber,
                    token = scaleToken,
                )
            }

            else -> {
                WifiSetupInfo(
                    ssid = currentState.wifiPasswordForm.ssid.value,
                    bssid = currentState.wifiStatus?.bssid ?: "",
                    password = if (hasPassword) currentState.wifiPasswordForm.password.value else "",
                    userNumber = currentUserNumber,
                    token = scaleToken,
                )
            }
        }
    }

    /**
     * Starts smart connect process.
     * Equivalent to TypeScript startSmartConnect()
     */
    fun startSmartConnect() {
        scope.launch {
            try {
                // If permission is skipped stop the scale to pair through the normal setup
                if (getState().permissionsSkipped) {
                    return@launch
                }

                // Determine the correct setup type based on scaleInfo
                val setupType = when (scaleInfo?.setupType) {
                    DeviceSetupType.EspTouchWifi -> WifiSetupType.ESP_TOUCH_WIFI
                    DeviceSetupType.Wifi -> WifiSetupType.FIRST
                    else -> WifiSetupType.FIRST // Default fallback
                }

                val info = getSetupInfo(setupType)
                wifiScaleService.stop()
                connectedSsid = info.ssid
                connectedBssid = info.bssid

                wifiScaleService.connect(
                    setupInfo = info,
                    setupType = setupType,
                    onSuccess = {
                        AppLog.d(TAG, "Connection successful")
                        onIntent(WifiScaleSetupIntent.SetConnectionSuccess(true))
                    },
                    onError = { error ->
                        AppLog.e(TAG, "Connection failed: $error")
                    },
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "startSmartConnect - Error starting connect", e)
            }
        }
    }

    /**
     * Starts AP mode process.
     * Equivalent to TypeScript startApMode()
     */
    fun startApMode(count: Int = 0) {
        scope.launch {
            try {
                val info = getSetupInfo(WifiSetupType.CHANGE)
                wifiScaleService.stop()
                info.ssid = connectedSsid ?: info.ssid
                info.bssid = connectedBssid ?: info.bssid
                wifiScaleService.connect(
                    setupInfo = info,
                    setupType = WifiSetupType.CHANGE,
                    onSuccess = {
                        AppLog.d(TAG, "AP Mode connection successful")
                        onIntent(WifiScaleSetupIntent.SetConnectionSuccess(true))
                    },
                    onError = { error ->
                        AppLog.e(TAG, "AP Mode connection failed: $error")
                    },
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "startApMode - Error starting AP mode", e)

                // Retry logic similar to TypeScript
                if (count < 5) {
                    delay(5000) // 5 seconds delay
                    startApMode(count + 1)
                } else {
                    AppLog.e(TAG, "AP Mode failed after 5 attempts")
                }
            }
        }
    }

    /**
     * Checks if scale token is available.
     * Shows a toast message if the token is not available.
     * Equivalent to TypeScript checkScaleToken()
     */
    fun checkScaleToken(): Boolean {
        if (scaleToken.isNullOrEmpty()) {
            AppLog.w(TAG, "checkScaleToken - No scale token available")
            showToast(
                Toast.Simple(
                    title = DeviceSetupStrings.PermissionAlerts.InternetRequired.Title,
                    message = DeviceSetupStrings.PermissionAlerts.InternetRequired.Message,
                    action = null,
                ),
            )
            return false
        }
        return true
    }

    /**
     * Saves the scale and waits for it to appear in pairedScales before completing.
     * Call from a coroutine; runs sequentially so navigation after this sees the updated list.
     */
    private suspend fun checkAndSaveScale() {
        val currentSku = getState().sku
        if (currentSku.isBlank()) {
            AppLog.e(TAG, "SKU is blank, cannot save scale")
            return
        }
        val scaleModelInfo = DEVICES.find { it.sku == currentSku }
        val wifiDevice = Device(
            device = GGDeviceDetail(
                deviceName = scaleModelInfo?.productName ?: "",
                macAddress = getState().macAddress,
                identifier = "",
            ),
            sku = currentSku,
            deviceType = DeviceSetupType.Wifi.value,
            nickname = scaleModelInfo?.productName ?: DeviceSetupStrings.UnknownScale,
            token = scaleToken,
            userNumber = getState().selectedUser,
        )
        val savedDevice = deviceService.saveScale(wifiDevice)
        waitForScaleInPairedList(savedDevice, currentSku)
        // Auto-switch the dashboard header to the newly added scale (MOB-422).
        switchActiveProductAfterSetup()
    }

    /**
     * Waits for the saved scale to appear in [deviceService.pairedScales] (with timeout).
     * Ensures list is updated before we dismiss loader / navigate so the scale list is not empty.
     */
    private suspend fun waitForScaleInPairedList(savedDevice: Device?, currentSku: String) {
        val listWithScale = withTimeoutOrNull(DeviceSetupConstants.WAIT_FOR_SCALE_IN_LIST_MS) {
            deviceService.pairedScales.first { list ->
                list.any { device ->
                    if (savedDevice != null) device.id == savedDevice.id
                    else device.sku == currentSku && device.deviceType == DeviceSetupType.Wifi.value
                }
            }
        }
        if (listWithScale == null) {
            AppLog.w(TAG, "Timeout waiting for WiFi scale in paired list; continuing anyway")
        }
    }

    /**
     * Saves the scale configuration. Shows loader, awaits save and list update, then dismisses loader.
     * Equivalent to TypeScript saveScale(). Run before navigating so scale list is populated.
     */
    fun saveScale() {
        scope.launch {
            showLoader(DeviceSetupStrings.SaveScaleLoader)
            try {
                checkAndSaveScale()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error saving scale", e)
            } finally {
                dismissLoader()
            }
        }
    }

    /**
     * Starts exit setup process.
     * Equivalent to TypeScript startExitSetup()
     */
    fun startExitSetup(canExit: Boolean = false) {
        val currentState = getState()
        try {
            wifiScaleService.stop()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error stopping WiFi service", e)
        }
        if (currentState.saved || canExit) {
            // Only wait for scale to appear in paired list when the scale was actually saved.
            // Exit paths like troubleshooting / error-code / MAC-only never save a scale,
            // so waiting would block on the full timeout and delay the FINISH response.
            navigateBack(waitForScaleInList = currentState.saved)
            return
        }

        enqueueDialog(
            DialogModel.Confirm(
                title = DeviceSetupStrings.ExitSetupAlert.Title,
                message = DeviceSetupStrings.ExitSetupAlert.Message(currentState.isConnected),
                confirmText = DeviceSetupStrings.ExitSetupAlert.Exit,
                cancelText = DeviceSetupStrings.ExitSetupAlert.Return,
                onConfirm = {
                    navigateBack()
                },
            ),
        )
    }

    /**
     * Navigates back from the setup screen.
     * @param waitForScaleInList If true (e.g. after setup finished), waits for saved scale to appear in pairedScales before navigating so the list is not empty.
     */
    fun navigateBack(waitForScaleInList: Boolean = false) {
        scope.launch {
            try {
                if (waitForScaleInList) {
                    waitForScaleInPairedList(savedDevice = null, currentSku = getState().sku)
                }
                navigateBackNav()
                AppLog.d(TAG, "Successfully navigated back from scale setup")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to navigate back from scale setup", e)
            }
        }
    }

    fun notificationPermission() {
        val canRequestNotifPermission =
            AppPermissionsHelper.canRequestNotificationPermission(getState().permissions)
        if (canRequestNotifPermission) {
            onRequestNotificationPermission()
        }
    }
}
