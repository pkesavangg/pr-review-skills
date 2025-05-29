package com.greatergoods.meapp.data.storage.db.repository

import com.greatergoods.meapp.data.storage.db.dao.DeviceDao
import com.greatergoods.meapp.data.storage.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepository(private val deviceDao: DeviceDao) {
    
    suspend fun insertDevice(device: DeviceEntity) = deviceDao.insertDevice(device)
    
    fun getAllDevices(): Flow<List<DeviceEntity>> = deviceDao.getAllDevices()
    
    fun getDevicesByType(type: String): Flow<List<DeviceEntity>> = deviceDao.getDevicesByType(type)
    
    suspend fun deleteDevice(device: DeviceEntity) = deviceDao.deleteDevice(device)
    
    // Mapping functions to convert DeviceEntity to wrapper classes
    fun mapToBpm(device: DeviceEntity): BpmEntity? {
        return if (device.type == "BPM") {
            BpmEntity(
                device = device,
                heartRate = 0, // You'll need to get this from somewhere
                timestamp = System.currentTimeMillis()
            )
        } else null
    }
    
    fun mapToScale(device: DeviceEntity): ScaleEntity? {
        return if (device.type == "SCALE") {
            ScaleEntity(
                device = device,
                weight = 0.0, // You'll need to get this from somewhere
                unit = "kg"
            )
        } else null
    }
    
    fun mapToDeviceMetaData(device: DeviceEntity): DeviceMetaDataEntity? {
        return if (device.type == "DEVICE_METADATA") {
            DeviceMetaDataEntity(
                device = device,
                firmwareVersion = "", // You'll need to get this from somewhere
                lastSyncTime = System.currentTimeMillis()
            )
        } else null
    }
    
    fun mapToR4ScalePreference(scale: ScaleEntity): R4ScalePreferenceEntity {
        return R4ScalePreferenceEntity(
            scale = scale,
            preference = "" // You'll need to get this from somewhere
        )
    }
} 