package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BabyEntryEntity)

    @Update
    suspend fun update(entry: BabyEntryEntity)

    @Query("DELETE FROM baby_entry WHERE id = :entryId")
    suspend fun delete(entryId: Long)

    @Query("SELECT * FROM baby_entry WHERE babyProfileId = :babyProfileId")
    fun observeByBabyProfileId(babyProfileId: String): Flow<List<BabyEntryEntity>>

    @Query("SELECT * FROM baby_entry WHERE id = :entryId")
    suspend fun getById(entryId: Long): BabyEntryEntity?

    @Query("DELETE FROM baby_entry WHERE babyProfileId = :babyProfileId")
    suspend fun deleteByBabyProfileId(babyProfileId: String)
}
