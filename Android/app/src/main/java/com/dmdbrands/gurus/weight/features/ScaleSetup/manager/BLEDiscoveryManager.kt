package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.Helper.ScaleMetricsHelper
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.getSKU
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BLEDiscoveryManager(
    private val ggDeviceService: GGDeviceService,
    private val deviceService: IDeviceService,
    private val accountService: IAccountService,
    private val bluetoothPreferencesService: BluetoothPreferencesService,
    private val sku: String,
    private val scope: CoroutineScope,
    private val operationTimeout: Long,
    private val connectionDelay: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val setDiscoveredScale: (Device?) -> Unit,
    private val setIsScaleConnected: (Boolean) -> Unit,
    private val onNext: () -> Unit,
    private val onExitSetup: (Boolean) -> Unit,
    private val startObservingDevices: () -> Unit,
    private val stopObservingDevices: () -> Unit,
    private val showDialog: (DialogModel) -> Unit,
    private val dismissCurrentDialog: () -> Unit,
    private val setModePreference: (Device) -> Unit,
) : IBLEDiscoveryManager {

    private val TAG = "BLEDiscoveryManager"
    private var pairingTimeoutJob: Job? = null

    override fun startPairing() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.WAKEUP,
                ConnectionState.Loading,
            ),
        )
        scope.launch {
            try {
                ggDeviceService.scanForPairing()
                startObservingDevices()
                pairingTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.WAKEUP) {
                        AppLog.w(TAG, "Pairing timeout reached after 5 minutes")
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.WAKEUP,
                                ConnectionState.Failed.Error,
                            ),
                        )
                        stopObservingDevices()
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during pairing process", e)
                onIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                        BtWifiSetupStep.WAKEUP,
                        ConnectionState.Failed.Error,
                    ),
                )
            }
        }
    }

    override fun cancelPairing() {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
    }

    override fun handleScanResponse(response: GGScanResponse.DeviceDetail) {
        val ggDeviceDetail = response.data
        when (response.type) {
            GGScanResponseType.NEW_DEVICE -> {
                if (ggDeviceDetail.protocolType == GGDeviceProtocolType.GG_DEVICE_PROTOCOL_R4.value) {
                    scope.launch {
                        val discoveredSku = ggDeviceDetail.getSKU()
                        if (!DeviceHelper.isBpmDevice(discoveredSku) &&
                            deviceService.pairedScales.first()
                                .any { it.device?.macAddress == ggDeviceDetail.macAddress } &&
                            !ggDeviceService.localSkipDevices.value.contains(ggDeviceDetail.broadcastIdString)
                        ) {
                            pairingTimeoutJob?.cancel()
                            pairingTimeoutJob = null
                            stopObservingDevices()
                            showDialog(
                                DialogModel.Alert(
                                    title = BtWifiScaleSetupStrings.KnownScaleDiscoveredAlert.Title,
                                    message = BtWifiScaleSetupStrings.KnownScaleDiscoveredAlert.Subtitle,
                                    onDismiss = {
                                        onExitSetup(true)
                                        dismissCurrentDialog()
                                    },
                                ),
                            )
                        } else {
                            pairingTimeoutJob?.cancel()
                            pairingTimeoutJob = null
                            val deviceSku = ggDeviceDetail.getSKU()
                            val shouldShow = if (deviceSku == SKU_0412) {
                                bluetoothPreferencesService.shouldShowDevice(ggDeviceDetail.macAddress)
                            } else {
                                true
                            }
                            if (!shouldShow) return@launch
                            stopObservingDevices()
                            customizeDevice(ggDeviceDetail)
                            AppLog.d(TAG, "Device discovered, waiting for scale to fully wake up")
                            delay(connectionDelay)
                            AppLog.d(TAG, "Wake up successful, proceeding to next step")
                            onIntent(
                                BtWifiScaleSetupIntent.SetStepConnectionState(
                                    BtWifiSetupStep.WAKEUP,
                                    ConnectionState.Success,
                                ),
                            )
                            onNext()
                        }
                    }
                }
            }

            GGScanResponseType.DEVICE_DISCONNECTED -> {
                setIsScaleConnected(false)
                setDiscoveredScale(getDiscoveredScale()?.copy(connectionStatus = BLEStatus.DISCONNECTED))
            }

            GGScanResponseType.DEVICE_CONNECTED -> {
                setIsScaleConnected(true)
                setDiscoveredScale(getDiscoveredScale()?.copy(connectionStatus = BLEStatus.CONNECTED))
            }

            else -> Unit
        }
    }

    private suspend fun customizeDevice(ggDeviceDetail: GGDeviceDetail) {
        val username = getDiscoveredScale()?.preferences?.displayName
            ?: accountService.activeAccountFlow.first()?.firstName?.take(20)
            ?: "Default"
        getState().usernameForm.username.onValueChange(username)
        val token = deviceService.getScaleToken()
        val device = Device(
            device = ggDeviceDetail,
            token = token,
            nickname = "Accucheck Verve Smart Scale",
        )
        val newScale = device.copy(
            deviceType = ScaleSetupType.BtWifiR4.value,
            sku = sku,
            preferences = ScaleMetricsHelper.getDefaultPreference(username, device.id),
        )
        setDiscoveredScale(newScale)
        setModePreference(newScale)
    }
}
