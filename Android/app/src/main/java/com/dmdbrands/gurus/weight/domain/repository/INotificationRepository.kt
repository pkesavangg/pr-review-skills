package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account

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
     * Gets the active account if it has unsynced notification settings.
     * Used by offline handler service to sync pending notification changes for active account.
     * @return The active account with unsynced notification settings, or null if active account is synced
     */
    suspend fun getUnsyncedActiveNotificationAccountFromDB(): Account?
}
