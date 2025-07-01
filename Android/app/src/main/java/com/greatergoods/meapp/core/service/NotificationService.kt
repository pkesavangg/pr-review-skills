package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.INotificationRepository
import com.greatergoods.meapp.domain.services.INotificationService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of notification service for managing notification settings.
 * Handles notification preferences with offline support.
 * Follows the same pattern as Angular updateNotificationsSetting method.
 */
@Singleton
class NotificationService @Inject constructor(
    private val notificationRepository: INotificationRepository,
    private val connectivityObserver: IConnectivityObserver
) : INotificationService {

    companion object {
        private const val TAG = "NotificationService"
    }

    /**
     * Checks if network is available for API calls.
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

    /**
     * Updates notification settings both online and offline.
     * Online: Updates via API, then saves to DB with isSynced = true
     * Offline: Saves to DB with isSynced = false for later sync
     *
     * @param notificationSettings The notification settings to update
     * @return The updated account or null if update fails
     */
    override suspend fun updateNotificationSettings(notificationSettings: NotificationSettingsRequest): Account? {
        AppLog.d(TAG, "Updating notification settings: $notificationSettings")
        return try {
            val activeAccount = notificationRepository.getActiveAccountFromDB()
                ?: throw IllegalStateException("No active account found")

            if (isNetworkAvailable()) {
                val response = notificationRepository.updateNotificationSettingsInAPI(notificationSettings)
                val notificationEntity = NotificationSettingsEntity(
                    accountId = response.account.id,
                    entryNotificationsEnabled = response.account.shouldSendEntryNotifications,
                    showWeightInNotifications = response.account.shouldSendWeightInEntryNotifications,
                    isSynced = true
                )
                val updatedAccount = notificationRepository.updateNotificationSettingsInDB(activeAccount.id, notificationEntity)
                updatedAccount
            } else {
                // Offline: Save to DB with isSynced = false for later sync
                val notificationEntity = NotificationSettingsEntity(
                    accountId = activeAccount.id,
                    entryNotificationsEnabled = notificationSettings.shouldSendEntryNotifications,
                    showWeightInNotifications = notificationSettings.shouldSendWeightInEntryNotifications,
                    isSynced = false
                )
                val updatedAccount = notificationRepository.updateNotificationSettingsInDB(activeAccount.id, notificationEntity)
                updatedAccount
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Notification settings update failed", e.toString())
            null
        }
    }
}
