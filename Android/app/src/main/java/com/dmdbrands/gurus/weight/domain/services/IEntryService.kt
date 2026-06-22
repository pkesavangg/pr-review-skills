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
   * Persists a baby reading locally under the active (parent) account, keyed by babyId,
   * and returns the new entry id. Baby entries have no server contract yet (MOB-428), so
   * this is a device-local write that is deliberately kept OUT of the sync queue — the sync
   * loop sends every unsynced entry as a ScaleEntry, so a baby entry must never reach it.
   * Server upload is a separate, backend-dependent ticket.
   */
  suspend fun addBabyEntry(entry: BabyEntry): Long

  /**
   * Hard-deletes a locally-saved baby entry by its id (cascades to the baby_entry row).
   * Used by the reading-arrival "Reassign" flow to drop a reading from the previously
   * chosen baby before re-saving it to another (MOB-428). Local-only — no sync side effect.
   */
  suspend fun deleteBabyEntryLocally(entryId: Long)
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
