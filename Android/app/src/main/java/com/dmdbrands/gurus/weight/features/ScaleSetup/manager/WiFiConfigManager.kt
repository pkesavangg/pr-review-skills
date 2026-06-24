package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGBTWifiConfig
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.ggbluetoothsdk.external.enums.GGWifiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WiFiConfigManager(
    private val ggDeviceService: GGDeviceService,
    private val scope: CoroutineScope,
    private val initialStep: BtWifiSetupStep,
    private val operationTimeout: Long,
    private val connectionDelay: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val onNext: () -> Unit,
    private val onExitSetup: (Boolean) -> Unit,
    private val requestNotificationPermission: () -> Unit,
) : IWiFiConfigManager {

    private val TAG = "WiFiConfigManager"
    private var wifiConnectionTimeoutJob: Job? = null
    var isWifiConfigured: Boolean = false
    var wifiMac: String? = null
    private var isAlreadyExited = false

    override fun gatherNetworks() {
        AppLog.d(TAG, "Starting network gathering process")
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.GATHERING_NETWORK,
                ConnectionState.Loading,
            ),
        )
        try {
            val scale = getDiscoveredScale() ?: run {
                AppLog.e(TAG, "discoveredScale is null when gathering networks")
                setGatheringNetworkFailed()
                return
            }
            ggDeviceService.getWifiList(scale.toGGBTDevice()) {
                scope.launch {
                    AppLog.d(TAG, "Network gathering successful")

                    val canRequestNotifPermission =
                        AppPermissionsHelper.canRequestNotificationPermission(getState().permissions)
                    if (canRequestNotifPermission) {
                        requestNotificationPermission()
                    }

                    val connectedSSID = suspendCancellableCoroutine<String> { cont ->
                        ggDeviceService.getConnectedWifiSSID(scale.toGGBTDevice()) { wifiMac ->
                            val ssid = wifiMac.cleanCorruptedChars()
                            this@WiFiConfigManager.isWifiConfigured = !ssid.isNullOrBlank()
                            cont.resume(ssid)
                        }
                    }
                    suspendCancellableCoroutine { cont ->
                        ggDeviceService.getConnectedWifiMacAddress(scale.toGGBTDevice()) { mac ->
                            this@WiFiConfigManager.wifiMac = mac
                            cont.resume(mac)
                        }
                    }
                    if (getState().currentStepConnectionState == ConnectionState.Loading) {
                        onIntent(BtWifiScaleSetupIntent.SetWifiList(it.wifi))
                        onIntent(BtWifiScaleSetupIntent.SetConnectedSSID(connectedSSID))
                        onNext()
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during network gathering", e)
            setGatheringNetworkFailed()
        }
    }

    override fun connectToWifi() {
        AppLog.d(TAG, "Starting wifi connection process with timeout")
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_WIFI,
                ConnectionState.Loading,
            ),
        )
        scope.launch {
            try {
                wifiConnectionTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.CONNECTING_WIFI) {
                        AppLog.w(TAG, "WiFi connection timeout reached")
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.CONNECTING_WIFI,
                                ConnectionState.Failed.Error,
                            ),
                        )
                    }
                }
                setupWifi()
            } catch (e: Exception) {
                wifiConnectionTimeoutJob?.cancel()
                wifiConnectionTimeoutJob = null
                AppLog.e(TAG, "Error during wifi connection", e)
                onIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                        BtWifiSetupStep.CONNECTING_WIFI,
                        ConnectionState.Failed.Error,
                    ),
                )
            }
        }
    }

    private fun setupWifi() {
        try {
            val scale = getDiscoveredScale() ?: run {
                AppLog.e(TAG, "discoveredScale is null when setting up WiFi")
                return
            }
            val ssid = getState().wifiPasswordForm.ssid.value
            val password = getState().wifiPasswordForm.password.value
            this.isWifiConfigured = false
            this.wifiMac = null
            ggDeviceService.setupWifi(
                scale.toGGBTDevice(),
                GGBTWifiConfig(ssid, password),
            ) {
                wifiConnectionTimeoutJob?.cancel()
                wifiConnectionTimeoutJob = null

                scope.launch {
                    if (it.wifiState == GGWifiState.GG_WIFI_STATE_CONNECTED.name) {
                        AppLog.d(TAG, "Wifi connection successful")
                        this@WiFiConfigManager.isWifiConfigured = ssid.isNotBlank()
                        ggDeviceService.getConnectedWifiMacAddress(scale.toGGBTDevice()) { mac ->
                            this@WiFiConfigManager.wifiMac = mac
                        }
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.CONNECTING_WIFI,
                                ConnectionState.Success,
                            ),
                        )
                        delay(connectionDelay)
                        clearWifiPasswordForm()
                        if (initialStep == BtWifiSetupStep.GATHERING_NETWORK) {
                            if (!isAlreadyExited) {
                                isAlreadyExited = true
                                onExitSetup(true)
                            }
                            return@launch
                        }
                        onNext()
                    } else {
                        AppLog.w(TAG, "Wifi connection failed")
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.CONNECTING_WIFI,
                                ConnectionState.Failed.Error,
                            ),
                        )
                        onIntent(BtWifiScaleSetupIntent.SetErrorCode(it.errorCode))
                    }
                }
            }
        } catch (e: Exception) {
            wifiConnectionTimeoutJob?.cancel()
            wifiConnectionTimeoutJob = null
            this.isWifiConfigured = false
            this.wifiMac = null
            AppLog.e(TAG, "Error during wifi setup", e)
            onIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(
                    BtWifiSetupStep.CONNECTING_WIFI,
                    ConnectionState.Failed.Error,
                ),
            )
        }
    }

    override fun handlePasswordNetworkStatus() {
        val currentState = getState()
        val isNoPasswordNetwork = currentState.wifiPasswordForm.noPasswordNetwork.value
        AppLog.d(TAG, "Handling password network status, isNoPasswordNetwork: $isNoPasswordNetwork")
        if (isNoPasswordNetwork) {
            currentState.wifiPasswordForm.password.removeValidator("required")
            currentState.wifiPasswordForm.password.reset("")
        } else {
            currentState.wifiPasswordForm.password.addValidator(
                FormValidations.required(),
            )
            currentState.wifiPasswordForm.password.reset("")
        }
        updateWifiPasswordFormValidation()
    }

    private fun updateWifiPasswordFormValidation() {
        val currentState = getState()
        if (currentState.currentStep == BtWifiSetupStep.WIFI_PASSWORD) {
            if (currentState.wifiPasswordForm.noPasswordNetwork.value) {
                currentState.wifiPasswordForm.ssid.isValueValid()
            } else {
                currentState.wifiPasswordForm.ssid.isValueValid() && currentState.wifiPasswordForm.password.isValueValid()
            }
        }
    }

    override fun clearWifiPasswordForm() {
        AppLog.d(TAG, "Clearing WiFi password form after successful connection")
        val currentState = getState()
        currentState.wifiPasswordForm.password.reset()
        currentState.wifiPasswordForm.noPasswordNetwork.onValueChange(false)
    }

    override fun cancelTimeout() {
        wifiConnectionTimeoutJob?.cancel()
        wifiConnectionTimeoutJob = null
    }

    private fun setGatheringNetworkFailed() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.GATHERING_NETWORK,
                ConnectionState.Failed.Error,
            ),
        )
    }
}
