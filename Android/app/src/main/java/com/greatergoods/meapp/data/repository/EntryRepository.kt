// data/repository/EntryRepository.kt
package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.EntryApi
import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryEntity
import com.greatergoods.meapp.data.storage.db.entity.BodyScaleEntryMetricEntity
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
    override fun getAllEntries(): Flow<List<EntryEntity>> = entryDao.getEntriesByAccountId("dummy_account_id")

    override fun getEntryById(id: Long): Flow<EntryEntity?> = flow {
        emit(entryDao.getEntryById(id))
    }

    override suspend fun saveEntry(entry: EntryEntity): Flow<Long> = flow {
        emit(entryDao.insert(entry))
    }

    override suspend fun saveEntries(entries: List<EntryEntity>) {
        entries.forEach { entryDao.insert(it) }
    }

    override suspend fun updateEntry(entry: EntryEntity): Flow<Int> = flow {
        emit(entryDao.update(entry))
    }

    override suspend fun deleteEntry(entry: EntryEntity): Flow<Int> = flow {
        emit(entryDao.delete(entry))
    }

    override fun getEntriesByDateRange(accountId: String, startDate: String, endDate: String): Flow<List<EntryEntity>> {
        return entryDao.getEntriesByTimeRange(accountId, startDate, endDate)
    }

    override fun getLatestEntry(accountId: String): Flow<EntryEntity?> = flow {
        val entries = entryDao.getEntriesByAccountId(accountId)
        entries.collect { list ->
            emit(list.maxByOrNull { it.entryTimestamp })
        }
    }

    override fun getLastNDaysEntries(accountId: String, days: Int): Flow<List<EntryEntity>> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis.toString()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = calendar.timeInMillis.toString()
        return entryDao.getEntriesByTimeRange(accountId, startDate, endDate)
    }

    override fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<EntryEntity>> {
        return entryDao.getEntriesByDeviceType(accountId, deviceType)
    }

    override fun getUnsyncedEntries(): Flow<List<EntryEntity>> {
        return entryDao.getUnsyncedEntries()
    }

    override suspend fun markEntrySynced(id: Long): Flow<Int> = flow {
        emit(entryDao.markEntrySynced(id))
    }

    override suspend fun markEntriesSynced(ids: List<Long>): Flow<Int> = flow {
        emit(entryDao.markEntriesSynced(ids))
    }

    override fun getEntriesByAccount(accountId: String): Flow<List<EntryEntity>> {
        return entryDao.getEntriesByAccountId(accountId)
    }

    override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Int> = flow {
        emit(0) // Placeholder
    }

    override suspend fun saveMetrics(metrics: List<BodyScaleEntryMetricEntity>) {
        entryDao.insertMetrics(metrics)
    }

    override fun getMetricsByEntryId(entryId: Long): Flow<BodyScaleEntryMetricEntity?> {
        return entryDao.getMetricsByEntryId(entryId)
    }

    override suspend fun saveScaleEntries(entries: List<BodyScaleEntryEntity>) {
        entryDao.insertScaleEntries(entries)
    }

    override suspend fun getScaleEntryById(entryId: Long): BodyScaleEntryEntity? {
        return entryDao.getScaleEntryById(entryId)
    }

    // New API related functions
    override suspend fun sendOperationToAPI(operation: EntryEntity) {
        try {
            entryApi.sendOperation(operation)
            // Mark as synced if successful
            entryDao.update(operation.copy(isSynced = true))
        } catch (e: Exception) {
            // If API call fails, add to opstack
            addToOpstack(operation)
            throw e
        }
    }

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

    // New Opstack related functions
    override suspend fun getOpstack(accountId: String): List<EntryEntity> {
        return entryDao.getOpstack(accountId)
    }

    override suspend fun addToOpstack(operation: EntryEntity) {
        entryDao.insert(operation.copy(isSynced = false))
    }

    override suspend fun removeFromOpstack(operation: EntryEntity) {
        entryDao.delete(operation)
    }

    override suspend fun incrementOpstackAttempts(operation: EntryEntity) {
        entryDao.incrementAttempts(operation.id)
    }
}
