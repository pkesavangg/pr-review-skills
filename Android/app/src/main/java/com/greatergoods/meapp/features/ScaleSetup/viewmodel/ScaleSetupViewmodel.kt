package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

abstract class ScaleSetupViewmodel<State : IReducer.State, Intent : IReducer.Intent>(
  open val ggDeviceService: GGDeviceService,
  protected val reducer: IReducer<State, Intent>,
) : BaseIntentViewModel<State, Intent>(reducer) {

  abstract override fun provideInitialState(): State

  /**
   * Called when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  protected abstract fun onScanResponse(device: GGScanResponse.DeviceDetail)

  protected abstract fun onEntryResponse(device: GGScanResponse.Entry)

  protected var discoveredScale: Device? = null

  private var deviceObservationJob: Job? = null

  private var entryObservationJob: Job? = null

  /**
   * Starts observing device scan responses. Call this when you want to begin collecting devices.
   */
  protected fun startObservingDevices() {
    if (deviceObservationJob == null) {
      deviceObservationJob = viewModelScope.launch {
        ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.DeviceDetail }
          .collect { scanResponse ->
            onScanResponse(scanResponse as GGScanResponse.DeviceDetail)
          }
      }
    }
  }

  protected fun startObservingEntries() {
    if (entryObservationJob == null) {
      entryObservationJob = viewModelScope.launch {
        ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.Entry }
          .collect { scanResponse ->
            onEntryResponse(scanResponse as GGScanResponse.Entry)
          }
      }
    }
  }

}
