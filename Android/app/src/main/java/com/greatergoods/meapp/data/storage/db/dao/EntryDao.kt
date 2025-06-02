package com.greatergoods.meapp.data.storage.db.dao

import androidx.room.*
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the entry table.
 * Provides methods to interact with the entry data in the database.
 * Includes methods for basic CRUD operations and specialized queries for different types of entries.
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
     * Get all entries for a specific account.
     * @param accountId The account ID
     * @return A Flow of all entries for the account
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId")
    fun getEntriesByAccountId(accountId: String): Flow<List<EntryEntity>>

    /**
     * Get all unsynced entries.
     * @return A Flow of all unsynced entries
     */
    @Query("SELECT * FROM entry WHERE isSynced = 0")
    fun getUnsyncedEntries(): Flow<List<EntryEntity>>

    /**
     * Get entries by device type for a specific account.
     * @param accountId The account ID
     * @param deviceType The device type
     * @return A Flow of entries for the specified device type
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND deviceType = :deviceType")
    fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<EntryEntity>>

    /**
     * Get entries within a time range for a specific account.
     * @param accountId The account ID
     * @param startTime The start timestamp
     * @param endTime The end timestamp
     * @return A Flow of entries within the time range
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND entryTimestamp BETWEEN :startTime AND :endTime")
    fun getEntriesByTimeRange(accountId: String, startTime: String, endTime: String): Flow<List<EntryEntity>>

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
     * Get the latest entry for a specific account.
     * @param accountId The account ID
     * @return The latest entry entity if found, null otherwise
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId ORDER BY entryTimestamp DESC LIMIT 1")
    suspend fun getLatestEntry(accountId: String): EntryEntity?

    /**
     * Delete all entries for a specific account.
     * @param accountId The account ID
     * @return The number of rows deleted
     */
    @Query("DELETE FROM entry WHERE accountId = :accountId")
    suspend fun deleteAllEntriesForAccount(accountId: String): Int

    /**
     * Get entries by operation type for a specific account.
     * @param accountId The account ID
     * @param operationType The operation type
     * @return A Flow of entries with the specified operation type
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND operationType = :operationType")
    fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<EntryEntity>>

    /**
     * Get entries by source type.
     * @param accountId The account ID
     * @param source The source type (manual, bluetooth scale, wifi scale, etc.)
     * @return A Flow of entries from the specified source
     */
    @Query("SELECT * FROM entry WHERE accountId = :accountId AND deviceType = :source")
    fun getEntriesBySource(accountId: String, source: String): Flow<List<EntryEntity>>

    // ----------- ENTRY METHODS -----------

    /**
     * Get all entries with their related details (BPM, Scale, ScaleMetric).
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @return List of Entry containing entries and their related data
     */
    @Transaction
    @Query("SELECT * FROM entry")
    suspend fun getAllEntries(): List<Entry>

    /**
     * Get a specific entry with all its related details by ID.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @param entryId The ID of the entry to fetch
     * @return Entry containing the entry and its related data, or null if not found
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE id = :entryId")
    suspend fun getEntry(entryId: Long): Entry?

    /**
     * Get all entries with their related details for a specific account.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @param accountId The account ID
     * @return List of Entry containing entries and their related data for the account
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE accountId = :accountId")
    suspend fun getEntriesByAccount(accountId: String): List<Entry>

    /**
     * Get all entries with their related details by operation type.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @param operationType The operation type to filter by
     * @return List of Entry containing entries and their related data
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE operationType = :operationType")
    suspend fun getEntriesByOperationType(operationType: String): List<Entry>

    /**
     * Get all BPM entries with their related details.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @return List of Entry containing BPM entries and their related data
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE deviceType = 'bpm'")
    suspend fun getBpmEntries(): List<Entry>

    /**
     * Get all Scale entries with their related details.
     * This method uses @Transaction to ensure all related data is fetched atomically.
     * @return List of Entry containing Scale entries and their related data
     */
    @Transaction
    @Query("SELECT * FROM entry WHERE deviceType = 'scale'")
    suspend fun getScaleEntries(): List<Entry>

    /**
     * Insert a list of metric entries into the database.
     * @param metrics The list of BodyScaleEntryMetricEntity objects to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<BodyScaleEntryMetricEntity>)

    /**
     * Get metrics for a specific entry.
     * @param entryId The ID of the entry
     * @return Flow of BodyScaleEntryMetricEntity for the entry
     */
    @Query("SELECT * FROM body_scale_entry_metric WHERE id = :entryId")
    fun getMetricsByEntryId(entryId: Long): Flow<BodyScaleEntryMetricEntity?>

    /**
     * Insert a list of scale entries into the database.
     * @param entries The list of BodyScaleEntryEntity objects to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScaleEntries(entries: List<BodyScaleEntryEntity>)

    /**
     * Get a scale entry by its ID.
     * @param entryId The ID of the entry
     * @return The BodyScaleEntryEntity if found, null otherwise
     */
    @Query("SELECT * FROM body_scale_entry WHERE id = :entryId")
    suspend fun getScaleEntryById(entryId: Long): BodyScaleEntryEntity?
} 