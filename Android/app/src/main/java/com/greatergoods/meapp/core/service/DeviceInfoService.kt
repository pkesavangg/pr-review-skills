package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.DeviceInfoUtil
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.data.storage.datastore.FcmDataStore
import com.greatergoods.meapp.domain.model.common.DeviceInfo
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.services.IDeviceInfoService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    private val fcmDataStore: FcmDataStore,
) : IDeviceInfoService {

    companion object {
        private const val TAG = "DeviceInfoService"
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

            val deviceInfo = DeviceInfo(
                appVersion = DeviceInfoUtil.getAppVersion(),
                deviceManufacturer = DeviceInfoUtil.getManufacturer(),
                deviceOSName = DeviceInfoUtil.getOSName(),
                deviceOSVersion = DeviceInfoUtil.getOSVersion(),
                deviceUUID = DeviceInfoUtil.getDeviceUUID(context),
                deviceModel = DeviceInfoUtil.getModel(),
                fcmToken = fcmToken,
            )

            deviceInfoRepository.updateDeviceInfo(deviceInfo)
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
    override suspend fun getFcmToken(): String {
        return try {
            val token = fcmDataStore.tokenFlow.first()
            AppLog.d(TAG, "Retrieved FCM token from DataStore: $token")
            token
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get FCM token from DataStore", e.toString())
            ""
        }
    }
}
