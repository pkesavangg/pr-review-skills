package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.DeviceInfoUtil
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.PartialAccount
import com.greatergoods.meapp.domain.model.common.DeviceInfo
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IAppRepository
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.services.IDeviceInfoService
import com.greatergoods.meapp.domain.services.IOfflineHandlerService
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
        private val connectivityObserver: IConnectivityObserver,
        private val offlineHandlerService: IOfflineHandlerService,
        private val appRepository: IAppRepository,
        private val accountRepository: IAccountRepository,
    ) : IDeviceInfoService {
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
         */
        private fun startNetworkMonitoring() {
            serviceScope.launch {
                connectivityObserver
                    .observe()
                    .distinctUntilChanged()
                    .collect { networkState ->
                        AppLog.d(TAG, "Network state changed: available=${!networkState.unAvailable}")

                        // When network becomes available, check for pending sync
                        if (networkState.available) {
                            AppLog.d(TAG, "Network is available, checking for pending offline sync")
                            offlineHandlerService.handleOfflineSync()
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
         * Updates the device info by fetching the latest FCM token from DataStore and sending device info to the API.
         */
        override suspend fun updateDeviceInfo() {
            try {
                fcmToken = getFcmToken()
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
                AppLog.e(TAG, "Failed to update device info", e.toString())
                throw e
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
                AppLog.e(TAG, "Failed to get FCM token from DataStore", e.toString())
                ""
            }
    }
