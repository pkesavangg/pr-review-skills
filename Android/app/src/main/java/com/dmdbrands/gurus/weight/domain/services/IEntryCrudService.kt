package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry

interface IEntryCrudService {
    suspend fun addEntry(entry: Entry, accountId: String?)
    suspend fun addEntry(entries: List<Entry>, accountId: String?)
    suspend fun deleteEntry(entry: Entry, accountId: String?)
}