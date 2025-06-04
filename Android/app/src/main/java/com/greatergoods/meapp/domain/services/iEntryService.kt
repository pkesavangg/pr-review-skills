// domain/service/IEntryService.kt
package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.data.storage.db.entity.Entry
import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
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

    suspend fun updateAllData(accountId: String)
    suspend fun saveNewEntry(entry: EntryEntity)
    suspend fun saveNewEntries(entries: List<EntryEntity>)
    suspend fun deleteEntry(entry: EntryEntity)
    suspend fun syncEntries()
    fun getEntriesByDeviceType(accountId: String, deviceType: String): Flow<List<Entry>>
}
