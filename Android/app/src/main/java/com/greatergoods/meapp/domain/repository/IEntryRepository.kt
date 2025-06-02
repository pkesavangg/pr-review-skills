package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.EntryEntity // Changed import
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining operations for managing entries in the application.
 * This interface provides a contract for data operations related to entries,
 * supporting both weight scale and blood pressure monitor functionalities.
 */
interface IEntryRepository {
    // Basic CRUD Operations
    /**
     * Retrieves all entries from the data source.
     * @return Flow of list of EntryEntity objects
     */
    fun getAllEntries(): Flow<List<EntryEntity>> // Changed return type

    /**
     * Retrieves a specific entry by its ID.
     * @param id The unique identifier of the entry
     * @return Flow of EntryEntity object, or null if not found
     */
    fun getEntryById(id: Long): Flow<EntryEntity?> // Changed parameter type and return type

    /**
     * Saves a new entry to the data source.
     * @param entry The EntryEntity object to be saved
     * @return Flow of the saved EntryEntity object's ID
     */
    suspend fun saveEntry(entry: EntryEntity): Flow<Long> // Changed parameter type and return type

    /**
     * Saves a list of new entries to the data source.
     * @param entries The list of EntryEntity objects to be saved
     */
    suspend fun saveEntries(entries: List<EntryEntity>) // New method

    /**
     * Updates an existing entry in the data source.
     * @param entry The EntryEntity object with updated information
     * @return Flow of Int indicating number of updated rows
     */
    suspend fun updateEntry(entry: EntryEntity): Flow<Int> // Changed parameter type and return type

    /**
     * Deletes an entry from the data source.
     * @param entry The EntryEntity to be deleted
     * @return Flow of Int indicating number of deleted rows
     */
    suspend fun deleteEntry(entry: EntryEntity): Flow<Int> // Changed parameter type and return type

    // Time-based Queries
    /**
     * Retrieves entries within a specific date range for a given account.
     * @param accountId The account ID
     * @param startDate The start date of the range (timestamp as String)
     * @param endDate The end date of the range (timestamp as String)
     * @return Flow of list of EntryEntity objects within the date range
     */
    fun getEntriesByDateRange(accountId: String, startDate: String, endDate: String): Flow<List<EntryEntity>> // Changed parameter types and return type

    /**
     * Retrieves the most recent entry for a given account.
     * @param accountId The account ID
     * @return Flow of the latest EntryEntity object, or null if no entries exist
     */
    fun getLatestEntry(accountId: String): Flow<EntryEntity?> // Added accountId, changed return type

    /**
     * Retrieves entries for the last N days for a given account.
     * @param accountId The account ID
     * @param days Number of days to look back
     * @return Flow of list of EntryEntity objects
     */
    fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<EntryEntity>> // Added accountId, changed return type

    // Device-specific Operations
    /**
     * Retrieves entries for a specific device type for a given account.
     * @param accountId The account ID
     * @param deviceType The type of device (e.g., "scale", "bpm")
     * @return Flow of list of EntryEntity objects for the device type
     */
    fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<EntryEntity>> // Changed return type

    /**
     * Retrieves entries from a specific data source for a given account.
     * This might need re-evaluation as EntryEntity doesn't directly have a 'source' field.
     * For now, it's removed. If source is a property of a related entity, the query needs to be adapted.
     */
    // fun getEntriesBySource(accountId: String, source: String): Flow<List<EntryEntity>>

    // Sync Operations
    /**
     * Retrieves all unsynced entries.
     * @return Flow of list of unsynced EntryEntity objects
     */
    fun getUnsyncedEntries(): Flow<List<EntryEntity>> // Changed return type

    /**
     * Marks an entry as synced.
     * @param id The ID of the entry to mark as synced
     * @return Flow of Int indicating number of updated rows
     */
    suspend fun markEntrySynced(id: Long): Flow<Int> // Changed parameter type and return type

    /**
     * Marks multiple entries as synced.
     * @param ids List of entry IDs to mark as synced
     * @return Flow of Int indicating number of updated rows
     */
    suspend fun markEntriesSynced(ids: List<Long>): Flow<Int> // Changed parameter type and return type

    // Account-specific Operations
    /**
     * Retrieves all entries for a specific account.
     * @param accountId The account ID
     * @return Flow of list of EntryEntity objects for the account
     */
    fun getEntriesByAccount(accountId: String): Flow<List<EntryEntity>> // Changed return type

    /**
     * Deletes all entries for a specific account.
     * @param accountId The account ID
     * @return Flow of Int indicating number of deleted rows
     */
    suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> // Changed return type

    /**
     * Saves a list of metric entries to the database.
     * @param metrics The list of BodyScaleEntryMetricEntity objects to be saved
     */
    suspend fun saveMetrics(metrics: List<BodyScaleEntryMetricEntity>)

    /**
     * Retrieves metrics for a specific entry.
     * @param entryId The ID of the entry
     * @return Flow of BodyScaleEntryMetricEntity objects for the entry
     */
    fun getMetricsByEntryId(entryId: Long): Flow<BodyScaleEntryMetricEntity?>

    /**
     * Saves a list of scale entries to the database.
     * @param entries The list of BodyScaleEntryEntity objects to be saved
     */
    suspend fun saveScaleEntries(entries: List<BodyScaleEntryEntity>)

    /**
     * Retrieves a scale entry by its ID.
     * @param entryId The ID of the entry
     * @return The BodyScaleEntryEntity if found, null otherwise
     */
    suspend fun getScaleEntryById(entryId: Long): BodyScaleEntryEntity?
}
