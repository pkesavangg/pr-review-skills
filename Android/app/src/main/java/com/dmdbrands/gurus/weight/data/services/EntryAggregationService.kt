package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.processWeight
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryAggregationService
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class EntryAggregationService(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    goalRepository: IGoalRepository,
) : IEntryAggregationService {

    private val TAG = "EntryAggregationService"

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var accountId: String? = null
    private var initialWeight: Double? = null

    private val weightSettingsFlow = combine(
        accountRepository.getActiveAccountWeightUnitFlow(),
        accountRepository.getActiveAccountWeightlessFlow(),
        goalRepository.getCurrentGoal(),
    ) { weightUnit, weightless, goal ->
        WeightSettings(weightUnit, weightless, goal)
    }.distinctUntilChanged()

    private val _latestEntry = MutableStateFlow<Entry?>(null)
    override val latestEntry: StateFlow<Entry?> = _latestEntry.asStateFlow()

    private val _last7Days = MutableStateFlow<List<Entry>>(listOf())
    override val last7Days: StateFlow<List<Entry>> = _last7Days.asStateFlow()

    private val _last30Days = MutableStateFlow<List<Entry>>(listOf())
    override val last30Days: StateFlow<List<Entry>> = _last30Days.asStateFlow()

    private val _monthYear = MutableStateFlow<List<HistoryMonth>>(listOf())
    private val monthYear: StateFlow<List<HistoryMonth>> = _monthYear.asStateFlow()

    private val _monthlyBodyScaleAverages = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
    override val monthlyBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> = _monthlyBodyScaleAverages.asStateFlow()

    private val _monthlyBodyScaleLatest = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
    override val monthlyBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> = _monthlyBodyScaleLatest.asStateFlow()

    private val _daywiseBodyScaleAverages = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
    override val daywiseBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> = _daywiseBodyScaleAverages.asStateFlow()

    private val _daywiseBodyScaleLatest = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
    override val daywiseBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> = _daywiseBodyScaleLatest.asStateFlow()

    private val _monthlyAverage = MutableStateFlow<List<HistoryMonth>>(listOf())
    override val monthlyAverage: StateFlow<List<HistoryMonth>> = _monthlyAverage.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    private val _longestStreak = MutableStateFlow(0)
    private val _totalCount = MutableStateFlow(0)

    /** Stored-format weight (0.1 lb) from oldest entry; null when account has initial weight. */
    private val _cachedStartingWeightStored = MutableStateFlow<Double?>(null)

    /** Bumped when progress cache is updated so progress Flow re-emits without adding more flows to combine. */
    private val _progressCacheVersion = MutableStateFlow(0)

    override val progress: Flow<Progress> =
        combine(
            combine(
                _latestEntry,
                _last7Days,
                _last30Days,
                weightSettingsFlow,
                _monthYear,
            ) { latest, last7, last30, weightSettings, monthYear ->
                ProgressFlowInputs(latest, last7, last30, weightSettings, monthYear)
            },
            _progressCacheVersion.asStateFlow(),
            accountRepository.getActiveAccount().map { it?.initialWeight }.distinctUntilChanged(),
        ) { inputs, _, initialWeight ->
            if (accountId == null) {
                Progress()
            } else {
                val startMs = System.currentTimeMillis()
                val weightSettings = inputs.weightSettings
                val unit = weightSettings.weightUnit ?: WeightUnit.LB
                val weightless = weightSettings.weightless
                val latestProcessed = inputs.latest?.process(unit, weightless)
                val last7Processed = inputs.last7.map { it.process(unit, weightless) }
                val last30Processed = inputs.last30.map { it.process(unit, weightless) }
                val monthYearProcessed = inputs.monthYear.map { it.process(unit, weightless) }
                val startingWeightDisplay = initialWeight
                    ?.takeUnless { it == 0.0 }
                    ?.let { processWeight(it, unit, weightless) }
                val firstRecordedWeightDisplay = _cachedStartingWeightStored.value?.let { oldestEntryWeightLb ->
                    val converted = convertWeight(oldestEntryWeightLb, WeightUnit.LB, unit)
                    if (weightless?.isWeightlessOn == true) converted - weightless.weightlessWeight else converted
                }
                val account = accountRepository.getActiveAccount().first()
                val goal = weightSettings.goal?.copy(
                    goalWeight = processWeight(weightSettings.goal.goalWeight, unit, weightless),
                    account = account,
                )
                val result = calculateProgressPure(
                    latestEntry = latestProcessed,
                    last7Days = last7Processed,
                    last30Days = last30Processed,
                    months = monthYearProcessed,
                    startingWeightDisplay = startingWeightDisplay,
                    firstRecordedWeightDisplay = firstRecordedWeightDisplay,
                    currentStreak = _currentStreak.value,
                    longestStreak = _longestStreak.value,
                    totalCount = _totalCount.value,
                    unit = unit,
                    goal = goal,
                )
                AppLog.d(TAG, "Progress emission took ${System.currentTimeMillis() - startMs}ms (cached DB)")
                result
            }
        }

    override fun setAccountId(accountId: String?, initialWeight: Double?) {
        this.accountId = accountId
        this.initialWeight = initialWeight
        _progressCacheVersion.value = _progressCacheVersion.value + 1
    }

    override fun startDataCollection(accountId: String) {
        serviceScope.launch { updateLatestEntry(accountId) } // also triggers updateProgressCache reactively
        serviceScope.launch { updateLast7Days(accountId) }
        serviceScope.launch { updateLast30Days(accountId) }
        serviceScope.launch { updateMonthYear(accountId) }
        serviceScope.launch { updateMonthlyBodyScaleAveragesWithJoin() }
        serviceScope.launch { updateDaywiseBodyScaleAveragesWithJoin() }
        serviceScope.launch { updateMonthlyAverage(accountId) }
    }

    override fun clearFlows() {
        serviceScope.cancel()
        _latestEntry.value = null
        _last7Days.value = emptyList()
        _last30Days.value = emptyList()
        _monthYear.value = emptyList()
        _monthlyBodyScaleAverages.value = emptyList()
        _monthlyBodyScaleLatest.value = emptyList()
        _daywiseBodyScaleAverages.value = emptyList()
        _daywiseBodyScaleLatest.value = emptyList()
        _currentStreak.value = 0
        _longestStreak.value = 0
        _totalCount.value = 0
        _cachedStartingWeightStored.value = null
        _progressCacheVersion.value = 0
        accountId = null
        initialWeight = null
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override suspend fun refreshEntryData() {
        val currentAccountId = accountId ?: return
        try {
            updateProgressCache(currentAccountId)
            AppLog.d(TAG, "Entry data refreshed - streak values should update")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error refreshing entry data", e)
        }
    }

    override suspend fun getMonthlyAverage(accountId: String): Flow<List<HistoryMonth>> =
        combine(
            entryRepository.getMonthlyAverage(accountId),
            weightSettingsFlow,
        ) { months, weightSettings ->
            months.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }

    override suspend fun monthDetails(startDate: String): Flow<List<Entry>> {
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val date = YearMonth.parse(startDate, formatter)
        val monthParam = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return combine(
            entryRepository.getMonthDetail(accountId ?: "", monthParam),
            weightSettingsFlow,
        ) { entries, weightSettings ->
            entries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }
    }

    private suspend fun updateLatestEntry(accountId: String) {
        try {
            entryRepository.getLatestEntry(accountId).collect { latest ->
                _latestEntry.value = latest
                serviceScope.launch { updateProgressCache(accountId) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating latest entry", e)
        }
    }

    private suspend fun updateLast7Days(accountId: String) {
        try {
            entryRepository.getLastNDaysEntries(accountId, 7).collect { entries ->
                _last7Days.value = entries
                AppLog.d(TAG, "Updated last 7 days: ${entries.size} entries")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating last 7 days", e)
            _last7Days.value = emptyList()
        }
    }

    private suspend fun updateLast30Days(accountId: String) {
        try {
            entryRepository.getLastNDaysEntries(accountId, 30).collect { entries ->
                _last30Days.value = entries
                AppLog.d(TAG, "Updated last 30 days: ${entries.size} entries")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating last 30 days", e)
            _last30Days.value = emptyList()
        }
    }

    private suspend fun updateMonthYear(accountId: String) {
        try {
            entryRepository.getMonthlyHistoryLastYear(accountId).collect {
                _monthYear.value = it
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating month year", e)
        }
    }

    /**
     * Updates cached progress-related values (streak, count, starting weight) from the database.
     * Runs four DB queries in parallel to avoid DB hits on every progress emission.
     */
    private suspend fun updateProgressCache(accountId: String) {
        try {
            coroutineScope {
                val entryDatesDeferred = async { entryRepository.getStreakData(accountId) }
                val longestDeferred = async { entryRepository.getLongestStreakCount(accountId) }
                val totalDeferred = async { entryRepository.getTotalCount(accountId) }
                val oldestDeferred =
                    if (initialWeight == null || initialWeight == 0.0) {
                        async { entryRepository.getOldestEntry(accountId) }
                    } else {
                        null
                    }
                val entryDates = entryDatesDeferred.await()
                _currentStreak.value = EntryServiceHelper.computeCurrentStreakFromDates(entryDates)
                _longestStreak.value = longestDeferred.await()
                _totalCount.value = totalDeferred.await()
                _cachedStartingWeightStored.value =
                    if (oldestDeferred != null) {
                        (oldestDeferred.await() as? ScaleEntry)?.scale?.scaleEntry?.weight?.toDouble()
                    } else {
                        null
                    }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating progress cache", e)
            _currentStreak.value = 0
            _longestStreak.value = 0
            _totalCount.value = 0
            _cachedStartingWeightStored.value = null
        }
        _progressCacheVersion.value = _progressCacheVersion.value + 1
    }

    private suspend fun updateMonthlyBodyScaleAveragesWithJoin() {
        try {
            getMonthlyBodyScaleAveragesWithJoin().collect {
                _monthlyBodyScaleAverages.value = it
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating monthly entry averages", e)
        }
    }

    private suspend fun updateDaywiseBodyScaleAveragesWithJoin() {
        try {
            getDaywiseBodyScaleAveragesWithJoin().collect {
                _daywiseBodyScaleAverages.value = it
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating day wise entry averages", e)
        }
    }

    private suspend fun updateMonthlyAverage(accountId: String) {
        try {
            getMonthlyAverage(accountId).collect { months ->
                _monthlyAverage.value = months
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error updating monthly average", e)
        }
    }

    private fun getMonthlyBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
        combine(
            entryRepository.getMonthlyBodyScaleAveragesWithJoin(accountId ?: ""),
            weightSettingsFlow,
        ) { summaries, weightSettings ->
            summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }

    private fun getMonthlyBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
        combine(
            entryRepository.getMonthlyBodyScaleLatestWithJoin(accountId ?: ""),
            weightSettingsFlow,
        ) { summaries, weightSettings ->
            summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }

    private fun getDaywiseBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
        combine(
            entryRepository.getDaywiseBodyScaleAveragesWithJoin(accountId ?: ""),
            weightSettingsFlow,
        ) { summaries, weightSettings ->
            summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }

    private fun getDaywiseBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
        combine(
            entryRepository.getDaywiseBodyScaleLatestWithJoin(accountId ?: ""),
            weightSettingsFlow,
        ) { summaries, weightSettings ->
            summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
        }
}
