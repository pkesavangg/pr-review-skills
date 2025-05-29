package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the device table.
 * Provides methods to interact with the device data in the database.
 */
@Dao
interface DeviceDao {
    /**
     * Insert a new device into the database.
     * @param device The device entity to insert
     * @return The row ID of the inserted device
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    /**
     * Get all devices from the database.
     * @return A Flow of all devices
     */
    @Query("SELECT * FROM device")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    /**
     * Get devices by type from the database.
     * @param deviceType The device type
     * @return A Flow of devices of the specified type
     */
    @Query("SELECT * FROM device WHERE deviceType = :deviceType")
    fun getDevicesByType(deviceType: String): Flow<List<DeviceEntity>>

    /**
     * Delete a device from the database.
     * @param device The device entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun deleteDevice(device: DeviceEntity)

    /**
     * Get a device by its ID.
     * @param deviceId The device ID
     * @return The device entity if found, null otherwise
     */
    @Query("SELECT * FROM device WHERE id = :deviceId")
    suspend fun getDeviceById(deviceId: String): DeviceEntity?

    /**
     * Get all devices for a specific user.
     * @param accountId The user ID
     * @return A Flow of all devices for the user
     */
    @Query("SELECT * FROM device WHERE accountId = :accountId")
    fun getDevicesByAccountId(accountId: String): Flow<List<DeviceEntity>>

    /**
     * Get all unsynced devices.
     * @return A Flow of all unsynced devices
     */
    @Query("SELECT * FROM device WHERE isSynced = 0")
    fun getUnsyncedDevices(): Flow<List<DeviceEntity>>

    /**
     * Get devices by device type for a specific user.
     * @param accountId The user ID
     * @param deviceType The device type
     * @return A Flow of devices of the specified type
     */
    @Query("SELECT * FROM device WHERE accountId = :accountId AND deviceType = :deviceType")
    fun getDevicesByType(accountId: String, deviceType: String): Flow<List<DeviceEntity>>

    /**
     * Get connected devices for a specific user.
     * @param accountId The user ID
     * @return A Flow of connected devices
     */
    @Query("SELECT * FROM device WHERE accountId = :accountId AND isConnected = 1")
    fun getConnectedDevices(accountId: String): Flow<List<DeviceEntity>>

    /**
     * Update device connection status.
     * @param id The device ID
     * @param isConnected The new connection status
     * @return The number of rows updated
     */
    @Query("UPDATE device SET isConnected = :isConnected WHERE id = :id")
    suspend fun updateConnectionStatus(id: String, isConnected: Boolean): Int

    /**
     * Update device sync status.
     * @param id The device ID
     * @param isSynced The new sync status
     * @return The number of rows updated
     */
    @Query("UPDATE device SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean): Int

    /**
     * Update device WiFi configuration status.
     * @param id The device ID
     * @param isWifiConfigured The new WiFi configuration status
     * @return The number of rows updated
     */
    @Query("UPDATE device SET isWifiConfigured = :isWifiConfigured WHERE id = :id")
    suspend fun updateWifiConfigStatus(id: String, isWifiConfigured: Boolean): Int

    /**
     * Update device deletion status.
     * @param id The device ID
     * @param isDeleted The new deletion status
     * @return The number of rows updated
     */
    @Query("UPDATE device SET isDeleted = :isDeleted WHERE id = :id")
    suspend fun updateDeletionStatus(id: String, isDeleted: Boolean): Int

    /**
     * Get device by MAC address.
     * @param mac The MAC address
     * @return The device entity if found, null otherwise
     */
    @Query("SELECT * FROM device WHERE mac = :mac")
    suspend fun getDeviceByMac(mac: String): DeviceEntity?

    /**
     * Get device by peripheral identifier.
     * @param peripheralId The peripheral identifier
     * @return The device entity if found, null otherwise
     */
    @Query("SELECT * FROM device WHERE peripheralIdentifier = :peripheralId")
    suspend fun getDeviceByPeripheralId(peripheralId: String): DeviceEntity?

    /**
     * Get device by broadcast ID.
     * @param broadcastId The broadcast ID
     * @return The device entity if found, null otherwise
     */
    @Query("SELECT * FROM device WHERE broadcastId = :broadcastId")
    suspend fun getDeviceByBroadcastId(broadcastId: String): DeviceEntity?

    /**
     * Update device nickname.
     * @param id The device ID
     * @param nickname The new nickname
     * @return The number of rows updated
     */
    @Query("UPDATE device SET nickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: String, nickname: String): Int

    /**
     * Update device token.
     * @param id The device ID
     * @param token The new token
     * @return The number of rows updated
     */
    @Query("UPDATE device SET token = :token WHERE id = :id")
    suspend fun updateToken(id: String, token: String): Int

    /**
     * Delete all devices for a specific user.
     * @param accountId The user ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM device WHERE accountId = :accountId")
    suspend fun deleteAllDevicesForAccount(accountId: String): Int
} 