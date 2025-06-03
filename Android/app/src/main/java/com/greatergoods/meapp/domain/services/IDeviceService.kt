package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.common.DeviceInfo

/**
 * Interface for device service operations.
 */
interface IDeviceService {
    fun getDeviceInfo(): DeviceInfo

    suspend fun updateDeviceInfo()

    suspend fun getFcmToken(): String
}
