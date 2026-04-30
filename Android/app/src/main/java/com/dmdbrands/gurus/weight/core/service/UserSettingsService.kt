package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeUtil.getCurrentTimestamp
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import javax.inject.Singleton

/**
 * Service implementation for user settings operations.
 * Handles business logic for streak and weightless mode settings.
 */
// Constructed via ServiceModule.provideUserSettingsService — no @Inject on the constructor.
// `dispatcher` is a Kotlin default (Dispatchers.IO in production); tests override it.
@Singleton
class UserSettingsService(
  private val userSettingsRepository: IUserSettingsRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IUserSettingsService {

    companion object {
      private const val TAG = "UserSettingsService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)

    // Eagerly seeded so consumers can read `.value` synchronously. The seed
    // (GraphSegment.DEFAULT) is what callers see until DataStore emits the persisted
    // value. retryWhen keeps the upstream alive forever on transient DataStore errors
    // — terminating with .catch would freeze the StateFlow and break account-switch /
    // setDefaultGraphSegment write-back propagation. CancellationException always
    // propagates so structured concurrency is preserved.
    override val defaultGraphSegment: StateFlow<GraphSegment> =
      userSettingsRepository.defaultGraphSegmentFlow
        .retryWhen { cause, attempt ->
          if (cause is CancellationException) return@retryWhen false
          AppLog.e(TAG, "Error reading default graph segment (attempt ${attempt + 1}); retrying", cause)
          delay(((attempt + 1) * 200L).coerceAtMost(5000L))
          true
        }
        .stateIn(serviceScope, SharingStarted.Eagerly, GraphSegment.DEFAULT)

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
        AppLog.e(TAG, "Error toggling streak setting", e)
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
        AppLog.e(TAG, "Error toggling weightless setting", e)
        throw e
      }
    }

    override suspend fun setDefaultGraphSegment(segment: GraphSegment) {
      try {
        AppLog.d(TAG, "Setting default graph segment to: $segment")
        userSettingsRepository.setDefaultGraphSegment(segment)
        AppLog.i(TAG, "Successfully updated default graph segment to $segment")
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppLog.e(TAG, "Error setting default graph segment", e)
        throw e
      }
    }

  }
