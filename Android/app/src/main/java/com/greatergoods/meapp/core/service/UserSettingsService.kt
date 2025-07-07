package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.metrics.StreakRequest
import com.greatergoods.meapp.domain.model.api.metrics.WeightlessRequest
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IUserSettingsRepository
import com.greatergoods.meapp.domain.services.IUserSettingsService
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service implementation for user settings operations.
 * Handles business logic for streak and weightless mode settings.
 */
@Singleton
class UserSettingsService
  @Inject
  constructor(
    private val userSettingsRepository: IUserSettingsRepository,
    private val connectivityObserver: IConnectivityObserver,
  ) : IUserSettingsService {
    private val TAG = "UserSettingsService"

    /**
     * Generates a timestamp in the format 'YYYY-MM-DD HH:mm:ss.SSSSSSZ'
     * Example: '2022-08-22 14:26:38.954039+05:30'
     */
    private fun getCurrentTimestamp(): String {
      val now = ZonedDateTime.now()
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX")
      return now.format(formatter)
    }

    /**
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

    /**
     * Toggles the streak setting for the active account.
     * Handles both online and offline scenarios.
     * @param isStreakOn Boolean indicating if streak should be enabled
     * @return Updated account with new streak settings
     */
    override suspend fun toggleStreakSetting(isStreakOn: Boolean): Account? =
      try {
        AppLog.d(TAG, "Toggling streak setting to: $isStreakOn")

        val streakRequest =
          StreakRequest(
            isStreakOn = isStreakOn,
            streakTimestamp = getCurrentTimestamp(),
          )

        if (isNetworkAvailable()) {
          // Online: Update via API and mark as synced in DB
          AppLog.d(TAG, "Network available - updating streak setting online")
          userSettingsRepository.updateStreakSetting(streakRequest)
        } else {
          // Offline: Store locally and mark as unsynced for later sync
          AppLog.d(TAG, "Network unavailable - storing streak setting for offline sync")
          userSettingsRepository.updateStreakSettingOffline(streakRequest)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling streak setting", e.toString())
        throw e
      }

    /**
     * Toggles the weightless setting for the active account.
     * Handles both online and offline scenarios.
     * @param isWeightlessOn Boolean indicating if weightless mode should be enabled
     * @param weightlessWeight Weight to use when weightless mode is enabled (optional)
     * @return Updated account with new weightless settings
     */
    override suspend fun toggleWeightlessSetting(
      isWeightlessOn: Boolean,
      weightlessWeight: Double?,
    ): Account? =
      try {
        AppLog.d(TAG, "Toggling weightless setting to: $isWeightlessOn, weight: $weightlessWeight")

        val weightlessRequest =
          WeightlessRequest(
            isWeightlessOn = isWeightlessOn,
            weightlessTimestamp = if (isWeightlessOn) getCurrentTimestamp() else null,
            weightlessWeight = if (isWeightlessOn) weightlessWeight else null,
          )

        if (isNetworkAvailable()) {
          // Online: Update via API and mark as synced in DB
          AppLog.d(TAG, "Network available - updating weightless setting online")
          userSettingsRepository.updateWeightlessSetting(weightlessRequest)
        } else {
          // Offline: Store locally and mark as unsynced for later sync
          AppLog.d(TAG, "Network unavailable - storing weightless setting for offline sync")
          userSettingsRepository.updateWeightlessSettingOffline(weightlessRequest)
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Error toggling weightless setting", e.toString())
        throw e
      }
  }
