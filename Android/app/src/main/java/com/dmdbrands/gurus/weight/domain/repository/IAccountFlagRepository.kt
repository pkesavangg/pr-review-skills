package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.AccountFlag

/**
 * Repository interface for account flag operations.
 * Handles all account flag related data operations.
 */
interface IAccountFlagRepository {
    /**
     * Gets all account flags for the current user.
     * @return List of account flags
     */
    suspend fun getAccountFlags(): List<AccountFlag>

    /**
     * Deletes a specific account flag by ID.
     * @param flagId The ID of the flag to delete
     * @return Boolean indicating success
     */
    suspend fun deleteAccountFlag(flagId: String): Boolean
}
