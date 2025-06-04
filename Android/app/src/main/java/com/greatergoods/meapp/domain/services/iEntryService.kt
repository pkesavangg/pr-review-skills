// domain/service/IEntryService.kt
package com.greatergoods.meapp.domain.service

import com.greatergoods.meapp.data.storage.db.entity.EntryEntity
import com.greatergoods.meapp.domain.model.common.Progress
import kotlinx.coroutines.flow.StateFlow

interface IEntryService {
    val isUpdating: StateFlow<Boolean>
    val latestEntry: StateFlow<EntryEntity?>
    val last7Days: StateFlow<List<EntryEntity>?>
    val last30Days: StateFlow<List<EntryEntity>?>
    val progress: StateFlow<Progress?>
    val lastUpdated: StateFlow<Long?>

    suspend fun updateAllData(accountId: String)
    suspend fun saveNewEntry(entry: EntryEntity)
    suspend fun saveNewEntries(entries: List<EntryEntity>)
    suspend fun deleteEntry(entry: EntryEntity)
    suspend fun syncEntries()
}
