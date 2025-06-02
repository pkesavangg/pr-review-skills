package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.HealthConnectData
import com.greatergoods.meapp.data.storage.datastore.HealthConnectDataStore
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IHealthConnectDataStoreRepository.
 */

@Singleton
class HealthConnectRepository @Inject constructor(
    private val dataStore: HealthConnectDataStore
) : IHealthConnectRepository {
    override val dataMapFlow: Flow<Map<String, HealthConnectData>> = dataStore.dataMapFlow

    override suspend fun getDataMap(): Map<String, HealthConnectData> = dataStore.getDataMap()

    override suspend fun setEntry(key: String, value: HealthConnectData) {
        dataStore.setEntry(key, value)
    }

    override suspend fun removeEntry(key: String) {
        dataStore.removeEntry(key)
    }

    override suspend fun clearData() {
        dataStore.clearData()
    }

    override suspend fun getByAccountId(accountId: String): HealthConnectData? =
        dataStore.getByAccountId(accountId)

    override suspend fun containsKey(key: String): Boolean =
        dataStore.getDataMap().containsKey(key)

    override suspend fun getEntryCount(): Int = dataStore.getDataMap().size
}
