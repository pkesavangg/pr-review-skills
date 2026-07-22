package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent.SetCurrentStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.library.ggbluetooth.model.GGLiveDataResponse
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the "One Last Step" / measurement slice of [BtWifiScaleSetupViewModel] (MOB-1501).
 * Holds the [measurementTimeoutJob] so its lifecycle stays with the step it guards.
 * Behaviour-preserving verbatim move.
 */
class BtWifiMeasurementManager(
    private val ggDeviceService: GGDeviceService,
    private val scope: CoroutineScope,
    private val operationTimeout: Long,
    private val getState: () -> BtWifiScaleSetupState,
    private val onIntent: (BtWifiScaleSetupIntent) -> Unit,
    private val getDiscoveredScale: () -> Device?,
    private val onNext: () -> Unit,
    private val startObservingEntries: () -> Unit,
    // Re-syncs paired devices so the R4 re-establishes its BLE link on a measurement retry
    // (lives in BtWifiPermissionsManager on the ViewModel; injected here for retryMeasurement). MOB-1580.
    private val onSyncForBleReconnection: suspend () -> Unit = {},
) {

    private val TAG = "BtWifiMeasurementManager"
    private var measurementTimeoutJob: Job? = null

    // Window to wait for the paired scale's BLE link to come back up when retrying a failed
    // measurement collection before surfacing the error again (MOB-1580).
    private val reconnectTimeoutMs: Long = 30_000L

    fun stepOn() {
        AppLog.d(TAG, "Starting step on process")
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Loading),
        )
        try {
            subscribeToLiveData()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error during step on", e)
            onIntent(
                BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Failed.Error),
            )
        }
    }

    private fun subscribeToLiveData(onLiveDataReady: () -> Unit = { onNext() }) {
        val scale = getDiscoveredScale() ?: run {
            AppLog.e(TAG, "discoveredScale is null when subscribing to live data")
            return
        }
        ggDeviceService.subscribeToLiveData(scale.toGGBTDevice()) {
            when (it) {
                is GGLiveDataResponse.Success -> {
                    onIntent(
                        BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.STEP_ON, ConnectionState.Success),
                    )
                    onLiveDataReady()
                }
                else -> Unit
            }
        }
    }

    /**
     * TRY AGAIN handler for the "Error Collecting Measurement" screen (MOB-1580).
     *
     * The plain [collectMeasurement] retry only re-attaches an entry observer to the app-wide
     * scan; it never re-issues a BLE connect/scan. When the R4's link was torn down as part of
     * the collection failure this can never succeed — the scale isn't connected and isn't
     * streaming live data, so the retry just sits on "Collecting Measurement" until the timeout
     * re-fails. Crucially the reconnect must NOT be gated on a Bluetooth-enable event: when BLE
     * is already on (the reported scenario) no such event ever fires.
     *
     * So on retry: if the scale is still CONNECTED we go straight to collecting; otherwise we
     * resume scanning and reconnect the paired scale over BLE, wait for it to come back up,
     * re-subscribe to live data, then resume collecting once the user steps on again.
     */
    fun retryMeasurement() {
        val scale = getDiscoveredScale()
        if (scale == null) {
            AppLog.e(TAG, "discoveredScale is null on measurement retry")
            setMeasurementFailed()
            return
        }
        if (scale.connectionStatus == BLEStatus.CONNECTED) {
            AppLog.d(TAG, "Scale still connected on retry — resuming collection")
            collectMeasurement()
            return
        }
        AppLog.d(TAG, "Scale disconnected on retry — re-initiating BLE connect before collecting")
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Loading),
        )
        scope.launch {
            try {
                ggDeviceService.resumeScan(true)
                onSyncForBleReconnection()
                val broadcast = scale.device?.broadcastId ?: scale.device?.broadcastIdString
                val reconnected = if (broadcast != null) {
                    withTimeoutOrNull(reconnectTimeoutMs) {
                        ggDeviceService.deviceCache.first { cache ->
                            (cache[broadcast] as? Device)?.connectionStatus == BLEStatus.CONNECTED
                        }
                    }
                } else {
                    AppLog.w(TAG, "No broadcast id for scale on measurement retry")
                    null
                }
                if (reconnected == null) {
                    AppLog.w(TAG, "Scale did not reconnect within timeout on measurement retry")
                    setMeasurementFailed()
                    return@launch
                }
                // Arm the measurement timeout before awaiting live data: if the scale reconnects
                // but never streams a reading, MEASUREMENT would otherwise stay stuck in Loading
                // with no way back to TRY AGAIN (MOB-1580 review follow-up).
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.MEASUREMENT) {
                        AppLog.w(TAG, "Timed out waiting for live data after reconnect on measurement retry")
                        setMeasurementFailed()
                    }
                }
                subscribeToLiveData(onLiveDataReady = { collectMeasurement() })
            } catch (e: Exception) {
                AppLog.e(TAG, "Error reconnecting scale on measurement retry", e)
                setMeasurementFailed()
            }
        }
    }

    fun collectMeasurement() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Loading),
        )
        scope.launch {
            try {
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = scope.launch {
                    delay(operationTimeout)
                    if (getState().currentStep == BtWifiSetupStep.MEASUREMENT) {
                        AppLog.w(TAG, "Measurement collection timeout reached")
                        onIntent(
                            BtWifiScaleSetupIntent.SetStepConnectionState(
                                BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error,
                            ),
                        )
                    }
                }
                startObservingEntries()
            } catch (e: Exception) {
                measurementTimeoutJob?.cancel()
                measurementTimeoutJob = null
                AppLog.e(TAG, "Error during measurement collection", e)
                onIntent(
                    BtWifiScaleSetupIntent.SetStepConnectionState(
                        BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error,
                    ),
                )
            }
        }
    }

    fun setMeasurementFailed() {
        // Stop the pending collection timeout so it can't overwrite/duplicate the failed state later.
        cancelMeasurementTimeout()
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Failed.Error),
        )
        onIntent(SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
    }

    fun cancelMeasurementTimeout() {
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null
    }
}
