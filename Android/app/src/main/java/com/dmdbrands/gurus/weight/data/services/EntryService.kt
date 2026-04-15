// domain/service/EntryService.kt
package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryCrudService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EntryService(
    private val crudService: IEntryCrudService,
    private val syncService: IEntrySyncService,
    private val entryRepository: IEntryRepository,
    private val goalService: IGoalService,
    private val appScope: CoroutineScope,
) : IEntryService {

    private val activeJobs = mutableListOf<Job>()

    private var accountId: String? = null

    override val isUpdating: StateFlow<Boolean> get() = syncService.isUpdating

    override suspend fun updateAllData(accountId: String?) {
        if (accountId == null) return
        clearAllData()
        this.accountId = accountId
        syncService.syncOperations(accountId)
    }

    private fun clearAllData() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        accountId = null
        syncService.reset()
    }

    override suspend fun addEntry(entry: Entry) = crudService.addEntry(entry, accountId)

    override suspend fun addEntry(entries: List<Entry>) = crudService.addEntry(entries, accountId)

    override suspend fun deleteEntry(entry: Entry) = crudService.deleteEntry(entry, accountId)

    override suspend fun syncOperations(newEntries: List<Entry>, deleteOps: List<Entry>) {
        val currentAccountId = accountId ?: return
        syncService.syncOperations(currentAccountId, newEntries, deleteOps)
    }

    override fun initializeGoalCardMonitoring(accountId: String) {
        activeJobs += appScope.launch {
            syncService.lastUpdated.collect {
                try {
                    val entries = entryRepository.getEntriesByAccount(accountId, false)
                    AppLog.d(TAG, "User has scale entries (>= 3), checking goal card ${entries.size} - accountid - $accountId")
                    if (entries.size >= GOAL_CARD_MIN_ENTRIES) {
                        goalService.checkGoalCard()
                        AppLog.d(TAG, "User has scale entries (>= 3), checking goal card")
                    } else {
                        AppLog.d(TAG, "User has only scale entries, not enough for goal card")
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error checking entries for goal card in init", e.toString())
                }
            }
        }
    }

    companion object {
        private const val TAG = "EntryService"
        private const val GOAL_CARD_MIN_ENTRIES = 3
    }
}
