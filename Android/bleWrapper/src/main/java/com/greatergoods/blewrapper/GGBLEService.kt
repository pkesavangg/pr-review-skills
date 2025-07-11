package com.greatergoods.blewrapper

import androidx.activity.ComponentActivity
import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.model.BluetoothContext
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow

class GGBLEService {
  lateinit var ggBluetooth: GGBluetooth
  val _deviceCallbackFlow =
    MutableStateFlow<GGScanResponse>(GGScanResponse.None)
  val _permissionCallbackFlow =
    MutableStateFlow<GGPermissionStatusMap>(mutableMapOf())

  /**
   *  Creates a singleton instance of GGBluetooth which is injected through activity
   *  @param activity - ComponentActivity
   */
  fun createInstance(activity: ComponentActivity) {
    if (!this::ggBluetooth.isInitialized) {
      ggBluetooth = GGBluetooth(
        BluetoothContext.ComposeActivity(activity),
      )
    }
  }
}
