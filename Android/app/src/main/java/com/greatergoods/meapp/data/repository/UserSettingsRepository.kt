package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.IUserSettingsAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.StreaksSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightlessSettingsEntity
import com.greatergoods.meapp.domain.model.api.metrics.StreakRequest
import com.greatergoods.meapp.domain.model.api.metrics.WeightlessRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for user settings operations.
 * Handles streak and weightless mode settings with API and database operations.
 */
@Singleton
class UserSettingsRepository @Inject constructor(
    private val userSettingsAPI: IUserSettingsAPI,
    private val accountDao: AccountDao
) : IUserSettingsRepository {

    private val TAG = "UserSettingsRepository"
    override suspend fun updateWeightlessInDB(weightlessRequest: WeightlessSettingsEntity) {
        // Update local database
        val activeAccount = accountDao.getActiveAccount().first()
        activeAccount?.let { account ->
            val weightlessSettingsEntity = WeightlessSettingsEntity(
                accountId = account.account.id,
                isWeightlessOn = weightlessRequest.isWeightlessOn ?: false, // Default to false if null
                weightlessTimestamp = weightlessRequest.weightlessTimestamp ?: "0", // Default to "0" if null
                weightlessWeight = weightlessRequest.weightlessWeight ?: 0.0f, // Default to 0.0 if null
                isSynced = true,
            )
            accountDao.updateWeightlessSettings(weightlessSettingsEntity)
        }
    }

    /**
     * Updates the streak setting for the active account.
     * @param streakRequest The streak setting to update
     * @return Updated account with new streak settings
     */
    override suspend fun updateStreakSetting(streakRequest: StreakRequest): Account? {
        return try {
            AppLog.d(TAG, "Updating streak setting: $streakRequest")

            val response: AccountResponse = userSettingsAPI.updateStreak(streakRequest)
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val streaksSettingsEntity = StreaksSettingsEntity(
                    accountId = account.account.id,
                    isStreakOn = streakRequest.isStreakOn,
                    streakTimestamp = streakRequest.streakTimestamp ?: System.currentTimeMillis().toString(),
                    isSynced = true
                )
                accountDao.updateStreaksSettings(streaksSettingsEntity)
                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating streak setting", e.toString())

            // Handle offline mode - update local database only
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val streaksSettingsEntity = StreaksSettingsEntity(
                    accountId = account.account.id,
                    isStreakOn = streakRequest.isStreakOn,
                    streakTimestamp = streakRequest.streakTimestamp ?: System.currentTimeMillis().toString(),
                    isSynced = false
                )
                accountDao.updateStreaksSettings(streaksSettingsEntity)

                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        }
    }

    /**
     * Updates the weightless setting for the active account.
     * @param weightlessRequest The weightless setting to update
     * @return Updated account with new weightless settings
     */
    override suspend fun updateWeightlessSetting(weightlessRequest: WeightlessRequest): Account? {
        return try {
            AppLog.d(TAG, "Updating weightless setting: $weightlessRequest")
            val response: AccountResponse = userSettingsAPI.updateWeightless(weightlessRequest)
            // Update local database
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val weightlessSettingsEntity = WeightlessSettingsEntity(
                    accountId = account.account.id,
                    isWeightlessOn = weightlessRequest.isWeightlessOn,
                    weightlessTimestamp = weightlessRequest.weightlessTimestamp ?: System.currentTimeMillis().toString(),
                    weightlessWeight = weightlessRequest.weightlessWeight?.toFloat() ?: 0.0f,
                    isSynced = true
                )
                accountDao.updateWeightlessSettings(weightlessSettingsEntity)

                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating weightless setting", e.toString())

            // Handle offline mode - update local database only
            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val weightlessSettingsEntity = WeightlessSettingsEntity(
                    accountId = account.account.id,
                    isWeightlessOn = weightlessRequest.isWeightlessOn,
                    weightlessTimestamp = weightlessRequest.weightlessTimestamp ?: System.currentTimeMillis().toString(),
                    weightlessWeight = weightlessRequest.weightlessWeight?.toFloat() ?: 0.0f,
                    isSynced = false
                )
                accountDao.updateWeightlessSettings(weightlessSettingsEntity)

                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        }
    }

    /**
     * Updates streak setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The streak setting request
     * @return Updated account with new streak settings
     */
    override suspend fun updateStreakSettingOffline(request: StreakRequest): Account? {
        return try {
            AppLog.d(TAG, "Updating streak setting offline: $request")

            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val streaksSettingsEntity = StreaksSettingsEntity(
                    accountId = account.account.id,
                    isStreakOn = request.isStreakOn,
                    streakTimestamp = request.streakTimestamp ?: System.currentTimeMillis().toString(),
                    isSynced = false // Mark as unsynced for offline mode
                )
                accountDao.updateStreaksSettings(streaksSettingsEntity)

                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating streak setting offline", e.toString())
            null
        }
    }

    /**
     * Updates weightless setting offline (stores locally for later sync).
     * Used when network is unavailable.
     * @param request The weightless setting request
     * @return Updated account with new weightless settings
     */
    override suspend fun updateWeightlessSettingOffline(request: WeightlessRequest): Account? {
        return try {
            AppLog.d(TAG, "Updating weightless setting offline: $request")

            val activeAccount = accountDao.getActiveAccount().first()
            activeAccount?.let { account ->
                val weightlessSettingsEntity = WeightlessSettingsEntity(
                    accountId = account.account.id,
                    isWeightlessOn = request.isWeightlessOn ?: false, // Default to false if null
                    weightlessTimestamp = request.weightlessTimestamp ?: System.currentTimeMillis().toString(),
                    weightlessWeight = request.weightlessWeight?.toFloat() ?: 0.0f,
                    isSynced = false // Mark as unsynced for offline mode
                )
                accountDao.updateWeightlessSettings(weightlessSettingsEntity)

                // Return updated account
                val updatedAccount = accountDao.getActiveAccount().first()
                updatedAccount?.let { AccountEntityMapper.toDomainFromAccountWithRelations(it) }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error updating weightless setting offline", e.toString())
            null
        }
    }

    /**
     * Gets accounts with unsynced streak settings changes.
     * Used by offline handler service for syncing streak settings specifically.
     * @return List of accounts with pending streak settings changes
     */
    override suspend fun getUnsyncedStreakAccountsFromDB(): List<Account> {
        return try {
            AppLog.d(TAG, "Getting unsynced streak accounts")

            val unsyncedAccounts = accountDao.getUnsyncedStreakAccounts().first()
            unsyncedAccounts.map { accountWithRelations ->
                AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error getting unsynced streak accounts", e.toString())
            emptyList()
        }
    }

    /**
     * Gets accounts with unsynced weightless settings changes.
     * Used by offline handler service for syncing weightless settings specifically.
     * @return List of accounts with pending weightless settings changes
     */
    override suspend fun getUnsyncedWeightlessAccountsFromDB(): List<Account> {
        return try {
            AppLog.d(TAG, "Getting unsynced weightless accounts")

            val unsyncedAccounts = accountDao.getUnsyncedWeightlessAccounts().first()
            unsyncedAccounts.map { accountWithRelations ->
                AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error getting unsynced weightless accounts", e.toString())
            emptyList()
        }
    }
}
