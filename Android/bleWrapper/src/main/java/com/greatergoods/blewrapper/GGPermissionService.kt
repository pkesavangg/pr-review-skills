package com.greatergoods.blewrapper

import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Service for handling BLE permission requests.
 * Uses a dynamic getter for ggBluetooth to ensure it always uses the latest instance
 * bound to the current Activity, preventing unregistered ActivityResultLauncher crashes.
 */
class GGPermissionService @Inject constructor(
  private val ggBleService: GGBLEService,
) : GGScanService() {
  override val ggBluetooth: GGBluetooth
    get() = ggBleService.ggBluetooth
  override val deviceCallbackFlow: MutableStateFlow<GGScanResponse>
    get() = ggBleService._deviceCallbackFlow
  override val permissionCallBackFlow: MutableStateFlow<GGPermissionStatusMap>
    get() = ggBleService._permissionCallbackFlow

  /**
   * Requests a specific permission.
   * @param permissionType The type of permission to request.
   */
  fun requestPermission(permissionType: String, callback: (Any?) -> Unit = {}) {
    ggBluetooth.requestPermission(permissionType, callback)
  }
}
