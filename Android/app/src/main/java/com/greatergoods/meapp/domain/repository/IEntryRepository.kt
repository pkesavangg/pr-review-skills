package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining operations for managing entries in the application.
 * Only supports the 14 direct functions from EntryDao.
 */
interface IEntryRepository {
    /**
     * Inserts a single entry.
     * @param entry The entry to insert.
     * @return The row ID of the inserted entry.
     */
    suspend fun insert(entry: Entry): Long

    /**
     * Inserts a list of entries.
     * @param entries The list of entries to insert.
     */
    suspend fun insert(entries: List<Entry>)

    /**
     * Marks an entry as deleted.
     * @param entry The entry to delete.
     */
    suspend fun delete(entry: Entry)

    /**
     * Updates an existing entry.
     * @param entry The entry to update.
     * @return The number of rows updated.
     */
    suspend fun update(entry: Entry): Int

    /**
     * Gets the latest valid entry for an account.
     * @param accountId The account ID.
     * @return The latest valid entry, or null if not found.
     */
    suspend fun getLatestEntry(accountId: String): Flow<Entry>?

    /**
     * Gets all valid entries for an account.
     * @param accountId The account ID.
     * @return List of valid entries.
     */
    suspend fun getEntriesByAccount(accountId: String): List<Entry>

    /**
     * Gets valid entries for an account within a time range.
     * @param accountId The account ID.
     * @param startTime The start timestamp.
     * @param endTime The end timestamp.
     * @return Flow of valid entries in the time range.
     */
    fun getEntriesByTimeRange(accountId: String, startTime: String, endTime: String): Flow<List<Entry>>

    /**
     * Gets valid entries for an account by device type.
     * @param accountId The account ID.
     * @param deviceType The device type.
     * @return Flow of valid entries for the device type.
     */
    fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>>

    /**
     * Gets an entry by its ID.
     * @param id The entry ID.
     * @return The entry, or null if not found.
     */
    suspend fun getEntryById(id: Long): Entry?

    /**
     * Gets entries for an account by operation type.
     * @param accountId The account ID.
     * @param operationType The operation type.
     * @return Flow of entries with the specified operation type.
     */
    fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<Entry>>

    /**
     * Gets all unsynced entries for an account.
     * @param accountId The account ID.
     * @return List of unsynced entries.
     */
    suspend fun getUnSynced(accountId: String): List<Entry>

    /**
     * Increments the attempts count for an entry.
     * @param id The entry ID.
     * @return The number of rows updated.
     */
    suspend fun incrementAttempts(id: Long): Int

    /**
     * Gets failed operations for an account.
     * @param accountId The account ID.
     * @param maxAttempts The maximum number of attempts.
     * @return List of failed operations.
     */
    suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<Entry>

    /**
     * Clears all unsynced entries for an account.
     * @param accountId The account ID.
     * @return The number of rows deleted.
     */
    suspend fun clearUnSynced(accountId: String): Int

    /**
     * Deletes an entry by its ID.
     * @param id The entry ID.
     * @return The number of rows deleted.
     */
    suspend fun deleteById(id: Long): Int

    /**
     * Retrieves entries for the last N days for a given account.
     * @param accountId The account ID
     * @param days Number of days to look back
     * @return Flow of list of Entry objects
     */
    fun getLastNDaysEntries(
        accountId: String,
        days: Int,
    ): Flow<List<Entry>>

    /**
     * Deletes all entries for a specific account.
     * @param accountId The account ID
     * @return Flow of Int indicating number of deleted rows
     */
    suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int>

    // Sync Operations
    /**
     * Sends an operation to the API for synchronization.
     * @param operation The EntryEntity representing the operation to sync
     */
    suspend fun sendOperationToAPI(operation: ScaleApiEntry?)

    /**
     * Gets operations from the API since a specific timestamp.
     * @param lastUpdated The timestamp to get operations since
     * @return List of EntryEntity objects from the API
     */
    suspend fun getOperationsFromAPI(lastUpdated: Long?): List<ScaleApiEntry>

    /**
     * Gets entries for a specific month and year.
     * @param accountId The account ID
     * @param month The month in YYYY-MM format
     * @return Flow of list of entries for the specified month
     */
    fun getMonthDetail(accountId: String, month: String): Flow<List<Entry?>>

    /**
     * Gets monthly aggregated data for the last year.
     * @param accountId The account ID
     * @return Flow of list of monthly aggregated data
     */
    fun getMonthsLastYear(accountId: String): Flow<List<HistoryMonth>>

    /**
     * Gets all monthly aggregated data.
     * @param accountId The account ID
     * @return Flow of list of all monthly aggregated data
     */
    fun getMonthsAll(accountId: String): Flow<List<HistoryMonth>>

    /**
     * Gets the operation count for an account.
     * @param accountId The account ID
     * @return The number of operations
     */
    suspend fun getOperationCount(accountId: String): Int
}
