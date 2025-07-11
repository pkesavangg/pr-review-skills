package com.greatergoods.blewrapper

import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import android.os.Build

class GGPermissionService @Inject constructor(
  ggBleService: GGBLEService,
) : GGScanService() {
  override val ggBluetooth: GGBluetooth = ggBleService.ggBluetooth
  override val deviceCallbackFlow: MutableStateFlow<GGScanResponse> =
    ggBleService._deviceCallbackFlow
  override val permissionCallBackFlow: MutableStateFlow<GGPermissionStatusMap> =
    ggBleService._permissionCallbackFlow

  /**
   * Checks if the scan permissions are enabled or not.
   * @param permissions The map of permissions to check.
   */
  fun checkScanPermissions(permissions: GGPermissionStatusMap): Boolean {
    val scanPermissions = convertToScanPermissions(permissions)
    val isEnabled = scanPermissions.all { it.value == GGPermissionState.ENABLED }
    return isEnabled
  }

  /**
   *  Requests the scan permissions if they are not enabled.
   *  @param permissions The map of permissions to request.
   */
  fun requestScanPermissions(permissions: GGPermissionStatusMap) {
    val scanPermissions = convertToScanPermissions(permissions)
    scanPermissions.forEach { type, status ->
      if (status != GGPermissionState.ENABLED) {
        requestPermission(type)
      }
    }
  }

  /**
   * Requests a specific permission.
   * @param permissionType The type of permission to request.
   */
  fun requestPermission(permissionType: String) {
    ggBluetooth.requestPermission(permissionType)
  }

  private fun convertToScanPermissions(permissions: GGPermissionStatusMap): GGPermissionStatusMap {
    return permissions.filter {
      it.key == GGPermissionType.BLUETOOTH_SWITCH ||
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
          it.key == GGPermissionType.NEARBY_DEVICE
        } else {
          it.key == GGPermissionType.LOCATION || it.key == GGPermissionType.LOCATION_SWITCH
        }
    } as GGPermissionStatusMap
  }
}
