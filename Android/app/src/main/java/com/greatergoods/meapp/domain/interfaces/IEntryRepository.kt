package com.greatergoods.meapp.domain.interfaces

import com.greatergoods.meapp.domain.model.EntryDTO
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining operations for managing entries in the application.
 * This interface provides a contract for data operations related to entries,
 * supporting both weight scale and blood pressure monitor functionalities.
 */
interface IEntryRepository {
    /**
     * Retrieves all entries from the data source.
     * @return Flow of list of EntryDTO objects
     */
    fun getAllEntries(): Flow<List<EntryDTO>>

    /**
     * Retrieves a specific entry by its ID.
     * @param id The unique identifier of the entry
     * @return Flow of EntryDTO object, or null if not found
     */
    fun getEntryById(id: String): Flow<EntryDTO?>

    /**
     * Saves a new entry to the data source.
     * @param entry The EntryDTO object to be saved
     * @return Flow of the saved EntryDTO object
     */
    suspend fun saveEntry(entry: EntryDTO): Flow<EntryDTO>

    /**
     * Updates an existing entry in the data source.
     * @param entry The EntryDTO object with updated information
     * @return Flow of the updated EntryDTO object
     */
    suspend fun updateEntry(entry: EntryDTO): Flow<EntryDTO>

    /**
     * Deletes an entry from the data source.
     * @param id The unique identifier of the entry to be deleted
     * @return Flow of Boolean indicating success of deletion
     */
    suspend fun deleteEntry(id: String): Flow<Boolean>

    // Time-based Queries

    /**
     * Retrieves entries within a specific date range.
     * @param startDate The start date of the range
     * @param endDate The end date of the range
     * @return Flow of list of EntryDTO objects within the date range
     */
    fun getEntriesByDateRange(
        startDate: Long,
        endDate: Long,
    ): Flow<List<EntryDTO>>

    /**
     * Retrieves the most recent entry.
     * @return Flow of the latest EntryDTO object, or null if no entries exist
     */
    fun getLatestEntry(): Flow<EntryDTO?>

    /**
     * Retrieves entries for the last N days.
     * @param days Number of days to look back
     * @return Flow of list of EntryDTO objects
     */
    fun getLastNDaysEntries(days: Int): Flow<List<EntryDTO>>

    // Device-specific Operations

    /**
     * Retrieves entries for a specific device type.
     * @param deviceType The type of device (e.g., "scale", "bpm")
     * @return Flow of list of EntryDTO objects for the device type
     */
    fun getEntriesByDeviceType(deviceType: String): Flow<List<EntryDTO>>

    /**
     * Retrieves entries from a specific data source.
     * @param source The source of the entry (e.g., "manual", "bluetooth", "wifi")
     * @return Flow of list of EntryDTO objects from the source
     */
    fun getEntriesBySource(source: String): Flow<List<EntryDTO>>

    // Sync Operations

    /**
     * Retrieves all unsynced entries.
     * @return Flow of list of unsynced EntryDTO objects
     */
    fun getUnsyncedEntries(): Flow<List<EntryDTO>>

    /**
     * Marks an entry as synced.
     * @param id The ID of the entry to mark as synced
     * @return Flow of Boolean indicating success
     */
    suspend fun markEntrySynced(id: String): Flow<Boolean>

    /**
     * Marks multiple entries as synced.
     * @param ids List of entry IDs to mark as synced
     * @return Flow of Boolean indicating success
     */
    suspend fun markEntriesSynced(ids: List<String>): Flow<Boolean>

    // Account-specific Operations

    /**
     * Retrieves all entries for a specific account.
     * @param accountId The account ID
     * @return Flow of list of EntryDTO objects for the account
     */
    fun getEntriesByAccount(accountId: String): Flow<List<EntryDTO>>

    /**
     * Deletes all entries for a specific account.
     * @param accountId The account ID
     * @return Flow of Boolean indicating success
     */
    suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Boolean>
}
