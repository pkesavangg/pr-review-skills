package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.shared.utilities.DeviceInfoUtil
import com.greatergoods.meapp.domain.model.common.DeviceInfo
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import com.greatergoods.meapp.domain.services.IDeviceService
import com.greatergoods.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.content.Context

/**
 * Service for managing device operations and device info, including FCM token management.
 * Interacts with the IDeviceRepository and NotificationService.
 */
@Singleton
class DeviceInfoService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val deviceInfoRepository: IDeviceInfoRepository,
        private val notificationService: NotificationService,
    ) : IDeviceService {
        private var fcmToken: String? = null

        /**
         * Returns a DeviceInfo object with local device information.
         * @param context The application context.
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

        override suspend fun updateDeviceInfo() {
            fcmToken = getFcmToken()
            deviceInfoRepository.updateDeviceInfo(getDeviceInfo())
        }

        /**
         * Gets the FCM token for this device using NotificationService.
         * @return The FCM token as a String.
         */
        override suspend fun getFcmToken(): String =
            suspendCancellableCoroutine { cont ->
                notificationService.fetchFCMToken(
                    onSuccess = { token -> cont.resume(token) },
                    onError = { exception -> cont.resumeWithException(exception ?: Exception("Unknown error")) },
                )
            }
    }
