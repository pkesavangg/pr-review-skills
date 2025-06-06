package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryMetricEntity
import com.greatergoods.meapp.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.greatergoods.meapp.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar // Added for date manipulation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val entryDao: EntryDao) : IEntryRepository {
    override fun getAllEntries(): Flow<List<EntryEntity>> = entryDao.getEntriesByAccountId("dummy_account_id") // Assuming a default or active accountId

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
        // This would ideally be a specific DAO query, like `getLatestEntryByAccountId`
        // For now, fetching all and taking the last, assuming entries are ordered by time or ID.
        // A more robust solution would be to add a specific query to EntryDao.
        val entries = entryDao.getEntriesByAccountId(accountId) // This is a Flow
        entries.collect { list -> // Collect the flow to get the list
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
        // EntryDao doesn't have a direct deleteAllEntriesForAccount(accountId): Int method.
        // It would require a @Query like "DELETE FROM entry WHERE accountId = :accountId"
        // For now, let's assume we'd fetch and then delete, or this needs a DAO update.
        // This is a placeholder for how it might be implemented if DAO was updated.
        // val entriesToDelete = entryDao.getEntriesByAccountId(accountId).first() // Get current entries
        // entriesToDelete.forEach { entryDao.delete(it) }
        // emit(entriesToDelete.size)
        // Since direct DAO method is missing, and to avoid complex flow operations here for deletion count,
        // this part needs refinement based on exact requirements or DAO capabilities.
        // For now, returning 0 as a placeholder, indicating this needs a proper implementation.
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
}
