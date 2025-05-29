package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.ScaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaleDao {
    @Query("SELECT * FROM scale WHERE id = :id")
    suspend fun getScaleById(id: String): ScaleEntity?

    @Query("SELECT * FROM scale")
    fun getAllScales(): Flow<List<ScaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScale(scale: ScaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScales(scales: List<ScaleEntity>)

    @Update
    suspend fun updateScale(scale: ScaleEntity)

    @Delete
    suspend fun deleteScale(scale: ScaleEntity)

    @Query("DELETE FROM scale WHERE id = :id")
    suspend fun deleteScaleById(id: String)

    @Query("DELETE FROM scale")
    suspend fun deleteAllScales()
} 