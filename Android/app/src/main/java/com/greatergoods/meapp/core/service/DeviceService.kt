package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.repository.IDeviceRepository
import com.greatergoods.meapp.domain.repository.IDeviceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing device/scale data operations.
 * Provides a centralized way to access and manage scale data with automatic synchronization.
 */
@Singleton
class DeviceService @Inject constructor(
    private val deviceRepository: IDeviceRepository,
) : IDeviceService {
    private val tag = "DeviceService"

    /**
     * StateFlow containing the current list of saved scales.
     * This is the main source of truth for scale data in the app.
     */
    private val _savedScales = MutableStateFlow<List<Device>>(emptyList())
    
    /**
     * Flow containing the current list of saved scales, filtered to exclude AppSync scales.
     * TODO: Include AppSync scales after AppSync support is implemented
     */
    override val savedScales: Flow<List<Device>> = _savedScales.asStateFlow()
        .map { devices ->
            devices.filter { device ->
                // Filter out AppSync scales for now
                device.deviceType?.lowercase() != "appsync"
            }
        }

    /**
     * Current account ID for filtering devices.
     */
    private var currentAccountId: String? = null

    init {
        AppLog.d(tag, "DeviceService initialized")
        // Note: We don't call syncScales here as we need an account ID first
        // This will be called when setAccountId is called
    }

    /**
     * Set the current account ID and initialize scale data for that account.
     * This should be called when the user logs in or switches accounts.
     *
     * @param accountId The account ID to set
     */
    override suspend fun setAccountId(accountId: String) {
        AppLog.d(tag, "Setting account ID: $accountId")
        currentAccountId = accountId
        // Sync scales from API
        syncScales()
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
     * Sync scales from the API and update the local database.
     * This method fetches scales from the server and merges them with local unsynced scales.
     */
    override suspend fun syncScales() {
        if (currentAccountId == null) {
            AppLog.w(tag, "Cannot sync scales: no account ID set")
            return
        }

        AppLog.d(tag, "Starting scale synchronization for account: $currentAccountId")
        try {
            // 1. Fetch from API
            val apiDevices = deviceRepository.getDevicesFromApi(currentAccountId!!)
            AppLog.d(tag, "Fetched ${apiDevices.size} devices from API")

            // 2. Save API devices to DB with correct accountId
            for (device in apiDevices) {
                // Ensure the device has the correct accountId
                val deviceWithAccountId = device.copy(accountId = currentAccountId!!)
                AppLog.d(tag, "Saving device ${device.id} with accountId: ${deviceWithAccountId.accountId}")
                deviceRepository.saveDeviceToDb(deviceWithAccountId)
            }
            AppLog.d(tag, "Saved API devices to DB")

            // 3. Get unsynced local devices
            val unsyncedDevices = deviceRepository.getUnsyncedDevices()
            AppLog.d(tag, "Found ${unsyncedDevices.size} unsynced local devices")

            // 4. Merge: API devices + unsynced local devices (not in API)
            val apiDeviceIds = apiDevices.map { it.id }.toSet()
            val mergedDevices = apiDevices.toMutableList()
            mergedDevices.addAll(unsyncedDevices.filter { !apiDeviceIds.contains(it.id) })

            // 5. Update StateFlow
            _savedScales.value = mergedDevices
            AppLog.d(tag, "Scale sync completed. Total devices: ${mergedDevices.size}")
        } catch (e: Exception) {
            AppLog.e(tag, "Error syncing scales", e.toString())
            // Return local devices if API fails
            try {
                val devices = deviceRepository.getDevices(currentAccountId!!).first()
                _savedScales.value = devices
                AppLog.d(tag, "Using local devices after sync failure. Total devices: ${devices.size}")
            } catch (localError: Exception) {
                AppLog.e(tag, "Error getting local devices after sync failure", localError.toString())
            }
        }
    }

    /**
     * Save a new scale or update an existing one.
     * If the scale is not synced, it will be marked as temporary and synced later.
     *
     * @param device The device to save
     */
    override suspend fun saveScale(device: Device) {
        AppLog.d(tag, "Saving scale: ${device.id}")
        try {
            deviceRepository.saveDeviceToDb(device)
            AppLog.d(tag, "Scale saved to DB: ${device.id}")
            // Try to sync with API
            try {
                val syncedDevice = deviceRepository.saveDeviceToApi(device, currentAccountId!!)
                deviceRepository.markDeviceSynced(device.id, true)
                AppLog.d(tag, "Scale synced to API: ${syncedDevice.id}")
            } catch (apiError: Exception) {
                deviceRepository.markDeviceSynced(device.id, false)
                AppLog.w(tag, "Failed to sync scale to API, marked as unsynced: ${device.id}")
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Error saving scale", e.toString())
        }
    }

    /**
     * Delete a scale from both local database and API.
     *
     * @param deviceId The ID of the device to delete
     */
    override suspend fun deleteScale(deviceId: String) {
        AppLog.d(tag, "Deleting scale: $deviceId")
        try {
            // First delete from API
            try {
                deviceRepository.deleteDeviceFromApi(deviceId)
                AppLog.d(tag, "Scale deleted from API successfully")
            } catch (apiError: Exception) {
                AppLog.w(tag, "Failed to delete scale from API, will retry later: $deviceId")
            }
            // Then delete from local database
            deviceRepository.deleteDeviceFromDb(deviceId)
            AppLog.d(tag, "Scale deleted from local database successfully")
        } catch (e: Exception) {
            AppLog.e(tag, "Error deleting scale", e.toString())
        }
    }

    /**
     * Update a scale's nickname.
     *
     * @param deviceId The ID of the device
     * @param nickname The new nickname
     */
    override suspend fun updateScaleNickname(deviceId: String, nickname: String) {
        AppLog.d(tag, "Updating scale nickname: $deviceId -> $nickname")
        try {
            deviceRepository.updateDeviceNickname(deviceId, nickname)
            AppLog.d(tag, "Scale nickname updated successfully")
        } catch (e: Exception) {
            AppLog.e(tag, "Error updating scale nickname", e.toString())
        }
    }

    /**
     * Get a specific scale by its ID.
     *
     * @param deviceId The ID of the device
     * @return The device if found, null otherwise
     */
    override suspend fun getScale(deviceId: String): Device? {
        return try {
            deviceRepository.getDevice(deviceId).first()
        } catch (e: Exception) {
            AppLog.e(tag, "Error getting scale", e.toString())
            null
        }
    }

    /**
     * Get scales by type.
     *
     * @param deviceType The type of devices to get
     * @return List of devices of the specified type
     */
    override suspend fun getScalesByType(deviceType: String): List<Device> {
        return _savedScales.value.filter { it.deviceType == deviceType }
    }

    /**
     * Get connected scales.
     *
     * @return List of currently connected devices
     */
    override suspend fun getConnectedScales(): List<Device> {
        return _savedScales.value.filter { it.isConnected }
    }

    /**
     * Get unsynced scales (temporary scales).
     *
     * @return List of devices that are not yet synced with the server
     */
    override suspend fun getUnsyncedScales(): List<Device> {
        return _savedScales.value.filter { !it.isSynced }
    }

    /**
     * Check if a scale exists by broadcast ID.
     *
     * @param broadcastId The broadcast ID to check
     * @return True if the scale exists, false otherwise
     */
    override suspend fun scaleExistsByBroadcastId(broadcastId: String): Boolean {
        return try {
            deviceRepository.deviceExistsByBroadcastId(broadcastId).first()
        } catch (e: Exception) {
            AppLog.e(tag, "Error checking scale existence by broadcast ID", e.toString())
            false
        }
    }

    /**
     * Check if a scale exists by MAC address.
     *
     * @param mac The MAC address to check
     * @return True if the scale exists, false otherwise
     */
    override suspend fun scaleExistsByMac(mac: String): Boolean {
        return try {
            deviceRepository.deviceExistsByMac(mac).first()
        } catch (e: Exception) {
            AppLog.e(tag, "Error checking scale existence by MAC", e.toString())
            false
        }
    }

    /**
     * Get a scale by broadcast ID.
     *
     * @param broadcastId The broadcast ID to search for
     * @return The device if found, null otherwise
     */
    override suspend fun getScaleByBroadcastId(broadcastId: String): Device? {
        return try {
            deviceRepository.getDeviceByBroadcastId(broadcastId).first()
        } catch (e: Exception) {
            AppLog.e(tag, "Error getting scale by broadcast ID", e.toString())
            null
        }
    }

    /**
     * Get a scale by MAC address.
     *
     * @param mac The MAC address to search for
     * @return The device if found, null otherwise
     */
    override suspend fun getScaleByMac(mac: String): Device? {
        return try {
            deviceRepository.getDeviceByMac(mac).first()
        } catch (e: Exception) {
            AppLog.e(tag, "Error getting scale by MAC", e.toString())
            null
        }
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
}
