package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IDeviceInfoAPI
import com.greatergoods.meapp.domain.model.common.DeviceInfo
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
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
    /**
     * Updates device info for the account (including FCM token).
     * @param deviceInfo The device info to update.
     * @return The updated DeviceInfo from the API.
     */
    override suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): Response<Unit> =
        deviceApi.updateDeviceInfo(deviceInfo)
}
