package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesCursorResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesSyncResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
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
  suspend fun update(entry: Entry): Long

  /**
   * Updates only the note column of an existing entry, leaving weight/metrics untouched
   * (MOB-438). Avoids the unit-conversion round-trip a full [update] would apply.
   */
  suspend fun updateNote(entry: Entry, note: String?)

  /**
   * Gets all valid entries for an account.
   * @param accountId The account ID.
   * @return List of valid entries.
   */
  suspend fun getEntriesByAccount(accountId: String, convertToDisplay: Boolean = true): List<Entry>

  /**
   * Gets an entry by its ID.
   * @param id The entry ID.
   * @return The entry, or null if not found.
   */
  suspend fun getEntryById(id: Long): Entry?

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
   * Sends a batch of unified entries to `POST /v3/entries/` as an atomic request.
   * Throws on any non-2xx (whole batch failed — server rolls back).
   * @param entries The mixed-category entries to write.
   * @return The unified response (persisted entries + sync timestamp).
   */
  suspend fun sendBatchToAPI(entries: List<UnifiedEntryRequest>): UnifiedEntryResponse

  /**
   * Gets operations from the API since a specific timestamp.
   * @param lastUpdated The timestamp to get operations since
   * @return List of EntryEntity objects from the API
   */
  suspend fun getOperationsFromAPI(syncTimeStamp: String): OperationsResponse?

  // ── Unified /v3/entries/ read (MOB-380) ───────────────────────────────────

  /** Fetches all entries since [start] (sync-mode delta). Throws on failure. */
  suspend fun getEntriesSync(start: String, category: String? = null): EntriesSyncResponse

  /** Fetches a single cursor page. Throws on failure. */
  suspend fun getEntriesPage(cursor: String? = null, limit: Int = 20, category: String? = null): EntriesCursorResponse

  /** Streams a CSV export body; null on non-2xx. Throws on network error. `babyId` required when category=baby. */
  suspend fun exportEntriesCsv(category: String? = null, babyId: String? = null, download: Boolean = false, utcOffset: Int = 0): okhttp3.ResponseBody?

  /**
   * Gets the operation count for an account.
   * @param accountId The account ID
   * @return The number of operations
   */
  suspend fun getOperationCount(accountId: String): Int

}
