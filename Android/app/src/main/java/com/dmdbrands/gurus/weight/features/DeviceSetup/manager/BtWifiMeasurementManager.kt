package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
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
import kotlinx.coroutines.launch

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
) {

    private val TAG = "BtWifiMeasurementManager"
    private var measurementTimeoutJob: Job? = null

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

    private fun subscribeToLiveData() {
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
                    onNext()
                }
                else -> Unit
            }
        }
    }

    fun collectMeasurement() {
        onIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(BtWifiSetupStep.MEASUREMENT, ConnectionState.Loading),
        )
        scope.launch {
            try {
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
