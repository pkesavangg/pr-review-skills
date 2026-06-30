package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Service interface for managing device/scale data operations.
 * Provides a centralized way to access and manage scale data with automatic synchronization.
 */
interface IDeviceService {

  suspend fun fetchScales(accountId: String? = null)

  val pairedScales: Flow<List<Device>>
  val connectedScales: Flow<List<Device>>
  val isWeightOnlyModeAlertShown: MutableStateFlow<Boolean>
  val hasBluetoothWifiScale: Flow<Boolean>

  /** Emits true when at least one paired device is a Weight Scale. */
  val hasWeightScale: Flow<Boolean>

  /**
   * Set the current account ID and initialize scale data for that account.
   * This should be called when the user logs in or switches accounts.
   *
   * @param accountId The account ID to set
   */
  suspend fun setAccountId(accountId: String)

  suspend fun onDeviceUpdate(
    deviceDetail: GGDeviceDetail,
    connectionStatus: BLEStatus? = null
  )

  fun getGGBTDevices(): Flow<List<GGBTDevice>>

  /**
   * Clear the current account data.
   * This should be called when the user logs out.
   */
  suspend fun clearAccountData()

  /**
   * Sync scales from the API and update the local database.
   * This method fetches scales from the server and merges them with local unsynced scales.
   */
  suspend fun syncDevices(
    tempDevice: Device? = null,
  )

  /**
   * Passively re-pull paired devices when the app returns to the foreground so that
   * devices paired on another platform (e.g. iOS) appear without an app restart. (MOB-1201)
   *
   * Fire-and-forget and safe to call on every foreground transition: it no-ops when no
   * account is set, when the network is unavailable, on the initial cold-start foreground
   * (already synced by the loading flow), and when called again within a short throttle window.
   */
  fun onAppForegrounded()

  /**
   * On-demand re-pull of paired devices, triggered when the user opens the My Devices screen,
   * so the list always reflects the latest server state (e.g. a scale paired on another phone)
   * without an app restart. (MOB-1201)
   *
   * Fire-and-forget; no-ops when no account is set or the network is unavailable.
   */
  fun refreshPairedDevices()

  /**
   * Save a new scale or update an existing one.
   * If the scale is not synced, it will be marked as temporary and synced later.
   *
   * @param device The device to save
   */
  suspend fun saveScale(device: Device): Device?

  suspend fun updateConnectionStatus(macAddress: String, connectionStatus: BLEStatus)

  /**
   * Delete a scale from both local database and API.
   *
   * @param deviceId The ID of the device to delete
   */
  suspend fun deleteScale(deviceId: String)

  /**
   * Update a scale's nickname.
   *
   * @param device The ID of the device
   * @param nickname The new nickname
   */
  suspend fun updateScaleNickname(
    device: Device,
    nickname: String,
  )

  /**
   * Get a specific scale by its ID.
   *
   * @param deviceId The ID of the device
   * @return The device if found, null otherwise
   */
  suspend fun getScale(deviceId: String): Device?

  /**
   * Get scales by type.
   *
   * @param deviceType The type of devices to get
   * @return List of devices of the specified type
   */
  suspend fun getScalesByType(deviceType: String): List<Device>

  /**
   * Get connected scales.
   *
   * @return List of currently connected devices
   */
  suspend fun getConnectedScales(): List<Device>

  /**
   * Get unsynced scales (temporary scales).
   *
   * @return List of devices that are not yet synced with the server
   */
  suspend fun getUnsyncedScales(): List<Device>

  /**
   * Check if a scale exists by MAC address.
   *
   * @param mac The MAC address to check
   * @return True if the scale exists, false otherwise
   */
  suspend fun scaleExistsByMac(mac: String): Boolean

  /**
   * Get a scale by broadcast ID.
   *
   * @param broadcastId The broadcast ID to search for
   * @param accountId The account ID to filter by
   * @return The device if found, null otherwise
   */
  suspend fun getScaleByBroadcastId(broadcastId: String, accountId: String): Device?

  /**
   * Get a scale by MAC address.
   *
   * @param mac The MAC address to search for
   * @return The device if found, null otherwise
   */
  suspend fun getScaleByMac(mac: String): Device?

  /**
   * Get the current account ID.
   *
   * @return The current account ID, or null if not set
   */
  suspend fun getCurrentAccountId(): String?

  /**
   * Check if the service is initialized with an account.
   *
   * @return True if an account ID is set, false otherwise
   */
  suspend fun isInitialized(): Boolean

  /**
   * Update scale preferences for a specific device.
   *
   * @param deviceId The ID of the device
   * @param preferences The preferences to update
   * @return True if successful, false otherwise
   */
  suspend fun updateScalePreferences(
    deviceId: String,
    preferences: R4ScalePreferenceApiModel,
  ): Boolean

  suspend fun updateScalePreferencesByMac(
    macAddress: String,
    preferences: R4ScalePreferenceApiModel
  ): Boolean

  /**
   * Get scale token from the API.
   * @return The scale token as a string.
   */
  suspend fun getScaleToken(isR4: Boolean = true): String?

  fun weightOnlyModeDismissAlert(onConfirm: () -> Unit)

  fun updateWeightOnlyModeAlertShown(isAlertShown: Boolean)

  /**
   * Get the current setup progress state.
   * @return true if setup is in progress, false otherwise
   */
  fun isSetupInProgress(): Boolean

  /**
   * Set the setup progress state.
   * @param inProgress true if setup is in progress, false if setup is complete or not started
   */
  fun setSetupInProgress(inProgress: Boolean)
}
