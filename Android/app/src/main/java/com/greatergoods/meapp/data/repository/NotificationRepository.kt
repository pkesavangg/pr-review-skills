package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.api.INotificationAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.AccountEntityMapper
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.INotificationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of notification repository for managing notification data operations.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val notificationAPI: INotificationAPI,
    private val accountDao: AccountDao
) : INotificationRepository {

    companion object {
        private const val TAG = "NotificationRepository"
    }

    /**
     * Updates notification settings via API.
     *
     * @param request The notification settings request
     * @return AccountResponse from the API
     */
    override suspend fun updateNotificationSettingsInAPI(request: NotificationSettingsRequest): AccountResponse {
        AppLog.d(TAG, "Updating notification settings via API: $request")
        return notificationAPI.updateNotificationSettings(request)
    }

    /**
     * Updates notification settings in the database for a specific account.
     * Marks the account as unsynced for offline handling.
     *
     * @param accountId The ID of the account to update
     * @param notificationSettings The notification settings entity
     * @return The updated account with all relations
     */
    override suspend fun updateNotificationSettingsInDB(
        accountId: String,
        notificationSettings: NotificationSettingsEntity
    ): Account {
        // Create updated settings with all fields
        val updatedNotificationSettings = NotificationSettingsEntity(
            accountId = accountId,
            entryNotificationsEnabled = notificationSettings.entryNotificationsEnabled,
            showWeightInNotifications = notificationSettings.showWeightInNotifications,
            isSynced = notificationSettings.isSynced
        )
        accountDao.updateNotificationSettings(updatedNotificationSettings)
        AppLog.d(TAG, "Updated notification settings in DB for account: $accountId")

        // Return the updated account with all relations
        val accountWithRelations = accountDao.getAccount(accountId).first()
            ?: throw IllegalStateException("Account not found after notification settings update")
        return AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
    }

    /**
     * Gets the active account from the database.
     *
     * @return The active account or null if none found
     */
    override suspend fun getActiveAccountFromDB(): Account? {
        AppLog.d(TAG, "Getting active account from DB")
        val activeAccountEntity = accountDao.getActiveAccount().first()
        return activeAccountEntity?.let { accountWithRelations ->
            AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
        }
    }

    /**
     * Gets all accounts with unsynced notification settings from the database.
     * Uses efficient SQL query to find accounts with unsynced notification data.
     *
     * @return List of accounts with unsynced notification data
     */
    override suspend fun getUnsyncedNotificationAccountsFromDB(): List<Account> {
        // Get accounts where either the main account is unsynced OR the notification settings are unsynced
        val unsyncedNotificationAccounts = accountDao.getUnsyncedNotificationAccounts().first()
        return unsyncedNotificationAccounts.map { accountWithRelations ->
            AccountEntityMapper.toDomainFromAccountWithRelations(accountWithRelations)
        }
    }
}
