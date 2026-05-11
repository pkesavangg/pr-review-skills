package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData as HcAccountData
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * Shared stubs for HealthConnect-related tests.
 */
object HealthConnectTestHelper {

    fun stubDefaultHealthConnect() {
        coEvery { anyConstructed<HealthConnect>().isAvailable() } returns true
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } returns setOf("perm1")
        coEvery { anyConstructed<HealthConnect>().saveData(any()) } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().deleteEntry(any()) } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().handleOnNewIntent(any()) } returns Unit
    }

    fun stubIntegrated(
        healthConnectRepository: IHealthConnectRepository,
        accountId: String,
    ) {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns false
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(accountId to mockData)
    }

    fun stubNotIntegrated(
        healthConnectRepository: IHealthConnectRepository,
        accountId: String,
    ) {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns true
            every { assignedTo } returns "other-account"
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(accountId to mockData)
    }

    fun stubDeviceInfo(deviceId: String = "device-uuid-test") {
        every { DeviceInfoUtil.getDeviceUUID(any()) } returns deviceId
    }
}
