package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IDeviceAPI
import com.greatergoods.meapp.domain.model.common.DeviceInfo
import com.greatergoods.meapp.domain.repository.IDeviceInfoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for updating device info via the API.
 */
@Singleton
class DeviceInfoRepository @Inject constructor(
    private val deviceApi: IDeviceAPI
) : IDeviceInfoRepository {
    /**
     * Updates device info for the account (including FCM token).
     * @param deviceInfo The device info to update.
     * @return The updated DeviceInfo from the API.
     */
    override suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): DeviceInfo =
        deviceApi.updateDeviceInfo(deviceInfo)
}
