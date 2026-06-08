package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiException
import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleMetaDataApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModels
import com.dmdbrands.gurus.weight.domain.model.api.device.toPairedDeviceRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toDeviceDetails
import com.dmdbrands.gurus.weight.domain.model.storage.toDeviceDomainModel
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDeviceRepository for managing device data (CRUD only)
 */
@Singleton
class DeviceRepository
@Inject
constructor(
  private val deviceApi: IDeviceAPI,
  private val deviceDao: DeviceDao,
) : IDeviceRepository {

  companion object {
    private const val TAG = "DeviceRepository"
  }

  // DB operations
  override fun getDevices(accountId: String, filterDeleted: Boolean): Flow<List<Device>> =
    deviceDao.getDevices(accountId)
      .map { deviceDetailsList ->
        deviceDetailsList
          .filter { deviceDetails ->
            if (filterDeleted) !deviceDetails.device.isDeleted else true
          }
          .map { deviceDetails ->
            deviceDetails.toDeviceDomainModel()
          }
      }

  override fun getDevice(deviceId: String): Flow<Device?> =
    flow {
      val deviceDetails = deviceDao.getDevice(deviceId)
      emit(deviceDetails?.toDeviceDomainModel())
    }

  override suspend fun updateDevice(device: Device, accountId: String) {
    deviceDao.updateDevice(device.toDeviceDetails(accountId))
  }

  override suspend fun saveDeviceToDb(device: Device, accountId: String) {
    val deviceDetails = device.toDeviceDetails(accountId)
    deviceDao.insertDevice(deviceDetails)
  }

  override suspend fun deleteAllDevicesForAccount(accountId: String){
    deviceDao.deleteAllDevicesForAccount(accountId)
  }

  override suspend fun deleteDeviceFromDb(deviceId: String) {
    deviceDao.deleteDevice(deviceId)
  }

  override fun deviceExistsByBroadcastId(broadcastId: String, accountId: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByBroadcastId(broadcastId, accountId)
      emit(device != null)
    }

  override fun deviceExistsByMac(mac: String, accountId: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByMac(mac, accountId = accountId)
      emit(device != null)
    }

  override fun deviceExistsByPeripheralId(peripheralId: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByPeripheralId(peripheralId)
      emit(device != null)
    }

  override fun getDeviceByBroadcastId(broadcastId: String, accountId: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByBroadcastIdString(broadcastId, accountId)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override fun getDeviceByMac(mac: String, accountId: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByMac(mac, accountId)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override fun getDeviceByPeripheralId(peripheralId: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByPeripheralId(peripheralId)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override suspend fun updateDeviceNickname(
    device: Device,
    nickname: String,
  ): Device {
    deviceDao.updateNickname(device.id, nickname)
    deviceApi.editScale(device.id, device.copy(nickname = nickname).toApiModel())
    val deviceDetails = deviceDao.getDevice(device.id)
    return deviceDetails?.toDeviceDomainModel() ?: throw IllegalStateException("Device not found")
  }

  override suspend fun getUnsyncedDevices(): List<Device> =
    deviceDao.getUnsyncedDevicesList().map { deviceEntity -> deviceEntity.toDeviceDomainModel() }

  override suspend fun markDeviceSynced(
    deviceId: String,
    isSynced: Boolean,
  ) {
    deviceDao.updateSyncStatus(deviceId, isSynced)
  }

  override suspend fun markDeviceDeleted(
    deviceId: String,
    isDeleted: Boolean,
  ) {
    deviceDao.updateDeletionStatus(deviceId, isDeleted)
  }

  // API operations
  override suspend fun getDevicesFromApi(accountId: String): List<Device> {
    AppLog.d(TAG, "Fetching devices from API for account: $accountId")
    val response = deviceApi.getPairedScales()
    if (response.isSuccessful) {
      val apiModels = response.body() ?: emptyList<DeviceApiModel>()
      AppLog.i(TAG, "Fetched ${apiModels.size} device(s) from API")
      return apiModels.toDomainModels()
    } else {
      AppLog.e(TAG, "getDevicesFromApi failed with code: ${response.code()}")
      throw Exception("API call failed with code: ${response.code()}")
    }
  }

  override suspend fun saveDeviceToApi(
    device: Device,
    accountId: String,
  ): Device {
    AppLog.d(TAG, "Saving device to API: ${device.id}")
    val response = deviceApi.saveScale(device.toApiModel())
    if (response.isSuccessful) {
      AppLog.i(TAG, "Device saved to API successfully: ${device.id}")
      val apiModel = response.body()
      return apiModel?.toDomainModel(
        device.connectionStatus,
        device.device?.wifiMacAddress,
        device.device?.isWifiConfigured ?: false,
      ) ?: device
    } else {
      AppLog.e(TAG, "saveDeviceToApi failed with code: ${response.code()}")
      throw Exception("Failed to save device to API: ${response.code()}")
    }
  }

  override suspend fun deleteDeviceFromApi(deviceId: String): Boolean {
    AppLog.d(TAG, "Deleting device from API: $deviceId")
    val response = deviceApi.deleteScale(deviceId)
    if (response.isSuccessful) {
      AppLog.i(TAG, "Device deleted from API successfully: $deviceId")
      return true
    } else {
      val errorBody = response.errorBody()?.string()
      AppLog.e(TAG, "deleteDeviceFromApi failed with code: ${response.code()}, error: $errorBody")
      throw Exception("Failed to delete device from API: ${response.code()}, Error: $errorBody")
    }
  }

  // ── Unified /v3/paired-device/ (MOB-378) ─────────────────────────────────

  override suspend fun createPairedDevice(device: Device, accountId: String): Device {
    AppLog.d(TAG, "createPairedDevice deviceId=${device.id}")
    val request = device.toPairedDeviceRequest()
    val response = deviceApi.createPairedDevice(request)
    if (response.isSuccessful) {
      val body = response.body()
      AppLog.i(TAG, "createPairedDevice succeeded")
      return body?.toDomainModel(device.connectionStatus, device.device?.wifiMacAddress, device.device?.isWifiConfigured ?: false) ?: device
    } else {
      AppLog.e(TAG, "createPairedDevice failed code=${response.code()}")
      throw DeviceApiException(response.code(), "createPairedDevice failed: ${response.code()}")
    }
  }

  override suspend fun getPairedDevices(deviceType: String?): List<Device> {
    AppLog.d(TAG, "getPairedDevices deviceType=$deviceType")
    val response = deviceApi.getPairedDevices(deviceType)
    if (response.isSuccessful) {
      val models = response.body() ?: emptyList()
      AppLog.i(TAG, "getPairedDevices returned ${models.size} devices")
      return models.toDomainModels()
    } else {
      AppLog.e(TAG, "getPairedDevices failed code=${response.code()}")
      throw DeviceApiException(response.code(), "getPairedDevices failed: ${response.code()}")
    }
  }

  override suspend fun updatePairedDevice(deviceId: String, device: Device, accountId: String): Device {
    AppLog.d(TAG, "updatePairedDevice deviceId=$deviceId")
    val request = device.toPairedDeviceRequest()
    val response = deviceApi.updatePairedDevice(deviceId, request)
    if (response.isSuccessful) {
      val body = response.body()
      AppLog.i(TAG, "updatePairedDevice succeeded deviceId=$deviceId")
      return body?.toDomainModel(device.connectionStatus, device.device?.wifiMacAddress, device.device?.isWifiConfigured ?: false) ?: device
    } else {
      AppLog.e(TAG, "updatePairedDevice failed code=${response.code()}")
      throw DeviceApiException(response.code(), "updatePairedDevice failed: ${response.code()}")
    }
  }

  override suspend fun deletePairedDevice(deviceId: String): Boolean {
    AppLog.d(TAG, "deletePairedDevice deviceId=$deviceId")
    val response = deviceApi.deletePairedDevice(deviceId)
    if (response.isSuccessful) {
      AppLog.i(TAG, "deletePairedDevice succeeded deviceId=$deviceId")
      return true
    } else {
      AppLog.e(TAG, "deletePairedDevice failed code=${response.code()}")
      throw DeviceApiException(response.code(), "deletePairedDevice failed: ${response.code()}")
    }
  }

  override suspend fun saveScalePreferencesToApi(
    preferences: R4ScalePreferenceApiModel,
  ): R4ScalePreferenceApiModel {
    AppLog.d(TAG, "Saving scale preferences to API")
    val response = deviceApi.saveScalePreferences(preferences)
    if (response.isSuccessful) {
      AppLog.i(TAG, "Scale preferences saved to API successfully")
      return response.body() ?: preferences
    } else {
      AppLog.e(TAG, "saveScalePreferencesToApi failed with code: ${response.code()}")
      throw Exception("Failed to save scale preferences to API: ${response.code()}")
    }
  }

  override suspend fun saveScaleMetaDataToApi(
    deviceId: String,
    metaData: ScaleMetaDataApiModel,
  ): Boolean {
    AppLog.d(TAG, "Saving scale metadata to API for device: $deviceId")
    val response = deviceApi.updateScaleMetadata(deviceId, metaData)
    if (response.isSuccessful) {
      AppLog.i(TAG, "Scale metadata saved to API successfully for device: $deviceId")
      return true
    } else {
      AppLog.e(TAG, "saveScaleMetaDataToApi failed with code: ${response.code()}")
      throw Exception("Failed to save scale meta data to API: ${response.code()}")
    }
  }

  override suspend fun getScaleTokenFromApi(isR4: Boolean): String {
    AppLog.d(TAG, "Fetching scale token from API (isR4=$isR4)")
    val param = if (isR4) "4" else null
    val response = deviceApi.getScaleToken(param)
    if (response.isSuccessful) {
      val tokenResponse = response.body()
      AppLog.i(TAG, "Scale token fetched successfully")
      return tokenResponse?.token ?: throw Exception("Token response is null")
    } else {
      AppLog.e(TAG, "getScaleTokenFromApi failed with code: ${response.code()}")
      throw Exception("Failed to get scale token from API: ${response.code()}")
    }
  }

  // Extension functions for model conversions
  // Removed - now in DeviceMappers.kt
}
