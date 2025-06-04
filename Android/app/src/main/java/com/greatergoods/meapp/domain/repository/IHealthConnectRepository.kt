package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for account data operations, abstracting HealthConnectDataStore.
 */
interface IHealthConnectRepository {
    /** Emits a Flow of the current map of account data. */
    val accountDataFlow: Flow<Map<String, HealthConnectData>>

    /** Gets the current map of account data. */
    suspend fun getAccountDataMap(): Map<String, HealthConnectData>

    /** Sets or updates an account data entry for the given accountId. */
    suspend fun addAccount(accountId: String, data: HealthConnectData)

    /** Removes an account data entry for the given accountId. */
    suspend fun removeAccount(accountId: String)

    /** Clears all account data entries. */
    suspend fun clearData()

    /** Gets an account data entry by its accountId. */
    suspend fun getAccountByID(accountId: String): HealthConnectData?

    /** Checks if an account data entry exists for the given accountId. */
    suspend fun hasAccountData(accountId: String): Boolean
}
