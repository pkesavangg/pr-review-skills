package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.BluetoothPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Bluetooth-related preferences.
 * Handles MAC address selection for 0412 scale setup filtering.
 * Uses Proto DataStore for persistent storage and session-based testing features.
 */
@Singleton
class BluetoothPreferencesService @Inject constructor(
  private val bluetoothPreferencesDataStore: BluetoothPreferencesDataStore,
) {
  private val TAG = "BluetoothPreferencesService"

  /**
   * Flow of the currently selected MAC address.
   */
  val selectedMacAddress: Flow<String> = bluetoothPreferencesDataStore.selectedMacAddressFlow

  /**
   *
   * Session-based testing features flag (not persisted).
   * Only enabled in debug builds, similar to Angular's enableTestingFeatures.
   */
  var enableTestingFeatures: Boolean = AppStatusService.enableTestingFeatures
    private set

  /**
   * Known MAC addresses from Angular implementation for 0412 scale filtering.
   */
  val knownMacAddresses: List<String> = BluetoothPreferencesDataStore.KNOWN_MAC_ADDRESSES

  /**
   * Session-based list of device IDs (broadcastId or MAC address) that should be skipped.
   * Used to prevent showing popups for devices that were dismissed or disconnected.
   * Not persisted across app restarts.
   */
  private val skipDevices: MutableSet<String> = mutableSetOf()

  /**
   * Sets the selected MAC address locally.
   * @param macAddress The MAC address to set as selected
   */
  suspend fun setSelectedMacAddressLocally(macAddress: String) {
    try {
      AppLog.d(TAG, "Setting selected MAC address: $macAddress")
      bluetoothPreferencesDataStore.setSelectedMacAddress(macAddress)
    } catch (e: Exception) {
      AppLog.e(TAG, "Error setting selected MAC address", e.toString())
      throw e
    }
  }

  /**
   * Gets the current selected MAC address value synchronously.
   * Note: This should be used sparingly as it blocks the calling thread.
   */
  suspend fun getCurrentSelectedMacAddress(): String {
    return try {
      selectedMacAddress.first()
    } catch (e: Exception) {
      AppLog.e(TAG, "Error getting current selected MAC address", e.toString())
      "All" // Default fallback
    }
  }

  /**
   * Checks if a device should be filtered based on the selected MAC address.
   * Used in AppViewModel.handleDeviceResponse() for 0412 scale filtering.
   * @param deviceMacAddress The MAC address of the discovered device
   * @return True if the device should be shown, false if filtered out
   */
  suspend fun shouldShowDevice(deviceMacAddress: String): Boolean {
    return try {
      if (!enableTestingFeatures) {
        // If testing features are disabled, show all devices
        return true
      }
      val selectedMac = getCurrentSelectedMacAddress()
      val result = selectedMac == "All" || selectedMac == deviceMacAddress
      AppLog.d(TAG, "Device filter check - Selected: $selectedMac, Device: $deviceMacAddress, Show: $result")
      result
    } catch (e: Exception) {
      AppLog.e(TAG, "Error checking device filter", e.toString())
      true // Default to showing device on error
    }
  }

  /**
   * Adds a device ID (broadcastId or MAC address) to the skip list.
   * Devices in the skip list will not trigger the scale discovered popup.
   * @param deviceId The broadcastId or MAC address of the device to skip
   */
  fun addSkipDevice(deviceId: String) {
    if (!skipDevices.contains(deviceId)) {
      skipDevices.add(deviceId)
      AppLog.d(TAG, "Added device to skip list: $deviceId")
    }
  }

  /**
   * Checks if a device ID (broadcastId or MAC address) is in the skip list.
   * @param deviceId The broadcastId or MAC address to check
   * @return True if the device is in the skip list, false otherwise
   */
  fun containsSkipDevice(deviceId: String): Boolean {
    return skipDevices.contains(deviceId)
  }

  /**
   * Clears all devices from the skip list.
   * Used when switching accounts to reset the skip state.
   */
  fun clearSkipDevices() {
    skipDevices.clear()
    AppLog.d(TAG, "Cleared all skip devices")
  }
}
