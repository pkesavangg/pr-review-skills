package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.domain.model.api.device.DeviceApiModel
import com.greatergoods.meapp.domain.model.api.device.R4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.api.device.ScaleMetaDataApiModel
import com.greatergoods.meapp.domain.model.api.device.toApiModel
import com.greatergoods.meapp.domain.model.api.device.toDomainModel
import com.greatergoods.meapp.domain.model.api.device.toDomainModels
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.domain.model.storage.toDeviceDetails
import com.greatergoods.meapp.domain.model.storage.toDeviceDomainModel
import com.greatergoods.meapp.domain.repository.IDeviceRepository
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
  // DB operations
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
    val existingDevice = deviceDao.getDeviceByMac(deviceDetails.device.mac ?: "") ?: deviceDao.getDeviceByBroadcastId(
      deviceDetails.device.broadcastId.toString(),
    )
    if (existingDevice == null)
      deviceDao.insertDevice(deviceDetails)
    else
      deviceDao.updateDevice(deviceDetails)
  }

  override suspend fun deleteDeviceFromDb(deviceId: String) {
    deviceDao.deleteDevice(deviceId)
  }

  override fun deviceExistsByBroadcastId(broadcastId: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByBroadcastId(broadcastId)
      emit(device != null)
    }

  override fun deviceExistsByMac(mac: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByMac(mac)
      emit(device != null)
    }

  override fun deviceExistsByPeripheralId(peripheralId: String): Flow<Boolean> =
    flow {
      val device = deviceDao.getDeviceByPeripheralId(peripheralId)
      emit(device != null)
    }

  override fun getDeviceByBroadcastId(broadcastId: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByBroadcastIdString(broadcastId)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override fun getDeviceByMac(mac: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByMac(mac)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override fun getDeviceByPeripheralId(peripheralId: String): Flow<Device?> =
    flow {
      val deviceEntity = deviceDao.getDeviceByPeripheralId(peripheralId)
      emit(deviceEntity?.toDeviceDomainModel())
    }

  override suspend fun updateDeviceNickname(
    deviceId: String,
    nickname: String,
  ): Device {
    deviceDao.updateNickname(deviceId, nickname)
    val deviceDetails = deviceDao.getDevice(deviceId)
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

  // API operations
  override suspend fun getDevicesFromApi(accountId: String): List<Device> {
    val response = deviceApi.getPairedScales()
    if (response.isSuccessful) {
      val apiModels = response.body() ?: emptyList<DeviceApiModel>()
      return apiModels.toDomainModels()
    } else {
      throw Exception("API call failed with code: ${response.code()}")
    }
  }

  override suspend fun saveDeviceToApi(
    device: Device,
    accountId: String,
  ): Device {
    val response = deviceApi.saveScale(device.toApiModel())
    if (response.isSuccessful) {
      val apiModel = response.body()
      return apiModel?.toDomainModel() ?: device
    } else {
      throw Exception("Failed to save device to API: ${response.code()}")
    }
  }

  override suspend fun deleteDeviceFromApi(deviceId: String): Boolean {
    val response = deviceApi.deleteScale(deviceId)
    if (response.isSuccessful) {
      return true
    } else {
      throw Exception("Failed to delete device from API: ${response.code()}")
    }
  }

  override suspend fun saveScalePreferencesToApi(
    preferences: R4ScalePreferenceApiModel,
  ): R4ScalePreferenceApiModel {
    val response = deviceApi.saveScalePreferences(preferences)
    if (response.isSuccessful) {
      return response.body() ?: preferences
    } else {
      throw Exception("Failed to save scale preferences to API: ${response.code()}")
    }
  }

  override suspend fun saveScaleMetaDataToApi(
    deviceId: String,
    metaData: ScaleMetaDataApiModel,
  ): Boolean {
    val response = deviceApi.updateScaleMetadata(deviceId, metaData)
    if (response.isSuccessful) {
      return true
    } else {
      throw Exception("Failed to save scale meta data to API: ${response.code()}")
    }
  }

  override suspend fun getScaleTokenFromApi(): String {
    val response = deviceApi.getScaleToken()
    if (response.isSuccessful) {
      val tokenResponse = response.body()
      return tokenResponse?.token ?: throw Exception("Token response is null")
    } else {
      throw Exception("Failed to get scale token from API: ${response.code()}")
    }
  }

  // Extension functions for model conversions
  // Removed - now in DeviceMappers.kt
}
