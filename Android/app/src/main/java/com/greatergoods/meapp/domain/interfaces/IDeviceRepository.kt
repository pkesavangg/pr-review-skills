package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.domain.models.Device
import com.greatergoods.meapp.domain.models.DeviceSearchInfo
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing device data operations
 */
interface IDeviceRepository {
    /**
     * Get all devices for a user
     * @param accountId The ID of the user account
     * @return Flow of list of devices
     */
    fun getDevices(accountId: String): Flow<List<Device>>

    /**
     * Get a specific device by its ID
     * @param deviceId The ID of the device
     * @return Flow of the device if found, null otherwise
     */
    fun getDevice(deviceId: String): Flow<Device?>

    /**
     * Save a new device or update an existing one
     * @param device The device to save
     * @return Flow of the saved device
     */
    suspend fun saveDevice(device: Device): Flow<Device>

    /**
     * Delete a device
     * @param deviceId The ID of the device to delete
     * @return Flow of boolean indicating success
     */
    suspend fun deleteDevice(deviceId: String): Flow<Boolean>

    /**
     * Check if a device exists with the given broadcast ID
     * @param broadcastId The broadcast ID to check
     * @return Flow of boolean indicating if device exists
     */
    fun deviceExistsByBroadcastId(broadcastId: String): Flow<Boolean>

    /**
     * Check if a device exists with the given MAC address
     * @param mac The MAC address to check
     * @return Flow of boolean indicating if device exists
     */
    fun deviceExistsByMac(mac: String): Flow<Boolean>

    /**
     * Check if a device exists with the given peripheral identifier
     * @param peripheralId The peripheral identifier to check
     * @return Flow of boolean indicating if device exists
     */
    fun deviceExistsByPeripheralId(peripheralId: String): Flow<Boolean>

    /**
     * Get device by broadcast ID
     * @param broadcastId The broadcast ID to search for
     * @return Flow of the device if found, null otherwise
     */
    fun getDeviceByBroadcastId(broadcastId: String): Flow<Device?>

    /**
     * Get device by MAC address
     * @param mac The MAC address to search for
     * @return Flow of the device if found, null otherwise
     */
    fun getDeviceByMac(mac: String): Flow<Device?>

    /**
     * Get device by peripheral identifier
     * @param peripheralId The peripheral identifier to search for
     * @return Flow of the device if found, null otherwise
     */
    fun getDeviceByPeripheralId(peripheralId: String): Flow<Device?>

    /**
     * Update device nickname
     * @param deviceId The ID of the device
     * @param nickname The new nickname
     * @return Flow of the updated device
     */
    suspend fun updateDeviceNickname(deviceId: String, nickname: String): Flow<Device>

    /**
     * Search for a device by peripheral identifier
     * @param peripheralIdentifier The peripheral identifier to search for
     * @param accountId The ID of the current user account
     * @param userNumber The user number to check against
     * @return Flow of DeviceSearchInfo containing search results
     */
    suspend fun searchDevice(
        peripheralIdentifier: String,
        accountId: String,
        userNumber: String
    ): Flow<DeviceSearchInfo>

    /**
     * Synchronize devices with the remote server
     * @return Flow of list of synchronized devices
     */
    suspend fun syncDevices(): Flow<List<Device>>

    /**
     * Save a device to the remote server
     * @param device The device to save
     * @return Flow of the saved device
     */
    suspend fun syncDeviceWithApi(device: Device): Flow<Device>

    /**
     * Delete a device from the remote server
     * @param deviceId The ID of the device to delete
     * @return Flow of boolean indicating success
     */
    suspend fun deleteDeviceFromApi(deviceId: String): Flow<Boolean>

    /**
     * Get all devices from the remote server
     * @return Flow of list of devices
     */
    suspend fun getDevicesFromApi(): Flow<List<Device>>

    /**
     * Mark a device as temporary by setting its sync status to false
     * This indicates the device is not yet synchronized with the server
     * @param device The device to mark as temporary
     * @return Flow of the updated device
     */
    suspend fun convertToTemporaryDevice(device: Device): Flow<Device>

    /**
     * Remove temporary status from a device by setting its sync status to true
     * This indicates the device is now synchronized with the server
     * @param device The device to update
     * @return Flow of the updated device
     */
    suspend fun removeTemporaryStatus(device: Device): Flow<Device>
}