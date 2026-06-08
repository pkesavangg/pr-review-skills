// data/repository/EntryRepository.kt
package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter.isValidIsoTimestamp
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertToStored
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for managing entries.
 * Only supports the 14 direct functions from EntryDao.
 */
@Singleton
class EntryRepository @Inject constructor(
  private val entryDao: EntryDao,
  private val entryApi: EntryApi,
) : IEntryRepository {
  /**
   * Inserts a single entry.
   */
  override suspend fun insert(entry: Entry): Long {
    return if (isValidIsoTimestamp(entry.entry.entryTimestamp))
      entryDao.insert(preserveLocalScaleNote(entry).convertToStored())
    else
      -1
  }

  /**
   * Updates a single entry.
   */
  override suspend fun update(entry: Entry): Long {
    return entryDao.update(preserveLocalScaleNote(entry).convertToStored())
  }

  /**
   * Note-only update (MOB-438) — writes just the note column for the entry's id, so weight
   * and metrics are never re-converted.
   */
  override suspend fun updateNote(entry: Entry, note: String?) {
    val id = entry.entry.id
    when (entry) {
      is ScaleEntry -> entryDao.updateScaleNote(id, note)
      is BpmEntry -> entryDao.updateBpmNote(id, note)
      is BabyEntry -> entryDao.updateBabyNote(id, note)
    }
  }

  /**
   * Inserts a list of entries.
   */
  override suspend fun insert(entries: List<Entry>) {
    val valid = entries.filter { isValidIsoTimestamp(it.entry.entryTimestamp) }

    // Batch-fetch existing local notes once per account, then merge in memory — avoids a
    // per-entry SELECT during a full sync (MOB-438 PR review).
    val accountIds = valid
      .filterIsInstance<ScaleEntry>()
      .filter { it.scale.scaleEntry.note.isNullOrBlank() }
      .map { it.entry.accountId }
      .toSet()
    val noteByKey = HashMap<String, String>()
    accountIds.forEach { accountId ->
      entryDao.getStoredScaleNotes(accountId).forEach { row ->
        row.note?.takeIf { it.isNotBlank() }?.let { noteByKey[noteKey(accountId, row.entryTimestamp)] = it }
      }
    }

    val merged = valid.map { entry ->
      if (entry is ScaleEntry && entry.scale.scaleEntry.note.isNullOrBlank()) {
        noteByKey[noteKey(entry.entry.accountId, entry.entry.entryTimestamp)]?.let { existing ->
          entry.copy(scale = entry.scale.copy(scaleEntry = entry.scale.scaleEntry.copy(note = existing)))
        } ?: entry
      } else {
        entry
      }
    }.map { it.convertToStored() }

    entryDao.insert(merged)
  }

  /**
   * Keeps a locally-entered weight note when an incoming (server-sourced) scale entry has
   * none. The server contract (ScaleApiEntry) carries no note field, so a synced entry would
   * otherwise overwrite the local note with null. Device-local only (MOB-438). Used for the
   * single-entry insert/update path; the bulk [insert] path batches the lookup instead.
   */
  private suspend fun preserveLocalScaleNote(entry: Entry): Entry {
    if (entry !is ScaleEntry || !entry.scale.scaleEntry.note.isNullOrBlank()) return entry
    val existingNote = entryDao.getStoredScaleNote(entry.entry.accountId, entry.entry.entryTimestamp)
    return if (existingNote.isNullOrBlank()) {
      entry
    } else {
      entry.copy(scale = entry.scale.copy(scaleEntry = entry.scale.scaleEntry.copy(note = existingNote)))
    }
  }

  private fun noteKey(accountId: String, timestamp: String): String = "$accountId$timestamp"

  /**
   * Marks an entry as deleted.
   */
  override suspend fun delete(entry: Entry) = entryDao.delete(entry)

  /**
   * Deletes an entry by its ID.
   */
  override suspend fun deleteById(id: Long): Int = entryDao.deleteById(id)

  /**
   * Gets an entry by its ID.
   */
  override suspend fun getEntryById(id: Long): Entry? =
    entryDao.getEntryById(id)?.toEntry()

  /**
   * Gets all valid entries for an account.
   */
  override suspend fun getEntriesByAccount(accountId: String, convertToDisplay: Boolean): List<Entry> =
    entryDao.getEntriesByAccount(accountId).mapNotNull { it.toEntry(convertToDisplay) }

  override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> = flow {
    // emit(entryDao.deleteAllEntriesForAccount(accountId))
  }

  /**
   * Gets all unsynced entries for an account.
   */
  override suspend fun getUnSynced(accountId: String): List<Entry> =
    entryDao.getUnSynced(accountId).mapNotNull { it.toEntry() }

  /**
   * Increments the attempts count for an entry.
   */
  override suspend fun incrementAttempts(id: Long): Int = entryDao.incrementAttempts(id)

  /**
   * Gets failed operations for an account.
   */
  override suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<Entry> =
    entryDao.getFailedOperations(accountId, maxAttempts).mapNotNull { it.toEntry() }

  /**
   * Clears all unsynced entries for an account.
   */
  override suspend fun clearUnSynced(accountId: String): Int = entryDao.clearUnSynced(accountId)

  /**
   * Sends an operation to the API and marks it as synced if successful. Adds to opstack if failed.
   * @param operation The operation entry to send.
   * @throws Exception if the API call fails.
   */
  override suspend fun sendOperationToAPI(operation: ScaleApiEntry?) {
    try {
      if (operation == null) {
        throw IllegalArgumentException("Operation cannot be null")
      }
      AppLog.d("EntryRepository", "Sending operation to API: ${operation}")
      entryApi.sendOperation(operation)
      AppLog.i("EntryRepository", "Operation sent successfully: ${operation}")
    } catch (e: Exception) {
      AppLog.e("EntryRepository", "Failed to send operation to API: ${operation}", e)
      throw e
    }
  }

  /**
   * Gets operations from the API since the last update, or all if lastUpdated is null.
   * @param lastUpdated The last updated timestamp, or null.
   * @return List of operations.
   */
  override suspend fun getOperationsFromAPI(syncTimeStamp: String): OperationsResponse? {
    AppLog.d("EntryRepository","getOperationsFromAPI - $syncTimeStamp")
    return try {
      val response = if (syncTimeStamp.isNotBlank()) {
        AppLog.d("EntryRepository","getOperationsFromAPI using sync timestamp - $syncTimeStamp")
        entryApi.getOperations(syncTimeStamp)
      } else {
        AppLog.d("EntryRepository","getOperationsFromAPI without sync timestamp - $syncTimeStamp")
        entryApi.getAllOperations()
      }
      response
    } catch (e: Exception) {
      AppLog.e("EntryRepository", "getOperationsFromAPI failed for syncTimeStamp: $syncTimeStamp", e)
      null
    }
  }

  /**
   * Gets the operation count for an account.
   * @param accountId The account ID
   * @return The number of operations
   */
  override suspend fun getOperationCount(accountId: String): Int =
    entryDao.getOperationCount(accountId)

}
