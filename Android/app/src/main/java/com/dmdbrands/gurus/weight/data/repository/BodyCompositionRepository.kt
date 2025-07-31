package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IBodyCompAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the IBodyCompositionRepository interface.
 * Handles body composition operations using Room database and API calls.
 * Follows the same pattern as AccountRepository for consistency.
 */
@Singleton
class BodyCompositionRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val bodyCompAPI: IBodyCompAPI,
) : IBodyCompositionRepository {

    companion object {
        private const val TAG = "BodyCompositionRepository"
    }

    // API Operations
    /**
     * Updates body composition via API and returns AccountResponse.
     */
    override suspend fun updateBodyCompInAPI(bodyCompData: BodyCompUpdateRequest): AccountResponse {
        return bodyCompAPI.updateBodyComp(bodyCompData)
    }

    /**
     * Updates body composition data in the database for a specific account.
     * Updates all body composition fields (height, activityLevel, weightUnit) at once.
     * Marks the account as unsynced for offline handling.
     *
     * @param accountId The ID of the account to update
     * @return The updated account with all relations
     */
    override suspend fun updateBodyCompInDB(
        accountId: String,
        bodyComposition: WeightCompSettingsEntity
    ) {
        // Create updated settings with all fields
        val updatedWeightCompSettings = WeightCompSettingsEntity(
            accountId = accountId,
            height = bodyComposition.height,
            activityLevel = bodyComposition.activityLevel,
            weightUnit = bodyComposition.weightUnit,
            isSynced = bodyComposition.isSynced
        )
        accountDao.updateWeightCompSettings(updatedWeightCompSettings)
    }

    /**
     * Gets the active account if it has unsynced body composition data.
     * Used by offline handler service to sync pending body composition changes for active account.
     * @return The active account with unsynced body comp data, or null if active account is synced
     */
    override suspend fun getUnsyncedActiveBodyCompAccountFromDB(): Account? {
        // Get active account if either the main account is unsynced OR the weight comp settings are unsynced
        val unsyncedActiveAccount = accountDao.getUnsyncedActiveBodyCompAccount().first()
        return unsyncedActiveAccount?.let {
            AccountEntityMapper.toDomainFromAccountWithRelations(it)
        }
    }

    /**
     * Gets the active account from the database.
     * Used by body composition service to get current account info.
     */
    override suspend fun getActiveAccountFromDB(): Account? {
        val activeAccountEntity = accountDao.getActiveAccount().first()
        return activeAccountEntity?.let {
            AccountEntityMapper.toDomainFromAccountWithRelations(it)
        }
    }
}
