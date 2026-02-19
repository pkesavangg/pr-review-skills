package com.greatergoods.blewrapper

import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log

abstract class GGScanService {

  abstract val ggBluetooth: GGBluetooth
  abstract val deviceCallbackFlow: MutableStateFlow<GGScanResponse>
  abstract val permissionCallBackFlow: MutableStateFlow<GGPermissionStatusMap>

  /**
   *  Starts the bluetooth scan and also the flow of GGScanResponse
   *
   */
  fun startScan(
    appType: String = GGAppType.BALANCE_HEALTH,
    userProfile: GGBTUserProfile
  ) {

    ggBluetooth.scan(
      userProfile,
      appType,
    ) { response ->
      when (response) {
        is GGScanResponse.Permission -> {
          Log.d("TAG", "scan started ${response.data}")
          permissionCallBackFlow.value = response.data
        }

        else -> {
          Log.e("TAG", "startScan: $response")
          deviceCallbackFlow.value = response
        }
      }
    }
  }

  /**
   *  Stops the bluetooth scan and also the flow of GGScanResponse
   */
  fun stopScan(clearDevices: Boolean = true) {
    if (clearDevices) {
      ggBluetooth.clearDevices()
    }
    ggBluetooth.stop()
  }

  fun pauseScan() {
    ggBluetooth.pauseScan()
  }

  fun resumeScan(clearOnlyPairing: Boolean = true) {
    ggBluetooth.resumeScan(clearOnlyPairing)
  }
}
