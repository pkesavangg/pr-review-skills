package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekGroup
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import kotlinx.coroutines.flow.Flow

/**
 * Repository for history read queries across all product types.
 * Single interface, single implementation — delegates to [EntryReadDao].
 */
interface IEntryReadRepository {

    // Weight
    fun getWeightMonthlyHistory(accountId: String): Flow<List<HistoryMonth>>
    fun getWeightMonthDetail(accountId: String, month: String): Flow<List<ScaleEntry>>

    // BPM
    fun getBpmMonthlyHistory(accountId: String): Flow<List<BpHistoryMonth>>
    fun getBpmMonthDetail(accountId: String, month: String): Flow<List<BpmEntry>>

    // Baby
    fun getBabyWeeklyHistory(accountId: String, babyId: String): Flow<List<BabyWeekGroup>>
    fun getBabyDayDetail(accountId: String, babyId: String, date: String): Flow<List<BabyEntry>>

    // Weight Graph
    fun getWeightMonthlyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>>
    fun getWeightDailyGraphData(accountId: String): Flow<List<PeriodBodyScaleSummary>>

    // Weight Snapshot (Dashboard mini-chart)
    fun getWeightSnapshotGraphData(accountId: String): Flow<List<WeightSnapshotPoint>>

    // BPM Graph
    fun getBpmMonthlyGraphData(accountId: String): Flow<List<PeriodBpmSummary>>
    fun getBpmDailyGraphData(accountId: String): Flow<List<PeriodBpmSummary>>

    // BPM Snapshot (Dashboard mini-chart)
    fun getBpmSnapshotGraphData(accountId: String): Flow<List<PeriodBpmSummary>>

    // BPM Last-N-days (one per-day avg per row, most recent first) — per-account,
    // not scoped to any chart window. Used by the three-reading-average card/sheet.
    fun getBpmLastNDayEntries(accountId: String, n: Int): Flow<List<PeriodBpmSummary>>

    // Baby Graph
    fun getBabyMonthlyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>
    fun getBabyDailyGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>

    // Baby Snapshot (Dashboard mini-chart)
    fun getBabySnapshotGraphData(accountId: String, babyId: String): Flow<List<PeriodBabySummary>>

    // ---------------------------------------------------------------------------
    // Cross-product read queries (moved from IEntryRepository)
    // ---------------------------------------------------------------------------

    /** Latest entry for an account (all product types). */
    suspend fun getLatestEntry(accountId: String): Flow<Entry?>

    /** Entries for the last N days for an account. */
    suspend fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<Entry>>

    /** Entries by operation type for an account. */
    fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<Entry>>

    /** Monthly history for the last 365 days. */
    fun getMonthlyHistoryLastYear(accountId: String): Flow<List<HistoryMonth>>

    /** Oldest entry for an account. */
    suspend fun getOldestEntry(accountId: String): Entry?

    /** Entry timestamps for streak calculation (one per day, newest first). */
    suspend fun getStreakData(accountId: String): List<String>

    /** Longest consecutive-day streak count. */
    suspend fun getLongestStreakCount(accountId: String): Int

    /** Total entry count for an account. */
    suspend fun getTotalCount(accountId: String): Int

    /** Days with at least one BP reading (newest first). */
    fun getBpmStreakDays(accountId: String): Flow<List<String>>
}
