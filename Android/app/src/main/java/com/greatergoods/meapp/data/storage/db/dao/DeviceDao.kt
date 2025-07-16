package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greatergoods.meapp.data.storage.db.entity.device.BodyScaleEntity
import com.greatergoods.meapp.data.storage.db.entity.device.BpmEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceDetails
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceMetaDataEntity
import com.greatergoods.meapp.data.storage.db.entity.device.R4ScalePreferenceEntity
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
   * Delete a device from the database.
   * @param device The device entity to delete
   * @return The number of rows deleted
   */
  @Delete
  suspend fun deleteDevice(device: DeviceEntity)

  /**
   * Update device sync status.
   * @param id The device ID
   * @param isSynced The new sync status
   * @return The number of rows updated
   */
  @Query("UPDATE device SET isSynced = :isSynced WHERE id = :id")
  suspend fun updateSyncStatus(
    id: String,
    isSynced: Boolean,
  ): Int

  /**
   * Update device deletion status.
   * @param id The device ID
   * @param isDeleted The new deletion status
   * @return The number of rows updated
   */
  @Query("UPDATE device SET isDeleted = :isDeleted WHERE id = :id")
  suspend fun updateDeletionStatus(
    id: String,
    isDeleted: Boolean,
  ): Int

  /**
   * Get device by MAC address.
   * @param mac The MAC address
   * @return The device entity if found, null otherwise
   */
  @Query("SELECT * FROM device WHERE mac = :mac")
  suspend fun getDeviceByMac(mac: String): DeviceDetails?

  /**
   * Get device by peripheral identifier.
   * @param peripheralId The peripheral identifier
   * @return The device entity if found, null otherwise
   */
  @Query("SELECT * FROM device WHERE peripheralIdentifier = :peripheralId")
  suspend fun getDeviceByPeripheralId(peripheralId: String): DeviceDetails?

  /**
   * Get device by broadcast ID.
   * @param broadcastId The broadcast ID
   * @return The device entity if found, null otherwise
   */
  @Query("SELECT * FROM device WHERE broadcastId = :broadcastId")
  suspend fun getDeviceByBroadcastId(broadcastId: String): DeviceDetails?

  /**
   * Update device nickname.
   * @param id The device ID
   * @param nickname The new nickname
   * @return The number of rows updated
   */
  @Query("UPDATE device SET nickname = :nickname WHERE id = :id")
  suspend fun updateNickname(
    id: String,
    nickname: String,
  ): Int

  /**
   * Update device token.
   * @param id The device ID
   * @param token The new token
   * @return The number of rows updated
   */
  @Query("UPDATE device SET token = :token WHERE id = :id")
  suspend fun updateToken(
    id: String,
    token: String,
  ): Int

  /**
   * Update device hasServerID status.
   * @param id The device ID
   * @param hasServerID The new hasServerID status
   * @return The number of rows updated
   */
  @Query("UPDATE device SET hasServerID = :hasServerID WHERE id = :id")
  suspend fun updateHasServerID(
    id: String,
    hasServerID: Boolean,
  ): Int

  /**
   * Delete all devices for a specific user.
   * @param accountId The user ID
   * @return The number of rows deleted
   */
  @Query("DELETE FROM device WHERE accountId = :accountId")
  suspend fun deleteAllDevicesForAccount(accountId: String): Int

  @Transaction
  @Query("SELECT * FROM device WHERE id = :deviceId")
  suspend fun getDevice(deviceId: String): DeviceDetails?

  @Transaction
  @Query("SELECT * FROM device WHERE accountId = :accountId")
  fun getDevices(accountId: String): Flow<List<DeviceDetails>>

  @Transaction
  @Query("SELECT * FROM device WHERE deviceType = :deviceType AND accountId = :accountId")
  fun getDevicesByTypeWithAccount(
    deviceType: String,
    accountId: String,
  ): Flow<List<DeviceDetails>>

  @Transaction
  @Query("SELECT * FROM device WHERE mac = :mac AND accountId = :accountId")
  suspend fun getDeviceByMacWithAccount(
    mac: String,
    accountId: String,
  ): DeviceDetails?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertScale(scale: BodyScaleEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBpm(bpm: BpmEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMeta(meta: DeviceMetaDataEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertR4Preference(preference: R4ScalePreferenceEntity)

  @Transaction
  suspend fun insertDevice(device: DeviceDetails) {
    insertDevice(device.device)
    device.scale?.let { insertScale(it) }
    device.bpm?.let { insertBpm(it) }
    device.meta?.let { insertMeta(it) }
    device.r4Preference?.let { insertR4Preference(it) }
  }

  @Query("DELETE FROM device WHERE id = :deviceId")
  suspend fun deleteDevice(deviceId: String)

  @Transaction
  suspend fun updateDeviceConnection(
    deviceId: String,
    isConnected: Boolean,
  ) {
    // Update the device's connection status
    // You might want to add a specific query for this
  }

  /**
   * Get all unsynced devices as a List (not Flow).
   * @return A List of all unsynced devices
   */
  @Query("SELECT * FROM device WHERE isSynced = 0")
  suspend fun getUnsyncedDevicesList(): List<DeviceDetails>
}
