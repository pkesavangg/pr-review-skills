package com.greatergoods.meapp.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.network.utility.NetworkState
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

abstract class ScaleSetupViewmodel<State : IReducer.State, Intent : IReducer.Intent>(
  open val ggDeviceService: GGDeviceService,
  open val connectivityObserver: IConnectivityObserver,
  open val permissionService: GGPermissionService,
  protected val reducer: IReducer<State, Intent>,
) : BaseIntentViewModel<State, Intent>(reducer) {

  abstract override fun provideInitialState(): State

  /**
   * Called when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  protected abstract fun onScanResponse(response: GGScanResponse.DeviceDetail)

  protected abstract fun onEntryResponse(response: GGScanResponse.Entry)

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

  protected fun stopObservingDevices() {
    deviceObservationJob?.cancel()
    deviceObservationJob = null
  }

  protected fun stopObservingEntries() {
    entryObservationJob?.cancel()
    entryObservationJob = null
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

  /**
   * Subscribes to permission updates and network state changes.
   * Combines permission status with network availability to properly handle WiFi switch permission.
   * The WiFi switch permission is considered enabled if either:
   * 1. Network is available (cellular or WiFi), OR
   * 2. WiFi switch is enabled in the permission callback flow
   *
   * @return Flow emitting updated permission status map
   */
  protected fun subscribePermissions(): Flow<GGPermissionStatusMap> {
    return combine(
      permissionService.permissionCallBackFlow,
      connectivityObserver.observe()
    ) { permissions, networkState ->
      val networkStatus = if (networkState.available) GGPermissionState.ENABLED else GGPermissionState.DISABLED
      val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED

      // WiFi switch is enabled if either network is available OR WiFi switch is enabled
      val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
                                       wifiSwitchStatus == GGPermissionState.ENABLED) {
        GGPermissionState.ENABLED
      } else {
        GGPermissionState.DISABLED
      }

      permissions.toMutableMap().apply {
        put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
      }
    }
  }
}
