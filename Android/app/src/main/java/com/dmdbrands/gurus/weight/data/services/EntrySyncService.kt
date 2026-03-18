package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry.Companion.fromScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

class EntrySyncService(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val goalService: IGoalService,
    private val healthConnectService: IHealthConnectService,
    private val healthConnectRepository: IHealthConnectRepository,
    private val appScope: CoroutineScope,
) : IEntrySyncService {

    private val TAG = "EntrySyncService"

    companion object {
        private const val GOAL_ALERT_DELAY_MS = 3000L
        private const val WEIGHT_CONVERSION_FACTOR = 10
    }

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    override fun reset() {
        _isUpdating.value = false
        _lastUpdated.value = null
    }

    /**
     * Main sync operations method that handles both new entries and deletions.
     */
    override suspend fun syncOperations(
        accountId: String,
        newEntries: List<Entry>,
        deleteOps: List<Entry>,
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

    /**
     * Tries to sync entry data to local health integrations (Health Connect).
     */
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
}