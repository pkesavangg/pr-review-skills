// domain/service/EntryService.kt
package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
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

    /**
     * Updates only an entry's note locally (e.g. editing a note from History). The note is
     * device-local (the server contract carries no note for weight); the local write is
     * preserved across sync by [IEntryRepository]. See MOB-438.
     */
    override suspend fun updateNote(entry: Entry, note: String?) {
        try {
            entryRepository.updateNote(entry, note)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating note: ${e.message}", e)
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

            for (operation in unSyncedEntries) {
                try {
                    entryRepository.sendOperationToAPI((operation as ScaleEntry).toScaleApiEntry())
                    val syncedOperation = operation.updateEntry(entry = operation.entry.copy(isSynced = true))
                    successfulOperations.add(syncedOperation)
                } catch (e: Exception) {
                    val failedOperation = operation.updateEntry(
                        entry = operation.entry.copy(
                            isSynced = false,
                            attempts = operation.entry.attempts.plus(1),
                        ),
                    )
                    failedOperations.add(failedOperation)
                    AppLog.e(TAG, "Error sending operation to API", e)
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
                // The server contract (ScaleApiEntry) carries no note field, so a freshly
                // created weight entry comes back from the API without its note. Re-attach
                // notes from the operations we just sent (matched by timestamp) so a
                // locally-entered note survives the sync round-trip (MOB-438, device-local).
                val localNotesByTimestamp = newEntries
                    .filterIsInstance<ScaleEntry>()
                    .mapNotNull { op ->
                        op.scale.scaleEntry.note
                            ?.takeIf { it.isNotBlank() }
                            ?.let { op.entry.entryTimestamp to it }
                    }
                    .toMap()
                val mergedApiOps = operationsFromApi.map { op ->
                    if (op.scale.scaleEntry.note.isNullOrBlank()) {
                        localNotesByTimestamp[op.entry.entryTimestamp]?.let { note ->
                            op.copy(scale = op.scale.copy(scaleEntry = op.scale.scaleEntry.copy(note = note)))
                        } ?: op
                    } else {
                        op
                    }
                }
                EntryServiceHelper.executeOperations(entryRepository, mergedApiOps)
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
