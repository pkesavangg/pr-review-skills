package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.common.DeviceInfo

/**
 * Service interface for device information operations including FCM token management and device info updates.
 */
interface IDeviceInfoService {
    /**
     * Returns a DeviceInfo object with local device and app information.
     * @return DeviceInfo with app and device details.
     */
    fun getDeviceInfo(): DeviceInfo

    /**
     * Updates device information on the server with current device details and FCM token.
     * This method fetches device details and sends them to the server.
     */
    suspend fun updateDeviceInfo()

    /**
     * Gets the current FCM token.
     * @return The current FCM token string.
     */
    suspend fun getFcmToken(): String
}
