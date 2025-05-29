package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.BpmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BpmDao {
    @Query("SELECT * FROM bpm WHERE id = :id")
    suspend fun getBpmById(id: String): BpmEntity?

    @Query("SELECT * FROM bpm")
    fun getAllBpms(): Flow<List<BpmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpm(bpm: BpmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpms(bpms: List<BpmEntity>)

    @Update
    suspend fun updateBpm(bpm: BpmEntity)

    @Delete
    suspend fun deleteBpm(bpm: BpmEntity)

    @Query("DELETE FROM bpm WHERE id = :id")
    suspend fun deleteBpmById(id: String)

    @Query("DELETE FROM bpm")
    suspend fun deleteAllBpms()
} 