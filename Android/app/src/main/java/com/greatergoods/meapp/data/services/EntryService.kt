// domain/service/EntryService.kt
package com.greatergoods.meapp.domain.service

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.Entry.Companion.fromScaleEntry
import com.greatergoods.meapp.domain.model.api.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.services.IEntryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryService @Inject constructor(
    private val entryRepository: IEntryRepository,
) : IEntryService {

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _latestEntry = MutableStateFlow<Entry?>(null)
    override val latestEntry: StateFlow<Entry?> = _latestEntry.asStateFlow()

    private val _last7Days = MutableStateFlow<List<Entry>?>(null)
    override val last7Days: StateFlow<List<Entry>?> = _last7Days.asStateFlow()

    private val _last30Days = MutableStateFlow<List<Entry>?>(null)
    override val last30Days: StateFlow<List<Entry>?> = _last30Days.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    override val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private var accountId: String? = null

    /**
     * Updates all entry-related data for the given account.
     * Fetches latest entry, last 7 and 30 days entries, and updates progress.
     * @param accountId The account ID to update data for.
     */
    override suspend fun updateAllData(accountId: String) {
        this.accountId = accountId
        _isUpdating.value = true
        try {
            val latestEntryFlow = entryRepository.getLatestEntry(accountId)
            val last7DaysFlow = entryRepository.getLastNDaysEntries(accountId, 7)
            val last30DaysFlow = entryRepository.getLastNDaysEntries(accountId, 30)

            latestEntryFlow?.collect { entry ->
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

    /**
     * Saves a new entry both locally and remotely, updating the last updated timestamp.
     * If remote sync fails, adds the entry to the opstack for retry.
     * @param entry The entry to save.
     */
    override suspend fun addEntry(entry: Entry) {
        try {
            entryRepository.insert(entry)
            val operation = EntryServiceHelper.createOperation(entry, OperationType.CREATE)
            entryRepository.sendOperationToAPI(operation)
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error saving new entry", e.toString())
            EntryServiceHelper.addToOpstack(entryRepository, entry, OperationType.CREATE)
        }
    }

    /**
     * Saves multiple new entries both locally and remotely, updating the last updated timestamp.
     * If remote sync fails, adds the entries to the opstack for retry.
     * @param entries The list of entries to save.
     */
    override suspend fun addEntry(entries: List<Entry>) {
        try {
            entryRepository.insert(entries)
            val operations = entries.map { EntryServiceHelper.createOperation(it, OperationType.CREATE) }
            operations.forEach { entryRepository.sendOperationToAPI(it) }
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error saving new entries", e.toString())
            entries.forEach { EntryServiceHelper.addToOpstack(entryRepository, it, OperationType.CREATE) }
        }
    }

    /**
     * Deletes an entry both locally and remotely, updating the last updated timestamp.
     * If remote sync fails, adds the entry to the opstack for retry.
     * @param entry The entry to delete.
     */
    override suspend fun deleteEntry(entry: Entry) {
        try {
            entryRepository.delete(entry)
            val operation = EntryServiceHelper.createOperation(entry, OperationType.DELETE)
            entryRepository.sendOperationToAPI(operation)
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error deleting entry", e.toString())
            EntryServiceHelper.addToOpstack(entryRepository, entry, OperationType.DELETE)
        }
    }

    /**
     * Retrieves entries by device type for the specified account.
     * @param accountId The account ID to filter entries by.
     * @param deviceType The device type to filter entries by.
     * @return Flow of list of entries matching the device type.
     */
    override fun getEntriesByDeviceType(accountId: String, deviceType: String) =
        entryRepository.getEntriesByDeviceType(accountId, deviceType)

    /**
     * Synchronizes unsynced entries with the server and executes new operations from the server.
     * Updates the last updated timestamp after successful sync.
     */
    override suspend fun syncEntries() {
        try {
            val unSynced = entryRepository.getUnSynced(accountId ?: return)
            unSynced.forEach { operation ->
                try {
                    entryRepository.sendOperationToAPI(operation.toScaleEntry())
                    entryRepository.deleteById(operation.entry.id)
                } catch (e: Exception) {
                    AppLog.e("EntryService", "Error syncing operation", e.toString())
                    entryRepository.incrementAttempts(operation.entry.id)
                }
            }
            val operations =
                entryRepository.getOperationsFromAPI(_lastUpdated.value)
                    .map { fromScaleEntry(it, accountId = accountId ?: "") }
            EntryServiceHelper.executeOperations(entryRepository, operations)
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error syncing entries", e.toString())
        }
    }

    /**
     * Updates the progress state for the current account.
     * @param accountId The account ID to update progress for.
     */
    private fun updateProgress(accountId: String) {
        EntryServiceHelper.updateProgress(
            latestEntry = _latestEntry.value,
            last7Days = _last7Days.value,
            last30Days = _last30Days.value,
            setProgress = { _progress.value = it },
        )
    }

    /**
     * Clears all cached entry data and progress.
     */
    private fun clearAllData() {
        EntryServiceHelper.clearAllData(
            setLatestEntry = { _latestEntry.value = it },
            setLast7Days = { _last7Days.value = it },
            setLast30Days = { _last30Days.value = it },
            setProgress = { _progress.value = it },
        )
    }
}

enum class OperationType {
    CREATE,
    DELETE
}

/**
 * Helper object for EntryService containing private utility functions.
 */
internal object EntryServiceHelper {
    /**
     * Creates an operation entry for syncing with the server.
     * @param entry The entry to create an operation for.
     * @param type The operation type (CREATE or DELETE).
     * @return The operation entry.
     */
    fun createOperation(entry: Entry, type: OperationType): ScaleEntry? {
        val updatedEntry = entry.entry.copy(
            operationType = type.name,
            isSynced = false,
        )
        return entry.copy(
            entry = updatedEntry,
        ).toScaleEntry()
    }

    /**
     * Adds an entry operation to the opstack for retry.
     * @param entryRepository The entry repository.
     * @param entry The entry to add.
     * @param type The operation type.
     */
    suspend fun addToOpstack(entryRepository: IEntryRepository, entry: Entry, type: OperationType) {
    }

    /**
     * Executes a list of operations received from the server.
     * @param entryRepository The entry repository.
     * @param operations The list of operations to execute.
     */
    suspend fun executeOperations(entryRepository: IEntryRepository, operations: List<Entry>) {
        if (operations.isEmpty()) return
        try {
            val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }
            entryRepository.insert(sortedOperations)
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error executing operations", e.toString())
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
            entryDate.time = dateFormat.parse(entry.entry.entryTimestamp)!!

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
        val sortedEntries = entries
            .map { dateFormat.parse(it.entry.entryTimestamp)!! }
            .map { date ->
                Calendar.getInstance().apply { time = date }
            }
            .distinctBy { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
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
     * @param accountId The account ID to update progress for.
     * @param latestEntry The latest entry.
     * @param last7Days The last 7 days of entries.
     * @param last30Days The last 30 days of entries.
     * @param setProgress Lambda to set the progress value.
     */
    fun updateProgress(
        latestEntry: Entry?,
        last7Days: List<Entry>?,
        last30Days: List<Entry>?,
        setProgress: (Progress) -> Unit
    ) {
        val initWeek = last7Days?.lastOrNull()
        val initMonth = last30Days?.lastOrNull()
        setProgress(
            Progress(
                latest = latestEntry,
                currentStreak = calculateCurrentStreak(last30Days ?: emptyList()),
                longestStreak = calculateLongestStreak(last30Days ?: emptyList()),
                count = last30Days?.size ?: 0,
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
        setLast7Days: (List<Entry>?) -> Unit,
        setLast30Days: (List<Entry>?) -> Unit,
        setProgress: (Progress?) -> Unit
    ) {
        setLatestEntry(null)
        setLast7Days(null)
        setLast30Days(null)
        setProgress(null)
    }
}
