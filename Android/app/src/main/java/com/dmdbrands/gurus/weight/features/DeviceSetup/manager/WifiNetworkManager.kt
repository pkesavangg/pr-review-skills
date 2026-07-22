package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.service.WifiDeviceService
import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScalePasswordFormControls
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.WifiScaleSetupState
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.greatergoods.blewrapper.GGPermissionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the WiFi network-status / SSID form / MAC and setup-form validation slice of
 * [WifiScaleSetupViewModel] (MOB-1501). Holds the live [wifiStatus], last auto-filled SSID and
 * the destroy flag driving [monitorNetworkStatus]. Behaviour-preserving verbatim move.
 */
class WifiNetworkManager(
    private val wifiScaleService: WifiDeviceService,
    private val permissionService: GGPermissionService,
    private val scope: CoroutineScope,
    private val getState: () -> WifiScaleSetupState,
    private val onIntent: (WifiScaleSetupIntent) -> Unit,
    private val getScaleToken: () -> String?,
    private val onRefreshScaleToken: () -> Unit,
) {

    private val TAG = "WifiNetworkManager"

    private var wifiStatus: WifiStatus? = null
    private var lastSsid: String? = null
    private var mac: String? = null
    private var isDestroyed = false

    fun stopMonitoring() {
        isDestroyed = true
    }

    /**
     * Gets network information including WiFi status.
     * Equivalent to TypeScript getNetworkInfo()
     */
    fun getNetworkInfo() {
        scope.launch {
            try {
                val status = wifiScaleService.getConnectedWifiInfo()
                wifiStatus = status

                // Update SSID if it changed
                if (status.ssid.isNotEmpty()) {
                    lastSsid = status.ssid
                    onIntent(WifiScaleSetupIntent.SetWifiSsid(status.ssid))
                    val hasLocationPermission = isAllLocationPermissionGranted()
                    if (hasLocationPermission) {
                        onIntent(WifiScaleSetupIntent.SetWifiAutoPopulated(true))
                    }
                    updateFormValuesWithSsid(status.ssid)
                }

                onIntent(WifiScaleSetupIntent.SetWifiStatus(status))
                AppLog.d(TAG, "getNetworkInfo: $status")
            } catch (e: Exception) {
                AppLog.e(TAG, "getNetworkInfo - Error getting network info", e)
            }
        }
    }

    /**
     * Updates the network status.
     * Equivalent to TypeScript updateNetworkStatus()
     *
     * Permission-state transitions are handled explicitly:
     * - Permission granted + live SSID available → auto-populate mode (isWifiAutoPopulated = true).
     * - Permission revoked (or denied) → clear lastSsid, clear form, revert to manual-entry mode
     *   (isWifiAutoPopulated = false) so the field is editable again.
     * - Permission granted but no SSID yet → keep current state, do not force manual mode.
     */
    fun updateNetworkStatus() {
        scope.launch {
            try {
                val hasLocationPermission = isAllLocationPermissionGranted()
                val status = wifiScaleService.getConnectedWifiInfo(hasLocationPermission)
                wifiStatus = status
                val currentState = getState()
                val hasAllRequiredPermissions = AppPermissionsHelper
                    .areRequiredPermissionsEnabled(currentState.permissions, setupType = DeviceSetupType.Wifi)

                if (!hasAllRequiredPermissions) {
                    onIntent(WifiScaleSetupIntent.SetWifiStatus(status))
                    return@launch
                }

                // Permissions are granted from here on.
                if (status.ssid.isNotEmpty()) {
                    val previousAutoFilledSsid = lastSsid
                    lastSsid = status.ssid
                    if (currentState.permissionsSkipped) {
                        // User skipped the permission screen but permissions are active.
                        // Auto-populate the field so they see the network name pre-filled,
                        // but only if they haven't already started typing their own value.
                        // Never set isWifiAutoPopulated=true here — the field must stay editable.
                        val ssidField = currentState.wifiPasswordForm.ssid
                        // dirty/touched alone is not reliable because SetWifiPasswordFormSsid
                        // marks the control dirty/touched programmatically during auto-fill.
                        // Compare the field value against the *previous* auto-filled SSID:
                        // if the user hasn't touched it, the field still holds the old auto-filled
                        // value (or is empty), so we can safely overwrite with the new SSID.
                        val userIsEditing = ssidField.value.isNotEmpty() &&
                            ssidField.value != previousAutoFilledSsid &&
                            ssidField.value != status.ssid
                        if (!userIsEditing) {
                            AppLog.d(TAG, "updateNetworkStatus - permissionsSkipped + untouched field; pre-filling SSID as editable")
                            updateFormValuesWithSsid(status.ssid)
                        } else {
                            AppLog.d(TAG, "updateNetworkStatus - permissionsSkipped + user is editing; not overriding input")
                        }
                    } else {
                        onIntent(WifiScaleSetupIntent.SetWifiSsid(status.ssid))
                        onIntent(WifiScaleSetupIntent.SetWifiAutoPopulated(true))
                        updateFormValuesWithSsid(status.ssid)
                    }
                } else {
                    // Permissions granted but no SSID yet — do not flip back to manual mode,
                    // just reflect the empty status so the user is not confused.
                    onIntent(WifiScaleSetupIntent.SetWifiSsid(""))
                    if (currentState.isWifiAutoPopulated) {
                        // Lost the network momentarily; keep lastSsid but switch to manual so the field
                        // is editable in case the network change is intentional.
                        AppLog.d(TAG, "updateNetworkStatus - Permission granted but SSID lost; switching to manual entry")
                        onIntent(WifiScaleSetupIntent.SetWifiAutoPopulated(false))
                        clearWifiPasswordFormSsid()
                    }
                }
                onIntent(WifiScaleSetupIntent.SetWifiStatus(status))
            } catch (e: Exception) {
                AppLog.e(TAG, "updateNetworkStatus - Error updating network status", e)
            }
        }
    }

    /**
     * Monitors network status continuously, updating every 1.5 seconds.
     * Equivalent to TypeScript monitorNetworkStatus()
     * Runs until the ViewModel is destroyed.
     */
    fun monitorNetworkStatus() {
        scope.launch {
            while (!isDestroyed) {
                try {
                    updateNetworkStatus()
                    if (getScaleToken().isNullOrEmpty()) {
                        onRefreshScaleToken()
                    }
                } catch (err: Exception) {
                    AppLog.e(TAG, "monitorNetworkStatus - Error monitoring network status", err)
                } finally {
                    // Wait 1.5 seconds before next iteration
                    delay(1500)
                }
            }
            AppLog.d(TAG, "monitorNetworkStatus - Stopped monitoring (ViewModel destroyed)")
        }
    }

    /**
     * Gets the MAC address of the connected WiFi network.
     * Equivalent to TypeScript getMacAddress()
     */
    suspend fun getMacAddress(): String? {
        return try {
            val ssid = wifiScaleService.getConnectedSsid()
            val scanResults = wifiScaleService.getScanResults()

            for (network in scanResults) {
                if (network.SSID == ssid) {
                    var mac = network.BSSID
                    val hexes = mac.split(":")
                    val formattedHexes = hexes.map { hex ->
                        if (hex.length == 1) "0$hex" else hex
                    }
                    mac = formattedHexes.joinToString(":")
                    this.mac = mac
                    AppLog.d(TAG, "getMacAddress - MAC address found: $mac")
                    return mac
                }
            }

            AppLog.w(TAG, "getMacAddress - No matching network found")
            null
        } catch (e: Exception) {
            AppLog.e(TAG, "getMacAddress - Error getting MAC address", e)
            null
        }
    }

    /**
     * Updates form values when SSID changes.
     * Helper method to keep form values in sync with network status.
     */
    private fun updateFormValuesWithSsid(ssid: String) {
        val currentState = getState()
        val currentStep = currentState.currentStep
        val currentStepIndex = currentState.currentStepIndex
        val arePermissionsCurrentlyEnabled = AppPermissionsHelper
            .areRequiredPermissionsEnabled(currentState.permissions, setupType = DeviceSetupType.Wifi)
        val shouldAutoPopulate = !currentState.permissionsSkipped || currentState.isGetMACSetup || arePermissionsCurrentlyEnabled
        // Skip flow with permissions on: respect user's clear - don't refill if they cleared the field.
        // We cannot rely on dirty/touched because SetWifiPasswordFormSsid marks the control
        // dirty/touched programmatically during auto-fill. Instead, treat the field as
        // "user cleared" when it is empty and lastSsid is non-null (meaning we previously
        // auto-filled a value that the user then removed).
        val ssidControl = currentState.wifiPasswordForm.ssid
        val userClearedSsid = ssidControl.value.isEmpty() && lastSsid != null
        if (currentState.permissionsSkipped && arePermissionsCurrentlyEnabled &&
            userClearedSsid &&
            currentState.wifiPasswordForm.ssid.value.isEmpty() && ssid.isNotEmpty() && currentStepIndex < 3
        ) {
            AppLog.d(TAG, "Skipping auto-population of WiFi form - permissions were skipped and user cleared the field")
            return
        }
        if (shouldAutoPopulate) {
            val isEarlyStep = currentStepIndex < 3
            if (isEarlyStep) {
                if (currentStep == WifiScaleSetupStep.WIFI_PASSWORD || currentStep == WifiScaleSetupStep.SCALE_INFO) {
                    onIntent(WifiScaleSetupIntent.SetWifiPasswordFormSsid(ssid))
                }
            } else if (currentStep == WifiScaleSetupStep.SWITCH_WIFI) {
                if (ssid.contains("gg_SmartScaleSetup", ignoreCase = true)) {
                    onIntent(WifiScaleSetupIntent.SetScaleNetworkFormSsid(ssid))
                }
            } else {
                if (currentStep == WifiScaleSetupStep.WIFI_PASSWORD || currentStep == WifiScaleSetupStep.SCALE_INFO) {
                    onIntent(WifiScaleSetupIntent.SetWifiPasswordFormSsid(ssid))
                }
            }
        } else {
            AppLog.d(TAG, "Skipping auto-population of WiFi form - permissions were skipped, not in MAC setup mode, and permissions not currently enabled")
        }
    }

    /**
     * Clears the WiFi password form when permissions are skipped.
     * Resets form controls to initial state to avoid validation errors.
     */
    fun clearWifiPasswordForm() {
        // Create fresh form controls with empty initial values
        val emptySsid = FormControl.create(
            initialValue = "",
            validators = listOf(FormValidations.required()),
        )
        val emptyPassword = FormControl.create(
            initialValue = "",
            validators = listOf(
                FormValidations.required(),
            ),
        )
        val noPasswordControl = FormControl.create(
            initialValue = false,
            validators = emptyList(),
        )

        // Update the form with fresh controls using the correct type
        onIntent(
            WifiScaleSetupIntent.SetWifiPasswordForm(
                WifiScalePasswordFormControls(
                    ssid = emptySsid,
                    password = emptyPassword,
                    noPasswordNetwork = noPasswordControl,
                ),
            ),
        )
        AppLog.d(TAG, "Cleared WiFi password form - reset all form controls to initial state")
    }

    /**
     * Clears only the SSID field of the WiFi password form, leaving password and toggle untouched.
     * Used when reverting from auto-populated mode to manual-entry mode after permission revocation.
     */
    fun clearWifiPasswordFormSsid() {
        val emptySsid = FormControl.create(
            initialValue = "",
            validators = listOf(FormValidations.required()),
        )
        val currentForm = getState().wifiPasswordForm
        onIntent(
            WifiScaleSetupIntent.SetWifiPasswordForm(
                WifiScalePasswordFormControls(
                    ssid = emptySsid,
                    password = currentForm.password,
                    noPasswordNetwork = currentForm.noPasswordNetwork,
                ),
            ),
        )
        AppLog.d(TAG, "clearWifiPasswordFormSsid - reset SSID form control to empty for manual entry")
    }

    /**
     * Checks if all location permissions are granted.
     */
    fun isAllLocationPermissionGranted(): Boolean {
        return try {
            val permissions = permissionService.permissionCallBackFlow.value
            val locationSwitchEnabled = permissions[GGPermissionType.LOCATION_SWITCH] == GGPermissionState.ENABLED
            val locationEnabled = permissions[GGPermissionType.LOCATION] == GGPermissionState.ENABLED

            locationSwitchEnabled && locationEnabled
        } catch (e: Exception) {
            AppLog.e(TAG, "Error checking location permissions", e.toString())
            false
        }
    }

    /**
     * Validates the WiFi password form to determine if user can proceed to next step.
     */
    fun isWifiPasswordFormValid(): Boolean {
        val currentState = getState()
        val form = currentState.wifiPasswordForm

        // Check if SSID is selected/entered
        val isSsidValid = form.ssid.value.isNotEmpty()

        // Check if password is valid (either entered or "no password" is selected)
        val isPasswordValid = if (form.noPasswordNetwork.value) {
            // If "no password" is selected, password field is not required
            true
        } else {
            // If "no password" is not selected, password must be entered and valid
            form.password.value.isNotEmpty() && form.password.isValueValid()
        }

        // Check if SSID form control is valid
        val isSsidFormValid = form.ssid.isValueValid()

        AppLog.d(
            TAG,
            "WiFi password form validation - SSID: $isSsidValid, Password: $isPasswordValid, SSID Form: $isSsidFormValid",
        )

        return isSsidValid && isPasswordValid && isSsidFormValid
    }

    /**
     * Validates if a user has been selected for the scale setup.
     */
    fun isUserSelected(): Boolean {
        val currentState = getState()
        val isSelected = currentState.selectedUser != null
        AppLog.d(TAG, "User selection validation - selected user: ${currentState.selectedUser}, is valid: $isSelected")
        return isSelected
    }

    /**
     * Validates if a WiFi mode has been selected for the scale setup.
     */
    fun isWifiModeSelected(): Boolean {
        val currentState = getState()
        val isSelected = !currentState.selectedWifiMode.isNullOrEmpty()
        AppLog.d(
            TAG,
            "WiFi mode selection validation - selected mode: ${currentState.selectedWifiMode}, is valid: $isSelected",
        )
        return isSelected
    }

    /**
     * Checks if the user is connected to the scale's WiFi network in AP mode.
     */
    fun isConnectedToScaleWifi(): Boolean {
        val currentState = getState()
        val isConnected = currentState.scaleNetworkForm.ssid.value.isNotEmpty()
        AppLog.d(TAG, "Scale WiFi connection check - is connected: $isConnected")
        return isConnected
    }
}
