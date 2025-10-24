package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

abstract class ScaleSetupViewmodel<State : IReducer.State, Intent : IReducer.Intent>(
  open val ggDeviceService: GGDeviceService,
  open val connectivityObserver: IConnectivityObserver,
  open val permissionService: GGPermissionService,
  protected val reducer: IReducer<State, Intent>,
) : BaseIntentViewModel<State, Intent>(reducer) {

  abstract override fun provideInitialState(): State

  override fun onCleared() {
    viewModelScope.launch {
      val device = this@ScaleSetupViewmodel.discoveredScale
      var connectedDeviceBroadcastID: String? = null

      ggDeviceService.localSkipDevices.first().forEach {
        if (device?.device?.broadcastId == it && device.connectionStatus == BLEStatus.CONNECTED) {
          connectedDeviceBroadcastID = it
        } else {
          ggDeviceService.skipDevice(it)
        }
      }
      if (connectedDeviceBroadcastID != null) {
        ggDeviceService.removeSkipDeviceBroadcastID(connectedDeviceBroadcastID)
      }
    }
  }

  /**
   * Called when a new device matching the protocol is found during setup.
   * @param device The GGDeviceDetail of the new device found.
   */
  protected abstract fun onScanResponse(response: GGScanResponse.DeviceDetail)

  protected open fun onEntryResponse(response: GGScanResponse.Entry) {}

  protected var discoveredScale: Device? = null

  private var deviceObservationJob: Job? = null

  private var entryObservationJob: Job? = null

  /**
   * Starts observing device scan responses. Call this when you want to begin collecting devices.
   */
  protected fun startObservingDevices() {
    AppLog.d(TAG, "Starting device observation")
    deviceObservationJob?.cancel()
    deviceObservationJob = viewModelScope.launch {
      try {
        ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.DeviceDetail }
          .collect { scanResponse ->
            AppLog.d(TAG, "Received device scan response: ${scanResponse::class.simpleName}")
            onScanResponse(scanResponse as GGScanResponse.DeviceDetail)
          }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error observing device scan responses", e)
      }
    }
  }

  protected fun stopObservingDevices() {
    AppLog.d(TAG, "Stopping device observation")
    deviceObservationJob?.cancel()
    deviceObservationJob = null
  }

  protected fun stopObservingEntries() {
    AppLog.d(TAG, "Stopping entry observation")
    entryObservationJob?.cancel()
    entryObservationJob = null
  }

  protected fun startObservingEntries() {
    AppLog.d(TAG, "Starting entry observation")
    if (entryObservationJob == null) {
      entryObservationJob = viewModelScope.launch {
        try {
          ggDeviceService.deviceCallbackFlow.filter { it is GGScanResponse.Entry }
            .collect { scanResponse ->
              AppLog.d(TAG, "Received entry scan response: ${scanResponse::class.simpleName}")
              onEntryResponse(scanResponse as GGScanResponse.Entry)
            }
        } catch (e: Exception) {
          AppLog.e(TAG, "Error observing entry scan responses", e)
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
  protected fun subscribePermissions(isSkipNetworkCheck: Boolean = false): Flow<GGPermissionStatusMap> {
    return if (isSkipNetworkCheck) {
      // When skipping network check, return just the WiFi switch status from permissions
      permissionService.permissionCallBackFlow.map { permissions ->
        permissions.toMutableMap().apply {
          // Keep the original WiFi switch status without network connectivity consideration
          put(GGPermissionType.WIFI_SWITCH, permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED)
        }
      }
    } else {
      // Original logic with network connectivity check
      AppLog.d(TAG, "Using combined permission and network flow")
      combine(
        permissionService.permissionCallBackFlow,
        connectivityObserver.observe(),
      ) { permissions, networkState ->
        val networkStatus = if (networkState.available) GGPermissionState.ENABLED else GGPermissionState.DISABLED
        val wifiSwitchStatus = permissions[GGPermissionType.WIFI_SWITCH] ?: GGPermissionState.DISABLED

        AppLog.d(TAG, "Network status: $networkStatus, WiFi switch status: $wifiSwitchStatus")

        // WiFi switch is enabled if either network is available OR WiFi switch is enabled
        val updatedWifiSwitchStatus = if (networkStatus == GGPermissionState.ENABLED ||
          wifiSwitchStatus == GGPermissionState.ENABLED
        ) {
          GGPermissionState.ENABLED
        } else {
          GGPermissionState.DISABLED
        }

        AppLog.d(TAG, "Updated WiFi switch status: $updatedWifiSwitchStatus")
        permissions.toMutableMap().apply {
          put(GGPermissionType.WIFI_SWITCH, updatedWifiSwitchStatus)
        }
      }
    }
  }

  companion object {
    private const val TAG = "ScaleSetupViewmodel"
  }
}
