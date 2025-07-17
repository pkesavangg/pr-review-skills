package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.device.R4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.api.device.toR4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.repository.IDeviceRepository
import com.greatergoods.meapp.domain.repository.IDeviceService
import com.greatergoods.meapp.features.common.enums.ScaleSetupType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
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

  // Internal scope for launching long-lived jobs
  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _pairedScales = MutableStateFlow<List<Device>>(emptyList())
  override val pairedScales = _pairedScales.asStateFlow()

  override suspend fun onDeviceUpdate(device: Device) {
    val scale = _pairedScales.value.find { it.device?.macAddress == device.device?.macAddress }
    if (scale != null) {
      _pairedScales.value = _pairedScales.value.map {
        if (it.device?.macAddress == scale.device?.macAddress) {
          device
        } else
          it
      }
    }
  }

  override suspend fun updateDevice(device: Device) {
    deviceRepository.updateDevice(device, currentAccountId!!)
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

  override suspend fun getScales(accountId: String?): Flow<List<Device>> {
    val resolvedAccountId = accountId ?: this.currentAccountId
    ?: throw IllegalArgumentException("No account ID provided")

    val devices = deviceRepository.getDevices(resolvedAccountId)
    return devices
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

    deviceCollectionJob = repositoryScope.launch {
      // Capture previous connection status
      val previousStatusMap = _pairedScales.value.associateBy({ it.id }, { it.connectionStatus })

      deviceRepository.getDevices(accountId).collect { devices ->
        val updatedDevices = devices.map { device ->
          val connectionStatus = previousStatusMap[device.id] ?: device.connectionStatus
          device.copy(connectionStatus = connectionStatus)
        }
        _pairedScales.value = updatedDevices
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
    tempDevice: Device?
  ) {
    val tag = "DeviceService-syncDevices"
    if (currentAccountId == null) return

    val unsyncedDevices = mutableListOf<Device>()
    val syncedDevicesToStore = mutableListOf<Device>()

    try {
      // 1. Get locally stored devices
      val storedDevices = deviceRepository.getDevices(currentAccountId!!, false).first().toMutableList()

      // 2. Inject a temporary new device if passed
      tempDevice?.let {
        it.copy(isSynced = false)
        storedDevices.add(it)
      }

      // 3. Classify devices
      val devicesToSync = storedDevices.filter { device ->
        (!device.isSynced || (device.preferences != null && !device.preferences.isSynced)) && !device.isDeleted
      }
      val deletedDevices = storedDevices.filter { it.isDeleted }
      storedDevices.filter {
        !it.isDeleted && it.isSynced && (it.preferences?.isSynced != false)
      }

      // 4. Sync new/updated devices
      for (device in devicesToSync) {
        try {
          var savedDevice = deviceRepository.saveDeviceToApi(device, currentAccountId!!)
          savedDevice = savedDevice.copy(isSynced = true)

          // Sync preference if needed
          if (savedDevice.deviceType == ScaleSetupType.BtWifiR4.value && savedDevice.preferences != null) {
            try {
              val updatedPref = savedDevice.preferences.copy(
                isSynced = true,
                id = savedDevice.id,
              )
              deviceRepository.saveScalePreferencesToApi(updatedPref.toR4ScalePreferenceApiModel())
              savedDevice = savedDevice.copy(preferences = updatedPref)
            } catch (e: Exception) {
              AppLog.e(tag, "Error syncing preference for ${savedDevice.id}", e.toString())
              savedDevice = savedDevice.copy(
                preferences = savedDevice.preferences?.copy(isSynced = false),
              )
            }
          }
          syncedDevicesToStore.add(savedDevice)
        } catch (e: Exception) {
          AppLog.e(tag, "Error syncing device ${device.id}", e.toString())
          unsyncedDevices.add(
            device.copy(
              isSynced = false,
            ),
          )
        }
      }

      // 5. Delete devices marked for deletion
      for (device in deletedDevices) {
        try {
          deviceRepository.deleteDeviceFromApi(device.id)
          deviceRepository.deleteDeviceFromDb(device.id)
        } catch (e: Exception) {
          AppLog.e(tag, "Error deleting device ${device.id}", e.toString())
          unsyncedDevices.add(device)
        }
      }
    } catch (e: Exception) {
      AppLog.e(tag, "General sync error for account $currentAccountId", e.toString())
      tempDevice?.let {
        unsyncedDevices.add(
          it.copy(
            id = UUID.randomUUID().toString(),
            isSynced = false,
          ),
        )
      }
    }

    // 6. Get fresh data from API and merge with unsynced
    val finalDevices = try {
      val apiDevices = deviceRepository.getDevicesFromApi(currentAccountId!!)
      apiDevices.map { it.copy(isSynced = true) } + unsyncedDevices.map { it.copy(isSynced = false) }
    } catch (e: Exception) {
      AppLog.e(tag, "Error fetching devices from API", e.toString())
      syncedDevicesToStore.map { it.copy(isSynced = true) } + unsyncedDevices.map { it.copy(isSynced = false) }
    }

    // 7. Store updated device list locally
    try {
      finalDevices.forEach { device ->
        deviceRepository.saveDeviceToDb(device, currentAccountId!!)
      }
    } catch (e: Exception) {
      AppLog.e(tag, "Error storing final device list", e.toString())
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
    syncDevices(device)
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
      if (!device.isSynced) {
        deviceRepository.deleteDeviceFromDb(deviceId)
        syncDevices()
      } else {
        syncDevices(device.copy(isDeleted = true))
      }
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
    _pairedScales.value.filter { it.deviceType == deviceType }

  /**
   * Get connected scales.
   *
   * @return List of currently connected devices
   */
  override suspend fun getConnectedScales(): List<Device> =
    _pairedScales.value.filter { it.connectionStatus == BLEStatus.CONNECTED }

  /**
   * Get unsynced scales (temporary scales).
   *
   * @return List of devices that are not yet synced with the server
   */
  override suspend fun getUnsyncedScales(): List<Device> = _pairedScales.value.filter { !it.hasServerID }

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
