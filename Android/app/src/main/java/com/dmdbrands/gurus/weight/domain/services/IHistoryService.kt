package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import kotlinx.coroutines.flow.Flow

/**
 * Service for history data across all product types.
 * Takes [ProductSelection] and returns sealed types — caller doesn't need to know
 * which repository function runs internally.
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 */
interface IHistoryService {

    /** Current account ID, set from LoadingScreenViewModel during startup. */
    val accountId: String?

    /** Called from LoadingScreenViewModel.loadData() — same pattern as other services. */
    fun setAccountId(accountId: String)

    fun getGroupedHistory(product: ProductSelection): Flow<GroupedHistory>

    fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail>

    fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData>

    fun getDailyGraphData(product: ProductSelection): Flow<GraphData>

    fun getWeightSnapshotGraphData(): Flow<List<WeightSnapshotPoint>>

    fun getBpmSnapshotGraphData(): Flow<List<PeriodBpmSummary>>

    /**
     * Last [n] per-day BP averages for the current account (most recent day first).
     * Scoped to the account — NOT to any selected chart window. Fewer than [n] rows
     * are returned when the account has BP entries on < [n] days.
     */
    fun getBpmLastNDayEntries(n: Int): Flow<List<PeriodBpmSummary>>

    fun getBabySnapshotGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>>

    fun getBabyDailyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>>

    fun getBabyMonthlyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>>
}
