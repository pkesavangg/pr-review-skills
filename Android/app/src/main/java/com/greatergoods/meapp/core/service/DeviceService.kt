package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.device.R4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.repository.IDeviceRepository
import com.greatergoods.meapp.domain.repository.IDeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing device/scale data operations.
 * Provides a centralized way to access and manage scale data with automatic synchronization.
 */
@Singleton
class DeviceService
@Inject
constructor(
  private val deviceRepository: IDeviceRepository,
) : IDeviceService {
  private val tag = "DeviceService"

  /**
   * StateFlow containing the current list of saved scales.
   * This is the main source of truth for scale data in the app.
   */
  private val _savedScales = MutableStateFlow<List<Device>>(emptyList())

  /**
   * Flow containing the current list of saved scales.
   */
  override val savedScales: Flow<List<Device>> = _savedScales.asStateFlow()

  // Internal scope for launching long-lived jobs
  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override suspend fun onDeviceUpdate(device: Device) {
    val scale = _savedScales.value.find { it.device?.broadcastId == device.device?.broadcastId }
    if (scale != null) {
      _savedScales.value = _savedScales.value.map {
        if (it.device?.broadcastId == device.device?.broadcastId) {
          scale
        } else
          it
      }
    }
  }

  /**
   * Current account ID for filtering devices.
   */
  private var currentAccountId: String? = null
  private var deviceCollectionJob: Job? = null

  init {
    AppLog.d(tag, "DeviceService initialized")
    // Note: We don't call syncScales here as we need an account ID first
    // This will be called when setAccountId is called
  }

  /**
   * Set the current account ID and initialize device data for that account.
   * This should be called when the user logs in or switches accounts.
   *
   * @param accountId The account ID to set
   */
  override suspend fun setAccountId(accountId: String) {
    AppLog.d(tag, "Setting account ID: $accountId")
    currentAccountId = accountId
    // Cancel any existing collector job
    deviceCollectionJob?.cancel()

    // Launch a new job to stay on the flow
    deviceCollectionJob = repositoryScope.launch {
      deviceRepository.getDevices(accountId).collect { devices ->
        _savedScales.value = devices
      }
    }
    // Sync devices from API and local DB
    syncDevices()
  }

  /**
   * Clear the current account data.
   * This should be called when the user logs out.
   */
  override suspend fun clearAccountData() {
    AppLog.d(tag, "Clearing account data")
    currentAccountId = null
    _savedScales.value = emptyList()
  }

  /**
   * Unified sync method for device operations and initial sync.
   * - If called with no arguments, performs initial sync: fetches from API, merges with local, updates StateFlow.
   * - If called with device lists, processes add/update/delete operations and updates StateFlow.
   *
   * @param newOrUpdatedDevices Devices to add or update (optional)
   * @param deletedDevices Devices to delete (optional)
   */
  override suspend fun syncDevices(
    newOrUpdatedDevices: List<Device>,
    deletedDevices: List<Device>
  ) {
    if (currentAccountId == null) return
    val tag = "DeviceService-syncDevices"
    try {
      // 1. Get unsynced local devices
      val unsyncedDevices = deviceRepository.getUnsyncedDevices().toMutableList()

      // 2. Add new/updated and deleted devices to the unsynced list
      newOrUpdatedDevices.forEach { device ->
        unsyncedDevices.removeAll { it.id == device.id }
        unsyncedDevices.add(0, device.copy(hasServerID = false))
      }
      deletedDevices.forEach { device ->
        unsyncedDevices.removeAll { it.id == device.id }
        unsyncedDevices.add(0, device.copy(hasServerID = false))
      }


      for (device in unsyncedDevices) {
        try {
          if (deletedDevices.any { it.id == device.id }) {
            deviceRepository.deleteDeviceFromApi(device.id)
            deviceRepository.deleteDeviceFromDb(device.id)
            AppLog.d(tag, "Device deleted from API and DB: ${device.id}")
          } else {
            deviceRepository.saveDeviceToDb(device, accountId = currentAccountId!!)
            val syncedDevice = deviceRepository.saveDeviceToApi(device, currentAccountId!!)
            deviceRepository.markDeviceSynced(device.id, true)
            AppLog.d(tag, "Device synced to API: ${syncedDevice.id}")
          }
        } catch (e: Exception) {
          AppLog.e(tag, "Error syncing device ${device.id}", e.toString())
          deviceRepository.markDeviceSynced(device.id, false)
        }
      }

      // 4. Fetch latest devices from API and update local DB
      AppLog.d(tag, "Fetching latest devices from API for account: $currentAccountId")
      val apiDevices = deviceRepository.getDevicesFromApi(currentAccountId!!)
      AppLog.d(tag, "Fetched ${apiDevices.size} devices from API")

      val processedDevices = apiDevices.map { apiDevice ->
        val existingDevice = try {
          when {
            apiDevice.device?.macAddress?.isNotEmpty() == true -> deviceRepository.getDeviceByMac(apiDevice.device.macAddress)
              .first()

            !apiDevice.device?.broadcastId.isNullOrEmpty() -> deviceRepository.getDeviceByBroadcastId(apiDevice.device.broadcastId!!)
              .first()

            else -> null
          }
        } catch (e: Exception) {
          null
        }
        if (existingDevice != null && !apiDevice.hasServerID) {
          apiDevice.copy(id = existingDevice.id, hasServerID = existingDevice.hasServerID)
        } else {
          apiDevice.copy(hasServerID = apiDevice.hasServerID)
        }
      }
      for (device in processedDevices) {
        deviceRepository.saveDeviceToDb(device, accountId = currentAccountId!!)
      }
      AppLog.d(tag, "Saved API devices to DB")
    } catch (e: Exception) {
      AppLog.e(tag, "Error in syncDevices", e.toString())
    }
  }

  /**
   * Save a new scale or update an existing one using syncDevices.
   * If the scale is not synced, it will be marked as temporary and synced later.
   *
   * @param device The device to save
   */
  override suspend fun saveScale(device: Device) {
    AppLog.d(tag, "saveScale (via syncDevices): ${device.id}")
    syncDevices(newOrUpdatedDevices = listOf(device))
  }

  /**
   * Delete a scale from both local database and API using syncDevices.
   *
   * @param deviceId The ID of the device to delete
   */
  override suspend fun deleteScale(deviceId: String) {
    AppLog.d(tag, "deleteScale (via syncDevices): $deviceId")
    val device = deviceRepository.getDevice(deviceId).first()
    if (device != null) {
      syncDevices(deletedDevices = listOf(device))
    } else {
      AppLog.w(tag, "Device not found for delete: $deviceId")
    }
  }

  /**
   * Update a scale's nickname.
   *
   * @param deviceId The ID of the device
   * @param nickname The new nickname
   */
  override suspend fun updateScaleNickname(
    deviceId: String,
    nickname: String,
  ) {
    AppLog.d(tag, "Updating scale nickname: $deviceId -> $nickname")
    try {
      deviceRepository.updateDeviceNickname(deviceId, nickname)
      AppLog.d(tag, "Scale nickname updated successfully")
    } catch (e: Exception) {
      AppLog.e(tag, "Error updating scale nickname", e.toString())
    }
  }

  /**
   * Update scale preferences for a specific device.
   *
   * @param deviceId The ID of the device
   * @param preferences The preferences to update
   * @return True if successful, false otherwise
   */
  override suspend fun updateScalePreferences(
    deviceId: String,
    preferences: R4ScalePreferenceApiModel,
  ): Boolean {
    AppLog.d(tag, "Updating scale preferences for device: $deviceId")
    return try {
      val updatedPreference =
        preferences.copy(
          wifiFotaScheduleTime = 0,
          tzOffset = getTimeZoneInMinutes(),
        )
      // TODO("Update preferences to the scale via ggBluetoothPlugin")
      // Save preferences to API
      deviceRepository.saveScalePreferencesToApi(updatedPreference)
      AppLog.d(tag, "Scale preferences updated successfully")
      true
    } catch (e: Exception) {
      AppLog.e(tag, "Error updating scale preferences", e.toString())
      false
    }
  }

  /**
   * Get a specific scale by its ID.
   *
   * @param deviceId The ID of the device
   * @return The device if found, null otherwise
   */
  override suspend fun getScale(deviceId: String): Device? =
    try {
      deviceRepository.getDevice(deviceId).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale", e.toString())
      null
    }

  /**
   * Get scales by type.
   *
   * @param deviceType The type of devices to get
   * @return List of devices of the specified type
   */
  override suspend fun getScalesByType(deviceType: String): List<Device> =
    _savedScales.value.filter { it.deviceType == deviceType }

  /**
   * Get connected scales.
   *
   * @return List of currently connected devices
   */
  override suspend fun getConnectedScales(): List<Device> =
    _savedScales.value.filter { it.connectionStatus == BLEStatus.CONNECTED }

  /**
   * Get unsynced scales (temporary scales).
   *
   * @return List of devices that are not yet synced with the server
   */
  override suspend fun getUnsyncedScales(): List<Device> = _savedScales.value.filter { !it.hasServerID }

  /**
   * Check if a scale exists by broadcast ID.
   *
   * @param broadcastId The broadcast ID to check
   * @return True if the scale exists, false otherwise
   */
  override suspend fun scaleExistsByBroadcastId(broadcastId: String): Boolean =
    try {
      deviceRepository.deviceExistsByBroadcastId(broadcastId).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error checking scale existence by broadcast ID", e.toString())
      false
    }

  /**
   * Check if a scale exists by MAC address.
   *
   * @param mac The MAC address to check
   * @return True if the scale exists, false otherwise
   */
  override suspend fun scaleExistsByMac(mac: String): Boolean =
    try {
      deviceRepository.deviceExistsByMac(mac).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error checking scale existence by MAC", e.toString())
      false
    }

  /**
   * Get a scale by broadcast ID.
   *
   * @param broadcastId The broadcast ID to search for
   * @return The device if found, null otherwise
   */
  override suspend fun getScaleByBroadcastId(broadcastId: String): Device? =
    try {
      deviceRepository.getDeviceByBroadcastId(broadcastId).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale by broadcast ID", e.toString())
      null
    }

  /**
   * Get a scale by MAC address.
   *
   * @param mac The MAC address to search for
   * @return The device if found, null otherwise
   */
  override suspend fun getScaleByMac(mac: String): Device? =
    try {
      deviceRepository.getDeviceByMac(mac).first()
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale by MAC", e.toString())
      null
    }

  /**
   * Get the current account ID.
   *
   * @return The current account ID, or null if not set
   */
  override suspend fun getCurrentAccountId(): String? = currentAccountId

  /**
   * Check if the service is initialized with an account.
   *
   * @return True if an account ID is set, false otherwise
   */
  override suspend fun isInitialized(): Boolean = currentAccountId != null

  /**
   * Get scale token from the API.
   * @return The scale token as a string.
   */
  override suspend fun getScaleToken(): String {
    AppLog.d(tag, "Getting scale token from API")
    return try {
      val token = deviceRepository.getScaleTokenFromApi()
      AppLog.d(tag, "Scale token retrieved successfully")
      token
    } catch (e: Exception) {
      AppLog.e(tag, "Error getting scale token", e.toString())
      throw e
    }
  }

  private fun getTimeZoneInMinutes(): Int {
    val timeZone = java.util.TimeZone.getDefault()
    val offsetInMillis = timeZone.getOffset(System.currentTimeMillis())
    return offsetInMillis / (60 * 1000) // convert milliseconds to minutes
  }
}
