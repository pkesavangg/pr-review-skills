// data/repository/EntryRepository.kt
package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.domain.model.api.entry.ScaleApiEntry
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.storage.entry.Entry
import com.greatergoods.meapp.domain.repository.IEntryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * Repository implementation for managing entries.
 * Only supports the 14 direct functions from EntryDao.
 */
@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val entryApi: EntryApi,
) : IEntryRepository {
    /**
     * Inserts a single entry.
     */
    override suspend fun insert(entry: Entry): Long {
        return entryDao.insert(entry)
    }

    /**
     * Updates a single entry.
     */
    override suspend fun update(entry: Entry): Int {
        return entryDao.update(entry.entry)
    }

    /**
     * Inserts a list of entries.
     */
    override suspend fun insert(entries: List<Entry>) = entryDao.insert(entries)

    /**
     * Marks an entry as deleted.
     */
    override suspend fun delete(entry: Entry) = entryDao.delete(entry)

    /**
     * Deletes an entry by its ID.
     */
    override suspend fun deleteById(id: Long): Int = entryDao.deleteById(id)

    /**
     * Gets an entry by its ID.
     */
    override suspend fun getEntryById(id: Long): Entry? = entryDao.getEntryById(id)?.toEntry()

    /**
     * Gets the latest valid entry for an account.
     */
    override suspend fun getLatestEntry(accountId: String): Flow<Entry>? =
        entryDao.getLatestEntry(accountId)?.map { flow ->
            flow.toEntry()
        }

    /**
     * Gets all valid entries for an account.
     */
    override suspend fun getEntriesByAccount(accountId: String): List<Entry> =
        entryDao.getEntriesByAccount(accountId).map { it.toEntry() }

    /**
     * Gets valid entries for an account within a time range.
     */
    override fun getEntriesByTimeRange(
        accountId: String,
        startTime: String,
        endTime: String
    ): Flow<List<Entry>> = entryDao.getEntriesByTimeRange(accountId, startTime, endTime).map { flow ->
        flow.map { it.toEntry() }
    }

    /**
     * Gets valid entries for an account by device type.
     */
    override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>> =
        entryDao.getEntriesByDeviceType(accountId, deviceType).map { flow ->
            flow.map { it.toEntry() }
        }

    /**
     * Retrieves entries for the last N days for a given account.
     * @param accountId The account ID
     * @param days Number of days to look back
     * @return Flow of list of Entry objects
     */
    override fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<Entry>> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis.toString()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = calendar.timeInMillis.toString()
        CoroutineScope(IO).launch {
            Log.d(
                "EntryRepository",
                "startDate: ${entryDao.getEntriesByTimeRange(accountId, startDate, endDate).first()}",
            )
        }
        return entryDao.getEntriesByTimeRange(accountId, startDate, endDate).map { flow ->
            flow.map { it.toEntry() }
        }
    }

    override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> = flow {
        // emit(entryDao.deleteAllEntriesForAccount(accountId))
    }

    /**
     * Gets entries for an account by operation type.
     */
    override fun getEntriesByOperationType(accountId: String, operationType: String): Flow<List<Entry>> =
        entryDao.getEntriesByOperationType(accountId, operationType).map { flow ->
            flow.map { it.toEntry() }
        }

    /**
     * Gets all unsynced entries for an account.
     */
    override suspend fun getUnSynced(accountId: String): List<Entry> =
        entryDao.getUnSynced(accountId).map { it.toEntry() }

    /**
     * Increments the attempts count for an entry.
     */
    override suspend fun incrementAttempts(id: Long): Int = entryDao.incrementAttempts(id)

    /**
     * Gets failed operations for an account.
     */
    override suspend fun getFailedOperations(accountId: String, maxAttempts: Int): List<Entry> =
        entryDao.getFailedOperations(accountId, maxAttempts).map { it.toEntry() }

    /**
     * Clears all unsynced entries for an account.
     */
    override suspend fun clearUnSynced(accountId: String): Int = entryDao.clearUnSynced(accountId)

    /**
     * Sends an operation to the API and marks it as synced if successful. Adds to opstack if failed.
     * @param operation The operation entry to send.
     * @throws Exception if the API call fails.
     */
    override suspend fun sendOperationToAPI(operation: ScaleApiEntry?) {
        try {
            if (operation == null) {
                throw IllegalArgumentException("Operation cannot be null")
            }
            entryApi.sendOperation(operation)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Gets operations from the API since the last update, or all if lastUpdated is null.
     * @param lastUpdated The last updated timestamp, or null.
     * @return List of operations.
     */
    override suspend fun getOperationsFromAPI(lastUpdated: Long?): List<ScaleApiEntry> {
        return try {
            val response = if (lastUpdated != null) {
                entryApi.getOperations(lastUpdated)
            } else {
                entryApi.getAllOperations()
            }
            response.operations.map { it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets entries for a specific month and year.
     * @param accountId The account ID
     * @param month The month in YYYY-MM format
     * @return Flow of list of entries for the specified month
     */
    override fun getMonthDetail(accountId: String, month: String): Flow<List<Entry?>> =
        entryDao.getMonthDetail(accountId, month).map { views ->
            views.map { it.toEntry() }
        }

    /**
     * Gets monthly aggregated data for the last year.
     * @param accountId The account ID
     * @return Flow of list of monthly aggregated data
     */
    override fun getMonthsLastYear(accountId: String): Flow<List<HistoryMonth>> {
        return flow {
            emit(listOf())
        }
    }

    /**
     * Gets all monthly aggregated data.
     * @param accountId The account ID
     * @return Flow of list of all monthly aggregated data
     */
    override fun getMonthsAll(accountId: String): Flow<List<HistoryMonth>> {
        return flow {
            emit(listOf())
        }
    }

    /**
     * Gets the operation count for an account.
     * @param accountId The account ID
     * @return The number of operations
     */
    override suspend fun getOperationCount(accountId: String): Int =
        entryDao.getOperationCount(accountId)
}
