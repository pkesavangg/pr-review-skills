// domain/service/EntryService.kt
package com.greatergoods.meapp.data.services

import android.util.Log
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.common.Progress
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.model.storage.entry.PeriodBodyScaleSummary
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry
import com.greatergoods.meapp.domain.model.storage.entry.ScaleEntry.Companion.fromScaleApiEntry
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IEntryRepository
import com.greatergoods.meapp.domain.services.IEntryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryService @Inject constructor(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository
) : IEntryService {

    private val _isUpdating = MutableStateFlow(false)
    override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _latestEntry = MutableStateFlow<Entry?>(null)
    override val latestEntry: StateFlow<Entry?> = _latestEntry.asStateFlow()

    private val _last7Days = MutableStateFlow<List<Entry>>(listOf())
    override val last7Days = _last7Days.asStateFlow()

    private val _last30Days = MutableStateFlow<List<Entry>>(listOf())
    override val last30Days = _last30Days.asStateFlow()
    private val _progress = MutableStateFlow<Progress?>(null)
    override val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    override val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private var accountId: String? = null
    private var initialWeight: Double? = null

    override suspend fun getMonthlyAverage(): Flow<List<HistoryMonth>> {
        Log.d("CHECKING", "Monthly history size: ${accountId}")
        return entryRepository.getMonthlyAverage(accountId ?: "")
    }

    /**
     * Updates all entry-related data for the given account.
     * Fetches latest entry, last 7 and 30 days entries, and updates progress.
     * @param accountId The account ID to update data for.
     */
    override suspend fun updateAccountId(accountId: String) {
        this.accountId = accountId
    }

    /**
     * Saves a new entry both locally and remotely.
     * @param entry The entry to save.
     */
    override suspend fun addEntry(entry: Entry) {
        val updatedEntry = entry.updateEntry(
            entry = entry.entry.copy(
                isSynced = false,
                operationType = OperationType.CREATE.name,
                accountId = "1",
            ),
        )
        // handle other types if you have them
        entryRepository.insert(updatedEntry)
    }

    /**
     * Saves multiple new entries both locally and remotely.
     * @param entries The list of entries to save.
     */
    override suspend fun addEntry(entries: List<Entry>) {
        try {
            val updatedEntries = entries.map { entry ->
                entry.updateEntry(
                    entry = entry.entry.copy(
                        isSynced = false,
                        operationType = OperationType.CREATE.name,
                        accountId = "1",
                    ),
                )
            }
            this.syncOperations(updatedEntries)
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
            val deleteEntry = entry.updateEntry(
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
    override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>> =
        entryRepository.getEntriesByDeviceType(accountId, deviceType)

    /**
     * Main sync operations method that handles both new entries and deletions.
     * Matches the TypeScript syncOperations flow exactly.
     */
    override suspend fun syncOperations(newEntries: List<Entry>, deleteOps: List<Entry>) {
        if (accountId == null) return

        try {
            // 1. Get existing unsynced entries
            val unSyncedEntries = entryRepository.getUnSynced(accountId!!).toMutableList()

            // 2. Add new operations to unsynced list
            newEntries.forEach { entry ->
                unSyncedEntries.add(0, entry.updateEntry(entry.entry.copy(accountId = accountId!!)))
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
                    entryRepository.sendOperationToAPI((operation as ScaleEntry).toScaleApiEntry())
                    // If successful, mark as synced
                    val syncedOperation = operation.updateEntry(
                        entry = operation.entry.copy(
                            isSynced = true,
                        ),
                    )
                    successfulOperations.add(syncedOperation)
                } catch (e: Exception) {
                    // If failed, increment attempts and store for retry
                    val failedOperation = operation.updateEntry(
                        entry = operation.entry.copy(
                            isSynced = false,
                            attempts = operation.entry.attempts.plus(1),
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
            }

            // 6. Execute operations from API
            if (operationsFromApi.isNotEmpty()) {
                EntryServiceHelper.executeOperations(
                    entryRepository,
                    operationsFromApi,
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
            latestEntry = _latestEntry.value as? ScaleEntry,
            last7Days = _last7Days.value,
            last30Days = _last30Days.value,
            initialWeight = initialWeight,
            setProgress = { _progress.value = it },
        )
    }

    private fun clearAllData() {
        EntryServiceHelper.clearAllData(
            setLatestEntry = { _latestEntry.value = it },
            setLast7Days = { _last7Days.value = it ?: emptyList() },
            setLast30Days = { _last30Days.value = it ?: emptyList() },
            setProgress = { _progress.value = it },
        )
    }

    /**
     * Gets monthly averages of body scale data for an account using JOINs.
     */
    override fun getMonthlyBodyScaleAveragesWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryRepository.getMonthlyBodyScaleAveragesWithJoin(accountId)

    /**
     * Gets the latest body scale entry for each month for an account using JOINs.
     */
    override fun getMonthlyBodyScaleLatestWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryRepository.getMonthlyBodyScaleLatestWithJoin(accountId)

    /**
     * Gets daywise averages of body scale data for an account using JOINs.
     */
    override fun getDaywiseBodyScaleAveragesWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryRepository.getDaywiseBodyScaleAveragesWithJoin(accountId)

    /**
     * Gets the latest body scale entry for each day for an account using JOINs.
     */
    override fun getDaywiseBodyScaleLatestWithJoin(accountId: String): Flow<List<PeriodBodyScaleSummary>> =
        entryRepository.getDaywiseBodyScaleLatestWithJoin(accountId)
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
    fun createOperation(entry: Entry, type: OperationType): Entry? {
        val updatedEntry = entry.entry.copy(
            operationType = type.name,
            isSynced = false,
        )
        return entry.updateEntry(
            entry = updatedEntry,
        )
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
     * Executes a list of operations, handling both create and delete operations.
     * @param entryRepository The entry repository.
     * @param operations The list of operations to execute.
     * @param userHasOperations Whether the user has existing operations.
     * @param arePlaceholders Whether the operations are placeholders (not yet synced).
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
            val createOperations =
                sortedOperations.filter { it.entry.operationType == OperationType.CREATE.name }
            val deleteOperations =
                sortedOperations.filter { it.entry.operationType == OperationType.DELETE.name }

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
        val sortedEntries = entries
            .map { dateFormat.parse(it.entry.entryTimestamp.toString())!! }
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
        setProgress: (Progress) -> Unit
    ) {
        // Filter only non-null ScaleEntry for calculations
        val last7ScaleEntries = last7Days.map { it as ScaleEntry }
        val last30ScaleEntries = last30Days.map { it as ScaleEntry }

        // Get the oldest (last) scale entry in each period for comparison
        val initWeek = last7ScaleEntries.lastOrNull()
        val initMonth = last30ScaleEntries.lastOrNull()
        val initYear =
            last30ScaleEntries.lastOrNull() // Placeholder: adjust if you have a year list

        // Calculate week, month, year, and total progress (all as Double)
        val week = if (latestEntry != null && initWeek != null) {
            latestEntry.scale.scaleEntry.weight.toDouble() - initWeek.scale.scaleEntry.weight.toDouble()
        } else 0.0

        val month = if (latestEntry != null && initMonth != null) {
            latestEntry.scale.scaleEntry.weight.toDouble() - initMonth.scale.scaleEntry.weight.toDouble()
        } else 0.0

        val year = if (latestEntry != null && initYear != null) {
            latestEntry.scale.scaleEntry.weight.toDouble() - initYear.scale.scaleEntry.weight.toDouble()
        } else 0.0

        val total = if (latestEntry != null && initialWeight != null) {
            latestEntry.scale.scaleEntry.weight.toDouble() - initialWeight
        } else 0.0

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
        setLast7Days: (List<Entry>) -> Unit,
        setLast30Days: (List<Entry>) -> Unit,
        setProgress: (Progress?) -> Unit
    ) {
        setLatestEntry(null)
        setLast7Days(emptyList())
        setLast30Days(emptyList())
        setProgress(null)
    }
}
