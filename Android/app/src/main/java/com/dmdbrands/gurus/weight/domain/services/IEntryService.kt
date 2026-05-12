// domain/service/IEntryService.kt
package com.dmdbrands.gurus.weight.domain.services

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
  suspend fun deleteEntry(entry: Entry)
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
