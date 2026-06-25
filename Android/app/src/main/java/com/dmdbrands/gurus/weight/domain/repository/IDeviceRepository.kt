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

  suspend fun deleteAllDevicesForAccount(accountId: String)

  suspend fun deleteDeviceFromDb(deviceId: String)

  fun deviceExistsByBroadcastId(broadcastId: String, accountId: String): Flow<Boolean>

  fun deviceExistsByMac(mac: String, accountId: String): Flow<Boolean>

  fun deviceExistsByPeripheralId(peripheralId: String): Flow<Boolean>

  fun getDeviceByBroadcastId(broadcastId: String, accountId: String): Flow<Device?>

  fun getDeviceByMac(mac: String, accountId: String): Flow<Device?>

  fun getDeviceByPeripheralId(peripheralId: String): Flow<Device?>

  suspend fun updateDeviceNickname(
    device: Device,
    nickname: String,
  ): Device

  suspend fun getUnsyncedDevices(): List<Device>

  suspend fun markDeviceSynced(
    deviceId: String,
    isSynced: Boolean,
  )

  suspend fun markDeviceDeleted(
    deviceId: String,
    isDeleted: Boolean,
  )

  /**
   * Reconciles persisted device setup/protocol types against the SKU's known setup type.
   *
   * Legacy Ionic migration could store a stale `type` (e.g. "appsync" for the Bluetooth
   * SKU 0375), which surfaces as the wrong Scale Type. This rewrites `deviceType` and
   * `protocolType` from the SKU lookup for any row that disagrees. Devices with an unknown
   * SKU are left untouched. Idempotent. (MOB-204)
   *
   * @return the number of device rows repaired
   */
  suspend fun repairDeviceTypesFromSku(): Int

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

  // ── Unified /v3/paired-device/ (MOB-378) ─────────────────────────────────

  /** Creates a device via the unified paired-device endpoint. */
  suspend fun createPairedDevice(device: Device, accountId: String): Device

  /** Lists all paired devices from the unified endpoint, optionally filtered. */
  suspend fun getPairedDevices(deviceType: String? = null): List<Device>

  /** Updates a device via the unified paired-device endpoint. */
  suspend fun updatePairedDevice(deviceId: String, device: Device, accountId: String): Device

  /** Deletes a device via the unified paired-device endpoint (returns true on 204). */
  suspend fun deletePairedDevice(deviceId: String): Boolean

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
  suspend fun getScaleTokenFromApi(isR4: Boolean): String
}
