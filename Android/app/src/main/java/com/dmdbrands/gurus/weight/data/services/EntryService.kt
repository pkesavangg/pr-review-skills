// domain/service/EntryService.kt
package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryAggregationService
import com.dmdbrands.gurus.weight.domain.services.IEntryCrudService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EntryService(
    private val crudService: IEntryCrudService,
    private val syncService: IEntrySyncService,
    private val aggregationService: IEntryAggregationService,
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val goalService: IGoalService,
    private val appScope: CoroutineScope,
) : IEntryService {

    private val activeJobs = mutableListOf<Job>()

    private var accountId: String? = null
    private var initialWeight: Double? = null

    private val _isEmpty = MutableStateFlow(false)
    override val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

    override val isUpdating: StateFlow<Boolean> get() = syncService.isUpdating
    override val lastUpdated: StateFlow<Long?> get() = syncService.lastUpdated
    override val latestEntry: StateFlow<Entry?> get() = aggregationService.latestEntry
    override val last7Days: StateFlow<List<Entry>> get() = aggregationService.last7Days
    override val last30Days: StateFlow<List<Entry>> get() = aggregationService.last30Days
    override val monthlyBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> get() = aggregationService.monthlyBodyScaleAverages
    override val monthlyBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> get() = aggregationService.monthlyBodyScaleLatest
    override val daywiseBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> get() = aggregationService.daywiseBodyScaleAverages
    override val daywiseBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> get() = aggregationService.daywiseBodyScaleLatest
    override val monthlyAverage: StateFlow<List<HistoryMonth>> get() = aggregationService.monthlyAverage
    override val progress: Flow<Progress> get() = aggregationService.progress

    override suspend fun updateAllData(accountId: String?) {
        if (accountId == null) return
        clearAllData()
        this.accountId = accountId

        try {
            val account = accountRepository.getActiveAccount().first()
            this.initialWeight = account?.initialWeight
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error updating account flows", e)
        }

        aggregationService.setAccountId(accountId, initialWeight)
        syncService.syncOperations(accountId)

        activeJobs += appScope.launch {
            entryRepository.getEntriesByOperationType(accountId, "create").collect {
                if (_isEmpty.value != it.isEmpty()) _isEmpty.value = it.isEmpty()
            }
        }

        aggregationService.startDataCollection(accountId)
    }

    fun clearAllData() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        _isEmpty.value = false
        accountId = null
        initialWeight = null
        syncService.reset()
        aggregationService.clearFlows()
    }

    override suspend fun addEntry(entry: Entry) = crudService.addEntry(entry, accountId)

    override suspend fun addEntry(entries: List<Entry>) = crudService.addEntry(entries, accountId)

    override suspend fun deleteEntry(entry: Entry) = crudService.deleteEntry(entry, accountId)

    override suspend fun syncOperations(newEntries: List<Entry>, deleteOps: List<Entry>) {
        val currentAccountId = accountId ?: return
        syncService.syncOperations(currentAccountId, newEntries, deleteOps)
    }

    override suspend fun refreshEntryData() = aggregationService.refreshEntryData()

    override suspend fun getMonthlyAverage(accountId: String): Flow<List<HistoryMonth>> =
        aggregationService.getMonthlyAverage(accountId)

    override suspend fun monthDetails(startDate: String): Flow<List<Entry>> =
        aggregationService.monthDetails(startDate)

    override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>> =
        entryRepository.getEntriesByDeviceType(accountId, deviceType)

    override fun initializeGoalCardMonitoring(accountId: String) {
        activeJobs += appScope.launch {
            syncService.lastUpdated.collect {
                try {
                    val entries = entryRepository.getEntriesByAccount(accountId, false)
                    AppLog.d("EntryService", "User has scale entries (>= 3), checking goal card ${entries.size} - accountid - $accountId")
                    if (entries.size >= 3) {
                        goalService.checkGoalCard()
                        AppLog.d("EntryService", "User has scale entries (>= 3), checking goal card")
                    } else {
                        AppLog.d("EntryService", "User has only scale entries, not enough for goal card")
                    }
                } catch (e: Exception) {
                    AppLog.e("EntryService", "Error checking entries for goal card in init", e.toString())
                }
            }
        }
    }
}