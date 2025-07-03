package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.proto.DashboardKey
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for dashboard visible metrics management.
 */
interface IDashboardService {
    /**
     * Sets the current account ID to be used by default in other methods.
     * @param accountId The account ID to set as default.
     */
    fun setAccountId(accountId: String)

    /**
     * Gets a Flow of visible metric keys for the given account.
     * If accountId is null, uses the stored accountId.
     * @param accountId The account ID (optional).
     * @return Flow emitting the list of visible metric keys.
     */
    fun getVisibleKeys(accountId: String? = null): Flow<List<DashboardKey>>

    /**
     * Updates the visible keys for the given account.
     * If accountId is null, uses the stored accountId.
     * @param accountId The account ID (optional).
     * @param keys The list of DashboardKey to set.
     */
    suspend fun updateVisibleKeys(accountId: String? = null, keys: List<DashboardKey> = listOf())

    /**
     * Checks if the given accountId has a visible keys entry.
     * If accountId is null, uses the stored accountId.
     * @param accountId The account ID to check (optional).
     * @return True if the accountId is present, false otherwise.
     */
    suspend fun hasVisibleKeys(accountId: String? = null): Boolean

    /**
     * Resets the visible keys for the given account to the default list.
     * If accountId is null, uses the stored accountId.
     * @param accountId The account ID (optional).
     */
    suspend fun resetVisibleKeys(accountId: String? = null)
}
