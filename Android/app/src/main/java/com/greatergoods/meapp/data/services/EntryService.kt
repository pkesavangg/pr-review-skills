// domain/service/EntryService.kt
package com.greatergoods.meapp.data.services

import com.greatergoods.meapp.core.logging.AppLog
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.AccountEntity
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.Entry.Companion.fromScaleEntry
import com.greatergoods.meapp.domain.model.api.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.common.HistoryMonth
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
import android.util.Log

@Singleton
class EntryService @Inject constructor(
    private val entryRepository: IEntryRepository,
    private val accountDao: AccountDao
) : IEntryService {

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _latestEntry = MutableStateFlow<Entry?>(null)
    override val latestEntry: StateFlow<Entry?> = _latestEntry.asStateFlow()

    private val _last7Days = MutableStateFlow<List<Entry>?>(null)
    override val last7Days: StateFlow<List<Entry>?> = _last7Days.asStateFlow()

    private val _last30Days = MutableStateFlow<List<Entry>?>(null)
    override val last30Days: StateFlow<List<Entry>?> = _last30Days.asStateFlow()

    private val _monthsLastYear = MutableStateFlow<List<HistoryMonth>?>(null)
    override val monthsLastYear: StateFlow<List<HistoryMonth>?> = _monthsLastYear.asStateFlow()

    private val _monthsAll = MutableStateFlow<List<HistoryMonth>?>(null)
    override val monthsAll: StateFlow<List<HistoryMonth>?> = _monthsAll.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    override val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private var accountId: String? = null
    private var initialWeight: Double? = null

    /**
     * Updates all entry-related data for the given account.
     * Fetches latest entry, last 7 and 30 days entries, and updates progress.
     * @param accountId The account ID to update data for.
     */
    override suspend fun updateAllData(accountId: String) {
        this.clearAllData()
        this.accountId = accountId
        _isUpdating.value = true
        try {
            updateLatestEntry(accountId)
            val last7DaysFlow = entryRepository.getLastNDaysEntries(accountId, 7)
            val last30DaysFlow = entryRepository.getLastNDaysEntries(accountId, 30)
            val monthsLastYearFlow = entryRepository.getMonthsLastYear(accountId)
            val monthsAllFlow = entryRepository.getMonthsAll(accountId)

            last7DaysFlow.collect { entries ->
                _last7Days.value = entries
            }

            last30DaysFlow.collect { entries ->
                _last30Days.value = entries
            }

            monthsLastYearFlow.collect { months ->
                _monthsLastYear.value = months
            }

            monthsAllFlow.collect { months ->
                _monthsAll.value = months
            }

            updateProgress(accountId)
        } finally {
            _isUpdating.value = false
        }
    }

    /**
     * Saves a new entry both locally and remotely.
     * @param entry The entry to save.
     */
    override suspend fun addEntry(entry: Entry) {
        try {
            Log.i("CHECKING", "EntryService adding entry: $entry")

            // Ensure we have a default account
            val defaultAccountId = "default"
            val defaultAccount = accountDao.getAccountById(defaultAccountId)

            if (defaultAccount == null) {
                // Create default account if it doesn't exist
                val newAccount = AccountEntity(
                    id = defaultAccountId,
                    isActiveAccount = true,
                    isLoggedIn = true,
                )
                accountDao.insert(newAccount)
                Log.i("CHECKING", "Created default account")
            }

            val updatedEntry = entry.copy(
                entry = entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.CREATE.name,
                    accountId = defaultAccountId,
                ),
            )
            Log.i("CHECKING", "EntryService updated entry: $updatedEntry")
            entryRepository.insert(updatedEntry)
            Log.i("CHECKING", "EntryService entry inserted successfully")
        } catch (e: Exception) {
            Log.e("CHECKING", "EntryService error adding entry", e)
            throw e
        }
    }

    /**
     * Saves multiple new entries both locally and remotely.
     * @param entries The list of entries to save.
     */
    override suspend fun addEntry(entries: List<Entry>) {
        try {
            val updatedEntries = entries.map { entry ->
                entry.copy(
                    entry = entry.entry.copy(
                        isSynced = false,
                        operationType = OperationType.CREATE.name,
                    ),
                )
            }
            syncOperations(updatedEntries)
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error saving new entries", e.toString())
        }
    }

    /**
     * Deletes an entry both locally and remotely.
     * @param entry The entry to delete.
     */
    override suspend fun deleteEntry(entry: Entry) {
        try {
            val deleteEntry = entry.copy(
                entry = entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.DELETE.name,
                ),
            )
            syncOperations(emptyList(), listOf(deleteEntry))
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error deleting entry", e.toString())
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
     * Main sync operations method that handles both new entries and deletions.
     * Matches the TypeScript syncOperations flow exactly.
     */
    override suspend fun syncOperations(
        newEntries: List<Entry>,
        deleteOps: List<Entry>
    ) {
        if (accountId == null) return

        try {
            // 1. Get existing unsynced entries
            val unSyncedEntries = entryRepository.getUnSynced(accountId!!).toMutableList()

            // 2. Add new operations to unsynced list
            newEntries.forEach { entry ->
                unSyncedEntries.add(0, entry)
            }
            deleteOps.forEach { entry ->
                unSyncedEntries.add(0, entry)
            }

            // 3. Process operations
            val successfulOperations = mutableListOf<Entry>()
            val failedOperations = mutableListOf<Entry>()

            for (operation in unSyncedEntries) {
                try {
                    // Try to send to API
                    entryRepository.sendOperationToAPI(operation.toScaleEntry())
                    // If successful, mark as synced
                    val syncedOperation = operation.copy(
                        entry = operation.entry.copy(
                            isSynced = true,
                        ),
                    )
                    successfulOperations.add(syncedOperation)
                } catch (e: Exception) {
                    // If failed, increment attempts and store for retry
                    val failedOperation = operation.copy(
                        entry = operation.entry.copy(
                            isSynced = false,
                            attempts = operation.entry.attempts + 1,
                        ),
                    )
                    failedOperations.add(failedOperation)
                    AppLog.e("EntryService", "Error sending operation to API", e.toString())
                }
            }

            if (failedOperations.isNotEmpty()) {
                EntryServiceHelper.executeOperations(
                    entryRepository,
                    failedOperations,
                    userHasOperations = true,
                )
            }

            // 4. Handle goal alerts
            val lastValidOperation = (successfulOperations + failedOperations)
                .filter { it.entry.operationType == OperationType.CREATE.name }
                .maxByOrNull { it.entry.entryTimestamp }

            lastValidOperation?.let {
                // Trigger goal alert if needed
                // TODO: Implement goal alert service
            }

            // 5. Get operations from API
            val operationCount = entryRepository.getOperationCount(accountId!!)
            val operationsFromAPI = try {
                entryRepository.getOperationsFromAPI(_lastUpdated.value)
                    .map { fromScaleEntry(it, accountId = accountId!!) }
            } catch (e: Exception) {
                AppLog.e("EntryService", "Error getting operations from API", e.toString())
                // If API fails, store successful operations as placeholders
                // This means these operations will be marked as synced but might need to be
                // re-synced later when API is available
                EntryServiceHelper.executeOperations(
                    entryRepository,
                    successfulOperations,
                    userHasOperations = operationCount > 0,
                    arePlaceholders = true,
                )
                emptyList()
            }

            // 6. Execute operations from API
            if (operationsFromAPI.isNotEmpty()) {
                EntryServiceHelper.executeOperations(
                    entryRepository,
                    operationsFromAPI,
                )
            }

            // 7. Update last updated timestamp
            _lastUpdated.value = System.currentTimeMillis()
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error in syncOperations", e.toString())
        }
    }

    private suspend fun updateLatestEntry(accountId: String) {
        try {
            entryRepository.getLatestEntry(accountId)?.collect { latest ->
                _latestEntry.value = latest
            }
        } catch (e: Exception) {
            AppLog.e("EntryService", "Error updating latest entry", e.toString())
        }
    }

    private fun updateProgress(accountId: String) {
        EntryServiceHelper.updateProgress(
            latestEntry = _latestEntry.value,
            last7Days = _last7Days.value,
            last30Days = _last30Days.value,
            initialWeight = initialWeight,
            setProgress = { _progress.value = it },
        )
    }

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
    DELETE,
    UPDATE,
}

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

    suspend fun executeOperations(
        entryRepository: IEntryRepository,
        operations: List<Entry>,
        userHasOperations: Boolean = true,
        arePlaceholders: Boolean = false
    ) {
        if (operations.isEmpty()) return

        try {
            // Sort operations by server timestamp
            val sortedOperations = operations.sortedBy { it.entry.serverTimestamp }

            // Separate create and delete operations
            val createOperations = sortedOperations.filter { it.entry.operationType == OperationType.CREATE.name }
            val deleteOperations = sortedOperations.filter { it.entry.operationType == OperationType.DELETE.name }

            // Handle create operations
            for (operation in createOperations) {
                // Check if entry exists
                val exists = if (userHasOperations) {
                    entryRepository.getEntryById(operation.entry.id) != null
                } else false

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
            AppLog.e("EntryService", "Error executing operations", e.toString())
            throw e
        }
    }

    fun updateProgress(
        latestEntry: Entry?,
        last7Days: List<Entry>?,
        last30Days: List<Entry>?,
        initialWeight: Double?,
        setProgress: (Progress) -> Unit
    ) {
        val initWeek = last7Days?.lastOrNull()
        val initMonth = last30Days?.lastOrNull()
        val initYear = last30Days?.lastOrNull()

        val week = if (latestEntry?.scaleEntry != null && initWeek != null) {
            latestEntry.scaleEntry.weight - (initWeek.scaleEntry?.weight ?: 0)
        } else 0.0

        val month = if (latestEntry?.scaleEntry != null && initMonth != null) {
            latestEntry.scaleEntry.weight - (initMonth.scaleEntry?.weight ?: 0)
        } else 0.0

        val year = if (latestEntry?.scaleEntry != null && initYear != null) {
            latestEntry.scaleEntry.weight - (initYear.scaleEntry?.weight ?: 0)
        } else 0.0

        val total = if (latestEntry?.scaleEntry != null && initialWeight != null) {
            latestEntry.scaleEntry.weight - initialWeight
        } else 0.0

        setProgress(
            Progress(
                latest = latestEntry,
                currentStreak = calculateCurrentStreak(last30Days ?: emptyList()),
                longestStreak = calculateLongestStreak(last30Days ?: emptyList()),
                count = last30Days?.size ?: 0,
                initWt = initialWeight ?: 0.0,
                week = week.toDouble(),
                month = month.toDouble(),
                year = year.toDouble(),
                total = total,
                initWeek = initWeek,
                initMonth = initMonth,
                initYear = initYear,
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
