package com.dmdbrands.gurus.weight.data.storage.datastore

import androidx.datastore.core.DataStore
import com.google.protobuf.MessageLite
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
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
    private val tag = "BaseProtoDataStore"

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
     * Gets the default instance of the Proto message.
     * Must be implemented by each DataStore to provide its specific default instance.
     */
    protected abstract fun getDefaultInstance(): T

    /**
     * Clears all fields in the Proto message by resetting it to its default instance.
     * Can be overridden to provide custom clear logic if needed.
     */
    open suspend fun clearData() {
        try {
            AppLog.i(tag, "Clearing DataStore: ${this::class.simpleName}")
            updateData { getDefaultInstance() }
            AppLog.i(tag, "Successfully cleared DataStore: ${this::class.simpleName}")
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to clear DataStore: ${this::class.simpleName}", e.toString())
            throw e
        }
    }
}

