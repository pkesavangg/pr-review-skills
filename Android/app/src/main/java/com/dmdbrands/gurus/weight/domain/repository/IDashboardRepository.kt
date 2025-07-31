package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.proto.MetricKey
import com.dmdbrands.gurus.weight.proto.MilestoneKey
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for dashboard visible metrics and milestones management.
 */
interface IDashboardRepository {
    /**
     * Gets a Flow of visible metric keys for the given account.
     * @param accountId The account ID.
     * @return Flow emitting the list of visible metric keys.
     */
    fun getVisibleMetricKeys(accountId: String): Flow<List<MetricKey>>

    /**
     * Gets a Flow of visible milestone keys for the given account.
     * @param accountId The account ID.
     * @return Flow emitting the list of visible milestone keys.
     */
    fun getVisibleMilestoneKeys(accountId: String): Flow<List<MilestoneKey>>

    /**
     * Updates the visible metric keys for the given account.
     * @param accountId The account ID.
     * @param keys The list of MetricKey to set.
     */
    suspend fun updateVisibleMetricKeys(accountId: String, keys: List<MetricKey> = listOf())

    /**
     * Updates the visible milestone keys for the given account.
     * @param accountId The account ID.
     * @param keys The list of MilestoneKey to set.
     */
    suspend fun updateVisibleMilestoneKeys(accountId: String, keys: List<MilestoneKey> = listOf())

    /**
     * Checks if the given accountId has a visible keys entry.
     * @param accountId The account ID to check.
     * @return True if the accountId is present, false otherwise.
     */
    suspend fun hasVisibleKeys(accountId: String): Boolean

    /**
     * Resets the visible metric keys for the given account to the default list.
     * @param accountId The account ID.
     */
    suspend fun resetVisibleMetricKeys(accountId: String)

    /**
     * Resets the visible milestone keys for the given account to the default list.
     * @param accountId The account ID.
     */
    suspend fun resetVisibleMilestoneKeys(accountId: String)

    /**
     * Resets both visible metric and milestone keys for the given account to the default lists.
     * @param accountId The account ID.
     */
    suspend fun resetVisibleKeys(accountId: String)
}
