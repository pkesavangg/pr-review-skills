package com.greatergoods.blewrapper

import com.dmdbrands.library.ggbluetooth.GGBluetooth
import com.dmdbrands.library.ggbluetooth.enums.ClearDataType
import com.dmdbrands.library.ggbluetooth.enums.GGBTSettingType
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGBTSetting
import com.dmdbrands.library.ggbluetooth.model.GGBTSettingValue
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.dmdbrands.library.ggbluetooth.model.GGBTWifiConfig
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGDeviceLogResponse
import com.dmdbrands.library.ggbluetooth.model.GGLiveDataResponse
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScaleUserResponse
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.dmdbrands.library.ggbluetooth.model.GGWifiResponse
import com.dmdbrands.library.ggbluetooth.model.GGWifiSetupResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface GGCacheDevice

/**
 * Service for managing BLE device operations.
 * Uses a dynamic getter for ggBluetooth to ensure it always uses the latest instance
 * bound to the current Activity, preventing unregistered ActivityResultLauncher crashes.
 *
 * Device-type agnostic: All methods (pairDevice, subscribeToLiveData, getDeviceInfo,
 * getUsers, disconnectDevice, etc.) work generically across device types including
 * scales and BPM monitors. The GGBluetooth SDK routes internally based on device type
 * via GGAppType resolution (CommonHelper.getAppType).
 *
 * For BPM: pass GGAppType.BALANCE_HEALTH to startScan(), then filter GGScanResponse by device type
 * at the app layer. No new bleWrapper methods are needed.
 */
class GGDeviceService @Inject constructor(
  private val ggBleService: GGBLEService
) : GGScanService() {
  override val ggBluetooth: GGBluetooth
    get() = ggBleService.ggBluetooth
  override val deviceCallbackFlow: MutableStateFlow<GGScanResponse>
    get() = ggBleService._deviceCallbackFlow
  override val permissionCallBackFlow: MutableStateFlow<GGPermissionStatusMap>
    get() = ggBleService._permissionCallbackFlow

  // Generic StateFlow for device cache
  private val _deviceCache: MutableStateFlow<Map<String, GGCacheDevice>> = MutableStateFlow(emptyMap())
  val deviceCache: StateFlow<Map<String, GGCacheDevice>> = _deviceCache

  private val _localSkipDevices: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
  val localSkipDevices: StateFlow<List<String>> = _localSkipDevices.asStateFlow()

  // Function to add a device to the cache
  fun addCacheDevice(broadcastId: String?, device: GGCacheDevice) {
    if (broadcastId == null) return
    val currentCache = _deviceCache.value
    val updatedCache = currentCache.toMutableMap()
    updatedCache[broadcastId] = device
    _deviceCache.value = updatedCache
  }

  fun addSkipDeviceBroadcastID(broadCastId: String) {
    if (!localSkipDevices.value.contains(broadCastId))
      _localSkipDevices.value += broadCastId
  }

  fun removeSkipDeviceBroadcastID(broadCastId: String) {
    _localSkipDevices.value -= broadCastId
  }

  /**
   * Initiates the pairing process with the specified device and returns the result through the callback.
   *
   * This method confirms the pairing with the provided `device` and triggers the `callback` function
   * with the result once the pairing process is completed. The `callback` will receive a `GGUserActionResponseType`
   * which indicates the outcome of the pairing attempt.
   *
   * The second attempt after paired will throw a response of already paired.
   *
   * @param device The GGBTDevice to pair with.
   * @param callback A callback function that will receive the result of the pairing process,
   *        in the form of `GGUserActionResponseType`.
   */
  fun pairDevice(
    device: GGBTDevice,
    replaceUser: Boolean = false,
    pairedSKUMonitors: MutableList<GGBTDevice> = mutableListOf(),
    callback: (GGUserActionResponseType) -> Unit
  ) {
    ggBluetooth.confirmPair(device, replaceUser, pairedSKUMonitors) {
      callback(it)
    }
  }

  fun deleteAccount(
    device: GGBTDevice,
    disconnect: Boolean = false,
    callback: (GGUserActionResponseType) -> Unit
  ) {
    try {
      ggBluetooth.deleteUser(device, disconnect, callback)
    } catch (e: Exception) {
      throw e
    }
  }

  fun getUsers(
    device: GGBTDevice,
    callback: (data: GGScaleUserResponse) -> Unit
  ) {
    ggBluetooth.getUsers(device, callback)
  }

  fun updateAccount(
    device: GGBTDevice,
    callback: (GGUserActionResponseType) -> Unit
  ) {
    ggBluetooth.updateAccount(device, callback)
  }

  fun updateProfile(
    profile: GGBTUserProfile,
    callback: (GGUserActionResponseType) -> Unit
  ) {
    ggBluetooth.updateProfile(profile, callback)
  }

  fun restoreAccount(
    device: GGBTDevice,
    accountName: String,
    callback: (GGUserActionResponseType) -> Unit
  ) {
    ggBluetooth.restoreAccount(device, accountName, callback)
  }

  /**
   * Disconnects the specified device from the Bluetooth connection.
   * @param device The GGBTDevice to disconnect.
   */
  fun disconnectDevice(device: GGBTDevice) {
    ggBluetooth.disconnectDevice(device.broadcastId)
    this.skipDevice(device.broadcastId, true)
  }

  fun skipDevice(broadCastId: String, considerForSession: Boolean = false) {
    ggBluetooth.skipDevice(broadCastId, considerForSession)
    this.addSkipDeviceBroadcastID(broadCastId)
  }

  fun clearPairedDevices() {
    ggBluetooth.clearDevices()
    _localSkipDevices.value = emptyList()
  }

  /**
   * Fetches the device information for the given device and invokes the provided callback with the result.
   * @param device The GGBTDevice for which to fetch information.
   * @param callback A function that will receive the device information as a result.
   */
  fun getDeviceInfo(device: GGBTDevice, callback: (GGDeviceDetail?) -> Unit) {
    ggBluetooth.getDeviceInfo(
      device = device,
      callback = callback,
    )
  }

  /**
   * Starts the scan for pairing process for the devices that have protocol type A6
   */
  fun scanForPairing() {
    ggBluetooth.scanForPairing()
  }

  /**
   * Subscribes to the measurement live data for the given device and invokes the provided callback with the result.
   * @param device The GGBTDevice for which to subscribe to live data.
   * @param callback A function that will receive the live data result.
   */
  fun subscribeToLiveData(device: GGBTDevice, callback: (GGLiveDataResponse) -> Unit) {
    ggBluetooth.getMeasurementLiveData(device) {
      callback(it)
    }
  }

  fun getWifiList(
    device: GGBTDevice,
    callback: (GGWifiResponse) -> Unit
  ) {
    ggBluetooth.getWifiList(device, callback)
  }

  fun setupWifi(
    device: GGBTDevice,
    wifiConfig: GGBTWifiConfig,
    callback: (GGWifiSetupResponse) -> Unit
  ) {
    ggBluetooth.setupWifi(device, wifiConfig, callback)
  }

  fun cancelWifi(device: GGBTDevice, callback: (Boolean) -> Unit) {
    ggBluetooth.cancelWifi(device, callback)
  }

  fun getConnectedWifiSSID(device: GGBTDevice, callback: (String) -> Unit) {
    ggBluetooth.getConnectedWifiSSID(device, callback)
  }

  fun getConnectedWifiMacAddress(device: GGBTDevice, callback: (String) -> Unit) {
    ggBluetooth.getWifiMacAddress(device, callback)
  }

  fun clearData(device: GGBTDevice, type: ClearDataType, callback: (String) -> Unit) {
    ggBluetooth.clearData(device, type, callback)
  }

  fun startMeasurement(device: GGBTDevice) {
    ggBluetooth.subscribeToLiveData(device)
  }

  fun syncDevices(devices: List<GGBTDevice>) {
    ggBluetooth.syncDevices(devices)
  }

  fun getDeviceLogs(device: GGBTDevice, callback: (GGDeviceLogResponse) -> Unit) {
    ggBluetooth.getDeviceLogs(device, callback)
  }

  fun startFirmwareUpgrade(device: GGBTDevice, timeStamp: Long = System.currentTimeMillis()) {
    ggBluetooth.startFirmwareUpdate(device, timeStamp)
  }

  fun changeUnit(device: GGBTDevice, unit: String, key: String = GGBTSettingType.UNIT) {
    ggBluetooth.updateSetting(
      device,
      listOf(
        GGBTSetting(
          key = key,
          value = GGBTSettingValue.String(unit),
        ),
      ),
    )
  }

  fun updateSettings(device: GGBTDevice, setting: GGBTSetting) {
    ggBluetooth.updateSetting(device, listOf(setting))
  }

  fun changeMode(device: GGBTDevice, mode: String) {
    ggBluetooth.updateSetting(
      device,
      listOf(
        GGBTSetting(
          key = GGBTSettingType.MUTE_MODE,
          value = GGBTSettingValue.String(mode),
        ),
      ),
    )
  }

  fun tare(device: GGBTDevice) {
    ggBluetooth.tare(device)
  }
}
