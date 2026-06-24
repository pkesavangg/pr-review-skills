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

    @Query("DELETE FROM baby WHERE babyId = :profileId")
    suspend fun delete(profileId: String)

    @Query("SELECT * FROM baby WHERE accountId = :accountId")
    fun observeByAccountId(accountId: String): Flow<List<BabyProfileEntity>>

    @Query("SELECT * FROM baby WHERE babyId = :profileId")
    suspend fun getById(profileId: String): BabyProfileEntity?

    /** Deletes any local babies for [accountId] whose id is not in [keepIds] (refresh reconcile). */
    @Query("DELETE FROM baby WHERE accountId = :accountId AND babyId NOT IN (:keepIds)")
    suspend fun deleteByAccountIdNotIn(accountId: String, keepIds: List<String>)

    @Query("UPDATE baby SET activeBabyId = :activeBabyId WHERE accountId = :accountId")
    suspend fun setActiveBabyId(accountId: String, activeBabyId: String)

    @Query("SELECT activeBabyId FROM baby WHERE accountId = :accountId LIMIT 1")
    suspend fun getActiveBabyId(accountId: String): String?

    @Query("UPDATE baby SET activeBabyId = NULL WHERE accountId = :accountId")
    suspend fun clearActiveBabyId(accountId: String)
}
