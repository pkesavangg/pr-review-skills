package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IAccountFlagAPI
import com.dmdbrands.gurus.weight.domain.model.AccountFlag
import com.dmdbrands.gurus.weight.domain.repository.IAccountFlagRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for account flag operations.
 * Handles all account flag related data operations using the API.
 *
 * @property accountFlagAPI The API interface for account flag operations
 */
@Singleton
class AccountFlagRepository
    @Inject
    constructor(
        private val accountFlagAPI: IAccountFlagAPI,
    ) : IAccountFlagRepository {

    /**
     * Gets all account flags for the current user.
     * @return List of account flags
     */
    override suspend fun getAccountFlags(): List<AccountFlag> =
        try {
            val response = accountFlagAPI.getAccountFlags()
            response.map { apiFlag ->
                AccountFlag(
                    id = apiFlag.id,
                    type = apiFlag.type,
                    trigger = apiFlag.trigger,
                    data = apiFlag.data
                )
            }
        } catch (e: Exception) {
            AppLog.e("AccountFlagRepository", "Failed to get account flags", e.toString())
            emptyList()
        }

    /**
     * Deletes a specific account flag by ID.
     * @param flagId The ID of the flag to delete
     * @return Boolean indicating success
     */
    override suspend fun deleteAccountFlag(flagId: String): Boolean =
        try {
            accountFlagAPI.deleteAccountFlag(flagId)
        } catch (e: Exception) {
            AppLog.e("AccountFlagRepository", "Failed to delete account flag: $flagId", e.toString())
            false
        }
}
