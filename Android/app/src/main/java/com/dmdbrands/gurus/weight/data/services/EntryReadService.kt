package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
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
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.WeightSnapshotPoint
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryReadRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
/**
 * Implementation of [IEntryReadService].
 * Routes by [ProductSelection] to the correct [IEntryReadRepository] function
 * and wraps the result in sealed [GroupedHistory] / [HistoryDetail].
 * accountId is set via [setAccountId] from LoadingScreenViewModel.
 */
class EntryReadService(
    private val entryReadRepository: IEntryReadRepository,
    private val accountRepository: IAccountRepository,
    private val goalRepository: IGoalRepository,
    private val appScope: CoroutineScope,
) : IEntryReadService {

    @Volatile
    private var _accountId: String? = null
    override val accountId: String? get() = _accountId

    // ── Single hot snapshot map (populated on setAccountId, instant for consumers) ──

    private val _snapshots = MutableStateFlow<PersistentMap<String, List<PeriodSummary>>>(persistentMapOf())
    override val snapshots: StateFlow<Map<String, List<PeriodSummary>>> = _snapshots.asStateFlow()
    private var hotJobs = mutableListOf<Job>()

    @Synchronized
    override fun setAccountId(accountId: String) {
        AppLog.d(TAG, "setAccountId: $accountId")
        _accountId = accountId

        // Cancel previous subscriptions (account switch)
        hotJobs.forEach { it.cancel() }
        hotJobs.clear()
        _snapshots.value = persistentMapOf()

        // Weight snapshot
        hotJobs += appScope.launch {
            entryReadRepository.getWeightSnapshotGraphData(accountId).collect { data ->
                _snapshots.update { it.put(IEntryReadService.KEY_WEIGHT, data) }
            }
        }

        // BP snapshot
        hotJobs += appScope.launch {
            entryReadRepository.getBpmSnapshotGraphData(accountId).collect { data ->
                _snapshots.update { it.put(IEntryReadService.KEY_BP, data) }
            }
        }

        // Baby snapshots — single query, split by babyId into the same map
        hotJobs += appScope.launch {
            entryReadRepository.getAllBabySnapshotGraphData(accountId)
                .map { list -> list.groupBy { it.babyId } }
                .collect { babyMap ->
                    _snapshots.update { current ->
                        val withoutBabies = current.builder().apply {
                            keys.filter { it.startsWith("baby:") }.forEach { remove(it) }
                        }
                        babyMap.forEach { (babyId, data) ->
                            withoutBabies[IEntryReadService.keyBaby(babyId)] = data
                        }
                        withoutBabies.build()
                    }
                }
        }
    }

    /**
     * Cold weight-progress stream. Combines the five long-lived inputs
     * (latest entry, last-7/last-30 windows, monthly-history, unit/weightless/goal/account)
     * and re-runs the streak + count + oldest queries on every combined emit.
     *
     * Kept on EntryReadService (read side) rather than EntryService (write side) so BP
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
            entryReadRepository.getLatestEntry(acctId),
            entryReadRepository.getLastNDaysEntries(acctId, LAST_7_DAYS),
            entryReadRepository.getLastNDaysEntries(acctId, LAST_30_DAYS),
            entryReadRepository.getMonthlyHistoryLastYear(acctId),
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
        emitAll(entryReadRepository.getLatestEntry(acctId))
    }

    override fun isWeightEmpty(): Flow<Boolean> {
        val acctId = _accountId ?: return flowOf(false)
        return entryReadRepository.getEntriesByOperationType(acctId, OPERATION_CREATE)
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
        return entryReadRepository.getBpmStreakDays(acctId)
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

        val streakDatesDeferred = async { entryReadRepository.getStreakData(acctId) }
        val longestDeferred = async { entryReadRepository.getLongestStreakCount(acctId) }
        val totalDeferred = async { entryReadRepository.getTotalCount(acctId) }
        // Only hit the oldest-entry query when the account has no explicit starting weight —
        // otherwise the initial-weight field wins and oldest is irrelevant.
        val oldestDeferred = if (initialWeight == null || initialWeight == 0.0) {
            async { entryReadRepository.getOldestEntry(acctId) }
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
            is ProductSelection.MyWeight -> combine(
                entryReadRepository.getWeightMonthlyHistory(acctId),
                weightUnitFlow(),
            ) { months, (unit, weightless) ->
                GroupedHistory.Weight(months.map { it.process(unit, weightless) })
            }

            is ProductSelection.BloodPressure -> entryReadRepository.getBpmMonthlyHistory(acctId)
                .map { GroupedHistory.BloodPressure(it) }

            is ProductSelection.Baby -> entryReadRepository.getBabyWeeklyHistory(
                acctId,
                product.profile.id,
                product.profile.sex,
                DateTimeConverter.isoToTimestamp(product.profile.birthdate),
            ).map { GroupedHistory.Baby(it) }

            // Baby scale owned but no profile yet: no entries to show. (MOB-416)
            is ProductSelection.BabyScale -> flowOf(GroupedHistory.Baby(emptyList()))
        }
    }

    override fun getDetail(product: ProductSelection, key: String): Flow<HistoryDetail> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDetail: ${product.productType}, key=$key")
        return when (product) {
            is ProductSelection.MyWeight -> combine(
                entryReadRepository.getWeightMonthDetail(acctId, key),
                weightUnitFlow(),
            ) { entries, (unit, weightless) ->
                HistoryDetail.Weight(entries.map { it.process(unit, weightless) }.filterIsInstance<ScaleEntry>())
            }

            is ProductSelection.BloodPressure -> entryReadRepository.getBpmMonthDetail(acctId, key)
                .map { HistoryDetail.BloodPressure(it) }

            is ProductSelection.Baby -> entryReadRepository.getBabyDayDetail(
                acctId,
                product.profile.id,
                key,
                product.profile.sex,
                DateTimeConverter.isoToTimestamp(product.profile.birthdate),
            ).map { HistoryDetail.Baby(it) }

            is ProductSelection.BabyScale -> flowOf(HistoryDetail.Baby(emptyList()))
        }
    }

    override fun getMonthlyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getMonthlyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> entryReadRepository.getWeightMonthlyGraphData(acctId)
                .map { GraphData.Weight(it) }

            is ProductSelection.BloodPressure -> entryReadRepository.getBpmMonthlyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> entryReadRepository.getBabyMonthlyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }

            is ProductSelection.BabyScale -> flowOf(GraphData.Baby(emptyList()))
        }
    }

    override fun getDailyGraphData(product: ProductSelection): Flow<GraphData> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        AppLog.d(TAG, "getDailyGraphData: ${product.productType}")
        return when (product) {
            is ProductSelection.MyWeight -> entryReadRepository.getWeightDailyGraphData(acctId)
                .map { GraphData.Weight(it) }

            is ProductSelection.BloodPressure -> entryReadRepository.getBpmDailyGraphData(acctId)
                .map { GraphData.BloodPressure(it) }

            is ProductSelection.Baby -> entryReadRepository.getBabyDailyGraphData(acctId, product.profile.id)
                .map { GraphData.Baby(it) }

            is ProductSelection.BabyScale -> flowOf(GraphData.Baby(emptyList()))
        }
    }

    override fun getBpmLastNDayEntries(n: Int): Flow<List<PeriodBpmSummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return entryReadRepository.getBpmLastNDayEntries(acctId, n)
    }

    override fun getBabyDailyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return entryReadRepository.getBabyDailyGraphData(acctId, babyProfileId)
    }

    override fun getBabyMonthlyGraphData(babyProfileId: String): Flow<List<PeriodBabySummary>> {
        val acctId = requireNotNull(_accountId) { "accountId not set" }
        return entryReadRepository.getBabyMonthlyGraphData(acctId, babyProfileId)
    }

    /**
     * Unit + weightless settings for processing history display data.
     * Lighter than the full [ProgressSettings] — no goal or account needed.
     */
    private fun weightUnitFlow(): Flow<Pair<WeightUnit?, Weightless?>> =
        combine(
            accountRepository.getActiveAccountWeightUnitFlow(),
            accountRepository.getActiveAccountWeightlessFlow(),
        ) { unit, weightless -> Pair(unit, weightless) }
            .distinctUntilChanged()

    companion object {
        private const val TAG = "EntryReadService"
        private const val LAST_7_DAYS = 7
        private const val LAST_30_DAYS = 30
        private const val OPERATION_CREATE = "create"
    }
}

/** Non-entry inputs that feed [EntryReadService.computeWeightProgress]. */
private data class ProgressSettings(
    val unit: WeightUnit?,
    val weightless: Weightless?,
    val goal: Goal?,
    val account: Account?,
)

/** Entry-flow inputs that feed [EntryReadService.computeWeightProgress]. */
private data class ProgressEntries(
    val latest: Entry?,
    val last7: List<Entry>,
    val last30: List<Entry>,
    val monthYear: List<HistoryMonth>,
)
