package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IBodyCompAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
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
    ): Account {
        // Create updated settings with all fields
        val updatedWeightCompSettings = WeightCompSettingsEntity(
            accountId = accountId,
            height = bodyComposition.height,
            activityLevel = bodyComposition.activityLevel,
            weightUnit = bodyComposition.weightUnit,
            isSynced = bodyComposition.isSynced
        )
        accountDao.updateWeightCompSettings(updatedWeightCompSettings)
        AppLog.d(TAG, "Updated body composition in DB for account: $accountId")

        // Return the updated account with all relations
        val updatedAccountWithRelations = accountDao.getAccount(accountId).first()
            ?: throw IllegalStateException("Failed to retrieve updated account")

        return AccountEntityMapper.toDomainFromAccountWithRelations(updatedAccountWithRelations)
    }

    /**
     * Gets all accounts with unsynced body composition data from the database.
     * Used by offline handler service to sync pending body composition changes.
     */
    override suspend fun getUnsyncedBodyCompAccountsFromDB(): List<Account> {
        // Get accounts where either the main account is unsynced OR the weight comp settings are unsynced
        val unsyncedBodyCompAccounts = accountDao.getUnsyncedBodyCompAccounts().first()
        return unsyncedBodyCompAccounts.map { accountWithRelations ->
            AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
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
