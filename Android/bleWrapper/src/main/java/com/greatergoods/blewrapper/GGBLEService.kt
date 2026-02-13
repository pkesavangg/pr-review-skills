package com.greatergoods.blewrapper

import androidx.activity.ComponentActivity
import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.model.BluetoothContext
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference

/**
 * Service that manages the GGBluetooth instance lifecycle.
 * Recreates the GGBluetooth instance when the Activity changes to ensure
 * ActivityResultLaunchers are properly registered with the current Activity.
 */
class GGBLEService {
  lateinit var ggBluetooth: GGBluetooth
    private set

  private var lastActivityRef: WeakReference<ComponentActivity>? = null

  val _deviceCallbackFlow =
    MutableStateFlow<GGScanResponse>(GGScanResponse.None)
  val _permissionCallbackFlow =
    MutableStateFlow<GGPermissionStatusMap>(mutableMapOf())

  /**
   * Creates or recreates the GGBluetooth instance for the given activity.
   * The instance is recreated when:
   * - It was never initialized, or
   * - The activity instance differs from the last one, or
   * - The last activity reference was garbage collected.
   *
   * This ensures ActivityResultLaunchers are always registered to the current Activity,
   * preventing crashes when requesting permissions after activity recreation.
   *
   * @param activity The current ComponentActivity to bind GGBluetooth to.
   */
  fun createInstance(activity: ComponentActivity) {
    val lastActivity = lastActivityRef?.get()
    val needsRecreation = !this::ggBluetooth.isInitialized ||
      lastActivity == null ||
      lastActivity !== activity

    if (needsRecreation) {
      ggBluetooth = GGBluetooth(
        BluetoothContext.ComposeActivity(activity),
      )
      lastActivityRef = WeakReference(activity)
    }
  }


  fun reset(){
    _deviceCallbackFlow.value = GGScanResponse.None
  }
}
