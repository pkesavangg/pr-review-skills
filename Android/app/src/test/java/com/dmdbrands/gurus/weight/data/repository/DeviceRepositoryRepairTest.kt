package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceRepository.repairDeviceTypesFromSku] (MOB-204).
 *
 * The repair reconciles a device's persisted `deviceType` / `protocolType` with the
 * setup type its SKU is known for. [com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper]
 * is exercised with its real SKU table, so SKU `0375` resolves to the Bluetooth setup type ("bluetooth").
 */
class DeviceRepositoryRepairTest {

    @MockK(relaxUnitFun = true)
    private lateinit var deviceApi: IDeviceAPI

    @MockK(relaxUnitFun = true)
    private lateinit var deviceDao: DeviceDao

    private lateinit var repository: DeviceRepository

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        repository = DeviceRepository(deviceApi, deviceDao)
    }

    @Test
    fun `rewrites stale appsync type for bluetooth SKU 0375`() = runTest {
        val stale = deviceEntity(sku = "0375", deviceType = "appsync", protocolType = "appsync")
        coEvery { deviceDao.getAllDevicesList() } returns listOf(stale)

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(1)
        coVerify { deviceDao.updateDevice(stale.copy(deviceType = "bluetooth", protocolType = "bluetooth")) }
    }

    @Test
    fun `leaves a device whose type already matches the SKU`() = runTest {
        val correct = deviceEntity(sku = "0375", deviceType = "bluetooth", protocolType = "bluetooth")
        coEvery { deviceDao.getAllDevicesList() } returns listOf(correct)

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(0)
        coVerify(exactly = 0) { deviceDao.updateDevice(any<DeviceEntity>()) }
    }

    @Test
    fun `repairs protocolType even when deviceType is already correct`() = runTest {
        val partial = deviceEntity(sku = "0375", deviceType = "bluetooth", protocolType = "appsync")
        coEvery { deviceDao.getAllDevicesList() } returns listOf(partial)

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(1)
        coVerify { deviceDao.updateDevice(partial.copy(deviceType = "bluetooth", protocolType = "bluetooth")) }
    }

    @Test
    fun `leaves devices with an unknown SKU untouched`() = runTest {
        val unknown = deviceEntity(sku = "9999", deviceType = "appsync", protocolType = "appsync")
        coEvery { deviceDao.getAllDevicesList() } returns listOf(unknown)

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(0)
        coVerify(exactly = 0) { deviceDao.updateDevice(any<DeviceEntity>()) }
    }

    @Test
    fun `returns zero when there are no devices`() = runTest {
        coEvery { deviceDao.getAllDevicesList() } returns emptyList()

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(0)
        coVerify(exactly = 0) { deviceDao.updateDevice(any<DeviceEntity>()) }
    }

    @Test
    fun `repairs only mismatched rows in a mixed set`() = runTest {
        val stale = deviceEntity(sku = "0375", deviceType = "appsync", protocolType = "appsync")
        val correct = deviceEntity(sku = "0375", deviceType = "bluetooth", protocolType = "bluetooth")
        val unknown = deviceEntity(sku = "9999", deviceType = "appsync", protocolType = "appsync")
        coEvery { deviceDao.getAllDevicesList() } returns listOf(stale, correct, unknown)

        val repaired = repository.repairDeviceTypesFromSku()

        assertThat(repaired).isEqualTo(1)
        coVerify(exactly = 1) { deviceDao.updateDevice(any<DeviceEntity>()) }
        coVerify { deviceDao.updateDevice(stale.copy(deviceType = "bluetooth", protocolType = "bluetooth")) }
    }

    private fun deviceEntity(
        sku: String,
        deviceType: String,
        protocolType: String,
    ): DeviceEntity = DeviceEntity(
        id = "device-$sku-$deviceType-$protocolType",
        accountId = "account1",
        peripheralIdentifier = null,
        nickname = "Scale",
        sku = sku,
        mac = null,
        password = null,
        deviceName = "Bluetooth Smart Scale",
        deviceType = deviceType,
        broadcastId = null,
        broadcastIdString = null,
        userNumber = null,
        protocolType = protocolType,
        createdAt = null,
        token = null,
    )
}
