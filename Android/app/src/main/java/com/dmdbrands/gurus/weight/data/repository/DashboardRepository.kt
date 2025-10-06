package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.DashboardKeysDatastore
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.repository.IDashboardRepository
import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IDashboardRepository for dashboard visible metrics and milestones management.
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val dashboardKeysDatastore: DashboardKeysDatastore
) : IDashboardRepository {
    /**
     * Gets a Flow of visible metric keys for the given account.
     */
    override fun getVisibleMetricKeys(accountId: String): Flow<List<MetricKey>> =
        dashboardKeysDatastore.accountVisibleKeysFlow.map { it[accountId]?.visibleMetricKeysList ?: emptyList() }

    /**
     * Gets a Flow of visible milestone keys for the given account.
     */
    override fun getVisibleMilestoneKeys(accountId: String): Flow<List<MilestoneKey>> =
        dashboardKeysDatastore.accountVisibleKeysFlow.map { it[accountId]?.visibleMilestoneKeysList ?: emptyList() }

    /**
     * Updates the visible metric keys for the given account.
     */
    override suspend fun updateVisibleMetricKeys(accountId: String, keys: List<MetricKey>, dashboardType: DashboardType) =
        dashboardKeysDatastore.updateVisibleMetricKeys(accountId, keys, dashboardType)

    /**
     * Updates the visible milestone keys for the given account.
     */
    override suspend fun updateVisibleMilestoneKeys(accountId: String, keys: List<MilestoneKey>) =
        dashboardKeysDatastore.updateVisibleMilestoneKeys(accountId, keys)

    /**
     * Checks if the given accountId has a visible keys entry.
     */
    override suspend fun hasVisibleKeys(accountId: String): Boolean =
        dashboardKeysDatastore.hasVisibleKeys(accountId)

    /**
     * Resets the visible metric keys for the given account to the default list.
     */
    override suspend fun resetVisibleMetricKeys(accountId: String, dashboardType: DashboardType) =
        dashboardKeysDatastore.resetVisibleMetricKeys(accountId, dashboardType)

    /**
     * Resets the visible milestone keys for the given account to the default list.
     */
    override suspend fun resetVisibleMilestoneKeys(accountId: String) =
        dashboardKeysDatastore.resetVisibleMilestoneKeys(accountId)

    /**
     * Resets both visible metric and milestone keys for the given account to the default lists.
     */
    override suspend fun resetVisibleKeys(accountId: String, dashboardType: DashboardType) =
        dashboardKeysDatastore.resetVisibleKeys(accountId, dashboardType)
}
