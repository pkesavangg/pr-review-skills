package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IDeviceInfoAPI
import com.dmdbrands.gurus.weight.domain.model.common.DeviceInfo
import com.dmdbrands.gurus.weight.domain.repository.IDeviceInfoRepository
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for updating device info via the API.
 * Implements [IDeviceInfoRepository].
 */
@Singleton
class DeviceInfoRepository @Inject constructor(
    private val deviceApi: IDeviceInfoAPI
) : IDeviceInfoRepository {

    companion object {
        private const val TAG = "DeviceInfoRepository"
    }

    /**
     * Updates device info for the account (including FCM token).
     * @param deviceInfo The device info to update.
     * @return The updated DeviceInfo from the API.
     */
    override suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): Response<Unit> {
        AppLog.d(TAG, "Updating device info via API")
        return try {
            val response = deviceApi.updateDeviceInfo(deviceInfo)
            if (response.isSuccessful) {
                AppLog.i(TAG, "Device info updated successfully")
            } else {
                AppLog.e(TAG, "updateDeviceInfo failed with code: ${response.code()}")
            }
            response
        } catch (e: Exception) {
            AppLog.e(TAG, "updateDeviceInfo threw an exception", e)
            throw e
        }
    }
}
