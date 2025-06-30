package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.db.entity.account.NotificationSettingsEntity
import com.greatergoods.meapp.data.storage.db.entity.account.WeightCompSettingsEntity
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.api.metrics.StreakRequest
import com.greatergoods.meapp.domain.model.api.metrics.WeightlessRequest
import com.greatergoods.meapp.domain.model.api.notification.NotificationSettingsRequest
import com.greatergoods.meapp.domain.model.api.user.BodyCompUpdateRequest
import com.greatergoods.meapp.domain.model.api.user.ProfileUpdateRequest
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IBodyCompositionRepository
import com.greatergoods.meapp.domain.repository.INotificationRepository
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import com.greatergoods.meapp.domain.repository.IGoalRepository
import com.greatergoods.meapp.domain.model.api.goal.GoalRequest
import com.greatergoods.meapp.domain.services.IOfflineHandlerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of offline handler service for managing offline data synchronization.
 * Follows the same pattern as the Angular offline-handler.service.ts.
 * Automatically monitors network connectivity and syncs unsynced data when online.
 */
@Singleton
class OfflineHandlerService
    @Inject
    constructor(
        private val accountRepository: IAccountRepository,
        private val bodyCompositionRepository: IBodyCompositionRepository,
        private val notificationRepository: INotificationRepository,
        private val userSettingsRepository: IUserSettingsRepository,
         private val goalRepository: IGoalRepository,
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
                // Sync user settings data if there are unsynced user settings accounts
                syncUserSettingsData()
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
                    val profileUpdateRequest =
                        ProfileUpdateRequest(
                            id = account.id,
                            firstName = account.firstName,
                            lastName = account.lastName,
                            email = account.email,
                            dob = account.dob,
                            gender = account.gender,
                            zipcode = account.zipcode,
                        )
                    val profileResponse = accountRepository.updateProfile(profileUpdateRequest)
                    val profileUpdate =
                        PartialAccount(
                            firstName = profileResponse.firstName,
                            lastName = profileResponse.lastName,
                            email = profileResponse.email,
                            dob = profileResponse.dob,
                            gender = profileResponse.gender,
                            zipcode = profileResponse.zipcode,
                            isSynced = true,
                        )
                    // Update account with profile response and mark as synced
                    accountRepository.updateAccount(profileResponse.id, profileUpdate)
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
                    val bodyCompUpdateRequest =
                        BodyCompUpdateRequest(
                            height = account.height ?: 1700,
                            activityLevel = account.activityLevel ?: "normal",
                            weightUnit = account.weightUnit?.value ?: "lb",
                        )
                    val bodyCompResponse = bodyCompositionRepository.updateBodyCompInAPI(bodyCompUpdateRequest)
                    // Insert WeightCompSettings entity with data from account
                    val weightCompSettings =
                        WeightCompSettingsEntity(
                            accountId = bodyCompResponse.account.id,
                            height = bodyCompResponse.account.height,
                            activityLevel = bodyCompResponse.account.activityLevel,
                            weightUnit = bodyCompResponse.account.weightUnit,
                            isSynced = true,
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
                    val notificationSettingsRequest =
                        NotificationSettingsRequest(
                            shouldSendEntryNotifications = account.entryNotificationsEnabled ?: false,
                            shouldSendWeightInEntryNotifications = account.showWeightInNotifications ?: false,
                        )
                    val notificationResponse =
                        notificationRepository.updateNotificationSettingsInAPI(notificationSettingsRequest)

                    // Create NotificationSettings entity with data from API response
                    val notificationSettings =
                        NotificationSettingsEntity(
                            accountId = notificationResponse.account.id,
                            entryNotificationsEnabled = notificationResponse.account.shouldSendEntryNotifications,
                            showWeightInNotifications = notificationResponse.account.shouldSendWeightInEntryNotifications,
                            isSynced = true,
                        )
                    notificationRepository.updateNotificationSettingsInDB(account.id, notificationSettings)

                    AppLog.i(TAG, "Successfully synced notification settings for account: ${account.id}")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error syncing notification settings for account ${account.id}", e.toString())
                }
            }
        }

        //  Syncs user settings data for accounts that have unsynced user settings changes.
        private suspend fun syncUserSettingsData() {
            AppLog.d(TAG, "Syncing user settings data")

            try {
                // Sync streak settings
                syncStreakSettings()
                // Sync weightless settings
                syncWeightlessSettings()
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing user settings data", e.toString())
            }
        }

        /**
         * Syncs streak settings for accounts with unsynced changes.
         */
        private suspend fun syncStreakSettings() {
            AppLog.d(TAG, "Syncing streak settings")
            try {
                val unsyncedAccounts = userSettingsRepository.getUnsyncedStreakAccountsFromDB()
                if (unsyncedAccounts.isEmpty()) {
                    AppLog.d(TAG, "No unsynced streak settings found")
                    return
                }
                AppLog.d(TAG, "Found ${unsyncedAccounts.size} accounts with unsynced streak settings")
                for (account in unsyncedAccounts) {
                    try {
                        AppLog.d(TAG, "Syncing streak settings for account: ${account.id}")
                        val streakRequest =
                            StreakRequest(
                                isStreakOn = account.isStreakOn ?: false,
                                streakTimestamp = account.streakTimestamp,
                            )
                        userSettingsRepository.updateStreakSetting(streakRequest)
                        AppLog.i(TAG, "Successfully synced streak settings for account: ${account.id}")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to sync streak settings for account ${account.id}", e.toString())
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing streak settings", e.toString())
            }
        }

        /**
         * Syncs weightless settings for accounts with unsynced changes.
         */
        private suspend fun syncWeightlessSettings() {
            try {
                val unsyncedAccounts = userSettingsRepository.getUnsyncedWeightlessAccountsFromDB()

                if (unsyncedAccounts.isEmpty()) {
                    AppLog.d(TAG, "No unsynced weightless settings found")
                    return
                }
                AppLog.d(TAG, "Found ${unsyncedAccounts.size} accounts with unsynced weightless settings")
                for (account in unsyncedAccounts) {
                    try {
                        AppLog.d(TAG, "Syncing weightless settings for account: ${account.id}")
                        val weightlessRequest =
                            WeightlessRequest(
                                isWeightlessOn = account.isWeightlessOn ?: false,
                                weightlessTimestamp = account.weightlessTimestamp,
                                weightlessWeight = account.weightlessWeight?.toDouble(),
                            )
                        userSettingsRepository.updateWeightlessSetting(weightlessRequest)
                        AppLog.i(TAG, "Successfully synced weightless settings for account: ${account.id}")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to sync weightless settings for account ${account.id}", e.toString())
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error syncing weightless settings", e.toString())
            }
        }
    }
