package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.common.DeviceInfo

/**
 * Interface for device service operations, including device info and FCM token management.
 */
interface IDeviceService {
    /**
     * Returns a DeviceInfo object with local device and app information.
     * @return DeviceInfo with app and device details.
     */
    fun getDeviceInfo(): DeviceInfo

    /**
     * Updates the device info by sending the latest device and FCM token information to the API.
     */
    suspend fun updateDeviceInfo()

    /**
     * Gets the FCM token for this device.
     * @return The FCM token as a String.
     */
    suspend fun getFcmToken(): String
}
