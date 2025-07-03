package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.proto.DashboardKey
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for dashboard visible metrics management.
 */
interface IDashboardRepository {
    /**
     * Gets a Flow of visible metric keys for the given account.
     * @param accountId The account ID.
     * @return Flow emitting the list of visible metric keys.
     */
    fun getVisibleKeys(accountId: String): Flow<List<DashboardKey>>

    /**
     * Updates the visible keys for the given account.
     * @param accountId The account ID.
     * @param keys The list of DashboardKey to set.
     */
    suspend fun updateVisibleKeys(accountId: String, keys: List<DashboardKey> = listOf())

    /**
     * Checks if the given accountId has a visible keys entry.
     * @param accountId The account ID to check.
     * @return True if the accountId is present, false otherwise.
     */
    suspend fun hasVisibleKeys(accountId: String): Boolean

    /**
     * Resets the visible keys for the given account to the default list.
     * @param accountId The account ID.
     */
    suspend fun resetVisibleKeys(accountId: String)
}
