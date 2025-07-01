package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Repository interface for notification data operations.
 */
interface INotificationRepository {

    /**
     * Updates notification settings via API.
     *
     * @param request The notification settings request
     * @return AccountResponse from the API
     */
    suspend fun updateNotificationSettingsInAPI(request: NotificationSettingsRequest): AccountResponse

    /**
     * Updates notification settings in the database for a specific account.
     * Marks the account as unsynced for offline handling.
     *
     * @param accountId The ID of the account to update
     * @param notificationSettings The notification settings entity
     * @return The updated account with all relations
     */
    suspend fun updateNotificationSettingsInDB(
        accountId: String,
        notificationSettings: NotificationSettingsEntity
    ): Account

    /**
     * Gets the active account from the database.
     *
     * @return The active account or null if none found
     */
    suspend fun getActiveAccountFromDB(): Account?

    /**
     * Gets all accounts with unsynced notification settings from the database.
     *
     * @return List of accounts with unsynced notification data
     */
    suspend fun getUnsyncedNotificationAccountsFromDB(): List<Account>
}
