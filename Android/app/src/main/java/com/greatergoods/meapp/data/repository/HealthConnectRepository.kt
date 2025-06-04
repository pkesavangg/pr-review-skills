package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IHealthConnectRepository for account data.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    private val dataStore: HealthConnectDataStore
) : IHealthConnectRepository {
    /** Emits a Flow of the current map of account data. */
    override val accountDataFlow: Flow<Map<String, HealthConnectData>> = dataStore.healthConnectDataFlow

    /** Gets the current map of account data. */
    override suspend fun getAccountDataMap(): Map<String, HealthConnectData> = dataStore.healthConnectData()

    /** Sets or updates an account data entry for the given accountId. */
    override suspend fun addAccount(accountId: String, data: HealthConnectData) {
        dataStore.setHealthConnectData(accountId, data)
    }

    /** Removes an account data entry for the given accountId. */
    override suspend fun removeAccount(accountId: String) {
        dataStore.removeHealthConnectData(accountId)
    }

    /** Clears all account data entries. */
    override suspend fun clearData() {
        dataStore.clearData()
    }

    /** Gets an account data entry by its accountId. */
    override suspend fun getAccountByID(accountId: String): HealthConnectData? =
        dataStore.getHealthConnectData(accountId)

    /** Checks if an account data entry exists for the given accountId. */
    override suspend fun hasAccountData(accountId: String): Boolean =
        dataStore.hasHealthConnectData(accountId)
}
