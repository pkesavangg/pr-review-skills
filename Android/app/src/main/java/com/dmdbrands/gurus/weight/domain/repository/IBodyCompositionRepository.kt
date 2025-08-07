package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

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
    )

    /**
     * Gets the active account if it has unsynced body composition data.
     * Used by offline handler service to sync pending body composition changes for active account.
     * @return The active account with unsynced body comp data, or null if active account is synced
     */
    suspend fun getUnsyncedActiveBodyCompAccountFromDB(): Account?

    /**
     * Gets the active account from the database.
     * Used by body composition service to get current account info.
     */
    suspend fun getActiveAccountFromDB(): Account?
}
