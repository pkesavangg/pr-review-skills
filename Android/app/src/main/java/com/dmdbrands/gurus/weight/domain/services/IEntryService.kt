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
  val latestEntry: StateFlow<Entry?>
  val last7Days: StateFlow<List<Entry>>
  val last30Days: StateFlow<List<Entry>>
  val progress: Flow<Progress>
  val lastUpdated: StateFlow<Long?>
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
   * Gets monthly averages of body scale data for an account using JOINs.
   * @return Flow of monthly averages as PeriodBodyScaleSummary.
   */
  fun getMonthlyBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>>

  /**
   * Gets the latest body scale entry for each month for an account using JOINs.
   * @return Flow of latest entries per month as PeriodBodyScaleSummary.
   */
  fun getMonthlyBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>>

  /**
   * Gets daywise averages of body scale data for an account using JOINs.
   * @return Flow of daywise averages as PeriodBodyScaleSummary.
   */
  fun getDaywiseBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>>

  /**
   * Gets the latest body scale entry for each day for an account using JOINs.
   * @return Flow of latest entries per day as PeriodBodyScaleSummary.
   */
  fun getDaywiseBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>>
}
