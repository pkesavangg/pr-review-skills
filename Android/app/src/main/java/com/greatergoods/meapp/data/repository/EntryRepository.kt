// data/repository/EntryRepository.kt
package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val entryApi: EntryApi
) : IEntryRepository {
    /**
     * Returns a Flow of all entries for a dummy account (for testing).
     */
    override fun getAllEntries(): Flow<List<Entry>> = entryDao.getEntriesByAccountId("dummy_account_id")

    /**
     * Returns a Flow emitting the entry with the given ID, or null if not found.
     * @param id The entry ID.
     */
    override fun getEntryById(id: Long): Flow<Entry?> = flow {
        emit(entryDao.getEntryById(id))
    }

    /**
     * Saves a single entry to the database and returns a Flow emitting the new row ID.
     * @param entry The entry to save.
     */
    override suspend fun saveEntry(entry: EntryEntity): Flow<Long> = flow {
        emit(entryDao.insert(entry))
    }

    /**
     * Saves multiple entries to the database.
     * @param entries The list of entries to save.
     */
    override suspend fun saveEntries(entries: List<EntryEntity>) {
        entries.forEach { entryDao.insert(it) }
    }

    /**
     * Updates an entry in the database and returns a Flow emitting the number of rows updated.
     * @param entry The entry to update.
     */
    override suspend fun updateEntry(entry: EntryEntity): Flow<Int> = flow {
        emit(entryDao.update(entry))
    }

    /**
     * Deletes an entry from the database and returns a Flow emitting the number of rows deleted.
     * @param entry The entry to delete.
     */
    override suspend fun deleteEntry(entry: EntryEntity): Flow<Int> = flow {
        emit(entryDao.delete(entry))
    }

    /**
     * Returns a Flow of entries for the given account and date range.
     * @param accountId The account ID.
     * @param startDate The start date (inclusive).
     * @param endDate The end date (inclusive).
     */
    override fun getEntriesByDateRange(accountId: String, startDate: String, endDate: String): Flow<List<Entry>> {
        return entryDao.getEntriesByTimeRange(accountId, startDate, endDate)
    }

    /**
     * Returns a Flow emitting the latest entry for the given account.
     * @param accountId The account ID.
     */
    override fun getLatestEntry(accountId: String): Flow<Entry?> = flow {
        emit(entryDao.getLatestEntry(accountId))
    }

    /**
     * Returns a Flow of entries for the last N days for the given account.
     * @param accountId The account ID.
     * @param days The number of days to look back.
     */
    override fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<Entry>> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis.toString()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = calendar.timeInMillis.toString()
        return entryDao.getEntriesByTimeRange(accountId, startDate, endDate)
    }

    /**
     * Returns a Flow of entries for the given account and device type.
     * @param accountId The account ID.
     * @param deviceType The device type.
     */
    override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>> {
        return entryDao.getEntriesByDeviceType(accountId, deviceType)
    }

    /**
     * Returns a Flow of unsynced entries.
     */
    override fun getUnsyncedEntries(): Flow<List<Entry>> {
        return entryDao.getUnsyncedEntries()
    }

    /**
     * Marks an entry as synced and returns a Flow emitting the number of rows updated.
     * @param id The entry ID.
     */
    override suspend fun markEntrySynced(id: Long): Flow<Int> = flow {
        emit(entryDao.markEntrySynced(id))
    }

    /**
     * Marks multiple entries as synced and returns a Flow emitting the number of rows updated.
     * @param ids The list of entry IDs.
     */
    override suspend fun markEntriesSynced(ids: List<Long>): Flow<Int> = flow {
        emit(entryDao.markEntriesSynced(ids))
    }

    /**
     * Returns a Flow of all entries for the given account.
     * @param accountId The account ID.
     */
    override fun getEntriesByAccount(accountId: String): Flow<List<Entry>> {
        return entryDao.getEntriesByAccountId(accountId)
    }

    /**
     * Deletes all entries for the given account. (Currently a placeholder.)
     * @param accountId The account ID.
     */
    override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> = flow {
        emit(0) // Placeholder
    }

    /**
     * Saves a list of metrics to the database.
     * @param metrics The list of metrics to save.
     */
    override suspend fun saveMetrics(metrics: List<BodyScaleEntryMetricEntity>) {
        entryDao.insertMetrics(metrics)
    }

    /**
     * Returns a Flow emitting the metrics for a given entry ID.
     * @param entryId The entry ID.
     */
    override fun getMetricsByEntryId(entryId: Long): Flow<BodyScaleEntryMetricEntity?> {
        return entryDao.getMetricsByEntryId(entryId)
    }

    /**
     * Saves a list of scale entries to the database.
     * @param entries The list of scale entries to save.
     */
    override suspend fun saveScaleEntries(entries: List<BodyScaleEntryEntity>) {
        entryDao.insertScaleEntries(entries)
    }

    /**
     * Returns the scale entry for the given ID, or null if not found.
     * @param entryId The entry ID.
     */
    override suspend fun getScaleEntryById(entryId: Long): BodyScaleEntryEntity? {
        return entryDao.getScaleEntryById(entryId)
    }

    /**
     * Sends an operation to the API and marks it as synced if successful. Adds to opstack if failed.
     * @param operation The operation entry to send.
     * @throws Exception if the API call fails.
     */
    override suspend fun sendOperationToAPI(operation: EntryEntity) {
        try {
            entryApi.sendOperation(operation)
            entryDao.update(operation.copy(isSynced = true))
        } catch (e: Exception) {
            addToOpstack(operation)
            throw e
        }
    }

    /**
     * Gets operations from the API since the last update, or all if lastUpdated is null.
     * @param lastUpdated The last updated timestamp, or null.
     * @return List of operations.
     */
    override suspend fun getOperationsFromAPI(lastUpdated: Long?): List<EntryEntity> {
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
     * Returns the list of unsynced operations (opstack) for the given account.
     * @param accountId The account ID.
     */
    override suspend fun getOpstack(accountId: String): List<EntryEntity> {
        return entryDao.getOpstack(accountId)
    }

    /**
     * Adds an operation to the opstack (unsynced operations).
     * @param operation The operation entry to add.
     */
    override suspend fun addToOpstack(operation: EntryEntity) {
        entryDao.insert(operation.copy(isSynced = false))
    }

    /**
     * Removes an operation from the opstack.
     * @param operation The operation entry to remove.
     */
    override suspend fun removeFromOpstack(operation: EntryEntity) {
        entryDao.delete(operation)
    }

    /**
     * Increments the attempt count for an operation in the opstack.
     * @param operation The operation entry to update.
     */
    override suspend fun incrementOpstackAttempts(operation: EntryEntity) {
        entryDao.incrementAttempts(operation.id)
    }
}
