package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [DataStore] implementation for unit testing.
 * Uses a [MutableStateFlow] to hold current state, enabling flow collection and atomic updates.
 * A [Mutex] ensures thread-safe updates when tests use multiple coroutines.
 */
class FakeDataStore<T>(initialData: T) : DataStore<T> {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(initialData)
    override val data: Flow<T> = _data.asStateFlow()

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        val newData = transform(_data.value)
        _data.value = newData
        newData
    }
}
