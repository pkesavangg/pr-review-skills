package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleTokenResponse
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModels
import com.dmdbrands.gurus.weight.domain.model.api.device.toApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.toDeviceDetails
import com.dmdbrands.gurus.weight.domain.model.storage.toDeviceDomainModel
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class DeviceRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var deviceApi: IDeviceAPI

    @MockK(relaxUnitFun = true)
    private lateinit var deviceDao: DeviceDao

    private lateinit var repository: DeviceRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("com.dmdbrands.gurus.weight.domain.model.storage.DeviceMappersKt")
        mockkStatic("com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiMapperKt")
        repository = DeviceRepository(deviceApi, deviceDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── getDevices ─────────────────────────────────────────────────────────────

    @Test
    fun `getDevices returns empty list when dao emits empty`() = runTest {
        every { deviceDao.getDevices("account1") } returns flowOf(emptyList())

        val result = repository.getDevices("account1", filterDeleted = false).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getDevices with filterDeleted true excludes deleted devices`() = runTest {
        val deletedDetails = buildDeviceDetails(isDeleted = true)
        val activeDetails = buildDeviceDetails(isDeleted = false)
        val activeDevice = Device(id = "active-device")
        every { deviceDao.getDevices("account1") } returns flowOf(listOf(deletedDetails, activeDetails))
        every { activeDetails.toDeviceDomainModel() } returns activeDevice

        val result = repository.getDevices("account1", filterDeleted = true).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("active-device")
    }

    @Test
    fun `getDevices with filterDeleted false includes deleted devices`() = runTest {
        val deletedDetails = buildDeviceDetails(isDeleted = true)
        val activeDetails = buildDeviceDetails(isDeleted = false)
        val deletedDevice = Device(id = "deleted-device")
        val activeDevice = Device(id = "active-device")
        every { deviceDao.getDevices("account1") } returns flowOf(listOf(deletedDetails, activeDetails))
        every { deletedDetails.toDeviceDomainModel() } returns deletedDevice
        every { activeDetails.toDeviceDomainModel() } returns activeDevice

        val result = repository.getDevices("account1", filterDeleted = false).first()

        assertThat(result).hasSize(2)
    }

    // ── getDevice ──────────────────────────────────────────────────────────────

    @Test
    fun `getDevice emits null when dao returns null`() = runTest {
        coEvery { deviceDao.getDevice("device1") } returns null

        val result = repository.getDevice("device1").first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDevice emits mapped device when dao returns details`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = "device1")
        coEvery { deviceDao.getDevice("device1") } returns details
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getDevice("device1").first()

        assertThat(result).isEqualTo(device)
    }

    // ── updateDevice ───────────────────────────────────────────────────────────

    @Test
    fun `updateDevice converts to DeviceDetails and delegates to dao`() = runTest {
        val device = Device(id = "device1")
        val details = buildDeviceDetails()
        every { any<Device>().toDeviceDetails(any()) } returns details

        repository.updateDevice(device, "account1")

        coVerify { deviceDao.updateDevice(details) }
    }

    // ── saveDeviceToDb ─────────────────────────────────────────────────────────

    @Test
    fun `saveDeviceToDb converts to DeviceDetails and delegates to dao`() = runTest {
        val device = Device(id = "device1")
        val details = buildDeviceDetails()
        every { any<Device>().toDeviceDetails(any()) } returns details

        repository.saveDeviceToDb(device, "account1")

        coVerify { deviceDao.insertDevice(details) }
    }

    // ── deleteAllDevicesForAccount ─────────────────────────────────────────────

    @Test
    fun `deleteAllDevicesForAccount delegates to dao`() = runTest {
        coEvery { deviceDao.deleteAllDevicesForAccount(any()) } returns 1
        repository.deleteAllDevicesForAccount("account1")
        coVerify { deviceDao.deleteAllDevicesForAccount("account1") }
    }

    // ── deleteDeviceFromDb ─────────────────────────────────────────────────────

    @Test
    fun `deleteDeviceFromDb delegates to dao`() = runTest {
        repository.deleteDeviceFromDb("device1")
        coVerify { deviceDao.deleteDevice("device1") }
    }

    // ── deviceExistsBy* ────────────────────────────────────────────────────────

    @Test
    fun `deviceExistsByBroadcastId emits true when device found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastId("broadcast1", "account1") } returns buildDeviceDetails()

        val result = repository.deviceExistsByBroadcastId("broadcast1", "account1").first()

        assertThat(result).isTrue()
    }

    @Test
    fun `deviceExistsByBroadcastId emits false when device not found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastId("broadcast1", "account1") } returns null

        val result = repository.deviceExistsByBroadcastId("broadcast1", "account1").first()

        assertThat(result).isFalse()
    }

    @Test
    fun `deviceExistsByMac emits true when device found`() = runTest {
        coEvery { deviceDao.getDeviceByMac("AA:BB:CC:DD", "account1") } returns buildDeviceDetails()

        val result = repository.deviceExistsByMac("AA:BB:CC:DD", "account1").first()

        assertThat(result).isTrue()
    }

    @Test
    fun `deviceExistsByPeripheralId emits false when device not found`() = runTest {
        coEvery { deviceDao.getDeviceByPeripheralId("peripheral1") } returns null

        val result = repository.deviceExistsByPeripheralId("peripheral1").first()

        assertThat(result).isFalse()
    }

    // ── getDeviceBy* ───────────────────────────────────────────────────────────

    @Test
    fun `getDeviceByBroadcastId emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastIdString(any(), any()) } returns null

        val result = repository.getDeviceByBroadcastId("broadcast1", "account1").first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDeviceByMac emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByMac(any(), any()) } returns null

        val result = repository.getDeviceByMac("AA:BB:CC:DD", "account1").first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDeviceByPeripheralId emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByPeripheralId(any()) } returns null

        val result = repository.getDeviceByPeripheralId("peripheral1").first()

        assertThat(result).isNull()
    }

    // ── updateDeviceNickname ───────────────────────────────────────────────────

    @Test
    fun `updateDeviceNickname updates dao, calls api, and returns refreshed device`() = runTest {
        val device = Device(id = "device1", nickname = "OldName")
        val updatedApiModel = buildDeviceApiModel()
        val refreshedDetails = buildDeviceDetails()
        val refreshedDevice = Device(id = "device1", nickname = "NewName")
        val editResponse = mockk<Response<DeviceApiModel>>()
        coEvery { deviceDao.updateNickname(any(), any()) } returns 1
        every { any<Device>().toApiModel() } returns updatedApiModel
        every { editResponse.isSuccessful } returns true
        every { editResponse.body() } returns updatedApiModel
        coEvery { deviceApi.editScale("device1", any()) } returns editResponse
        coEvery { deviceDao.getDevice("device1") } returns refreshedDetails
        every { refreshedDetails.toDeviceDomainModel() } returns refreshedDevice

        val result = repository.updateDeviceNickname(device, "NewName")

        coVerify { deviceDao.updateNickname("device1", "NewName") }
        assertThat(result).isEqualTo(refreshedDevice)
    }

    @Test(expected = IllegalStateException::class)
    fun `updateDeviceNickname throws when device not found after update`() = runTest {
        val device = Device(id = "device1")
        val updatedApiModel = buildDeviceApiModel()
        val editResponse = mockk<Response<DeviceApiModel>>()
        coEvery { deviceDao.updateNickname(any(), any()) } returns 1
        every { any<Device>().toApiModel() } returns updatedApiModel
        coEvery { deviceApi.editScale(any(), any()) } returns editResponse
        coEvery { deviceDao.getDevice("device1") } returns null

        repository.updateDeviceNickname(device, "NewName")
    }

    // ── getUnsyncedDevices ─────────────────────────────────────────────────────

    @Test
    fun `getUnsyncedDevices returns empty list when dao returns empty`() = runTest {
        coEvery { deviceDao.getUnsyncedDevicesList() } returns emptyList()

        val result = repository.getUnsyncedDevices()

        assertThat(result).isEmpty()
    }

    // ── markDeviceSynced / markDeviceDeleted ───────────────────────────────────

    @Test
    fun `markDeviceSynced delegates to dao with correct params`() = runTest {
        coEvery { deviceDao.updateSyncStatus(any(), any()) } returns 1
        repository.markDeviceSynced("device1", true)
        coVerify { deviceDao.updateSyncStatus("device1", true) }
    }

    @Test
    fun `markDeviceDeleted delegates to dao with correct params`() = runTest {
        coEvery { deviceDao.updateDeletionStatus(any(), any()) } returns 1
        repository.markDeviceDeleted("device1", true)
        coVerify { deviceDao.updateDeletionStatus("device1", true) }
    }

    // ── getDevicesFromApi ──────────────────────────────────────────────────────

    @Test
    fun `getDevicesFromApi returns mapped devices on success`() = runTest {
        val apiModels = listOf(buildDeviceApiModel())
        val devices = listOf(Device(id = "device1"))
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModels
        coEvery { deviceApi.getPairedScales() } returns response
        every { apiModels.toDomainModels() } returns devices

        val result = repository.getDevicesFromApi("account1")

        assertThat(result).isEqualTo(devices)
    }

    @Test
    fun `getDevicesFromApi returns empty list when api body is null`() = runTest {
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.getPairedScales() } returns response
        every { emptyList<DeviceApiModel>().toDomainModels() } returns emptyList()

        val result = repository.getDevicesFromApi("account1")

        assertThat(result).isEmpty()
    }

    @Test(expected = Exception::class)
    fun `getDevicesFromApi throws on non-success response`() = runTest {
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 500
        coEvery { deviceApi.getPairedScales() } returns response

        repository.getDevicesFromApi("account1")
    }

    // ── saveDeviceToApi ────────────────────────────────────────────────────────

    @Test
    fun `saveDeviceToApi returns mapped device on success`() = runTest {
        val device = Device(id = "device1")
        val apiModel = buildDeviceApiModel()
        val mappedDevice = Device(id = "device1", isSynced = true)
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModel
        coEvery { deviceApi.saveScale(apiModel) } returns response
        every { apiModel.toDomainModel(BLEStatus.DISCONNECTED, null, false) } returns mappedDevice

        val result = repository.saveDeviceToApi(device, "account1")

        assertThat(result).isEqualTo(mappedDevice)
    }

    @Test
    fun `saveDeviceToApi returns original device when body is null`() = runTest {
        val device = Device(id = "device1")
        val apiModel = buildDeviceApiModel()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.saveScale(apiModel) } returns response

        val result = repository.saveDeviceToApi(device, "account1")

        assertThat(result).isEqualTo(device)
    }

    @Test(expected = Exception::class)
    fun `saveDeviceToApi throws on non-success response`() = runTest {
        val device = Device(id = "device1")
        val apiModel = buildDeviceApiModel()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns false
        every { response.code() } returns 400
        coEvery { deviceApi.saveScale(apiModel) } returns response

        repository.saveDeviceToApi(device, "account1")
    }

    // ── deleteDeviceFromApi ────────────────────────────────────────────────────

    @Test
    fun `deleteDeviceFromApi returns true on success`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns true
        coEvery { deviceApi.deleteScale("device1") } returns response

        val result = repository.deleteDeviceFromApi("device1")

        assertThat(result).isTrue()
    }

    @Test(expected = Exception::class)
    fun `deleteDeviceFromApi throws on non-success response`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 404
        every { response.errorBody() } returns null
        coEvery { deviceApi.deleteScale("device1") } returns response

        repository.deleteDeviceFromApi("device1")
    }

    // ── getScaleTokenFromApi ───────────────────────────────────────────────────

    @Test
    fun `getScaleTokenFromApi returns token on success`() = runTest {
        val response = mockk<Response<ScaleTokenResponse>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns ScaleTokenResponse("abc123")
        coEvery { deviceApi.getScaleToken(any()) } returns response

        val result = repository.getScaleTokenFromApi(isR4 = true)

        assertThat(result).isEqualTo("abc123")
        coVerify { deviceApi.getScaleToken("4") }
    }

    @Test(expected = Exception::class)
    fun `getScaleTokenFromApi throws on non-success response`() = runTest {
        val response = mockk<Response<ScaleTokenResponse>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 401
        coEvery { deviceApi.getScaleToken(any()) } returns response

        repository.getScaleTokenFromApi(isR4 = false)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildDeviceDetails(isDeleted: Boolean = false): DeviceDetails {
        val entity = mockk<DeviceEntity>()
        every { entity.isDeleted } returns isDeleted
        val details = mockk<DeviceDetails>()
        every { details.device } returns entity
        return details
    }

    private fun buildDeviceApiModel(): DeviceApiModel = DeviceApiModel(
        id = "device1",
        nickname = "Test Scale",
        type = "A3",
        createdAt = null,
        userNumber = null,
        mac = "AA:BB:CC:DD:EE:FF",
        broadcastId = null,
        password = null,
        sku = null,
        name = "Scale 1",
        scaleToken = null,
        peripheralIdentifier = null,
        preference = null,
        latestVersion = null,
    )
}
