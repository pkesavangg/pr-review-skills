package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IEntryAggregationService {
    val latestEntry: StateFlow<Entry?>
    val last7Days: StateFlow<List<Entry>>
    val last30Days: StateFlow<List<Entry>>
    val monthlyBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>>
    val monthlyBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>>
    val daywiseBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>>
    val daywiseBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>>
    val monthlyAverage: StateFlow<List<HistoryMonth>>
    val progress: Flow<WeightProgress>

    fun setAccountId(accountId: String?, initialWeight: Double?)
    fun startDataCollection(accountId: String)
    fun clearFlows()
    suspend fun refreshEntryData()
    suspend fun getMonthlyAverage(accountId: String): Flow<List<HistoryMonth>>
    suspend fun monthDetails(startDate: String): Flow<List<Entry>>
}