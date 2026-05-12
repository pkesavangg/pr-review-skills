package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.BpProgress
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Service for history data across all product types.
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 *
 * Hot StateFlows (populated on [setAccountId], instant for consumers):
 * - [weightSnapshot], [bpmSnapshot] — dashboard mini-charts
 * - [weightHistory] — history screen month list
 *
 * Cold Flows (query on collect, skeleton masks the delay):
 * - [weightProgress], [bpProgress], [latestEntry], [isWeightEmpty]
 * - [getDailyGraphData], [getMonthlyGraphData] — chart data
 * - [getGroupedHistory], [getDetail] — on-demand
 */
interface IEntryReadService {

    val accountId: String?

    fun setAccountId(accountId: String)

    // ── Hot snapshot map (populated on setAccountId, instant for consumers) ──

    /**
     * All snapshot data in a single map — populated on [setAccountId].
     * Keys: [KEY_WEIGHT], [KEY_BP], `"baby:{profileId}"`.
     * Consumers collect this once and extract per-product data.
     */
    val snapshots: StateFlow<Map<String, List<PeriodSummary>>>

    companion object {
        const val KEY_WEIGHT = "weight"
        const val KEY_BP = "bp"
        fun keyBaby(profileId: String) = "baby:$profileId"
    }

    // ── Cold Flows (skeleton masks delay) ──

    fun weightProgress(): Flow<WeightProgress>
    fun latestEntry(): Flow<Entry?>
    fun isWeightEmpty(): Flow<Boolean>
    fun bpProgress(): Flow<BpProgress>

    fun getGroupedHistory(product: ProductSelection): Flow<GroupedHistory>
    fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail>

    fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData>
    fun getDailyGraphData(product: ProductSelection): Flow<GraphData>

    fun getBpmLastNDayEntries(n: Int): Flow<List<PeriodBpmSummary>>

    fun getBabyDailyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>>
    fun getBabyMonthlyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>>
}
