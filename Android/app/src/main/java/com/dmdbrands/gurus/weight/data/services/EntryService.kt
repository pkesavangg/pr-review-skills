// domain/service/EntryService.kt
package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.processWeight
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry.Companion.fromScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class combining weight unit and weightless settings for efficient flow operations.
 */
data class WeightSettings(
  val weightUnit: WeightUnit?,
  val weightless: Weightless?,
  val goal: Goal?
)

/** Holder for the five flows combined for progress; used to combine with progressCacheVersion. */
private data class ProgressFlowInputs(
  val latest: Entry?,
  val last7: List<Entry>,
  val last30: List<Entry>,
  val weightSettings: WeightSettings,
  val monthYear: List<HistoryMonth>,
)

@Singleton
class EntryService
@Inject
constructor(
  private val entryRepository: IEntryRepository,
  goalRepository: IGoalRepository,
  private val accountRepository: IAccountRepository,
  private val goalService: IGoalService,
  private val healthConnectService: IHealthConnectService,
  private val healthConnectRepository: IHealthConnectRepository,
) : IEntryService {

  companion object {
    private const val GOAL_ALERT_DELAY_MS = 3000L
    /**
     * Weight conversion factor to convert from stored weight unit to pounds.
     * Weight is stored in 0.1 lb increments (e.g., 150.5 lbs is stored as 1505),
     * so multiplying by 10 converts to full pounds for the goal alert service.
     */
    private const val WEIGHT_CONVERSION_FACTOR = 10
  }

  private val _isEmpty = MutableStateFlow(false)
  override val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

  private val _isUpdating = MutableStateFlow(false)
  override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

  private val _latestEntry = MutableStateFlow<Entry?>(null)
  override val latestEntry: StateFlow<Entry?> = _latestEntry.asStateFlow()

  private val _last7Days = MutableStateFlow<List<Entry>>(listOf())
  override val last7Days = _last7Days.asStateFlow()

  private val _last30Days = MutableStateFlow<List<Entry>>(listOf())
  override val last30Days = _last30Days.asStateFlow()

  private val _monthYear = MutableStateFlow<List<HistoryMonth>>(listOf())
  private val monthYear: StateFlow<List<HistoryMonth>> = _monthYear.asStateFlow()

  // Add new MutableStateFlow properties for body scale data
  private val _monthlyBodyScaleAverages = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
  override val monthlyBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> =
    _monthlyBodyScaleAverages.asStateFlow()

  private val _monthlyBodyScaleLatest = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
  override val monthlyBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> = _monthlyBodyScaleLatest.asStateFlow()

  private val _daywiseBodyScaleAverages = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
  override val daywiseBodyScaleAverages: StateFlow<List<PeriodBodyScaleSummary>> =
    _daywiseBodyScaleAverages.asStateFlow()

  private val _daywiseBodyScaleLatest = MutableStateFlow<List<PeriodBodyScaleSummary>>(listOf())
  override val daywiseBodyScaleLatest: StateFlow<List<PeriodBodyScaleSummary>> = _daywiseBodyScaleLatest.asStateFlow()

  private val _monthlyAverage = MutableStateFlow<List<HistoryMonth>>(listOf())
  override val monthlyAverage: StateFlow<List<HistoryMonth>> = _monthlyAverage.asStateFlow()

  private var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  // Combined flow for account properties - initialized with defaults
  private val weightSettingsFlow = combine(
    accountRepository.getActiveAccountWeightUnitFlow(),
    accountRepository.getActiveAccountWeightlessFlow(),
    goalRepository.getCurrentGoal(),
  ) { weightUnit, weightless, goal ->
    WeightSettings(weightUnit, weightless, goal)
  }.distinctUntilChanged()

  private val _lastUpdated = MutableStateFlow<Long?>(null)
  override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

  /** Cached progress-related values; updated only when entry data is refreshed to avoid DB hits on every progress emission. */
  private val _currentStreak = MutableStateFlow(0)
  private val _longestStreak = MutableStateFlow(0)
  private val _totalCount = MutableStateFlow(0)
  /** Stored-format weight (0.1 lb) when starting weight is derived from oldest entry; null when account has initial weight. */
  private val _cachedStartingWeightStored = MutableStateFlow<Double?>(null)
  /** Bumped when progress cache is updated so progress Flow re-emits without adding more flows to combine. */
  private val _progressCacheVersion = MutableStateFlow(0)

  private var accountId: String? = null
  private var initialWeight: Double? = null

  /**
   * Initializes goal card monitoring by checking entry count and setting up listeners.
   * This function monitors the lastUpdated flow and checks if the user has enough entries
   * to display the goal card. Also refreshes entry data to trigger progress recalculation.
   */
  override fun initializeGoalCardMonitoring(accountId: String) {
    repositoryScope.launch {
      lastUpdated.collect { lastUpdatedValue ->
        try {
          // This collector only handles goal card checking
          val entries = entryRepository.getEntriesByAccount(accountId, false)
          AppLog.d("EntryService", "User has  scale entries (>= 3), checking goal card ${entries.size} - accountid - $accountId")
          if (entries.size >= 3) {
            goalService.checkGoalCard()
            AppLog.d("EntryService", "User has  scale entries (>= 3), checking goal card")
          } else {
            AppLog.d("EntryService", "User has only  scale entries, not enough for goal card")
          }
          // Set up collector to refresh entry data when lastUpdated changes
        } catch (e: Exception) {
          AppLog.e("EntryService", "Error checking entries for goal card in init", e.toString())
        }
      }
    }
  }

  override suspend fun getMonthlyAverage(accountId: String): Flow<List<HistoryMonth>> =
    combine(
      entryRepository.getMonthlyAverage(accountId),
      weightSettingsFlow,
    ) { months, weightSettings ->
      months.map {
        it.process(weightSettings.weightUnit, weightSettings.weightless)
      }
    }

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
    ) { inputs, _ ->
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
        val account = accountRepository.getActiveAccount().first()
        val startingWeightDisplay = account?.initialWeight
          ?.takeUnless { it == 0.0 }
          ?.let {
          processWeight(it, unit, weightless)
        }
        val firstRecordedWeightDisplay = _cachedStartingWeightStored.value?.let { oldestEntryWeightLb ->
          val converted = convertWeight(oldestEntryWeightLb, WeightUnit.LB, unit)
          if (weightless?.isWeightlessOn == true) converted - weightless.weightlessWeight else converted
        }

        val goal = weightSettings.goal?.copy(
          goalWeight = processWeight(
            weightSettings.goal.goalWeight,
            unit,
            weightless,
          ),
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
        AppLog.d("EntryService", "Progress emission took ${System.currentTimeMillis() - startMs}ms (cached DB)")
        result
      }
    }


  override suspend fun monthDetails(startDate: String): Flow<List<Entry>> {
    val input = startDate
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    val date = YearMonth.parse(input, formatter)
    val monthParam = date.format(DateTimeFormatter.ofPattern("yyyy-MM")) // "2024-03"

    return combine(
      entryRepository.getMonthDetail(accountId ?: "", monthParam),
      weightSettingsFlow,
    ) { entries, weightSettings ->
      entries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
    }
  }

  private suspend fun updateLast7Days(accountId: String) {
    try {
      entryRepository.getLastNDaysEntries(accountId, 7).collect { entries ->
        _last7Days.value = entries
        AppLog.d("EntryService", "Updated last 7 days: ${entries.size} entries")
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating last 7 days", e)
      _last7Days.value = emptyList()
    }
  }

  private suspend fun updateLast30Days(accountId: String) {
    try {
      entryRepository.getLastNDaysEntries(accountId, 30).collect { entries ->
        _last30Days.value = entries
        AppLog.d("EntryService", "Updated last 30 days: ${entries.size} entries")
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating last 30 days", e)
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
      AppLog.e("EntryService", "Error updating month year", e)
    }
  }

  /**
   * Refreshes the cached streak/total/starting-weight values after a sync.
   *
   * The other entry StateFlows (_latestEntry, _last7Days, _last30Days, _monthYear) are kept
   * fresh by the long-lived Room Flow subscriptions launched once in updateAllData(); they
   * react to DB changes automatically. Only updateProgressCache() is a one-shot recompute,
   * so that's the only thing this needs to call.
   *
   * (Older versions of this method also called updateLatestEntry/Last7/Last30/MonthYear
   * sequentially. Each of those .collect { } calls on a Room Flow never returns, so the
   * first call blocked forever and updateProgressCache never ran — leaving streak counts
   * stale after every addEntry. See MA dashboard streak fix.)
   */
  override suspend fun refreshEntryData() {
    val currentAccountId = accountId ?: return
    try {
      updateProgressCache(currentAccountId)
      AppLog.d("EntryService", "Progress cache refreshed - streak values updated")
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error refreshing progress cache", e)
    }
  }

  /**
   * Updates cached progress-related values (streak, count, starting weight) from the database.
   * Called only when entry data is refreshed so that the progress Flow does not hit the DB on every emission.
   * Runs four DB queries in parallel (streak dates, longest streak, total count, oldest entry).
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
      AppLog.e("EntryService", "Error updating progress cache", e)
      _currentStreak.value = 0
      _longestStreak.value = 0
      _totalCount.value = 0
      _cachedStartingWeightStored.value = null
    }
    _progressCacheVersion.value = _progressCacheVersion.value + 1
  }

  /**
   * Updates all entry-related data for the given account.
   * Fetches latest entry, last 7 and 30 days entries, and updates progress.
   * @param accountId The account ID to update data for.
   */
  override suspend fun updateAllData(accountId: String?) {
    if (accountId == null) {
      return
    }
    // Cancel all ongoing coroutines when switching accounts
    clearAllData()
    this.accountId = accountId
    // Update account-related flows and cache active account for progress (avoids getActiveAccount().first() in hot path)
    try {
      val account = accountRepository.getActiveAccount().first()
      this.initialWeight = account?.initialWeight
      _progressCacheVersion.value = _progressCacheVersion.value + 1
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error updating account flows", e)
    }
    this.syncOperations()
    repositoryScope.launch {
      entryRepository.getEntriesByOperationType(accountId, "create").collect {
        if (_isEmpty.value != it.isEmpty())
          _isEmpty.value = it.isEmpty()
      }
    }

    repositoryScope.launch {
      updateLatestEntry(accountId)
    }
    repositoryScope.launch {
      updateLast7Days(accountId)
    }
    repositoryScope.launch {
      updateLast30Days(accountId)
    }
    repositoryScope.launch {
      updateMonthYear(accountId)
    }
    repositoryScope.launch {
      updateProgressCache(accountId)
    }

    // Add new body scale data updates
    repositoryScope.launch {
      updateMonthlyBodyScaleAveragesWithJoin()
    }

    // Add new body scale data updates
    repositoryScope.launch {
      updateDaywiseBodyScaleAveragesWithJoin()
    }

    // Add monthly average subscription
    repositoryScope.launch {
      updateMonthlyAverage(accountId)
    }



    // Check for goal card after account data is updated
    repositoryScope.launch {
      try {
        val entries = getEntriesByDeviceType(accountId, "scale").first()
        AppLog.d("EntryService", "Account updated - Found ${entries.size} scale entries for account $accountId")
      } catch (e: Exception) {
        AppLog.e("EntryService", "Error checking entries for goal card after account update", e.toString())
      }
    }
  }

  /**
   * Clears all entry data when account is null or user logs out.
   */
  fun clearAllData() {
    repositoryScope.cancel()
    _latestEntry.value = null
    _last7Days.value = emptyList()
    _last30Days.value = emptyList()
    _monthYear.value = emptyList()
    _monthlyBodyScaleAverages.value = emptyList()
    _monthlyBodyScaleLatest.value = emptyList()
    _daywiseBodyScaleAverages.value = emptyList()
    _daywiseBodyScaleLatest.value = emptyList()
    _isUpdating.value = false
    _lastUpdated.value = null
    _currentStreak.value = 0
    _longestStreak.value = 0
    _totalCount.value = 0
    _cachedStartingWeightStored.value = null
    _progressCacheVersion.value = 0
    accountId = null
    initialWeight = null
    repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  /**
   * Saves a new entry both locally and remotely.
   * @param entry The entry to save.
   */
  override suspend fun addEntry(entry: Entry) {
    var updatedEntry = entry.updateEntry(
      entry.entry.copy(
        isSynced = false,
        operationType = OperationType.CREATE.name,
        accountId = accountId ?: entry.entry.accountId,
      ),
    )

    if (updatedEntry is ScaleEntry) {
      updatedEntry = updatedEntry.copy(
        scale = updatedEntry.scale.copy(
          scaleEntry = updatedEntry.scale.scaleEntry.copy(
            weight = convertWeight(
              updatedEntry.scale.scaleEntry.weight,
              updatedEntry.entry.unit,
              WeightUnit.LB,
            ),
          ),
        ),
      )
    }
    // handle other types if you have them
    syncOperations(
      listOf(updatedEntry),
    )

    // Check for goal card after adding new entry
    repositoryScope.launch {
      try {
        val entries = getEntriesByDeviceType(accountId ?: "", "scale").first()
        AppLog.d("EntryService", "Entry added - Found ${entries.size} scale entries for account $accountId")
      } catch (e: Exception) {
        AppLog.e("EntryService", "Error checking entries for goal card after adding entry", e.toString())
      }
    }
  }

  /**
   * Saves multiple new entries both locally and remotely.
   * @param entries The list of entries to save.
   */
  override suspend fun addEntry(entries: List<Entry>) {
    try {
      val updatedEntries = entries.map { entry ->
        val baseEntry = entry.entry.copy(
          isSynced = false,
          operationType = OperationType.CREATE.name,
          accountId = accountId ?: entry.entry.accountId,
        )

        val updatedEntry = entry.updateEntry(baseEntry)

        if (updatedEntry is ScaleEntry) {
          updatedEntry.copy(
            scale = updatedEntry.scale.copy(
              scaleEntry = updatedEntry.scale.scaleEntry.copy(
                weight = convertWeight(
                  updatedEntry.scale.scaleEntry.weight,
                  updatedEntry.entry.unit,
                  WeightUnit.LB,
                ),
              ),
            ),
          )
        } else {
          updatedEntry
        }
      }

      syncOperations(updatedEntries)

      // Check for goal card after adding new entries
      repositoryScope.launch {
        try {
          val entries = getEntriesByDeviceType(accountId ?: "", "scale").first()
          AppLog.d("EntryService", "Entries added - Found ${entries.size} scale entries for account $accountId")
        } catch (e: Exception) {
          AppLog.e("EntryService", "Error checking entries for goal card after adding entries", e.toString())
        }
      }
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error saving new entries", e)
    }
  }

  /**
   * Deletes an entry both locally and remotely.
   * @param entry The entry to delete.
   */
  override suspend fun deleteEntry(entry: Entry) {
    try {
      val deleteEntry =
        entry.updateEntry(
          entry =
            entry.entry.copy(
              isSynced = false,
              operationType = OperationType.DELETE.name,
            ),
        )
      syncOperations(emptyList(), listOf(deleteEntry))
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error deleting entry", e)
    }
  }

  /**
   * Retrieves entries by device type for the specified account.
   * @param accountId The account ID to filter entries by.
   * @param deviceType The device type to filter entries by.
   * @return Flow of list of entries matching the device type.
   */
  override fun getEntriesByDeviceType(
    accountId: String,
    deviceType: String,
  ): Flow<List<Entry>> = entryRepository.getEntriesByDeviceType(accountId, deviceType)

  /**
   * Main sync operations method that handles both new entries and deletions.
   * Matches the TypeScript syncOperations flow exactly.
   */
  override suspend fun syncOperations(
    newEntries: List<Entry>,
    deleteOps: List<Entry>,
  ) {
    if (accountId == null) return

    try {
      _isUpdating.value = true
      // 1. Get existing unsynced entries
      val unSyncedEntries = entryRepository.getUnSynced(accountId!!).toMutableList()

      // 2. Add new operations to unsynced list
      newEntries.forEach { entry ->
        unSyncedEntries.add(0, entry.updateEntry(entry.entry.copy(accountId = accountId!!)))
      }
      deleteOps.forEach { entry ->
        unSyncedEntries.add(0, entry)
      }

      unSyncedEntries.sortBy { it.entry.entryTimestamp }

      // 3. Process operations
      val successfulOperations = mutableListOf<Entry>()
      val failedOperations = mutableListOf<Entry>()

      for (operation in unSyncedEntries) {
        try {
          // Try to send to API
          entryRepository.sendOperationToAPI((operation as ScaleEntry).toScaleApiEntry())
          // If successful, mark as synced
          val syncedOperation =
            operation.updateEntry(
              entry =
                operation.entry.copy(
                  isSynced = true,
                ),
            )
          successfulOperations.add(syncedOperation)

          } catch (e: Exception) {
          // If failed, increment attempts and store for retry
          val failedOperation =
            operation.updateEntry(
              entry =
                operation.entry.copy(
                  isSynced = false,
                  attempts = operation.entry.attempts.plus(1),
                ),
            )
          failedOperations.add(failedOperation)
          AppLog.e("EntryService", "Error sending operation to API", e)
        }
      }

      if (failedOperations.isNotEmpty()) {
        EntryServiceHelper.executeOperations(
          entryRepository,
          failedOperations,
          userHasOperations = true,
        )
      }

      // Try local integration for create operations
      newEntries.forEach { operation ->
        if (operation.entry.operationType == OperationType.CREATE.name) {
          tryLocalIntegration(operation)
        }
      }

      // 4. Handle goal alerts
      val lastValidOperation =
        (successfulOperations + failedOperations)
          .filter { it.entry.operationType == OperationType.CREATE.name }
          .maxByOrNull { it.entry.entryTimestamp }

      // 5. Get operations from API
      val operationCount = entryRepository.getOperationCount(accountId!!)
      val operationsFromApi = mutableListOf<ScaleEntry>()
      try {
        val syncTimeStamp = accountRepository.getSyncTimeStamp().first()
        val response = entryRepository.getOperationsFromAPI(syncTimeStamp)
        if (response == null) {
          AppLog.w("EntryService", "No operations received from API")
          _isUpdating.value = false
          return
        }
        val scaleEntries =
          response.operations.map { fromScaleApiEntry(it, accountId = accountId!!) }
        operationsFromApi.addAll(scaleEntries)
        accountRepository.updateSyncTimeStamp(response.timestamp)
      } catch (e: Exception) {
        AppLog.e("EntryService", "Error getting operations from API", e)
        // If API fails, store successful operations as placeholders
        // This means these operations will be marked as synced but might need to be
        // re-synced later when API is available
        EntryServiceHelper.executeOperations(
          entryRepository,
          successfulOperations,
          userHasOperations = operationCount > 0,
          arePlaceholders = true,
        )
      }

      // 6. Execute operations from API
      if (operationsFromApi.isNotEmpty()) {
        EntryServiceHelper.executeOperations(
          entryRepository,
          operationsFromApi,
        )
        // Mirrors operation.service.ts#executeOperations (Ionic app): forward
        // every server-side CREATE to Health Connect. Covers Wi-Fi and R4
        // scales whose readings reach the app only via the server, not
        // through addEntry()/syncOperations(newEntries=...).
        // Server responses use lowercase operationType ("create"), while
        // locally-built entries use OperationType.CREATE.name ("CREATE"),
        // so compare ignoring case.
        operationsFromApi
          .filter { it.entry.operationType.equals(OperationType.CREATE.name, ignoreCase = true) }
          .forEach { tryLocalIntegration(it) }
      }

      // 7. API sync is done: clear loader now and refresh the progress cache in the background
      // so the dashboard streak/total updates without blocking the user.
      _lastUpdated.value = System.currentTimeMillis()

      repositoryScope.launch {
        refreshEntryData()
      }
      _isUpdating.value = false

      // 8. Handle goal alerts (similar to TypeScript operation.service.ts)
      // Use lastValidOperation directly to avoid race condition with _latestEntry StateFlow
      if (lastValidOperation != null && lastValidOperation is ScaleEntry) {
        val operationWeight = lastValidOperation.scale.scaleEntry.weight
        repositoryScope.launch {
          try {
            delay(GOAL_ALERT_DELAY_MS)
            // Convert stored weight (0.1 lb increments) to full pounds for goal alert service
            goalService.showGoalCompletionAlert(operationWeight * WEIGHT_CONVERSION_FACTOR)
          } catch (err: Exception) {
            AppLog.e("EntryService", "syncOperations - unable to set Goal met", err)
          }
        }
      }
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error in syncOperations", e)
      _isUpdating.value = false
    }
  }

  private suspend fun updateLatestEntry(accountId: String) {
    try {
      entryRepository.getLatestEntry(accountId).collect { latest ->
          _latestEntry.value = latest
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating latest entry", e)
    }
  }

  private suspend fun updateMonthlyBodyScaleAveragesWithJoin() {
    try {
      getMonthlyBodyScaleAveragesWithJoin()
        .collect {
          _monthlyBodyScaleAverages.value = it
        }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating monthly entry averages", e)
    }
  }

  private suspend fun updateDaywiseBodyScaleAveragesWithJoin() {
    try {
      getDaywiseBodyScaleAveragesWithJoin()
        .collect {
          _daywiseBodyScaleAverages.value = it
        }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating day wise entry averages", e)
    }
  }

  private suspend fun updateMonthlyAverage(accountId: String) {
    try {
      getMonthlyAverage(accountId).collect { months ->
        _monthlyAverage.value = months
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      AppLog.e("EntryService", "Error updating monthly average", e)
    } finally {
    }
  }

  /**
   * Pure in-memory progress calculation. No DB or repository calls.
   * Used by the progress Flow with precomputed streak/count/starting weight from cached StateFlows.
   */
  private fun calculateProgressPure(
    latestEntry: Entry?,
    last7Days: List<Entry>,
    last30Days: List<Entry>,
    months: List<HistoryMonth>,
    startingWeightDisplay: Double?,
    firstRecordedWeightDisplay: Double?,
    currentStreak: Int,
    longestStreak: Int,
    totalCount: Int,
    unit: WeightUnit,
    goal: Goal?,
  ): Progress {
    var week: Double? = null
    var initWeek: Entry? = null
    var month: Double? = null
    var initMonth: Entry? = null
    var year: Double? = null
    var initYear: HistoryMonth? = null
    var total: Double? = null
    initWeek = if (last7Days.isNotEmpty()) last7Days.last() else null
    initMonth = if (last30Days.isNotEmpty()) last30Days.last() else null
    initYear = if (months.isNotEmpty()) months.last() else null
    if (latestEntry != null && initWeek != null && latestEntry is ScaleEntry && initWeek is ScaleEntry) {
      week = latestEntry.scale.scaleEntry.weight.toDouble() - initWeek.scale.scaleEntry.weight.toDouble()
    }
    if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
      month = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
    }
    // For total milestone, prefer the actual first recorded history baseline.
    // This avoids stale/mis-scaled goal starting weights producing incorrect totals.
    val totalBaselineWeight = firstRecordedWeightDisplay ?: startingWeightDisplay
    if (latestEntry != null && latestEntry is ScaleEntry && totalBaselineWeight != null) {
      total = latestEntry.scale.scaleEntry.weight.toDouble() - totalBaselineWeight
      AppLog.d(
        "EntryService",
        "Total milestone calc -> latest=${latestEntry.scale.scaleEntry.weight}, starting=$startingWeightDisplay, firstRecorded=$firstRecordedWeightDisplay, baseline=$totalBaselineWeight, total=$total",
      )
    }
    val thirtyDaysAgoDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
    if (initYear != null && initYear.entryTimestamp != null) {
      try {
        val yearMonthFormat = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
        val initYearDate = yearMonthFormat.parse(initYear.entryTimestamp)
        val initYearCalendar = Calendar.getInstance().apply {
          if (initYearDate != null) time = initYearDate
          set(Calendar.DAY_OF_MONTH, 1)
        }
        val thirtyDaysAgoDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(thirtyDaysAgoDate.time)
        val initYearDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(initYearCalendar.time)
        if (initYearDateString >= thirtyDaysAgoDateString) {
          if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
            year = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
          }
        } else {
          if (latestEntry != null && initYear.avgWeight != null && latestEntry is ScaleEntry) {
            year = latestEntry.scale.scaleEntry.weight.toDouble() - initYear.avgWeight!!
          }
        }
      } catch (e: Exception) {
        AppLog.e("EntryService", "Error parsing initYear date: ${initYear.entryTimestamp}", e)
        if (latestEntry != null && initYear.avgWeight != null && latestEntry is ScaleEntry) {
          year = latestEntry.scale.scaleEntry.weight.toDouble() - initYear.avgWeight!!
        }
      }
    }
    return Progress(
      latest = latestEntry,
      goal = goal,
      currentStreak = currentStreak,
      longestStreak = longestStreak,
      count = totalCount,
      initWt = totalBaselineWeight ?: 0.0,
      week = week,
      month = month,
      year = year,
      total = total,
      unit = unit,
      initWeek = initWeek,
      initMonth = initMonth,
      initYear = initYear,
    )
  }

  /**
   * Gets monthly averages of body scale data for an account using JOINs.
   */
  private fun getMonthlyBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
    combine(
      entryRepository.getMonthlyBodyScaleAveragesWithJoin(this.accountId ?: ""),
      weightSettingsFlow,
    ) { summaries, weightSettings ->
      summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
    }

  /**
   * Gets the latest body scale entry for each month for an account using JOINs.
   */
  private fun getMonthlyBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
    combine(
      entryRepository.getMonthlyBodyScaleLatestWithJoin(this.accountId ?: ""),
      weightSettingsFlow,
    ) { summaries, weightSettings ->
      summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
    }

  /**
   * Gets daywise averages of body scale data for an account using JOINs.
   */
  private fun getDaywiseBodyScaleAveragesWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
    combine(
      entryRepository.getDaywiseBodyScaleAveragesWithJoin(this.accountId ?: ""),
      weightSettingsFlow,
    ) { summaries, weightSettings ->
      summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
    }

  /**
   * Gets the latest body scale entry for each day for an account using JOINs.
   */
  private fun getDaywiseBodyScaleLatestWithJoin(): Flow<List<PeriodBodyScaleSummary>> =
    combine(
      entryRepository.getDaywiseBodyScaleLatestWithJoin(this.accountId ?: ""),
      weightSettingsFlow,
    ) { summaries, weightSettings ->
      summaries.map { it.process(weightSettings.weightUnit, weightSettings.weightless) }
    }

  /**
   * Tries to sync entry data to local health integrations (Health Connect).
   * Similar to tryLocalIntegration in Angular operation service.
   * @param entry The entry to sync to health integrations
   */
  private suspend fun tryLocalIntegration(entry: Entry) {
    AppLog.d("EntryService", "Operation: tryLocalIntegration called", entry.toString())
    try {
      val isIntegrated = healthConnectService.checkIntegrated()
      if (!isIntegrated) {
        return
      }
      if (entry is ScaleEntry) {
        val summary = entry.toPeriodBodyScaleSummary()
        if (summary != null) {
          healthConnectService.syncData(listOf(summary))
          val latestEntry = HealthConnectSyncEntry(
            weight = summary.weight,
            timestamp = ConversionTools.convertToUTC(summary.entryTimestamp),
            type = IntegrationType.HEALTH_CONNECT.value,
            sentAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            bodyFat = summary.bodyFat,
            muscleMass = summary.muscleMass,
            water = summary.water,
            bmi = summary.bmi,
            data = mapOf(),
          )
          healthConnectRepository.syncEntry(latestEntry)
          AppLog.d("EntryService", "Successfully synced entry to Health Connect")
        } else {
          AppLog.w("EntryService", "Could not convert entry to PeriodBodyScaleSummary")
        }
      }
    } catch (err: Exception) {
      AppLog.e("EntryService", "Error syncing to Health Connect", err)
      // Don't throw - this is a non-critical operation
    }
  }

  /**
   * Calculates the current streak based on the TypeScript implementation.
   * @return The current streak count
   */
  private suspend fun getCurrentStreak(): Int {
    if (accountId == null) return 0

    try {
      val entryDates = entryRepository.getStreakData(accountId!!)
      if (entryDates.isEmpty()) return 0

      var score = 0
      val dateToCheck = Calendar.getInstance()

      // Helper function to compare dates by year, month, and day
      fun datesAreSame(d1: Calendar, d2: Calendar): Boolean =
        d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) &&
          d1.get(Calendar.MONTH) == d2.get(Calendar.MONTH) &&
          d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)

      // Helper function to add one to score and subtract one day
      fun addOne() {
        score++
        dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
      }

      // Remove the first (most recent) entry from the array
      val firstEntryTimestamp = entryDates.first()
      val remainingDates = entryDates.drop(1)

      // Parse the first entry date
      val firstEntryDate = Calendar.getInstance().apply {
        time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
          .parse(firstEntryTimestamp) ?: return 0
      }

      // Check if the most recent entry is from today, if not, check if it is from yesterday
      if (datesAreSame(dateToCheck, firstEntryDate)) {
        addOne()
      } else {
        dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
        if (datesAreSame(dateToCheck, firstEntryDate)) {
          addOne()
        }
      }

      // Check all remaining entries
      for (entryTimestamp in remainingDates) {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entryTimestamp) ?: break
        val entryDate = Calendar.getInstance().apply { time = parsed }
        if (datesAreSame(dateToCheck, entryDate)) {
          addOne()
        } else {
          break
        }
      }

      return score
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error calculating current streak", e)
      return 0
    }
  }
}

enum class OperationType {
  CREATE,
  DELETE,
}

internal object EntryServiceHelper {

  /**
   * Computes longest streak (max consecutive days with an entry) from a list of entry dates (yyyy-MM-dd).
   * Pure in-memory single pass; no DB access. Use in place of getLongestStreakCount for large datasets.
   */
  fun computeLongestStreakFromDates(entryDates: List<String>): Int {
    if (entryDates.isEmpty()) return 0
    val sorted = entryDates.distinct().sorted()
    var maxStreak = 1
    var current = 1
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fun toDayMillis(s: String): Long =
      (fmt.parse(s)?.time ?: 0L) / (24 * 60 * 60 * 1000)
    for (i in 1 until sorted.size) {
      val prevDay = toDayMillis(sorted[i - 1])
      val thisDay = toDayMillis(sorted[i])
      if (thisDay == prevDay + 1) {
        current++
        maxStreak = maxOf(maxStreak, current)
      } else {
        current = 1
      }
    }
    return maxStreak
  }

  /**
   * Computes current streak count from a list of entry dates (yyyy-MM-dd, newest first).
   * Pure in-memory calculation; no DB access.
   */
  fun computeCurrentStreakFromDates(entryDates: List<String>): Int {
    if (entryDates.isEmpty()) return 0
    var score = 0
    val dateToCheck = Calendar.getInstance()
    fun datesAreSame(d1: Calendar, d2: Calendar): Boolean =
      d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) &&
        d1.get(Calendar.MONTH) == d2.get(Calendar.MONTH) &&
        d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)
    fun addOne() {
      score++
      dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
    }
    val firstEntryTimestamp = entryDates.first()
    val remainingDates = entryDates.drop(1)
    val firstEntryDate = Calendar.getInstance().apply {
      time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(firstEntryTimestamp) ?: return 0
    }
    if (datesAreSame(dateToCheck, firstEntryDate)) {
      addOne()
    } else {
      dateToCheck.add(Calendar.DAY_OF_YEAR, -1)
      if (datesAreSame(dateToCheck, firstEntryDate)) addOne()
    }
    for (entryTimestamp in remainingDates) {
      val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entryTimestamp) ?: break
      val entryDate = Calendar.getInstance().apply { time = parsed }
      if (datesAreSame(dateToCheck, entryDate)) addOne() else break
    }
    return score
  }

  fun processWeight(
    weight: Double,
    unit: WeightUnit?,
    weightLess: Weightless?,
  ): Double {

    val convertedWeight = ConversionTools.convertStoredToDisplay(weight, unit == WeightUnit.KG)
    return if (weightLess?.isWeightlessOn == true) convertedWeight - weightLess.weightlessWeight else convertedWeight
  }

  /**
   * Executes a list of operations received from the server.
   * @param entryRepository The entry repository.
   * @param operations The list of operations to execute.
   */
  suspend fun executeOperations(
    entryRepository: IEntryRepository,
    operations: List<Entry>,
  ) {
    if (operations.isEmpty()) return
    try {
      val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
      entryRepository.insert(sortedOperations)
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error executing operations", e)
    }
  }

  /**
   * Executes a list of operations, handling both create and delete operations.
   * @param entryRepository The entry repository.
   * @param operations The list of operations to execute.
   * @param userHasOperations Whether the user has existing operations.
   * @param arePlaceholders Whether the operations are placeholders (not yet synced).
   * @param tryLocalIntegration Optional suspend function to call for local integration (Health Connect).
   */
  suspend fun executeOperations(
    entryRepository: IEntryRepository,
    operations: List<Entry>,
    userHasOperations: Boolean = true,
    arePlaceholders: Boolean = false,
  ) {
    if (operations.isEmpty()) return

    try {
      // Sort operations by server timestamp
      val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
      // Separate create and delete operations
      val createOperations =
        sortedOperations.filter { it.entry.operationType == OperationType.CREATE.name }
      val deleteOperations =
        sortedOperations.filter { it.entry.operationType == OperationType.DELETE.name }

      // Handle create operations
      for (operation in createOperations) {
        // Check if entry exists
        val exists =
          if (userHasOperations) {
            entryRepository.getEntryById(operation.entry.id) != null
          } else {
            false
          }

        if (exists) {
          // Update existing entry
          entryRepository.update(operation)
        } else {
          // Insert new entry
          entryRepository.insert(operation)
        }
      }

      // Handle delete operations
      for (operation in deleteOperations) {
        entryRepository.delete(operation)
      }
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error executing operations", e)
      throw e
    }
  }
}
