package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.api.device.R4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.storage.Device
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing device/scale data operations.
 * Provides a centralized way to access and manage scale data with automatic synchronization.
 */
interface IDeviceService {
  /**
   * StateFlow containing the current list of saved scales.
   * This is the main source of truth for scale data in the app.
   */
  val savedScales: Flow<List<Device>>

  /**
   * Set the current account ID and initialize scale data for that account.
   * This should be called when the user logs in or switches accounts.
   *
   * @param accountId The account ID to set
   */
  suspend fun setAccountId(accountId: String)

  /**
   * Clear the current account data.
   * This should be called when the user logs out.
   */
  suspend fun clearAccountData()

  /**
   * Sync scales from the API and update the local database.
   * This method fetches scales from the server and merges them with local unsynced scales.
   */
  suspend fun syncScales()

  /**
   * Save a new scale or update an existing one.
   * If the scale is not synced, it will be marked as temporary and synced later.
   *
   * @param device The device to save
   */
  suspend fun saveScale(device: Device)

  /**
   * Delete a scale from both local database and API.
   *
   * @param deviceId The ID of the device to delete
   */
  suspend fun deleteScale(deviceId: String)

  /**
   * Update a scale's nickname.
   *
   * @param deviceId The ID of the device
   * @param nickname The new nickname
   */
  suspend fun updateScaleNickname(
    deviceId: String,
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
   * Check if a scale exists by broadcast ID.
   *
   * @param broadcastId The broadcast ID to check
   * @return True if the scale exists, false otherwise
   */
  suspend fun scaleExistsByBroadcastId(broadcastId: String): Boolean

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
   * @return The device if found, null otherwise
   */
  suspend fun getScaleByBroadcastId(broadcastId: String): Device?

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
}
