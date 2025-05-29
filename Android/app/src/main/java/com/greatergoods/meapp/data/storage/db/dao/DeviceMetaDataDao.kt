package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.DeviceMetaDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceMetaDataDao {
    @Query("SELECT * FROM device_meta_data WHERE id = :id")
    suspend fun getDeviceMetaDataById(id: String): DeviceMetaDataEntity?

    @Query("SELECT * FROM device_meta_data")
    fun getAllDeviceMetaData(): Flow<List<DeviceMetaDataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceMetaData(metaData: DeviceMetaDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceMetaDataList(metaDataList: List<DeviceMetaDataEntity>)

    @Update
    suspend fun updateDeviceMetaData(metaData: DeviceMetaDataEntity)

    @Delete
    suspend fun deleteDeviceMetaData(metaData: DeviceMetaDataEntity)

    @Query("DELETE FROM device_meta_data WHERE id = :id")
    suspend fun deleteDeviceMetaDataById(id: String)

    @Query("DELETE FROM device_meta_data")
    suspend fun deleteAllDeviceMetaData()
} 