package com.dmdbrands.gurus.weight.core.service

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
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
  private val integrationRepository: IIntegrationRepository

) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IDeviceInfoService {
  companion object {
    private const val TAG = "DeviceInfoService"
  }

  private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  init {
    // Start monitoring network connectivity for auto-sync
    startNetworkMonitoring()
  }

  /**
   * Starts monitoring network connectivity to auto-sync when network becomes available.
   * Handles both initial state and state changes, including when app starts offline.
   */
  private fun startNetworkMonitoring() {
    serviceScope.launch {
      // Get initial network state to handle app starting offline
      val initialNetworkState = connectivityObserver.getCurrentNetworkState()
      AppLog.d(TAG, "Initial network state: available=${initialNetworkState.available}")

      // Handle initial state if offline
      if (!initialNetworkState.available) {
        AppLog.d(TAG, "App started offline, showing network error")
        showNetworkError()
      }

      // Monitor network state changes
      connectivityObserver
        .observe()
        .distinctUntilChanged()
        .collect { networkState ->
          AppLog.d(TAG, "Network state changed: available=${networkState.available}")

          // When network becomes available, check for pending sync
          if (networkState.available) {
            AppLog.d(TAG, "Network is available, checking for pending offline sync")
            offlineHandlerService.handleOfflineSync()
            healthConnectRepository.syncIntegration()
            integrationRepository.updateLocalAccount()
          } else {
            AppLog.d(TAG, "Network is unavailable, skipping offline sync")
            showNetworkError()
          }
        }
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
            AppLog.i(TAG, "FCM token populated from Firebase (e.g. post-migration): $token")
          }
        }.onFailure { e -> AppLog.w(TAG, "Could not fetch FCM token from Firebase: ${e.message}") }
      }
      AppLog.d(TAG, "Updating device info with FCM token: $fcmToken")

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
      AppLog.e(TAG, "Failed to update device info", e)
    }
  }

  /**
   * Gets the FCM token from DataStore.
   * @return The FCM token as a String.
   */
  override suspend fun getFcmToken(): String =
    try {
      val token = appRepository.getFcmToken()
      AppLog.d(TAG, "Retrieved FCM token from DataStore: $token")
      token
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to get FCM token from DataStore", e)
      ""
    }
}
