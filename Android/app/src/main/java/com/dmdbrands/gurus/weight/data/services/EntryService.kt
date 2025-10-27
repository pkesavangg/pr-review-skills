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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

  private var accountId: String? = null
  private var initialWeight: Double? = null

  /**
   * Initializes goal card monitoring by checking entry count and setting up listeners.
   * This function monitors the lastUpdated flow and checks if the user has enough entries
   * to display the goal card.
   */
  override fun initializeGoalCardMonitoring() {
    repositoryScope.launch {
      lastUpdated.collect { lastUpdated ->
        try {
          val entries = entryRepository.getEntriesByAccount(accountId ?: "", false)
          if (entries.size >= 3) {
            goalService.checkGoalCard()
            AppLog.d("EntryService", "User has  scale entries (>= 3), checking goal card")
          } else {
            AppLog.d("EntryService", "User has only  scale entries, not enough for goal card")
          }
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

  override val progress: Flow<Progress> = combine(
    latestEntry,
    last7Days,
    last30Days,
    weightSettingsFlow,
    monthYear,
  ) { latest, last7, last30, weightSettings, monthYear ->
    calculateProgress(
      latest?.process(weightSettings.weightUnit, weightSettings.weightless),
      last7.map { it.process(weightSettings.weightUnit, weightSettings.weightless) },
      last30.map { it.process(weightSettings.weightUnit, weightSettings.weightless) },
      monthYear.map { it.process(weightSettings.weightUnit, weightSettings.weightless) },
      processWeight(this.initialWeight ?: 0.0, weightSettings.weightUnit, weightSettings.weightless),
      goal = weightSettings.goal?.copy(
        goalWeight = processWeight(
          weightSettings.goal.goalWeight,
          weightSettings.weightUnit,
          weightSettings.weightless,
        ),
        account = accountRepository.getActiveAccount().first(),
      ),
      unit = weightSettings.weightUnit ?: WeightUnit.LB,
    )
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
    val endDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
    val startDate = endDate.minusDays(7)
    val entries = entryRepository.getEntriesInRange(
      accountId,
      startDate.toString(),
      endDate.toString(),
    )
    _last7Days.value = entries
  }

  private suspend fun updateLast30Days(accountId: String) {
    val endDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
    val startDate = endDate.minusDays(30)
    val entries = entryRepository.getEntriesInRange(
      accountId,
      startDate.toString(),
      endDate.toString(),
    )
    _last30Days.value = entries
  }

  private suspend fun updateMonthYear(accountId: String) {
    try {
      val months = entryRepository.getMonthlyAverage(accountId).first()
      _monthYear.value = months
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error updating month year", e)
    }
  }

  /**
   * Updates all entry-related data for the given account.
   * Fetches latest entry, last 7 and 30 days entries, and updates progress.
   * @param accountId The account ID to update data for.
   */
  override suspend fun updateAccountId(accountId: String?) {
    if (accountId == null) {
      return
    }
    // Cancel all ongoing coroutines when switching accounts
    clearAllData()
    this.accountId = accountId
    // Update account-related flows
    try {
      this.initialWeight = accountRepository.getActiveAccount().first()?.initialWeight
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
      updateLast7Days(accountId)
    }
    repositoryScope.launch {
      updateLast30Days(accountId)
    }
    repositoryScope.launch {
      updateMonthYear(accountId)
    }
    repositoryScope.launch {
      updateLatestEntry(accountId)
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

          // Try local integration for create operations
          if (operation.entry.operationType == OperationType.CREATE.name) {
            tryLocalIntegration(operation)
          }
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
          tryLocalIntegration = { operation -> tryLocalIntegration(operation) }
        )
      }

      // 4. Handle goal alerts
      val lastValidOperation =
        (successfulOperations + failedOperations)
          .filter { it.entry.operationType == OperationType.CREATE.name }
          .maxByOrNull { it.entry.entryTimestamp }

      lastValidOperation?.let {
        // Get weight from the latest entry if it's a ScaleEntry
        val latestWeight = when (val latest = _latestEntry.value) {
          is ScaleEntry -> latest.scale.scaleEntry.weight.toDouble()
          else -> null
        }
        // Trigger goal alert if needed
        latestWeight?.let { weight ->
          goalService.showGoalCompletionAlert(weight * 10)
        }
      }

      // 5. Get operations from API
      val operationCount = entryRepository.getOperationCount(accountId!!)
      val operationsFromApi = mutableListOf<ScaleEntry>()
      try {
        val syncTimeStamp = accountRepository.getSyncTimeStamp().first()
        val response = entryRepository.getOperationsFromAPI(syncTimeStamp)
        if (response == null) {
          AppLog.w("EntryService", "No operations received from API")
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
          tryLocalIntegration = { operation -> tryLocalIntegration(operation) }
        )
      }

      // 6. Execute operations from API
      if (operationsFromApi.isNotEmpty()) {
        EntryServiceHelper.executeOperations(
          entryRepository,
          operationsFromApi,
          tryLocalIntegration = { operation -> tryLocalIntegration(operation) }
        )
      }

      // 7. Update last updated timestamp
      _lastUpdated.value = System.currentTimeMillis()
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error in syncOperations", e)
    } finally {
      _isUpdating.value = false
    }
  }

  private suspend fun updateLatestEntry(accountId: String) {
    try {
      entryRepository.getLatestEntry(accountId)?.collect { latest ->
        _latestEntry.value = latest
      }
    } catch (e: Exception) {
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
      AppLog.e("EntryService", "Error updating day wise entry averages", e)
    }
  }

  private suspend fun updateMonthlyAverage(accountId: String) {
    try {
      getMonthlyAverage(accountId).collect { months ->
        _monthlyAverage.value = months
      }
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error updating monthly average", e)
    } finally {
    }
  }

  /**
   * Calculates progress based on the latest entry, last 7 days, and last 30 days data.
   * This function is used by the progress Flow to reactively calculate progress.
   */
  private suspend fun calculateProgress(
    latestEntry: Entry?,
    last7Days: List<Entry>,
    last30Days: List<Entry>,
    months: List<HistoryMonth>,
    initialWeight: Double?,
    unit: WeightUnit,
    goal: Goal?
  ): Progress {
    if (accountId == null) {
      return Progress()
    }

    try {
      var week = 0.0
      var initWeek: Entry? = null
      var month = 0.0
      var initMonth: Entry? = null
      var year = 0.0
      var initYear: HistoryMonth? = null
      var total = 0.0
      var startingWeight: Double? = null
      var oldestEntry: Entry? = null

      // Get initial entries for each period
      initWeek = if (last7Days.isNotEmpty()) last7Days.last() else null
      initMonth = if (last30Days.isNotEmpty()) last30Days.last() else null
      initYear = if (months.isNotEmpty()) months.last() else null

      // Calculate week and month progress
      if (latestEntry != null && initWeek != null && latestEntry is ScaleEntry && initWeek is ScaleEntry) {
        week = latestEntry.scale.scaleEntry.weight.toDouble() - initWeek.scale.scaleEntry.weight.toDouble()
      }

      if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
        month = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
      }

      // Get starting weight (either from account or oldest entry)
      if (initialWeight != null && initialWeight != 0.0) {
        startingWeight = initialWeight
      } else {
        oldestEntry = entryRepository.getOldestEntry(accountId!!)
        if (oldestEntry is ScaleEntry) {
          startingWeight = oldestEntry.scale.scaleEntry.weight.toDouble()
        }
      }

      // Calculate total progress
      if (latestEntry != null && startingWeight != null && latestEntry is ScaleEntry) {
        total = latestEntry.scale.scaleEntry.weight.toDouble() - startingWeight
      }

      val thirtyDaysAgoDate = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -30) // Fixed: subtract 30 days, not add
      }

      if (initYear != null && initYear.entryTimestamp != null) {
        try {
          // Parse the MMM yyyy format (e.g., "May 2024")
          val yearMonthFormat = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
          val initYearDate = yearMonthFormat.parse(initYear.entryTimestamp)

          val initYearCalendar = Calendar.getInstance().apply {
            time = initYearDate
            // Set to first day of the month for comparison
            set(Calendar.DAY_OF_MONTH, 1)
          }

          val thirtyDaysAgoDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(thirtyDaysAgoDate.time)
          val initYearDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(initYearCalendar.time)

          if (initYearDateString >= thirtyDaysAgoDateString) {
            // If initYear is within last 30 days, use initMonth for year calculation
            if (latestEntry != null && initMonth != null && latestEntry is ScaleEntry && initMonth is ScaleEntry) {
              year = latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
            }
          } else {
            // If initYear is older than 30 days, use initYear for year calculation
            if (latestEntry != null && initYear.avgWeight != null && latestEntry is ScaleEntry) {
              year = latestEntry.scale.scaleEntry.weight.toDouble() - initYear.avgWeight!!
            }
          }
        } catch (e: Exception) {
          AppLog.e("EntryService", "Error parsing initYear date: ${initYear.entryTimestamp}", e)
          // Fallback: use initYear for year calculation if parsing fails
          if (latestEntry != null && initYear.avgWeight != null && latestEntry is ScaleEntry) {
            year = latestEntry.scale.scaleEntry.weight.toDouble() - initYear.avgWeight!!
          }
        }
      }

      // Get streak information
      val currentStreak = getCurrentStreak()
      val longestStreak = entryRepository.getLongestStreakCount(accountId!!)
      val totalCount = entryRepository.getTotalCount(accountId!!)

      return Progress(
        latest = latestEntry,
        goal = goal,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        count = totalCount,
        initWt = initialWeight ?: 0.0,
        week = week,
        month = month,
        year = year,
        total = total,
        unit = unit,
        initWeek = initWeek,
        initMonth = initMonth,
        initYear = initYear,
      )
    } catch (e: Exception) {
      AppLog.e("EntryService", "Error calculating progress", e)
      return Progress()
    }
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
        time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
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
        val entryDate = Calendar.getInstance().apply {
          time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .parse(entryTimestamp)
        }

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
   * Creates an operation entry for syncing with the server.
   * @param entry The entry to create an operation for.
   * @param type The operation type (CREATE or DELETE).
   * @return The operation entry.
   */
  fun createOperation(
    entry: Entry,
    type: OperationType,
  ): Entry {
    val updatedEntry =
      entry.entry.copy(
        operationType = type.name,
        isSynced = false,
      )
    return entry.updateEntry(
      entry = updatedEntry,
    )
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
   * @param tryLocalIntegration Optional suspend function to call for local integration (Health Connect).
   */
  suspend fun executeOperations(
    entryRepository: IEntryRepository,
    operations: List<Entry>,
    tryLocalIntegration: (suspend (Entry) -> Unit)? = null,
  ) {
    if (operations.isEmpty()) return
    try {
      val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
      entryRepository.insert(sortedOperations)

      // Try local integration for create operations
      for (operation in sortedOperations.filter { it.entry.operationType == OperationType.CREATE.name }) {
        tryLocalIntegration?.invoke(operation)
      }
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
    tryLocalIntegration: (suspend (Entry) -> Unit)? = null,
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

        // Try local integration for create operations
        tryLocalIntegration?.invoke(operation)
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

  /**
   * Calculates the current streak of consecutive days with entries.
   * @param entries The list of entries.
   * @return The current streak count.
   */
  fun calculateCurrentStreak(entries: List<Entry>): Int {
    if (entries.isEmpty()) return 0

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = Calendar.getInstance()
    var currentDate = today.clone() as Calendar
    var streak = 0

    val sortedEntries = entries.sortedByDescending { it.entry.entryTimestamp }
    for (entry in sortedEntries) {
      val entryDate = Calendar.getInstance()
      entryDate.time = dateFormat.parse(entry.entry.entryTimestamp.toString())!!

      if (entryDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
        entryDate.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR)
      ) {
        streak++
        currentDate.add(Calendar.DAY_OF_YEAR, -1)
      } else {
        break
      }
    }

    return streak
  }

  /**
   * Calculates the longest streak of consecutive days with entries.
   * @param entries The list of entries.
   * @return The longest streak count.
   */
  fun calculateLongestStreak(entries: List<Entry>): Int {
    if (entries.isEmpty()) return 0

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sortedEntries =
      entries
        .map { dateFormat.parse(it.entry.entryTimestamp.toString())!! }
        .map { date ->
          Calendar.getInstance().apply { time = date }
        }.distinctBy { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        .sortedByDescending { it.timeInMillis }

    var longestStreak = 1
    var currentStreak = 1

    for (i in 1 until sortedEntries.size) {
      val previous = sortedEntries[i - 1]
      val current = sortedEntries[i]

      val expected = previous.clone() as Calendar
      expected.add(Calendar.DAY_OF_YEAR, -1)

      if (current.get(Calendar.YEAR) == expected.get(Calendar.YEAR) &&
        current.get(Calendar.DAY_OF_YEAR) == expected.get(Calendar.DAY_OF_YEAR)
      ) {
        currentStreak++
      } else {
        longestStreak = maxOf(longestStreak, currentStreak)
        currentStreak = 1
      }
    }

    return maxOf(longestStreak, currentStreak)
  }

  /**
   * Updates the progress state for the current account.
   * Calculates week, month, year, and total progress using only ScaleEntry weights.
   * Ignores non-scale entries (e.g., BpmEntry).
   *
   * @param latestEntry The latest scale entry (nullable).
   * @param last7Days The last 7 days of entries (may include nulls or non-scale entries).
   * @param last30Days The last 30 days of entries (may include nulls or non-scale entries).
   * @param initialWeight The initial weight for total progress calculation (nullable).
   * @param setProgress Lambda to set the progress value.
   */
  fun updateProgress(
    latestEntry: ScaleEntry?,
    last7Days: List<Entry>,
    last30Days: List<Entry>,
    initialWeight: Double?,
    setProgress: (Progress) -> Unit,
  ) {
    // Filter only non-null ScaleEntry for calculations
    val last7ScaleEntries = last7Days.map { it as ScaleEntry }
    val last30ScaleEntries = last30Days.map { it as ScaleEntry }

    // Get the oldest (last) scale entry in each period for comparison
    val initWeek = last7ScaleEntries.lastOrNull()
    val initMonth = last30ScaleEntries.lastOrNull()
    val initYear: HistoryMonth? = null // Placeholder: adjust if you have a year list

    // Calculate week, month, year, and total progress (all as Double)
    val week =
      if (latestEntry != null && initWeek != null) {
        latestEntry.scale.scaleEntry.weight
          .toDouble() -
          initWeek.scale.scaleEntry.weight
            .toDouble()
      } else {
        0.0
      }

    val month =
      if (latestEntry != null && initMonth != null) {
        latestEntry.scale.scaleEntry.weight
          .toDouble() -
          initMonth.scale.scaleEntry.weight
            .toDouble()
      } else {
        0.0
      }

    val year =
      if (latestEntry != null && initYear != null) {
        latestEntry.scale.scaleEntry.weight
          .toDouble() -
          initYear.avgWeight!!
      } else {
        0.0
      }

    val total =
      if (latestEntry != null && initialWeight != null) {
        latestEntry.scale.scaleEntry.weight
          .toDouble() - initialWeight
      } else {
        0.0
      }

    setProgress(
      Progress(
        latest = latestEntry,
        currentStreak = calculateCurrentStreak(last30ScaleEntries),
        longestStreak = calculateLongestStreak(last30ScaleEntries),
        count = last30ScaleEntries.size,
        initWt = initialWeight ?: 0.0,
        week = week,
        month = month,
        year = year,
        total = total,
        initWeek = initWeek,
        initMonth = initMonth,
        initYear = null,
      ),
    )
  }

  /**
   * Clears all cached entry data and progress.
   * @param setLatestEntry Lambda to set the latest entry value.
   * @param setLast7Days Lambda to set the last 7 days value.
   * @param setLast30Days Lambda to set the last 30 days value.
   * @param setProgress Lambda to set the progress value.
   */
  fun clearAllData(
    setLatestEntry: (Entry?) -> Unit,
    setLast7Days: (List<Entry>) -> Unit,
    setLast30Days: (List<Entry>) -> Unit,
    setProgress: (Progress?) -> Unit,
  ) {
    setLatestEntry(null)
    setLast7Days(emptyList())
    setLast30Days(emptyList())
    setProgress(null)
  }
}
