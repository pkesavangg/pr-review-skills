package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.common.DeviceInfo
import retrofit2.Response

/**
 * Interface for updating device info via the API.
 */
interface IDeviceInfoRepository {
    /**
     * Updates device info for the account (including FCM token).
     * @param deviceInfo The device info to update.
     * @return The updated DeviceInfo from the API.
     */
    suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): Response<Unit>
}
