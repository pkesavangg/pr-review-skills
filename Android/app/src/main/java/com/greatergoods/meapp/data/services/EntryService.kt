// domain/service/EntryService.kt
package com.greatergoods.meapp.domain.service

import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.core.logging.AppLog
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryService @Inject constructor(
    private val entryRepository: IEntryRepository,
) : IEntryService {

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _latestEntry = MutableStateFlow<EntryEntity?>(null)
    override val latestEntry: StateFlow<EntryEntity?> = _latestEntry.asStateFlow()

    private val _last7Days = MutableStateFlow<List<EntryEntity>?>(null)
    override val last7Days: StateFlow<List<EntryEntity>?> = _last7Days.asStateFlow()

    private val _last30Days = MutableStateFlow<List<EntryEntity>?>(null)
    override val last30Days: StateFlow<List<EntryEntity>?> = _last30Days.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    override val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private var accountId: String? = null

    override suspend fun updateAllData(accountId: String) {
        this.accountId = accountId
        _isUpdating.value = true
        try {
            // Collect all required data from repository
            val latestEntryFlow = entryRepository.getLatestEntry(accountId)
            val last7DaysFlow = entryRepository.getLastNDaysEntries(accountId, 7)
            val last30DaysFlow = entryRepository.getLastNDaysEntries(accountId, 30)

            // Collect flows
            latestEntryFlow.collect { entry ->
                _latestEntry.value = entry
            }

            last7DaysFlow.collect { entries ->
                _last7Days.value = entries
            }

            last30DaysFlow.collect { entries ->
                _last30Days.value = entries
                updateProgress(accountId)
            }
        } finally {
            _isUpdating.value = false
        }
    }

    override suspend fun saveNewEntry(entry: EntryEntity) {
        try {
            // First save locally
            entryRepository.saveEntry(entry)

            // Then sync with server
            val operation = createOperation(entry, OperationType.CREATE)
            entryRepository.sendOperationToAPI(operation)

            // Update last updated timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error saving new entry", e.toString())
            // Add to opstack for retry
            addToOpstack(entry, OperationType.CREATE)
        }
    }

    override suspend fun saveNewEntries(entries: List<EntryEntity>) {
        try {
            // First save locally
            entryRepository.saveEntries(entries)

            // Then sync with server
            val operations = entries.map { createOperation(it, OperationType.CREATE) }
            operations.forEach { entryRepository.sendOperationToAPI(it) }

            // Update last updated timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error saving new entries", e.toString())
            // Add to opstack for retry
            entries.forEach { addToOpstack(it, OperationType.CREATE) }
        }
    }

    override suspend fun deleteEntry(entry: EntryEntity) {
        try {
            // First delete locally
            entryRepository.deleteEntry(entry)

            // Then sync with server
            val operation = createOperation(entry, OperationType.DELETE)
            entryRepository.sendOperationToAPI(operation)

            // Update last updated timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error deleting entry", e.toString())
            // Add to opstack for retry
            addToOpstack(entry, OperationType.DELETE)
        }
    }

    override suspend fun syncEntries() {
        try {
            // Get unsynced entries from opstack
            val opstack = entryRepository.getOpstack(accountId ?: return)

            // Try to sync each operation
            opstack.forEach { operation ->
                try {
                    entryRepository.sendOperationToAPI(operation)
                    // Remove from opstack if successful
                    entryRepository.removeFromOpstack(operation)
                } catch (e: Exception) {
                    AppLog.e("EntryService", "Error syncing operation", e.toString())
                    // Increment attempts count
                    entryRepository.incrementOpstackAttempts(operation)
                }
            }

            // Get new operations from server
            val operations = entryRepository.getOperationsFromAPI(_lastUpdated.value)
            executeOperations(operations)

            // Update last updated timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error syncing entries", e.toString())
        }
    }

    private fun createOperation(entry: EntryEntity, type: OperationType): EntryEntity {
        return entry.copy(
            operationType = type.name,
            isSynced = false,
        )
    }

    private suspend fun addToOpstack(entry: EntryEntity, type: OperationType) {
        try {
            val operation = createOperation(entry, type)
            entryRepository.addToOpstack(operation)
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error adding to opstack", e.toString())
        }
    }

    private suspend fun executeOperations(operations: List<EntryEntity>) {
        if (operations.isEmpty()) return

        try {
            // Sort operations by server timestamp
            val sortedOperations = operations.sortedBy { it.serverTimestamp }

            // Execute each operation
            sortedOperations.forEach { operation ->
                when (operation.operationType) {
                    OperationType.CREATE.name -> {
                        entryRepository.saveEntry(operation)
                    }

                    OperationType.DELETE.name -> {
                        entryRepository.deleteEntry(operation)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error executing operations", e.toString())
        }
    }

    private suspend fun updateProgress(accountId: String) {
        val latestEntry = _latestEntry.value
        val last7Days = _last7Days.value
        val last30Days = _last30Days.value

        val initWeek = last7Days?.lastOrNull()
        val initMonth = last30Days?.lastOrNull()

        _progress.value = Progress(
            latest = latestEntry,
            currentStreak = calculateCurrentStreak(last30Days ?: emptyList()),
            longestStreak = calculateLongestStreak(last30Days ?: emptyList()),
            count = last30Days?.size ?: 0,
            initWeek = initWeek,
            initMonth = initMonth,
            initYear = null,
        )
    }

    private fun calculateCurrentStreak(entries: List<EntryEntity>): Int {
        if (entries.isEmpty()) return 0

        var streak = 0
        val today = LocalDate.now()
        var currentDate = today

        for (entry in entries.sortedByDescending { it.entryTimestamp }) {
            val entryDate = LocalDate.parse(entry.entryTimestamp)
            if (entryDate == currentDate) {
                streak++
                currentDate = currentDate.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }

    private fun calculateLongestStreak(entries: List<EntryEntity>): Int {
        if (entries.isEmpty()) return 0

        var longestStreak = 0
        var currentStreak = 1
        val sortedEntries = entries.sortedByDescending { it.entryTimestamp }

        if (sortedEntries.isEmpty()) return 0

        var previousDate = LocalDate.parse(sortedEntries[0].entryTimestamp)

        for (i in 1 until sortedEntries.size) {
            val currentDate = LocalDate.parse(sortedEntries[i].entryTimestamp)
            if (currentDate == previousDate.minusDays(1)) {
                currentStreak++
            } else {
                longestStreak = maxOf(longestStreak, currentStreak)
                currentStreak = 1
            }
            previousDate = currentDate
        }

        return maxOf(longestStreak, currentStreak)
    }

    private fun clearAllData() {
        _latestEntry.value = null
        _last7Days.value = null
        _last30Days.value = null
        _progress.value = null
    }
}

enum class OperationType {
    CREATE,
    DELETE
}
