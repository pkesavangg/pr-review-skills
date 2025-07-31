package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleMetaDataApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing device data operations (CRUD only, no sync logic)
 */
interface IDeviceRepository {
  // DB operations
  fun getDevices(accountId: String, filterDeleted: Boolean = true): Flow<List<Device>>

  fun getDevice(deviceId: String): Flow<Device?>

  suspend fun updateDevice(device: Device, accountId: String)

  /**
   * Save a device to the local database.
   * @param device The device to save. Must have a valid accountId.
   * @throws IllegalArgumentException if accountId is blank
   */
  suspend fun saveDeviceToDb(device: Device, accountId: String)

  suspend fun deleteDeviceFromDb(deviceId: String)

  fun deviceExistsByBroadcastId(broadcastId: String): Flow<Boolean>

  fun deviceExistsByMac(mac: String): Flow<Boolean>

  fun deviceExistsByPeripheralId(peripheralId: String): Flow<Boolean>

  fun getDeviceByBroadcastId(broadcastId: String): Flow<Device?>

  fun getDeviceByMac(mac: String): Flow<Device?>

  fun getDeviceByPeripheralId(peripheralId: String): Flow<Device?>

  suspend fun updateDeviceNickname(
    deviceId: String,
    nickname: String,
  ): Device

  suspend fun getUnsyncedDevices(): List<Device>

  suspend fun markDeviceSynced(
    deviceId: String,
    isSynced: Boolean,
  )

  // API operations

  /**
   * Gets all devices from the API for the given account.
   */
  suspend fun getDevicesFromApi(accountId: String): List<Device>

  /**
   * Saves a device to the API for the given account.
   */
  suspend fun saveDeviceToApi(
    device: Device,
    accountId: String,
  ): Device

  suspend fun deleteDeviceFromApi(deviceId: String): Boolean

  /**
   * Save scale preferences to the API.
   */
  suspend fun saveScalePreferencesToApi(preferences: R4ScalePreferenceApiModel): R4ScalePreferenceApiModel

  /**
   * Save scale metadata to the API.
   */
  suspend fun saveScaleMetaDataToApi(
    deviceId: String,
    metaData: ScaleMetaDataApiModel,
  ): Boolean

  /**
   * Get scale token from the API.
   * @return The scale token as a string.
   */
  suspend fun getScaleTokenFromApi(): String
}
