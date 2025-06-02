package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Base class for Proto DataStore operations.
 *
 * @param T The type of the Proto message.
 * @property dataStore The DataStore instance for the Proto type.
 */
abstract class BaseProtoDataStore<T : MessageLite>(
    protected val dataStore: DataStore<T>
) {
    /**
     * Returns a [Flow] of the Proto data.
     */
    val dataFlow: Flow<T> get() = dataStore.data

    /**
     * Returns the current snapshot of the Proto data.
     */
    suspend fun getData(): T = dataStore.data.first()

    /**
     * Updates the Proto data atomically.
     *
     * @param transform The transformation to apply.
     * @return The updated Proto data.
     */
    suspend fun updateData(transform: suspend (T) -> T): T {
        return dataStore.updateData(transform)
    }

    /**
     * Clears all fields in the Proto message.
     * Override this to provide custom clear logic.
     */
    abstract suspend fun clearData()
}

