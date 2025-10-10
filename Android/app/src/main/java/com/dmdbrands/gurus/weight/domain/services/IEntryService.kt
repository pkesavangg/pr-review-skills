// domain/service/IEntryService.kt
package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IEntryService {
  val isUpdating: StateFlow<Boolean>
  val isEmpty: StateFlow<Boolean>
  val latestEntry: StateFlow<Entry?>
  val last7Days: StateFlow<List<Entry>>
  val last30Days: StateFlow<List<Entry>>
  val progress: Flow<Progress>
  val lastUpdated: StateFlow<Long?>
  val monthlyBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>>
  val monthlyBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>>
  val daywiseBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>>
  val daywiseBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>>
  suspend fun getMonthlyAverage(): Flow<List<HistoryMonth>>

  suspend fun monthDetails(startDate: String): Flow<List<Entry>>

  suspend fun updateAccountId(accountId: String?)
  suspend fun addEntry(entry: Entry)
  suspend fun addEntry(entries: List<Entry>)
  suspend fun deleteEntry(entry: Entry)
  suspend fun syncOperations(
    newEntries: List<Entry> = emptyList(),
    deleteOps: List<Entry> = emptyList()
  )

  fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>>

  /**
   * Initializes goal card monitoring by checking entry count and setting up listeners.
   * This function monitors the lastUpdated flow and checks if the user has enough entries
   * to display the goal card.
   */
  fun initializeGoalCardMonitoring()
}
