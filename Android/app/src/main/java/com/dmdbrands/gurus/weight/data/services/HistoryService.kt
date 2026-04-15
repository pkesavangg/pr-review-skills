package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.processWeight
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.GroupedHistory
import com.dmdbrands.gurus.weight.domain.model.common.HistoryDetail
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.model.common.BpProgress
import com.dmdbrands.gurus.weight.domain.model.common.Streak
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.IHistoryRepository
import com.dmdbrands.gurus.weight.domain.services.IHistoryService
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of [IHistoryService].
 * Routes by [ProductSelection] to the correct [IHistoryRepository] function
 * and wraps the result in sealed [GroupedHistory] / [HistoryDetail].
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 */
class HistoryService @Inject constructor(
    private val historyRepository: IHistoryRepository,
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val goalRepository: IGoalRepository,
) : IHistoryService {

    private var _accountId: String? = null
    override val accountId: String? get() = _accountId

    override fun setAccountId(accountId: String) {
        AppLog.d(TAG, "setAccountId: $accountId")
        _accountId = accountId
    }

    /**
     * Cold weight-progress stream. Combines the five long-lived inputs
     * (latest entry, last-7/last-30 windows, monthly-history, unit/weightless/goal/account)
     * and re-runs the streak + count + oldest queries on every combined emit.
     *
     * Kept on HistoryService (read side) rather than EntryService (write side) so BP
     * can join the same shape via `bpProgress()` in a later phase.
     */
    override fun weightProgress(): Flow<WeightProgress> = flow {
        val acctId = _accountId ?: run {
            emit(WeightProgress())
            return@flow
        }

        val settingsFlow = combine(
            accountRepository.getActiveAccountWeightUnitFlow(),
            accountRepository.getActiveAccountWeightlessFlow(),
            goalRepository.getCurrentGoal(),
            accountRepository.getActiveAccount(),
        ) { unit, weightless, goal, account ->
            ProgressSettings(unit, weightless, goal, account)
        }.distinctUntilChanged()

        val entriesFlow = combine(
            entryRepository.getLatestEntry(acctId),
            entryRepository.getLastNDaysEntries(acctId, LAST_7_DAYS),
            entryRepository.getLastNDaysEntries(acctId, LAST_30_DAYS),
            entryRepository.getMonthlyHistoryLastYear(acctId),
        ) { latest, last7, last30, monthYear ->
            ProgressEntries(latest, last7, last30, monthYear)
        }

        emitAll(
            combine(entriesFlow, settingsFlow) { entries, settings ->
                computeWeightProgress(acctId, entries, settings)
            },
        )
    }

    override fun latestEntry(): Flow<Entry?> = flow {
        val acctId = _accountId ?: run {
            emit(null)
            return@flow
        }
        emitAll(entryRepository.getLatestEntry(acctId))
    }

    override fun isWeightEmpty(): Flow<Boolean> {
        val acctId = _accountId ?: return flowOf(false)
        return entryRepository.getEntriesByOperationType(acctId, OPERATION_CREATE)
            .map { it.isEmpty() }
            .distinctUntilChanged()
    }

    /**
     * BP progress = streak + day-count derived from a single DAO flow.
     *
     * Current and longest streak are computed in Kotlin from the days list via
     * [EntryServiceHelper] rather than in SQL — the list is small (≤ days-with-BP)
     * and keeps the DAO surface to one query.
     */
    override fun bpProgress(): Flow<BpProgress> {
        val acctId = _accountId ?: return flowOf(BpProgress())
        return entryRepository.getBpmStreakDays(acctId)
            .map { days ->
                BpProgress(
                    streak = Streak(
                        current = EntryServiceHelper.computeCurrentStreakFromDates(days),
                        longest = EntryServiceHelper.computeLongestStreakFromDates(days),
                    ),
                    count = days.size,
                )
            }
            .distinctUntilChanged()
    }

    private suspend fun computeWeightProgress(
        acctId: String,
        entries: ProgressEntries,
        settings: ProgressSettings,
    ): WeightProgress = coroutineScope {
        val unit = settings.unit ?: WeightUnit.LB
        val weightless = settings.weightless
        val initialWeight = settings.account?.initialWeight

        val streakDatesDeferred = async { entryRepository.getStreakData(acctId) }
        val longestDeferred = async { entryRepository.getLongestStreakCount(acctId) }
        val totalDeferred = async { entryRepository.getTotalCount(acctId) }
        // Only hit the oldest-entry query when the account has no explicit starting weight —
        // otherwise the initial-weight field wins and oldest is irrelevant.
        val oldestDeferred = if (initialWeight == null || initialWeight == 0.0) {
            async { entryRepository.getOldestEntry(acctId) }
        } else null

        val currentStreak = EntryServiceHelper.computeCurrentStreakFromDates(streakDatesDeferred.await())
        val longestStreak = longestDeferred.await()
        val totalCount = totalDeferred.await()
        val firstRecordedStored = (oldestDeferred?.await() as? ScaleEntry)
            ?.scale?.scaleEntry?.weight?.toDouble()

        val latestProcessed = entries.latest?.process(unit, weightless)
        val last7Processed = entries.last7.map { it.process(unit, weightless) }
        val last30Processed = entries.last30.map { it.process(unit, weightless) }
        val monthYearProcessed = entries.monthYear.map { it.process(unit, weightless) }

        val startingWeightDisplay = initialWeight
            ?.takeUnless { it == 0.0 }
            ?.let { processWeight(it, unit, weightless) }
        val firstRecordedWeightDisplay = firstRecordedStored?.let { oldestEntryWeightLb ->
            val converted = convertWeight(oldestEntryWeightLb, WeightUnit.LB, unit)
            if (weightless?.isWeightlessOn == true) converted - weightless.weightlessWeight else converted
        }
        val goal = settings.goal?.copy(
            goalWeight = processWeight(settings.goal.goalWeight, unit, weightless),
            account = settings.account,
        )

        calculateProgressPure(
            latestEntry = latestProcessed,
            last7Days = last7Processed,
            last30Days = last30Processed,
            months = monthYearProcessed,
            startingWeightDisplay = startingWeightDisplay,
            firstRecordedWeightDisplay = firstRecordedWeightDisplay,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalCount = totalCount,
            unit = unit,
            goal = goal,
        )
    }

    override fun getGroupedHistory(product: ProductSelection): Flow<GroupedHistory> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getGroupedHistory: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthlyHistory(acctId)
                .map { GroupedHistory.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthlyHistory(acctId)
                .map { GroupedHistory.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyWeeklyHistory(acctId, product.profile.id)
                .map { GroupedHistory.Baby(it) }
        }
    }

    override fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDetail: ${product.productType}, key=$key")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthDetail(acctId, key)
                .map { HistoryDetail.Weight(it) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthDetail(acctId, key)
                .map { HistoryDetail.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyDayDetail(acctId, product.profile.id, key)
                .map { HistoryDetail.Baby(it) }
        }
    }

    override fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getMonthlyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightMonthlyGraphData(acctId)
                .map { list -> GraphData.Weight(list.map { it.scaleWeightToDisplay() }) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmMonthlyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyMonthlyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }
        }
    }

    override fun getDailyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDailyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> historyRepository.getWeightDailyGraphData(acctId)
                .map { list -> GraphData.Weight(list.map { it.scaleWeightToDisplay() }) }

            is ProductSelection.BloodPressure -> historyRepository.getBpmDailyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> historyRepository.getBabyDailyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }
        }
    }

    override fun getWeightSnapshotGraphData(): Flow<List<WeightSnapshotPoint>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getWeightSnapshotGraphData(acctId)
    }

    override fun getBpmSnapshotGraphData(): Flow<List<PeriodBpmSummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBpmSnapshotGraphData(acctId)
    }

    override fun getBpmLastNDayEntries(n: Int): Flow<List<PeriodBpmSummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBpmLastNDayEntries(acctId, n)
    }

    override fun getBabySnapshotGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabySnapshotGraphData(acctId, babyProfileId)
    }

    override fun getBabyDailyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabyDailyGraphData(acctId, babyProfileId)
    }

    override fun getBabyMonthlyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return historyRepository.getBabyMonthlyGraphData(acctId, babyProfileId)
    }

    /**
     * Scales stored weight fields (×10 stored) to display lbs (÷10).
     * Chart-facing conversion only; kept local to the data layer to avoid
     * depending on feature-layer helpers.
     */
    private fun PeriodBodyScaleSummary.scaleWeightToDisplay(): PeriodBodyScaleSummary =
        copy(weight = weight / DISPLAY_SCALE)

    companion object {
        private const val TAG = "HistoryService"
        private const val DISPLAY_SCALE = 10.0
        private const val LAST_7_DAYS = 7
        private const val LAST_30_DAYS = 30
        private const val OPERATION_CREATE = "create"
    }
}

/** Non-entry inputs that feed [HistoryService.computeWeightProgress]. */
private data class ProgressSettings(
    val unit: WeightUnit?,
    val weightless: Weightless?,
    val goal: Goal?,
    val account: Account?,
)

/** Entry-flow inputs that feed [HistoryService.computeWeightProgress]. */
private data class ProgressEntries(
    val latest: Entry?,
    val last7: List<Entry>,
    val last30: List<Entry>,
    val monthYear: List<HistoryMonth>,
)
