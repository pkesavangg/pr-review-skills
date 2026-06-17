package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
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
    private val deviceService: IDeviceService,
    private val notificationRepository: INotificationRepository,
    private val userSettingsRepository: IUserSettingsRepository,
    private val goalRepository: IGoalRepository,
    connectivityObserver: IConnectivityObserver,
    dialogQueueService: IDialogQueueService,
    appNavigationService: IAppNavigationService,
  ) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IOfflineHandlerService {
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
        if (!isNetworkAvailable()) {
          AppLog.w(TAG, "Network not available, skipping sync")
          return
        }
        // Sync profile data if there are unsynced accounts
        syncProfileData()
        // Sync body composition data if there are unsynced body comp accounts
        syncBodyCompositionData()
        // Sync device data if there are unsynced devices
        syncDeviceData()
        // Sync notification settings if there are unsynced notification accounts
        syncNotificationData()
        syncGoalData()
        syncWeightlessSettings()
        syncStreakSettings()
        syncDashboardData()
        // Sync user settings data if there are unsynced user settings accounts
        AppLog.i(TAG, "Selective offline sync process completed")
      } catch (e: Exception) {
        AppLog.e(TAG, "Offline sync process failed", e)
      }
    }

    /**
     * Syncs profile data for accounts that have unsynced profile changes.
     */
    private suspend fun syncProfileData() {
      val unSyncedAccount = accountRepository.getUnsyncedActiveAccount() ?: return
      try {
          val profileUpdateRequest =
            ProfileUpdateRequest(
              id = unSyncedAccount.id,
              firstName = unSyncedAccount.firstName,
              lastName = unSyncedAccount.lastName,
              email = unSyncedAccount.email,
              dob = unSyncedAccount.dob,
              gender = unSyncedAccount.gender,
              zipcode = unSyncedAccount.zipcode,
            )
           accountRepository.updateProfile(profileUpdateRequest)
          AppLog.i(TAG, "Successfully synced profile data for account: ${unSyncedAccount.id}")
        } catch (e: Exception) {
          AppLog.e(TAG, "Error syncing profile data for account ${unSyncedAccount.id}", e)
        }
    }

    /**
     * Syncs body composition data for the active account if it has unsynced changes.
     */
    private suspend fun syncBodyCompositionData() {
      val unsyncedAccount = bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB()
      if (unsyncedAccount == null) {
        AppLog.d(TAG, "No unsynced body composition data for active account")
        return
      }
      try {
        // Sync body composition data (height, activity level, weight unit)
        val bodyCompUpdateRequest =
          BodyCompUpdateRequest(
            height = unsyncedAccount.height ?: 1700,
            activityLevel = unsyncedAccount.activityLevel ?: "normal",
            weightUnit = unsyncedAccount.weightUnit.value,
          )
        val bodyCompResponse = bodyCompositionRepository.updateBodyCompInAPI(bodyCompUpdateRequest)
        // Insert WeightCompSettings entity with data from account
        val weightCompSettings =
          WeightCompSettingsEntity(
            accountId = bodyCompResponse.account.id,
            height = bodyCompResponse.account.height ?: BodyCompUpdateRequest.DEFAULT_HEIGHT,
            activityLevel = bodyCompResponse.account.activityLevel,
            weightUnit = bodyCompResponse.account.weightUnit,
            isSynced = true,
          )
        bodyCompositionRepository.updateBodyCompInDB(unsyncedAccount.id, weightCompSettings)
        AppLog.i(TAG, "Successfully synced body composition data for account: ${unsyncedAccount.id}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error syncing body composition data for account ${unsyncedAccount.id}", e)
      }
    }

    /**
     * Syncs notification settings for the active account if it has unsynced changes.
     */
    private suspend fun syncNotificationData() {
      val unsyncedAccount = notificationRepository.getUnsyncedActiveNotificationAccountFromDB()
      if (unsyncedAccount == null) {
        AppLog.d(TAG, "No unsynced notification data for active account")
        return
      }
      try {
        // Sync notification settings (entry notifications and weight in notifications)
        val notificationSettingsRequest =
          NotificationSettingsRequest(
            shouldSendEntryNotifications = unsyncedAccount.shouldSendEntryNotifications ?: false,
            shouldSendWeightInEntryNotifications = unsyncedAccount.shouldSendWeightInEntryNotifications ?: false,
          )
        val notificationResponse =
          notificationRepository.updateNotificationSettingsInAPI(notificationSettingsRequest)

        // Create NotificationSettings entity with data from API response
        val notificationSettings =
          NotificationSettingsEntity(
            accountId = notificationResponse.account.id,
            shouldSendEntryNotifications = notificationResponse.account.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications = notificationResponse.account.shouldSendWeightInEntryNotifications,
            isSynced = true,
          )
        notificationRepository.updateNotificationSettingsInDB(unsyncedAccount.id, notificationSettings)
        AppLog.i(TAG, "Successfully synced notification settings for account: ${unsyncedAccount.id}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error syncing notification settings for account ${unsyncedAccount.id}", e)
      }
    }

    /**
     * Syncs goal data for the active account if it has unsynced changes.
     */
    private suspend fun syncGoalData() {
      val unsyncedAccount = goalRepository.getUnsyncedActiveGoalAccountFromDB()
      if (unsyncedAccount == null) {
        AppLog.d(TAG, "No unsynced goal data for active account")
        return
      }
      try {
        val goalData =
          GoalData(
            goalWeight = unsyncedAccount.goalWeight ?: 0.0,
            initialWeight = unsyncedAccount.initialWeight,
            type = unsyncedAccount.goalType ?: "maintain",
            metPreviousGoal = unsyncedAccount.metPreviousGoal,
          )
        goalRepository.updateGoalSetting(goalData)
        AppLog.i(TAG, "Successfully synced goal settings for account: ${unsyncedAccount.id}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to sync goal settings for account ${unsyncedAccount.id}", e)
      }
    }

    /**
     * Syncs streak settings for the active account if it has unsynced changes.
     */
    private suspend fun syncStreakSettings() {
      val unsyncedAccount = userSettingsRepository.getUnsyncedActiveStreakAccountFromDB()
      if (unsyncedAccount == null) {
        AppLog.d(TAG, "No unsynced streak data for active account")
        return
      }
      try {
        val streakRequest =
          StreakRequest(
            isStreakOn = unsyncedAccount.isStreakOn ?: false,
            streakTimestamp = unsyncedAccount.streakTimestamp,
          )
        userSettingsRepository.updateStreakSetting(streakRequest)
        AppLog.i(TAG, "Successfully synced streak settings for account: ${unsyncedAccount.id}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to sync streak settings for account ${unsyncedAccount.id}", e)
      }
    }

    /**
     * Syncs weightless settings for the active account if it has unsynced changes.
     */
    private suspend fun syncWeightlessSettings() {
      val unsyncedAccount = userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB()
      if (unsyncedAccount == null) {
        AppLog.d(TAG, "No unsynced weightless data for active account")
        return
      }
      try {
        val weightlessRequest =
          WeightlessRequest(
            isWeightlessOn = unsyncedAccount.isWeightlessOn ?: false,
            weightlessTimestamp = unsyncedAccount.weightlessTimestamp,
            weightlessWeight = unsyncedAccount.weightlessWeight?.toDouble(),
          )
        userSettingsRepository.updateWeightlessSetting(weightlessRequest)
        AppLog.i(TAG, "Successfully synced weightless settings for account: ${unsyncedAccount.id}")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to sync weightless settings for account ${unsyncedAccount.id}", e)
      }
    }

    /**
     * Syncs device data using DeviceService.
     */
    private suspend fun syncDeviceData() {
      try {
        AppLog.d(TAG, "Syncing devices using DeviceService")
        deviceService.syncDevices()
        AppLog.i(TAG, "Device sync completed")
      } catch (e: Exception) {
        AppLog.e(TAG, "Error during device sync", e)
      }
    }

    /**
     * Syncs dashboard settings for the active account if it has unsynced changes.
     */
    private suspend fun syncDashboardData() {
      val unsyncedSettings = accountRepository.getUnsyncedActiveDashboardSettings()
      if (unsyncedSettings == null) {
        AppLog.d(TAG, "No unsynced dashboard data for active account")
        return
      }
      try {
        // Get dashboard type from settings
        val dashboardType = DashboardType.entries.find {
          it.value.equals(unsyncedSettings.dashboardType, ignoreCase = true)
        } ?: DashboardType.DASHBOARD_4_METRICS

        // Sync dashboard metrics and progress metrics via API
        accountRepository.updateDashboardMetrics(unsyncedSettings.dashboardMetrics)
        accountRepository.updateProgressMetrics(unsyncedSettings.dashboardMilestones)

        // Update dashboard settings as synced
        accountRepository.updateDashboardSettings(
          accountId = unsyncedSettings.accountId,
          dashboardMetrics = unsyncedSettings.dashboardMetrics,
          dashboardMilestones = unsyncedSettings.dashboardMilestones,
          dashboardType = dashboardType,
          isSynced = true,
        )

        AppLog.i(TAG, "Successfully synced dashboard settings for account: ${unsyncedSettings.accountId}")
      } catch (e: Exception) {
         AppLog.e(TAG, "Error syncing dashboard settings for account ${unsyncedSettings.accountId}", e)
      }
    }
  }
