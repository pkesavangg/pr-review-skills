package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the entry table.
 * Provides methods to interact with the entry data in the database.
 */
@Dao
interface EntryDao {
    /**
     * Insert a new entry into the database.
     * @param entry The entry entity to insert
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    /**
     * Update an existing entry in the database.
     * @param entry The entry entity to update
     * @return The number of rows updated
     */
    @Update
    suspend fun update(entry: EntryEntity): Int

    /**
     * Delete an entry from the database.
     * @param entry The entry entity to delete
     * @return The number of rows deleted
     */
    @Delete
    suspend fun delete(entry: EntryEntity): Int

    /**
     * Get an entry by its ID.
     * @param id The entry ID
     * @return The entry entity if found, null otherwise
     */
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getEntryById(id: Long): EntryEntity?

    /**
     * Get all entries for a specific user.
     * @param userId The user ID
     * @return A Flow of all entries for the user
     */
    @Query("SELECT * FROM entry WHERE userId = :userId")
    fun getEntriesByUserId(userId: String): Flow<List<EntryEntity>>

    /**
     * Get all unsynced entries.
     * @return A Flow of all unsynced entries
     */
    @Query("SELECT * FROM entry WHERE isSynced = 0")
    fun getUnsyncedEntries(): Flow<List<EntryEntity>>

    /**
     * Get entries by device type for a specific user.
     * @param userId The user ID
     * @param deviceType The device type
     * @return A Flow of entries for the specified device type
     */
    @Query("SELECT * FROM entry WHERE userId = :userId AND deviceType = :deviceType")
    fun getEntriesByDeviceType(userId: String, deviceType: String): Flow<List<EntryEntity>>

    /**
     * Get entries within a time range for a specific user.
     * @param userId The user ID
     * @param startTime The start timestamp
     * @param endTime The end timestamp
     * @return A Flow of entries within the time range
     */
    @Query("SELECT * FROM entry WHERE userId = :userId AND entryTimestamp BETWEEN :startTime AND :endTime")
    fun getEntriesByTimeRange(userId: String, startTime: String, endTime: String): Flow<List<EntryEntity>>

    /**
     * Mark an entry as synced.
     * @param id The entry ID
     * @return The number of rows updated
     */
    @Query("UPDATE entry SET isSynced = 1 WHERE id = :id")
    suspend fun markEntrySynced(id: Long): Int

    /**
     * Mark multiple entries as synced.
     * @param ids List of entry IDs
     * @return The number of rows updated
     */
    @Query("UPDATE entry SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markEntriesSynced(ids: List<Long>): Int

    /**
     * Get the latest entry for a specific user.
     * @param userId The user ID
     * @return The latest entry entity if found, null otherwise
     */
    @Query("SELECT * FROM entry WHERE userId = :userId ORDER BY entryTimestamp DESC LIMIT 1")
    suspend fun getLatestEntry(userId: String): EntryEntity?

    /**
     * Delete all entries for a specific user.
     * @param userId The user ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM entry WHERE userId = :userId")
    suspend fun deleteAllEntriesForUser(userId: String): Int

    /**
     * Get entries by operation type for a specific user.
     * @param userId The user ID
     * @param operationType The operation type
     * @return A Flow of entries with the specified operation type
     */
    @Query("SELECT * FROM entry WHERE userId = :userId AND operationType = :operationType")
    fun getEntriesByOperationType(userId: String, operationType: String): Flow<List<EntryEntity>>

    /**
     * Get entries with their associated scale data.
     * @param userId The user ID
     * @return A Flow of entries with scale data
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE userId = :userId AND deviceType = 'scale'")
    fun getScaleEntries(userId: String): Flow<List<EntryEntity>>

    /**
     * Get entries with their associated BPM data.
     * @param userId The user ID
     * @return A Flow of entries with BPM data
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE userId = :userId AND deviceType = 'bpm'")
    fun getBpmEntries(userId: String): Flow<List<EntryEntity>>

    /**
     * Get entries by source type.
     * @param userId The user ID
     * @param source The source type (manual, bluetooth scale, wifi scale, etc.)
     * @return A Flow of entries from the specified source
     */
    @Query("SELECT * FROM entry WHERE userId = :userId AND deviceType = :source")
    fun getEntriesBySource(userId: String, source: String): Flow<List<EntryEntity>>

    /**
     * Get verified entries for a specific user.
     * @param userId The user ID
     * @return A Flow of verified entries
     */
    @Query("SELECT * FROM entry WHERE userId = :userId AND verified = 1")
    fun getVerifiedEntries(userId: String): Flow<List<EntryEntity>>
} 