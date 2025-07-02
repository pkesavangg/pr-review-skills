package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Repository interface for managing body composition operations.
 * Provides methods for updating activity level, weight unit, and height.
 */
interface IBodyCompositionRepository {

    // API Operations
    /**
     * Updates body composition via API and returns AccountResponse.
     */
    suspend fun updateBodyCompInAPI(bodyCompData: BodyCompUpdateRequest): AccountResponse

    // DB Operations
    /**
     * Updates body composition data in the database for a specific account.
     * Updates all body composition fields (height, activityLevel, weightUnit) at once.
     * Marks the account as unsynced for offline handling.
     *
     * @param accountId The ID of the account to update
     * @return The updated account with all relations
     */
    suspend fun updateBodyCompInDB(
        accountId: String,
        bodyComposition: WeightCompSettingsEntity
    ): Account

    /**
     * Gets all accounts with unsynced body composition data from the database.
     * Used by offline handler service to sync pending body composition changes.
     */
    suspend fun getUnsyncedBodyCompAccountsFromDB(): List<Account>

    /**
     * Gets the active account from the database.
     * Used by body composition service to get current account info.
     */
    suspend fun getActiveAccountFromDB(): Account?
}
