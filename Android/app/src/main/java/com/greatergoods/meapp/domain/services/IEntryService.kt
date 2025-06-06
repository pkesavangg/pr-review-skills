// domain/service/IEntryService.kt
package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.domain.model.common.HistoryMonth
import com.greatergoods.meapp.domain.model.common.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IEntryService {
    val isUpdating: StateFlow<Boolean>
    val latestEntry: StateFlow<Entry?>
    val last7Days: StateFlow<List<Entry>?>
    val last30Days: StateFlow<List<Entry>?>
    val progress: StateFlow<Progress?>
    val lastUpdated: StateFlow<Long?>
    val monthsLastYear: StateFlow<List<HistoryMonth>?>
    val monthsAll: StateFlow<List<HistoryMonth>?>

    suspend fun updateAllData(accountId: String)
    suspend fun addEntry(entry: Entry)
    suspend fun addEntry(entries: List<Entry>)
    suspend fun deleteEntry(entry: Entry)
    suspend fun syncOperations(
        newEntries: List<Entry> = emptyList(),
        deleteOps: List<Entry> = emptyList()
    )

    fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>>
}
