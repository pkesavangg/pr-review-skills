package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.db.dao.EntryDao
import com.greatergoods.meapp.domain.interfaces.IEntryRepository
import com.greatergoods.meapp.domain.model.EntryDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of IEntryRepository for testing and development purposes.
 * This implementation provides basic functionality without actual database operations.
 */
@Singleton
class EntryRepository @Inject constructor() : IEntryRepository {

    // In-memory storage for testing
    private val entries = mutableListOf<EntryDTO>()
    private var lastId = 0L

    // Basic CRUD Operations
    override fun getAllEntries(): Flow<List<EntryDTO>> = flow {
        emit(entries.toList())
    }

    override fun getEntryById(id: String): Flow<EntryDTO?> = flow {
        emit(entries.find { it.entry.id.toString() == id })
    }

    override suspend fun saveEntry(entry: EntryDTO): Flow<EntryDTO> = flow {
        val newEntry = entry.copy(entry = entry.entry.copy(id = ++lastId))
        entries.add(newEntry)
        emit(newEntry)
    }

    override suspend fun updateEntry(entry: EntryDTO): Flow<EntryDTO> = flow {
        val index = entries.indexOfFirst { it.entry.id == entry.entry.id }
        if (index != -1) {
            entries[index] = entry
            emit(entry)
        } else {
            emit(entry)
        }
    }

    override suspend fun deleteEntry(id: String): Flow<Boolean> = flow {
        val index = entries.indexOfFirst { it.entry.id.toString() == id }
        if (index != -1) {
            entries.removeAt(index)
            emit(true)
        } else {
            emit(false)
        }
    }

    // Time-based Queries
    override fun getEntriesByDateRange(startDate: Long, endDate: Long): Flow<List<EntryDTO>> = flow {
        emit(entries.filter { 
            val timestamp = it.entry.entryTimestamp.toLong()
            timestamp in startDate..endDate 
        })
    }

    override fun getLatestEntry(): Flow<EntryDTO?> = flow {
        emit(entries.maxByOrNull { it.entry.entryTimestamp.toLong() })
    }

    override fun getLastNDaysEntries(days: Int): Flow<List<EntryDTO>> = flow {
        val endDate = System.currentTimeMillis()
        val startDate = endDate - (days * 24 * 60 * 60 * 1000)
        emit(entries.filter { 
            val timestamp = it.entry.entryTimestamp.toLong()
            timestamp in startDate..endDate 
        })
    }

    // Device-specific Operations
    override fun getEntriesByDeviceType(deviceType: String): Flow<List<EntryDTO>> = flow {
        emit(entries.filter { it.entry.deviceType == deviceType })
    }

    override fun getEntriesBySource(source: String): Flow<List<EntryDTO>> = flow {
        emit(entries.filter { it.entry.source == source })
    }

    // Sync Operations
    override fun getUnsyncedEntries(): Flow<List<EntryDTO>> = flow {
        emit(entries.filter { !it.entry.isSynced })
    }

    override suspend fun markEntrySynced(id: String): Flow<Boolean> = flow {
        val index = entries.indexOfFirst { it.entry.id.toString() == id }
        if (index != -1) {
            val entry = entries[index]
            entries[index] = entry.copy(entry = entry.entry.copy(isSynced = true))
            emit(true)
        } else {
            emit(false)
        }
    }

    override suspend fun markEntriesSynced(ids: List<String>): Flow<Boolean> = flow {
        var success = false
        ids.forEach { id ->
            val index = entries.indexOfFirst { it.entry.id.toString() == id }
            if (index != -1) {
                val entry = entries[index]
                entries[index] = entry.copy(entry = entry.entry.copy(isSynced = true))
                success = true
            }
        }
        emit(success)
    }

    // Account-specific Operations
    override fun getEntriesByAccount(accountId: String): Flow<List<EntryDTO>> = flow {
        emit(entries.filter { it.entry.accountId == accountId })
    }

    override suspend fun deleteAllEntriesForAccount(accountId: String): Flow<Boolean> = flow {
        val initialSize = entries.size
        entries.removeAll { it.entry.accountId == accountId }
        emit(entries.size < initialSize)
    }

    // Helper Functions
    private fun isConsecutiveDay(date1: Long, date2: Long): Boolean {
        val dayInMillis = 24 * 60 * 60 * 1000
        return Math.abs(date1 - date2) <= dayInMillis
    }
} 