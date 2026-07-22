package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.toGGBTDevice
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0412
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.cleanCorruptedChars
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.greatergoods.blewrapper.GGDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the R4 WiFi slice of [DeviceDetailsViewModel] (MOB-1500) — connected-SSID / MAC-address
 * lookup and navigation into the BtWifi scale setup flow. Behaviour-preserving verbatim move.
 */
class DeviceWifiManager(
  private val ggDeviceService: GGDeviceService,
  private val scope: CoroutineScope,
  private val getState: () -> DeviceDetailsState,
  private val onIntent: (DeviceDetailsIntent) -> Unit,
  private val navigateTo: suspend (AppRoute) -> Unit,
) {

  private val TAG = "DeviceDetailsViewModel"

  fun configureR4ScaleDetails() {
    scope.launch {
      try {
        val scale = getState().scale ?: return@launch
        ggDeviceService.getConnectedWifiSSID(scale.toGGBTDevice()) { ssid ->
          onIntent(DeviceDetailsIntent.SetConnectedSSID(if (ssid.isEmpty()) null else ssid.cleanCorruptedChars()))
        }
        fetchWifiMacAddress()
      } catch (e: Exception) {
        AppLog.e(TAG, "Error configuring R4 scale details: ${e.message}")
        onIntent(DeviceDetailsIntent.SetConnectedSSID(null))
      }
    }
  }

  /**
   * Fetches the WiFi MAC address from the connected R4 scale.
   * Only fetches if the scale is an R4 scale and is connected.
   */
  private fun fetchWifiMacAddress() {
    scope.launch {
      val scale = getState().scale
      if (scale != null &&
        scale.deviceType == DeviceSetupType.BtWifiR4.value &&
        scale.connectionStatus == BLEStatus.CONNECTED
      ) {
        try {
          ggDeviceService.getConnectedWifiMacAddress(scale.toGGBTDevice()) { macAddress ->
            onIntent(DeviceDetailsIntent.SetWifiMacAddress(macAddress))
          }
        } catch (e: Exception) {
          AppLog.e("DeviceDetailsViewModel", "Failed to fetch WiFi MAC address", e)
          onIntent(DeviceDetailsIntent.SetWifiMacAddress(""))
        }
      }
    }
  }

  fun openWiFiSetup() {
    scope.launch {
      try {
        val scale = getState().scale
        if (scale != null) {
          ggDeviceService.addCacheDevice(scale.device?.broadcastId, scale)
          navigateTo(
            AppRoute.DeviceSetup.BtWifiScaleSetup(
              scale.sku ?: SKU_0412,
              BtWifiSetupStep.GATHERING_NETWORK,
              scale.device?.broadcastId,
            ),
          )
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate to WiFi setup", e)
      }
    }
  }
}
