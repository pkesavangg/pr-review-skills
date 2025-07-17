package com.greatergoods.blewrapper

import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class GGPermissionService @Inject constructor(
  ggBleService: GGBLEService,
) : GGScanService() {
  override val ggBluetooth: GGBluetooth = ggBleService.ggBluetooth
  override val deviceCallbackFlow: MutableStateFlow<GGScanResponse> =
    ggBleService._deviceCallbackFlow
  override val permissionCallBackFlow: MutableStateFlow<GGPermissionStatusMap> =
    ggBleService._permissionCallbackFlow

  /**
   * Requests a specific permission.
   * @param permissionType The type of permission to request.
   */
  fun requestPermission(permissionType: String) {
    ggBluetooth.requestPermission(permissionType)
  }
}
