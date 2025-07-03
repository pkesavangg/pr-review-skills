package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.storage.datastore.DashboardKeysDatastore
import com.greatergoods.meapp.domain.repository.IDashboardRepository
import com.greatergoods.meapp.proto.DashboardKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDashboardRepository for dashboard visible metrics management.
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val dashboardKeysDatastore: DashboardKeysDatastore
) : IDashboardRepository {
    /**
     * Gets a Flow of visible metric keys for the given account.
     */
    override fun getVisibleKeys(accountId: String): Flow<List<DashboardKey>> =
        dashboardKeysDatastore.accountVisibleKeysFlow.map { it[accountId]?.visibleKeysList ?: emptyList() }

    /**
     * Updates the visible keys for the given account.
     */
    override suspend fun updateVisibleKeys(accountId: String, keys: List<DashboardKey>) =
        dashboardKeysDatastore.updateVisibleKeys(accountId, keys)

    /**
     * Checks if the given accountId has a visible keys entry.
     */
    override suspend fun hasVisibleKeys(accountId: String): Boolean =
        dashboardKeysDatastore.hasVisibleKeys(accountId)

    /**
     * Resets the visible keys for the given account to the default list.
     */
    override suspend fun resetVisibleKeys(accountId: String) =
        dashboardKeysDatastore.resetVisibleKeys(accountId)
}
