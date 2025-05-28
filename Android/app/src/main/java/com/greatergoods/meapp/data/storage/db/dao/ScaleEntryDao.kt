package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.ScaleEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the scale_entry table.
 * Provides methods to interact with scale-specific entry data in the database.
 */
@Dao
interface ScaleEntryDao {
    /**
     * Insert a new scale entry into the database.
     * @param scaleEntry The scale entry entity to insert
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scaleEntry: ScaleEntryEntity): Long

    /**
     * Update an existing scale entry in the database.
     * @param scaleEntry The scale entry entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(scaleEntry: ScaleEntryEntity): Int

    /**
     * Delete a scale entry from the database.
     * @param scaleEntry The scale entry entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun delete(scaleEntry: ScaleEntryEntity): Int

    /**
     * Get a scale entry by its ID.
     * @param id The scale entry ID
     * @return The scale entry entity if found, null otherwise
     */
    @Query("SELECT * FROM scale_entry WHERE id = :id")
    suspend fun getScaleEntryById(id: Long): ScaleEntryEntity?

    /**
     * Get a scale entry by its associated entry ID.
     * @param entryId The entry ID
     * @return The scale entry entity if found, null otherwise
     */
    @Query("SELECT * FROM scale_entry WHERE entryId = :entryId")
    suspend fun getScaleEntryByEntryId(entryId: Long): ScaleEntryEntity?

    /**
     * Get all scale entries for a specific user.
     * @param userId The user ID
     * @return A Flow of all scale entries for the user
     */
    @Query("SELECT * FROM scale_entry WHERE entryId IN (SELECT id FROM entry WHERE userId = :userId)")
    fun getScaleEntriesByUserId(userId: String): Flow<List<ScaleEntryEntity>>

    /**
     * Get all unverified scale entries.
     * @return A Flow of all unverified scale entries
     */
    @Query("SELECT * FROM scale_entry WHERE verified = 0")
    fun getUnverifiedScaleEntries(): Flow<List<ScaleEntryEntity>>

    /**
     * Mark a scale entry as verified.
     * @param id The scale entry ID
     * @return The number of rows updated
     */
    @Query("UPDATE scale_entry SET verified = 1 WHERE id = :id")
    suspend fun markScaleEntryVerified(id: Long): Int

    /**
     * Get scale entries by source type.
     * @param source The source type (bluetooth scale, wifi scale, etc.)
     * @return A Flow of scale entries from the specified source
     */
    @Query("SELECT * FROM scale_entry WHERE source = :source")
    fun getScaleEntriesBySource(source: String): Flow<List<ScaleEntryEntity>>

    /**
     * Delete all scale entries for a specific user.
     * @param userId The user ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM scale_entry WHERE entryId IN (SELECT id FROM entry WHERE userId = :userId)")
    suspend fun deleteAllScaleEntriesForUser(userId: String): Int
} 