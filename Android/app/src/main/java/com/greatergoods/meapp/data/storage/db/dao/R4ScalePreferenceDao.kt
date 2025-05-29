package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.R4ScalePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface R4ScalePreferenceDao {
    @Query("SELECT * FROM r4_scale_preference WHERE id = :id")
    suspend fun getR4ScalePreferenceById(id: String): R4ScalePreferenceEntity?

    @Query("SELECT * FROM r4_scale_preference")
    fun getAllR4ScalePreferences(): Flow<List<R4ScalePreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertR4ScalePreference(preference: R4ScalePreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertR4ScalePreferences(preferences: List<R4ScalePreferenceEntity>)

    @Update
    suspend fun updateR4ScalePreference(preference: R4ScalePreferenceEntity)

    @Delete
    suspend fun deleteR4ScalePreference(preference: R4ScalePreferenceEntity)

    @Query("DELETE FROM r4_scale_preference WHERE id = :id")
    suspend fun deleteR4ScalePreferenceById(id: String)

    @Query("DELETE FROM r4_scale_preference")
    suspend fun deleteAllR4ScalePreferences()
} 