package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.BpmEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the bpm_entry table.
 * Provides methods to interact with blood pressure entry data in the database.
 */
@Dao
interface BpmEntryDao {
    /**
     * Insert a new BPM entry into the database.
     * @param bpmEntry The BPM entry entity to insert
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bpmEntry: BpmEntryEntity): Long

    /**
     * Update an existing BPM entry in the database.
     * @param bpmEntry The BPM entry entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(bpmEntry: BpmEntryEntity): Int

    /**
     * Delete a BPM entry from the database.
     * @param bpmEntry The BPM entry entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun delete(bpmEntry: BpmEntryEntity): Int

    /**
     * Get a BPM entry by its ID.
     * @param id The BPM entry ID
     * @return The BPM entry entity if found, null otherwise
     */
    @Query("SELECT * FROM bpm_entry WHERE id = :id")
    suspend fun getBpmEntryById(id: Long): BpmEntryEntity?

    /**
     * Get a BPM entry by its associated entry ID.
     * @param entryId The entry ID
     * @return The BPM entry entity if found, null otherwise
     */
    @Query("SELECT * FROM bpm_entry WHERE entryId = :entryId")
    suspend fun getBpmEntryByEntryId(entryId: Long): BpmEntryEntity?

    /**
     * Get all BPM entries for a specific user.
     * @param userId The user ID
     * @return A Flow of all BPM entries for the user
     */
    @Query("SELECT * FROM bpm_entry WHERE entryId IN (SELECT id FROM entry WHERE userId = :userId)")
    fun getBpmEntriesByUserId(userId: String): Flow<List<BpmEntryEntity>>

    /**
     * Get all unverified BPM entries.
     * @return A Flow of all unverified BPM entries
     */
    @Query("SELECT * FROM bpm_entry WHERE verified = 0")
    fun getUnverifiedBpmEntries(): Flow<List<BpmEntryEntity>>

    /**
     * Mark a BPM entry as verified.
     * @param id The BPM entry ID
     * @return The number of rows updated
     */
    @Query("UPDATE bpm_entry SET verified = 1 WHERE id = :id")
    suspend fun markBpmEntryVerified(id: Long): Int

    /**
     * Get BPM entries by source type.
     * @param source The source type (manual, bluetooth device, etc.)
     * @return A Flow of BPM entries from the specified source
     */
    @Query("SELECT * FROM bpm_entry WHERE source = :source")
    fun getBpmEntriesBySource(source: String): Flow<List<BpmEntryEntity>>

    /**
     * Get BPM entries within a specific blood pressure range.
     * @param minSystolic Minimum systolic value
     * @param maxSystolic Maximum systolic value
     * @param minDiastolic Minimum diastolic value
     * @param maxDiastolic Maximum diastolic value
     * @return A Flow of BPM entries within the specified range
     */
    @Query("SELECT * FROM bpm_entry WHERE systolic BETWEEN :minSystolic AND :maxSystolic AND diastolic BETWEEN :minDiastolic AND :maxDiastolic")
    fun getBpmEntriesByRange(minSystolic: Int, maxSystolic: Int, minDiastolic: Int, maxDiastolic: Int): Flow<List<BpmEntryEntity>>

    /**
     * Delete all BPM entries for a specific user.
     * @param userId The user ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM bpm_entry WHERE entryId IN (SELECT id FROM entry WHERE userId = :userId)")
    suspend fun deleteAllBpmEntriesForUser(userId: String): Int
} 