package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import kotlinx.coroutines.flow.Flow

/**
 * Interface for HealthConnectDataStore repository.
 */
interface IHealthConnectRepository {
    /**
     * Returns a [kotlinx.coroutines.flow.Flow] of the current map of HealthConnectData.
     */
    val dataMapFlow: Flow<Map<String, HealthConnectData>>

    /**
     * Gets the current map of HealthConnectData.
     */
    suspend fun getDataMap(): Map<String, HealthConnectData>

    /**
     * Sets or updates a HealthConnectData entry for the given key.
     */
    suspend fun setEntry(key: String, value: HealthConnectData)

    /**
     * Removes a HealthConnectData entry for the given key.
     */
    suspend fun removeEntry(key: String)

    /**
     * Clears all HealthConnectData entries.
     */
    suspend fun clearData()

    /**
     * Gets a HealthConnectData entry by its account id (key).
     * @param accountId The key for the HealthConnectData entry.
     * @return The HealthConnectData if present, or null.
     */
    suspend fun getByAccountId(accountId: String): HealthConnectData?

    /**
     * Checks if a HealthConnectData entry exists for the given key.
     */
    suspend fun containsKey(key: String): Boolean

    /**
     * Gets the count of all HealthConnectData entries.
     */
    suspend fun getEntryCount(): Int
}
