package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account

/**
 * Service interface for managing notification settings.
 * Handles notification preferences with offline support.
 */
interface INotificationService {

    /**
     * Updates notification settings both online and offline.
     * Online: Updates via API, then saves to DB with isSynced = true
     * Offline: Saves to DB with isSynced = false for later sync
     *
     * @param notificationSettings The notification settings to update
     * @return The updated account or null if update fails
     */
    suspend fun updateNotificationSettings(notificationSettings: NotificationSettingsRequest): Account?
}
