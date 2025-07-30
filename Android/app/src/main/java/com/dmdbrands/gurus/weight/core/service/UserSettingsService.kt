package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeUtil.getCurrentTimestamp
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
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
     * Checks if network is available using the connectivity observer
     */
    private fun isNetworkAvailable(): Boolean = !connectivityObserver.getCurrentNetworkState().unAvailable

    /**
     * Toggles the streak setting for the active account.
     * Handles both online and offline scenarios.
     * @param isStreakOn Boolean indicating if streak should be enabled
     * @return Updated account with new streak settings
     */
    override suspend fun toggleStreakSetting(isStreakOn: Boolean) {
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
    ) {
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

  }
