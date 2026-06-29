// domain/service/IEntryService.kt
package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import kotlinx.coroutines.flow.StateFlow

/**
 * Write + sync surface for entries. Read flows (progress, latestEntry, isEmpty)
 * live on [IEntryReadService] — this interface is deliberately narrow.
 */
interface IEntryService {
  /** Mirrors [IEntrySyncService.isUpdating]; exposed here for VMs that already inject EntryService. */
  val isUpdating: StateFlow<Boolean>

  suspend fun updateAllData(accountId: String?)
  suspend fun addEntry(entry: Entry)
  suspend fun addEntry(entries: List<Entry>)

  /** Updates only an entry's note locally (e.g. editing from History). Device-local; see MOB-438. */
  suspend fun updateNote(entry: Entry, note: String?)
  suspend fun deleteEntry(entry: Entry)

  /**
   * Restores a soft-deleted entry (Undo from the History delete toast). Re-stamps the row as
   * create, upserts it in place, and re-syncs so the reading reappears locally and on the server.
   */
  suspend fun restoreEntry(entry: Entry)

  /**
   * Persists a baby reading under the active (parent) account, keyed by babyId, and returns
   * the new local entry id. The Me App 2.0 unified API now carries baby entries
   * (POST /v3/entries/, category=baby — §2.16), so the reading is written unsynced and pushed
   * to the server via the sync loop (mapped by [BabyEntry.toUnifiedRequest]). The returned id
   * lets the reading-arrival "Reassign" flow remove this row before re-saving to another baby.
   */
  suspend fun addBabyEntry(entry: BabyEntry): Long

  /**
   * Edits an existing baby reading in place. The row keeps its local id and is re-stamped
   * operationType=edit, then pushed to POST /v3/entries/ (category=baby — §2.16, the only
   * category that supports `edit`) on the same endpoint as create. Used when editing a reading
   * from the History detail screen. Does NOT delete + re-create (which collided on the shared id
   * and resolved to a delete).
   */
  suspend fun editBabyEntry(entry: BabyEntry)

  /**
   * Removes a previously-assigned baby entry by its id. The row is marked operationType=delete
   * and pushed to POST /v3/entries/ (category=baby — §2.16) so the deletion propagates to the
   * server, then dropped locally. Used by the reading-arrival "Reassign" flow to move a reading
   * off the previously chosen baby before re-saving it to another.
   */
  suspend fun deleteBabyEntry(entryId: Long)
  suspend fun syncOperations(
    newEntries: List<Entry> = emptyList(),
    deleteOps: List<Entry> = emptyList(),
  )

  /**
   * Watches sync completions and triggers goal-card reveal once the account has
   * ≥ 3 scale entries. Launched once from AppViewModel after account load.
   */
  fun initializeGoalCardMonitoring(accountId: String)
}
