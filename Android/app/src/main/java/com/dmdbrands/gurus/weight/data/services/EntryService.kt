// domain/service/EntryService.kt
package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.api.entry.toDomainEntry
import com.dmdbrands.gurus.weight.domain.model.api.entry.toUnifiedRequestOrNull
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry.Companion.fromScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Write + sync owner for weight entries. Flattened from the previous
 * EntryService / EntryCrudService / EntrySyncService trio — the two helpers
 * had zero external consumers, so they are now private methods on this class.
 *
 * Read flows (progress, latestEntry, isEmpty) live on IEntryReadService.
 */
class EntryService(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val goalService: IGoalService,
    private val healthConnectService: IHealthConnectService,
    private val healthConnectRepository: IHealthConnectRepository,
    private val analyticsService: IAnalyticsService,
    private val appScope: CoroutineScope,
) : IEntryService {

    private val activeJobs = mutableListOf<Job>()

    private var accountId: String? = null

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)

    override suspend fun updateAllData(accountId: String?) {
        if (accountId == null) return
        clearAllData()
        this.accountId = accountId
        syncOperationsInternal(accountId)
    }

    private fun clearAllData() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        accountId = null
        _isUpdating.value = false
        _lastUpdated.value = null
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /** Saves a new entry both locally and remotely. */
    override suspend fun addEntry(entry: Entry) {
        val currentAccountId = accountId ?: return
        var updatedEntry = entry.updateEntry(
            entry.entry.copy(
                isSynced = false,
                operationType = OperationType.CREATE.name,
                accountId = currentAccountId,
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
        syncOperationsInternal(currentAccountId, listOf(updatedEntry))
        analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED)
    }

    /** Saves multiple new entries both locally and remotely. */
    override suspend fun addEntry(entries: List<Entry>) {
        val currentAccountId = accountId ?: return
        try {
            val updatedEntries = entries.map { entry ->
                val baseEntry = entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.CREATE.name,
                    accountId = currentAccountId,
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
            syncOperationsInternal(currentAccountId, updatedEntries)
            analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error saving new entries", e)
        }
    }

    /** Deletes an entry both locally and remotely. */
    override suspend fun deleteEntry(entry: Entry) {
        val currentAccountId = accountId ?: return
        try {
            val deleteEntry = entry.updateEntry(
                entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.DELETE.name,
                ),
            )
            syncOperationsInternal(currentAccountId, emptyList(), listOf(deleteEntry))
        } catch (e: Exception) {
            AppLog.e(TAG, "Error deleting entry", e)
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────

    override suspend fun syncOperations(newEntries: List<Entry>, deleteOps: List<Entry>) {
        val currentAccountId = accountId ?: return
        syncOperationsInternal(currentAccountId, newEntries, deleteOps)
    }

    /**
     * Main sync operations method that handles both new entries and deletions.
     */
    private suspend fun syncOperationsInternal(
        accountId: String,
        newEntries: List<Entry> = emptyList(),
        deleteOps: List<Entry> = emptyList(),
    ) {
        try {
            _isUpdating.value = true

            // 1. Get existing unsynced entries
            val unSyncedEntries = entryRepository.getUnSynced(accountId).toMutableList()

            // 2. Add new operations to unsynced list
            newEntries.forEach { entry ->
                unSyncedEntries.add(0, entry.updateEntry(entry.entry.copy(accountId = accountId)))
            }
            deleteOps.forEach { entry ->
                unSyncedEntries.add(0, entry)
            }
            unSyncedEntries.sortBy { it.entry.entryTimestamp }

            // 3. Process operations
            val successfulOperations = mutableListOf<Entry>()
            val failedOperations = mutableListOf<Entry>()

            // Build a single atomic batch for POST /v3/entries/. Baby entries map to
            // null and are skipped until Android 3 / MOB-381 wires the baby write.
            val sendable = unSyncedEntries.mapNotNull { op ->
                op.toUnifiedRequestOrNull()?.let { request -> op to request }
            }
            if (sendable.isNotEmpty()) {
                try {
                    val response = entryRepository.sendBatchToAPI(sendable.map { it.second })
                    // Whole batch succeeded — mark every sent op synced.
                    sendable.forEach { (op, _) ->
                        successfulOperations.add(op.updateEntry(entry = op.entry.copy(isSynced = true)))
                    }
                    // Persist the server-confirmed entries (with serverTimestamp) and advance the cursor.
                    // TODO(MOB-380): the legacy GET refetch below also advances the sync cursor. When the
                    // unified GET replaces operation/r4, drive a single sync-cursor source to avoid
                    // skipping cross-device entries between the two timestamp values.
                    val confirmed = response.entries.mapNotNull { it.toDomainEntry(accountId) }
                    EntryServiceHelper.executeOperations(entryRepository, confirmed)
                    accountRepository.updateSyncTimeStamp(response.timestamp)
                } catch (e: Exception) {
                    // Atomic failure — the whole batch is rolled back server-side; leave every
                    // op unsynced (attempts++) so the entire batch is retried on the next sync.
                    sendable.forEach { (op, _) ->
                        failedOperations.add(
                            op.updateEntry(
                                entry = op.entry.copy(
                                    isSynced = false,
                                    attempts = op.entry.attempts.plus(1),
                                ),
                            ),
                        )
                    }
                    AppLog.e(TAG, "Atomic batch send failed; whole batch left unsynced for retry", e)
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

            // 4. Capture last valid operation for goal alert
            val lastValidOperation =
                (successfulOperations + failedOperations)
                    .filter { it.entry.operationType == OperationType.CREATE.name }
                    .maxByOrNull { it.entry.entryTimestamp }

            // 5. Get operations from API
            val operationCount = entryRepository.getOperationCount(accountId)
            val operationsFromApi = mutableListOf<ScaleEntry>()
            try {
                val syncTimeStamp = accountRepository.getSyncTimeStamp().first()
                val response = entryRepository.getOperationsFromAPI(syncTimeStamp)
                if (response == null) {
                    AppLog.w(TAG, "No operations received from API")
                    _isUpdating.value = false
                    return
                }
                val scaleEntries = response.operations.map { fromScaleApiEntry(it, accountId = accountId) }
                operationsFromApi.addAll(scaleEntries)
                accountRepository.updateSyncTimeStamp(response.timestamp)
            } catch (e: Exception) {
                AppLog.e(TAG, "Error getting operations from API", e)
                EntryServiceHelper.executeOperations(
                    entryRepository,
                    successfulOperations,
                    userHasOperations = operationCount > 0,
                    arePlaceholders = true,
                )
            }

            // 6. Execute operations from API
            if (operationsFromApi.isNotEmpty()) {
                EntryServiceHelper.executeOperations(entryRepository, operationsFromApi)
            }

            // 7. API sync done: update timestamp, clear loader
            _lastUpdated.value = System.currentTimeMillis()
            _isUpdating.value = false

            // 8. Handle goal alerts
            if (lastValidOperation != null && lastValidOperation is ScaleEntry) {
                val operationWeight = lastValidOperation.scale.scaleEntry.weight
                appScope.launch {
                    try {
                        delay(GOAL_ALERT_DELAY_MS)
                        goalService.showGoalCompletionAlert(operationWeight * WEIGHT_CONVERSION_FACTOR)
                    } catch (err: Exception) {
                        AppLog.e(TAG, "syncOperations - unable to set Goal met", err)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error in syncOperations", e)
            _isUpdating.value = false
        }
    }

    /** Tries to sync entry data to local health integrations (Health Connect). */
    private suspend fun tryLocalIntegration(entry: Entry) {
        AppLog.d(TAG, "Operation: tryLocalIntegration called", entry.toString())
        try {
            val isIntegrated = healthConnectService.checkIntegrated()
            if (!isIntegrated) return
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
                    AppLog.d(TAG, "Successfully synced entry to Health Connect")
                } else {
                    AppLog.w(TAG, "Could not convert entry to PeriodBodyScaleSummary")
                }
            }
        } catch (err: Exception) {
            AppLog.e(TAG, "Error syncing to Health Connect", err)
            // Don't throw - this is a non-critical operation
        }
    }

    // ── Goal-card monitoring ─────────────────────────────────────────────

    override fun initializeGoalCardMonitoring(accountId: String) {
        activeJobs += appScope.launch {
            _lastUpdated.collect {
                try {
                    val entries = entryRepository.getEntriesByAccount(accountId, false)
                    AppLog.d(TAG, "User has scale entries (>= 3), checking goal card ${entries.size} - accountid - $accountId")
                    if (entries.size >= GOAL_CARD_MIN_ENTRIES) {
                        goalService.checkGoalCard()
                        AppLog.d(TAG, "User has scale entries (>= 3), checking goal card")
                    } else {
                        AppLog.d(TAG, "User has only scale entries, not enough for goal card")
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error checking entries for goal card in init", e.toString())
                }
            }
        }
    }

    companion object {
        private const val TAG = "EntryService"
        private const val GOAL_CARD_MIN_ENTRIES = 3
        private const val GOAL_ALERT_DELAY_MS = 3000L
        private const val WEIGHT_CONVERSION_FACTOR = 10
    }
}
