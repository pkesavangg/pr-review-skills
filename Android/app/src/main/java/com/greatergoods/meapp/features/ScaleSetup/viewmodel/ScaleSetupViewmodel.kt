package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class ScaleSetupViewmodel<State : IReducer.State, Intent : IReducer.Intent>(
  private val protocolType: String,
  open val ggDeviceService: GGDeviceService,
  protected val reducer: IReducer<State, Intent>,
) : BaseIntentViewModel<State, Intent>(reducer) {

  abstract override fun provideInitialState(): State

  /**
   * Called when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  protected abstract fun onScanResponse(device: GGScanResponse.DeviceDetail)

  protected var discoveredScale: Device? = null

  private var deviceObservationJob: Job? = null

  /**
   * Starts observing device scan responses. Call this when you want to begin collecting devices.
   */
  protected fun startObservingDevices() {
    if (deviceObservationJob == null) {
      deviceObservationJob = viewModelScope.launch {
        ggDeviceService.deviceCallbackFlow
          .collect { scanResponse ->
            handleScanResponse(scanResponse)
          }
      }
    }
  }

  /**
   * Stops observing device scan responses. Call this to stop collecting devices.
   */
  protected fun stopObservingDevices() {
    deviceObservationJob?.cancel()
    deviceObservationJob = null
  }

  private fun handleScanResponse(scanResponse: GGScanResponse) {
    when (scanResponse) {
      is GGScanResponse.DeviceDetail -> {
        handleDeviceDetail(scanResponse)
      }

      else -> null
    }
  }

  private fun handleDeviceDetail(response: GGScanResponse.DeviceDetail) {
    response.data
    onScanResponse(response)
  }
}
