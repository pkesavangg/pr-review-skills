package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.repository.INotificationRepository
import com.greatergoods.meapp.domain.services.IOfflineHandlerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of offline handler service for managing offline data synchronization.
 * Follows the same pattern as the Angular offline-handler.service.ts.
 * Automatically monitors network connectivity and syncs unsynced data when online.
 */
@Singleton
class OfflineHandlerService @Inject constructor(
    private val accountRepository: IAccountRepository,
    private val bodyCompositionRepository: IBodyCompositionRepository,
    private val notificationRepository: INotificationRepository,
    private val connectivityObserver: IConnectivityObserver,
) : IOfflineHandlerService {

    companion object {
        private const val TAG = "OfflineHandlerService"
    }

    /**
     * Handles offline data synchronization when network connectivity is restored.
     * Uses selective syncing - only syncs the specific APIs that have unsynced data.
     */
    override suspend fun handleOfflineSync() {
        AppLog.d(TAG, "Starting selective offline sync process")
        try {
            // Check if network is available
            if (connectivityObserver.getCurrentNetworkState().unAvailable) {
                AppLog.w(TAG, "Network not available, skipping sync")
                return
            }
            // Sync profile data if there are unsynced accounts
            syncProfileData()
            // Sync body composition data if there are unsynced body comp accounts
            syncBodyCompositionData()
            // Sync notification settings if there are unsynced notification accounts
            syncNotificationData()
            AppLog.i(TAG, "Selective offline sync process completed")
        } catch (e: Exception) {
            AppLog.e(TAG, "Offline sync process failed", e.toString())
        }
    }

    /**
     * Syncs profile data for accounts that have unsynced profile changes.
     */
    private suspend fun syncProfileData() {
        val unsyncedAccounts = accountRepository.getUnsyncedAccountsFromDB()
        if (unsyncedAccounts.isEmpty()) {
            AppLog.d(TAG, "No unsynced profile accounts found")
            return
        }

        for (account in unsyncedAccounts) {
            try {
                // Sync profile data (basic info like name, email, gender, etc.)
                val profileUpdateRequest = ProfileUpdateRequest(
                    id = account.id,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    email = account.email,
                    dob = account.dob,
                    gender = account.gender,
                    zipcode = account.zipcode,
                )
                val profileResponse = accountRepository.updateProfileInAPI(profileUpdateRequest)
                val profileUpdate = PartialAccount(
                    firstName = profileResponse.account.firstName,
                    lastName = profileResponse.account.lastName,
                    email = profileResponse.account.email,
                    dob = profileResponse.account.dob,
                    gender = profileResponse.account.gender,
                    zipcode = profileResponse.account.zipcode,
                    isSynced = true,
                )
                // Update account with profile response and mark as synced
                accountRepository.updateAccountInDB(profileResponse.account.id, profileUpdate)
                AppLog.i(TAG, "Successfully synced profile data for account: ${account.id}")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing profile data for account ${account.id}", e.toString())
            }
        }
    }

    /**
     * Syncs body composition data for accounts that have unsynced body comp changes.
     */
    private suspend fun syncBodyCompositionData() {
        val unsyncedBodyCompAccounts = bodyCompositionRepository.getUnsyncedBodyCompAccountsFromDB()
        if (unsyncedBodyCompAccounts.isEmpty()) {
            AppLog.d(TAG, "No unsynced body composition accounts found")
            return
        }
        for (account in unsyncedBodyCompAccounts) {
            try {
                // Sync body composition data (height, activity level, weight unit)
                val bodyCompUpdateRequest = BodyCompUpdateRequest(
                    height = account.height ?: 1700,
                    activityLevel = account.activityLevel ?: "normal",
                    weightUnit = account.weightUnit?.value ?:  "lb",
                )
                val bodyCompResponse = bodyCompositionRepository.updateBodyCompInAPI(bodyCompUpdateRequest)
                // Insert WeightCompSettings entity with data from account
                val weightCompSettings = WeightCompSettingsEntity(
                    accountId = bodyCompResponse.account.id,
                    height = bodyCompResponse.account.height ,
                    activityLevel = bodyCompResponse.account.activityLevel ,
                    weightUnit = bodyCompResponse.account.weightUnit ,
                    isSynced = true
                )
                bodyCompositionRepository.updateBodyCompInDB(account.id, weightCompSettings)
                // Update account with body comp response and mark as synced
                AppLog.i(TAG, "Successfully synced body composition data for account: ${account.id}")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing body composition data for account ${account.id}", e.toString())
            }
        }
    }

    /**
     * Syncs notification settings for accounts that have unsynced notification changes.
     */
    private suspend fun syncNotificationData() {
        val unsyncedNotificationAccounts = notificationRepository.getUnsyncedNotificationAccountsFromDB()
        if (unsyncedNotificationAccounts.isEmpty()) {
            AppLog.d(TAG, "No unsynced notification accounts found")
            return
        }

        for (account in unsyncedNotificationAccounts) {
            try {
                // Sync notification settings (entry notifications and weight in notifications)
                val notificationSettingsRequest = NotificationSettingsRequest(
                    shouldSendEntryNotifications = account.entryNotificationsEnabled ?: false,
                    shouldSendWeightInEntryNotifications = account.showWeightInNotifications ?: false
                )
                val notificationResponse = notificationRepository.updateNotificationSettingsInAPI(notificationSettingsRequest)

                // Create NotificationSettings entity with data from API response
                val notificationSettings = NotificationSettingsEntity(
                    accountId = notificationResponse.account.id,
                    entryNotificationsEnabled = notificationResponse.account.shouldSendEntryNotifications,
                    showWeightInNotifications = notificationResponse.account.shouldSendWeightInEntryNotifications,
                    isSynced = true
                )
                notificationRepository.updateNotificationSettingsInDB(account.id, notificationSettings)

                AppLog.i(TAG, "Successfully synced notification settings for account: ${account.id}")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing notification settings for account ${account.id}", e.toString())
            }
        }
    }
}
