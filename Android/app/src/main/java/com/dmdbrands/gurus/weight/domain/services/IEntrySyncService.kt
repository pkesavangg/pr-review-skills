package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import kotlinx.coroutines.flow.StateFlow

interface IEntrySyncService {
    val isUpdating: StateFlow<Boolean>
    val lastUpdated: StateFlow<Long?>

    suspend fun syncOperations(
        accountId: String,
        newEntries: List<Entry> = emptyList(),
        deleteOps: List<Entry> = emptyList(),
    )

    fun reset()
}