package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dmdbrands.gurus.weight.data.storage.db.entity.baby.BabyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BabyProfileEntity)

    @Update
    suspend fun update(profile: BabyProfileEntity)

    @Query("DELETE FROM baby_profiles WHERE id = :profileId")
    suspend fun delete(profileId: String)

    @Query("SELECT * FROM baby_profiles WHERE accountId = :accountId")
    fun observeByAccountId(accountId: String): Flow<List<BabyProfileEntity>>

    @Query("SELECT * FROM baby_profiles WHERE id = :profileId")
    suspend fun getById(profileId: String): BabyProfileEntity?
}
