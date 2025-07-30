package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.common.DeviceInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH

/**
 * API interface for device-related endpoints.
 */
interface IDeviceInfoAPI {
    companion object Companion {
        private const val DEVICE = "account/device/"
    }

    /**
     * Update device info for the account (including FCM token).
     * @param deviceInfo The device info to update.
     */
    @PATCH(DEVICE)
    suspend fun updateDeviceInfo(@Body deviceInfo: DeviceInfo): Response<Unit>
}
