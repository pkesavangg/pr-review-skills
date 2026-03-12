package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IEntryCrudService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight

class EntryCrudService(
    private val syncService: IEntrySyncService,
) : IEntryCrudService {

    private val TAG = "EntryCrudService"

    /**
     * Saves a new entry both locally and remotely.
     */
    override suspend fun addEntry(entry: Entry, accountId: String?) {
        val currentAccountId = accountId ?: return
        var updatedEntry = entry.updateEntry(
            entry.entry.copy(
                isSynced = false,
                operationType = OperationType.CREATE.name,
                accountId = currentAccountId,
            ),
        )
        if (updatedEntry is ScaleEntry) {
            updatedEntry = updatedEntry.copy(
                scale = updatedEntry.scale.copy(
                    scaleEntry = updatedEntry.scale.scaleEntry.copy(
                        weight = convertWeight(
                            updatedEntry.scale.scaleEntry.weight,
                            updatedEntry.entry.unit,
                            WeightUnit.LB,
                        ),
                    ),
                ),
            )
        }
        syncService.syncOperations(currentAccountId, listOf(updatedEntry))
    }

    /**
     * Saves multiple new entries both locally and remotely.
     */
    override suspend fun addEntry(entries: List<Entry>, accountId: String?) {
        val currentAccountId = accountId ?: return
        try {
            val updatedEntries = entries.map { entry ->
                val baseEntry = entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.CREATE.name,
                    accountId = currentAccountId,
                )
                val updatedEntry = entry.updateEntry(baseEntry)
                if (updatedEntry is ScaleEntry) {
                    updatedEntry.copy(
                        scale = updatedEntry.scale.copy(
                            scaleEntry = updatedEntry.scale.scaleEntry.copy(
                                weight = convertWeight(
                                    updatedEntry.scale.scaleEntry.weight,
                                    updatedEntry.entry.unit,
                                    WeightUnit.LB,
                                ),
                            ),
                        ),
                    )
                } else {
                    updatedEntry
                }
            }
            syncService.syncOperations(currentAccountId, updatedEntries)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error saving new entries", e)
        }
    }

    /**
     * Deletes an entry both locally and remotely.
     */
    override suspend fun deleteEntry(entry: Entry, accountId: String?) {
        val currentAccountId = accountId ?: return
        try {
            val deleteEntry = entry.updateEntry(
                entry.entry.copy(
                    isSynced = false,
                    operationType = OperationType.DELETE.name,
                ),
            )
            syncService.syncOperations(currentAccountId, emptyList(), listOf(deleteEntry))
        } catch (e: Exception) {
            AppLog.e(TAG, "Error deleting entry", e)
        }
    }
}