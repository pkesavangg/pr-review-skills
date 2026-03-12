package com.dmdbrands.gurus.weight.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.FcmTokenUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.model.common.DeviceInfo
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceInfoRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Service for managing device operations and device info, including FCM token management.
 * Interacts with the IDeviceInfoRepository and FcmDataStore.
 */
@Singleton
class DeviceInfoService
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val deviceInfoRepository: IDeviceInfoRepository,
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  appNavigationService: IAppNavigationService,
  private val offlineHandlerService: IOfflineHandlerService,
  private val appRepository: IAppRepository,
  private val accountRepository: IAccountRepository,
  private val healthConnectRepository: IHealthConnectRepository,
  private val integrationRepository: IIntegrationRepository,
  private val entryService: IEntryService

) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IDeviceInfoService {
  companion object {
    private const val TAG = "DeviceInfoService"
    private const val NETWORK_UNAVAILABLE_DEBOUNCE_MS = 2000L
  }

  private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  /** Re-entry guard: skip sync if already running (tryLock semantics). */
  private val onlineSyncGuard = AtomicBoolean(false)

  init {
    // Start monitoring network connectivity for auto-sync
    startNetworkMonitoring()
  }

  /**
   * Starts monitoring network connectivity with debounced, stable availability.
   * Only acts after network state has been stable for [NETWORK_DEBOUNCE_MS] ms to avoid
   * flapping on Samsung mobile data. When stable and available, runs online sync once (guarded).
   * Checks whether the app is currently in the foreground.
   */
  private fun isAppInForeground(): Boolean =
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

  /**
   * Starts monitoring network connectivity to auto-sync when network becomes available.
   * Handles both initial state and state changes, including when app starts offline.
   *
   * Uses a debounce before showing network error toasts to avoid false positives
   * caused by transient network loss during Android Doze mode or background-to-foreground
   * transitions.
   */
  @OptIn(FlowPreview::class)
  private fun startNetworkMonitoring() {
      // Get initial network state to handle app starting offline
      val initialNetworkState = connectivityObserver.getCurrentNetworkState()
      AppLog.d(TAG, "Initial network state: available=${initialNetworkState.available}")

      // Handle initial state if offline
      if (!initialNetworkState.available) {
        AppLog.d(TAG, "App started offline, showing network error")
        showNetworkError()
      }
    serviceScope.launch {
      connectivityObserver
        .observe()
        .map { it.available }
        .distinctUntilChanged()
        .collect { available ->
          AppLog.d(TAG, "Network stable: available=$available")
          if (available) {
            runOnlineSyncOnce()
          } else {
            // Debounce: wait before showing the error to filter out transient
            // unavailability caused by Doze mode exit or background-to-foreground
            // transitions. A genuine outage will persist beyond this delay.
            AppLog.d(TAG, "Network reported unavailable, debouncing before showing error")
            delay(NETWORK_UNAVAILABLE_DEBOUNCE_MS)

            // Re-check actual network state after the debounce window
            val currentState = connectivityObserver.getCurrentNetworkState()
            if (currentState.unAvailable && isAppInForeground()) {
              AppLog.d(TAG, "Network still unavailable after debounce and app is in foreground, showing error")
              showNetworkError()
            } else {
              AppLog.d(
                TAG,
                "Network recovered during debounce or app is in background, skipping toast " +
                  "(available=${currentState.available}, foreground=${isAppInForeground()})"
              )
            }
          }
        }
    }
  }

  /**
   * Runs online sync (offline handler, entry sync, Health Connect, integration) at most one at a time.
   * Returns immediately if a sync is already in progress. Each step is wrapped in try/catch so one
   * failure does not prevent others or crash.
   */
  private suspend fun runOnlineSyncOnce() {
    if (!onlineSyncGuard.compareAndSet(false, true)) {
      AppLog.d(TAG, "Online sync already running, skipping")
      return
    }
    try {
      try {
        offlineHandlerService.handleOfflineSync()
      } catch (e: Exception) {
        AppLog.e(TAG, "Offline sync failed", e)
      }
      try {
        entryService.syncOperations()
      } catch (e: Exception) {
        AppLog.e(TAG, "Entry sync failed", e)
      }
      try {
        healthConnectRepository.syncIntegration()
      } catch (e: Exception) {
        AppLog.e(TAG, "Health Connect sync failed", e)
      }
      try {
        integrationRepository.updateLocalAccount()
      } catch (e: Exception) {
        AppLog.e(TAG, "Integration update failed", e)
      }
    } finally {
      onlineSyncGuard.set(false)
    }
  }

  /**
   * The current FCM token for this device (cached after retrieval).
   */
  private var fcmToken: String = ""

  /**
   * Returns a DeviceInfo object with local device and app information, including the FCM token if available.
   * @return DeviceInfo with app and device details.
   */
  override fun getDeviceInfo(): DeviceInfo =
    DeviceInfo(
      appVersion = DeviceInfoUtil.getAppVersion(),
      deviceManufacturer = DeviceInfoUtil.getManufacturer(),
      deviceOSName = DeviceInfoUtil.getOSName(),
      deviceOSVersion = DeviceInfoUtil.getOSVersion(),
      deviceUUID = DeviceInfoUtil.getDeviceUUID(context),
      deviceModel = DeviceInfoUtil.getModel(),
      fcmToken = fcmToken,
    )

  override suspend fun updateLocalIntegrationInfo() {
    AppLog.d(TAG, "Update local integration info")
    integrationRepository.updateLocalAccount()
  }

  /**
   * Updates the device info by fetching the latest FCM token (from DataStore or Firebase when empty, e.g. after migration) and sending device info to the API.
   */
  override suspend fun updateDeviceInfo() {
    try {
      fcmToken = getFcmToken()
      // After migration from older app, DataStore may not have the token; fetch from Firebase and persist.
      if (fcmToken.isBlank()) {
        runCatching {
          val token = FcmTokenUtil.getCurrentToken()
          if (token.isNotBlank()) {
            appRepository.setFcmToken(token)
            fcmToken = token
            AppLog.i(TAG, "FCM token populated from Firebase (e.g. post-migration)")
          }
        }.onFailure { e -> AppLog.w(TAG, "Could not fetch FCM token from Firebase: ${e.message}") }
      }
      AppLog.d(TAG, "Updating device info with FCM token: fcm token is not empty ${fcmToken.isNotEmpty()}")

      val deviceInfo =
        DeviceInfo(
          appVersion = DeviceInfoUtil.getAppVersion(),
          deviceManufacturer = DeviceInfoUtil.getManufacturer(),
          deviceOSName = DeviceInfoUtil.getOSName(),
          deviceOSVersion = DeviceInfoUtil.getOSVersion(),
          deviceUUID = DeviceInfoUtil.getDeviceUUID(context),
          deviceModel = DeviceInfoUtil.getModel(),
          fcmToken = fcmToken,
        )

      deviceInfoRepository.updateDeviceInfo(deviceInfo)

      // Update FCM token for the active account
      val activeAccount = accountRepository.getActiveAccount().first()
      activeAccount?.let { account ->
        val partialUpdate = PartialAccount(fcmToken = fcmToken)
        accountRepository.updateAccount(account.id, partialUpdate)
      }

      AppLog.i(TAG, "Device info updated successfully", deviceInfo.toString())
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to update device info $e")
    }
  }

  /**
   * Gets the FCM token from DataStore.
   * @return The FCM token as a String.
   */
  override suspend fun getFcmToken(): String =
    try {
      val token = appRepository.getFcmToken()
      AppLog.d(TAG, "Retrieved FCM token from DataStore")
      token
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to get FCM token from DataStore", e)
      ""
    }
}
