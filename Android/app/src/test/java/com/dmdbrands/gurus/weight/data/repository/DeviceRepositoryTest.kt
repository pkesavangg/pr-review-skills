package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IDeviceAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiException
import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.PairedDeviceRequest
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleMetaDataApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleTokenResponse
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toDomainModels
import com.dmdbrands.gurus.weight.domain.model.api.device.toApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.toPairedDeviceRequest
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
        every { deviceDao.getDevices(ACCOUNT_ID) } returns flowOf(emptyList())

        val result = repository.getDevices(ACCOUNT_ID, filterDeleted = false).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getDevices with filterDeleted true excludes deleted devices`() = runTest {
        val deletedDetails = buildDeviceDetails(isDeleted = true)
        val activeDetails = buildDeviceDetails(isDeleted = false)
        val activeDevice = Device(id = "active-device")
        every { deviceDao.getDevices(ACCOUNT_ID) } returns flowOf(listOf(deletedDetails, activeDetails))
        every { activeDetails.toDeviceDomainModel() } returns activeDevice

        val result = repository.getDevices(ACCOUNT_ID, filterDeleted = true).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("active-device")
    }

    @Test
    fun `getDevices with filterDeleted false includes deleted devices`() = runTest {
        val deletedDetails = buildDeviceDetails(isDeleted = true)
        val activeDetails = buildDeviceDetails(isDeleted = false)
        val deletedDevice = Device(id = "deleted-device")
        val activeDevice = Device(id = "active-device")
        every { deviceDao.getDevices(ACCOUNT_ID) } returns flowOf(listOf(deletedDetails, activeDetails))
        every { deletedDetails.toDeviceDomainModel() } returns deletedDevice
        every { activeDetails.toDeviceDomainModel() } returns activeDevice

        val result = repository.getDevices(ACCOUNT_ID, filterDeleted = false).first()

        assertThat(result).hasSize(2)
    }

    // ── getDevice ──────────────────────────────────────────────────────────────

    @Test
    fun `getDevice emits null when dao returns null`() = runTest {
        coEvery { deviceDao.getDevice(DEVICE_ID) } returns null

        val result = repository.getDevice(DEVICE_ID).first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDevice emits mapped device when dao returns details`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = DEVICE_ID)
        coEvery { deviceDao.getDevice(DEVICE_ID) } returns details
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getDevice(DEVICE_ID).first()

        assertThat(result).isEqualTo(device)
    }

    // ── updateDevice ───────────────────────────────────────────────────────────

    @Test
    fun `updateDevice converts to DeviceDetails and delegates to dao`() = runTest {
        val device = Device(id = DEVICE_ID)
        val details = buildDeviceDetails()
        every { any<Device>().toDeviceDetails(any()) } returns details

        repository.updateDevice(device, ACCOUNT_ID)

        coVerify { deviceDao.updateDevice(details) }
    }

    // ── saveDeviceToDb ─────────────────────────────────────────────────────────

    @Test
    fun `saveDeviceToDb converts to DeviceDetails and delegates to dao`() = runTest {
        val device = Device(id = DEVICE_ID)
        val details = buildDeviceDetails()
        every { any<Device>().toDeviceDetails(any()) } returns details

        repository.saveDeviceToDb(device, ACCOUNT_ID)

        coVerify { deviceDao.insertDevice(details) }
    }

    // ── deleteAllDevicesForAccount ─────────────────────────────────────────────

    @Test
    fun `deleteAllDevicesForAccount delegates to dao`() = runTest {
        coEvery { deviceDao.deleteAllDevicesForAccount(any()) } returns 1
        repository.deleteAllDevicesForAccount(ACCOUNT_ID)
        coVerify { deviceDao.deleteAllDevicesForAccount(ACCOUNT_ID) }
    }

    // ── deleteDeviceFromDb ─────────────────────────────────────────────────────

    @Test
    fun `deleteDeviceFromDb delegates to dao`() = runTest {
        repository.deleteDeviceFromDb(DEVICE_ID)
        coVerify { deviceDao.deleteDevice(DEVICE_ID) }
    }

    // ── deviceExistsBy* ────────────────────────────────────────────────────────

    @Test
    fun `deviceExistsByBroadcastId emits true when device found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastId(BROADCAST_ID, ACCOUNT_ID) } returns buildDeviceDetails()

        val result = repository.deviceExistsByBroadcastId(BROADCAST_ID, ACCOUNT_ID).first()

        assertThat(result).isTrue()
    }

    @Test
    fun `deviceExistsByBroadcastId emits false when device not found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastId(BROADCAST_ID, ACCOUNT_ID) } returns null

        val result = repository.deviceExistsByBroadcastId(BROADCAST_ID, ACCOUNT_ID).first()

        assertThat(result).isFalse()
    }

    @Test
    fun `deviceExistsByMac emits true when device found`() = runTest {
        coEvery { deviceDao.getDeviceByMac(DEVICE_MAC, ACCOUNT_ID) } returns buildDeviceDetails()

        val result = repository.deviceExistsByMac(DEVICE_MAC, ACCOUNT_ID).first()

        assertThat(result).isTrue()
    }

    @Test
    fun `deviceExistsByPeripheralId emits false when device not found`() = runTest {
        coEvery { deviceDao.getDeviceByPeripheralId(PERIPHERAL_ID) } returns null

        val result = repository.deviceExistsByPeripheralId(PERIPHERAL_ID).first()

        assertThat(result).isFalse()
    }

    // ── getDeviceBy* ───────────────────────────────────────────────────────────

    @Test
    fun `getDeviceByBroadcastId emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByBroadcastIdString(any(), any()) } returns null

        val result = repository.getDeviceByBroadcastId(BROADCAST_ID, ACCOUNT_ID).first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDeviceByMac emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByMac(any(), any()) } returns null

        val result = repository.getDeviceByMac(DEVICE_MAC, ACCOUNT_ID).first()

        assertThat(result).isNull()
    }

    @Test
    fun `getDeviceByPeripheralId emits null when not found`() = runTest {
        coEvery { deviceDao.getDeviceByPeripheralId(any()) } returns null

        val result = repository.getDeviceByPeripheralId(PERIPHERAL_ID).first()

        assertThat(result).isNull()
    }

    // ── updateDeviceNickname ───────────────────────────────────────────────────

    @Test
    fun `updateDeviceNickname updates dao, calls api, and returns refreshed device`() = runTest {
        val device = Device(id = DEVICE_ID, nickname = "OldName")
        val updatedApiModel = buildDeviceApiModel()
        val refreshedDetails = buildDeviceDetails()
        val refreshedDevice = Device(id = DEVICE_ID, nickname = "NewName")
        val editResponse = mockk<Response<DeviceApiModel>>()
        coEvery { deviceDao.updateNickname(any(), any()) } returns 1
        every { any<Device>().toApiModel() } returns updatedApiModel
        every { editResponse.isSuccessful } returns true
        every { editResponse.body() } returns updatedApiModel
        coEvery { deviceApi.editScale(DEVICE_ID, any()) } returns editResponse
        coEvery { deviceDao.getDevice(DEVICE_ID) } returns refreshedDetails
        every { refreshedDetails.toDeviceDomainModel() } returns refreshedDevice

        val result = repository.updateDeviceNickname(device, "NewName")

        coVerify { deviceDao.updateNickname(DEVICE_ID, "NewName") }
        assertThat(result).isEqualTo(refreshedDevice)
    }

    @Test(expected = IllegalStateException::class)
    fun `updateDeviceNickname throws when device not found after update`() = runTest {
        val device = Device(id = DEVICE_ID)
        val updatedApiModel = buildDeviceApiModel()
        val editResponse = mockk<Response<DeviceApiModel>>()
        coEvery { deviceDao.updateNickname(any(), any()) } returns 1
        every { any<Device>().toApiModel() } returns updatedApiModel
        coEvery { deviceApi.editScale(any(), any()) } returns editResponse
        coEvery { deviceDao.getDevice(DEVICE_ID) } returns null

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
        repository.markDeviceSynced(DEVICE_ID, true)
        coVerify { deviceDao.updateSyncStatus(DEVICE_ID, true) }
    }

    @Test
    fun `markDeviceDeleted delegates to dao with correct params`() = runTest {
        coEvery { deviceDao.updateDeletionStatus(any(), any()) } returns 1
        repository.markDeviceDeleted(DEVICE_ID, true)
        coVerify { deviceDao.updateDeletionStatus(DEVICE_ID, true) }
    }

    // ── getDevicesFromApi ──────────────────────────────────────────────────────

    @Test
    fun `getDevicesFromApi returns mapped devices on success`() = runTest {
        val apiModels = listOf(buildDeviceApiModel())
        val devices = listOf(Device(id = DEVICE_ID))
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModels
        coEvery { deviceApi.getPairedScales() } returns response
        every { apiModels.toDomainModels() } returns devices

        val result = repository.getDevicesFromApi(ACCOUNT_ID)

        assertThat(result).isEqualTo(devices)
    }

    @Test
    fun `getDevicesFromApi returns empty list when api body is null`() = runTest {
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.getPairedScales() } returns response
        every { emptyList<DeviceApiModel>().toDomainModels() } returns emptyList()

        val result = repository.getDevicesFromApi(ACCOUNT_ID)

        assertThat(result).isEmpty()
    }

    @Test(expected = Exception::class)
    fun `getDevicesFromApi throws on non-success response`() = runTest {
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 500
        coEvery { deviceApi.getPairedScales() } returns response

        repository.getDevicesFromApi(ACCOUNT_ID)
    }

    // ── saveDeviceToApi ────────────────────────────────────────────────────────

    @Test
    fun `saveDeviceToApi returns mapped device on success`() = runTest {
        val device = Device(id = DEVICE_ID)
        val apiModel = buildDeviceApiModel()
        val mappedDevice = Device(id = DEVICE_ID, isSynced = true)
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModel
        coEvery { deviceApi.saveScale(apiModel) } returns response
        every { apiModel.toDomainModel(BLEStatus.DISCONNECTED, null, false) } returns mappedDevice

        val result = repository.saveDeviceToApi(device, ACCOUNT_ID)

        assertThat(result).isEqualTo(mappedDevice)
    }

    @Test
    fun `saveDeviceToApi returns original device when body is null`() = runTest {
        val device = Device(id = DEVICE_ID)
        val apiModel = buildDeviceApiModel()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.saveScale(apiModel) } returns response

        val result = repository.saveDeviceToApi(device, ACCOUNT_ID)

        assertThat(result).isEqualTo(device)
    }

    @Test(expected = Exception::class)
    fun `saveDeviceToApi throws on non-success response`() = runTest {
        val device = Device(id = DEVICE_ID)
        val apiModel = buildDeviceApiModel()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toApiModel() } returns apiModel
        every { response.isSuccessful } returns false
        every { response.code() } returns 400
        coEvery { deviceApi.saveScale(apiModel) } returns response

        repository.saveDeviceToApi(device, ACCOUNT_ID)
    }

    // ── deleteDeviceFromApi ────────────────────────────────────────────────────

    @Test
    fun `deleteDeviceFromApi returns true on success`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns true
        coEvery { deviceApi.deleteScale(DEVICE_ID) } returns response

        val result = repository.deleteDeviceFromApi(DEVICE_ID)

        assertThat(result).isTrue()
    }

    @Test(expected = Exception::class)
    fun `deleteDeviceFromApi throws on non-success response`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 404
        every { response.errorBody() } returns null
        coEvery { deviceApi.deleteScale(DEVICE_ID) } returns response

        repository.deleteDeviceFromApi(DEVICE_ID)
    }

    // ── Unified /v3/paired-device/ (MOB-378) ──────────────────────────────────

    @Test
    fun `createPairedDevice returns mapped device on success`() = runTest {
        val device = Device(id = DEVICE_ID)
        val request = mockk<PairedDeviceRequest>()
        val apiModel = buildDeviceApiModel()
        val mapped = Device(id = DEVICE_ID, isSynced = true)
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toPairedDeviceRequest() } returns request
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModel
        coEvery { deviceApi.createPairedDevice(request) } returns response
        every { apiModel.toDomainModel(BLEStatus.DISCONNECTED, null, false) } returns mapped

        val result = repository.createPairedDevice(device, ACCOUNT_ID)

        assertThat(result).isEqualTo(mapped)
    }

    @Test
    fun `createPairedDevice throws DeviceApiException carrying the http code on failure`() = runTest {
        val device = Device(id = DEVICE_ID)
        val request = mockk<PairedDeviceRequest>()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toPairedDeviceRequest() } returns request
        every { response.isSuccessful } returns false
        every { response.code() } returns 409
        coEvery { deviceApi.createPairedDevice(request) } returns response

        val error = runCatching { repository.createPairedDevice(device, ACCOUNT_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DeviceApiException::class.java)
        assertThat((error as DeviceApiException).code).isEqualTo(409)
    }

    @Test
    fun `getPairedDevices returns mapped devices on success`() = runTest {
        val apiModels = listOf(buildDeviceApiModel())
        val devices = listOf(Device(id = DEVICE_ID))
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModels
        coEvery { deviceApi.getPairedDevices(any()) } returns response
        every { apiModels.toDomainModels() } returns devices

        val result = repository.getPairedDevices(deviceType = null)

        assertThat(result).isEqualTo(devices)
    }

    @Test
    fun `getPairedDevices throws DeviceApiException carrying the http code on failure`() = runTest {
        val response = mockk<Response<List<DeviceApiModel>>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 500
        coEvery { deviceApi.getPairedDevices(any()) } returns response

        val error = runCatching { repository.getPairedDevices(deviceType = null) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DeviceApiException::class.java)
        assertThat((error as DeviceApiException).code).isEqualTo(500)
    }

    @Test
    fun `updatePairedDevice returns mapped device on success`() = runTest {
        val device = Device(id = DEVICE_ID)
        val request = mockk<PairedDeviceRequest>()
        val apiModel = buildDeviceApiModel()
        val mapped = Device(id = DEVICE_ID, isSynced = true)
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toPairedDeviceRequest() } returns request
        every { response.isSuccessful } returns true
        every { response.body() } returns apiModel
        coEvery { deviceApi.updatePairedDevice(DEVICE_ID, request) } returns response
        every { apiModel.toDomainModel(BLEStatus.DISCONNECTED, null, false) } returns mapped

        val result = repository.updatePairedDevice(DEVICE_ID, device, ACCOUNT_ID)

        assertThat(result).isEqualTo(mapped)
    }

    @Test
    fun `updatePairedDevice throws DeviceApiException carrying the http code on failure`() = runTest {
        val device = Device(id = DEVICE_ID)
        val request = mockk<PairedDeviceRequest>()
        val response = mockk<Response<DeviceApiModel>>()
        every { device.toPairedDeviceRequest() } returns request
        every { response.isSuccessful } returns false
        every { response.code() } returns 401
        coEvery { deviceApi.updatePairedDevice(DEVICE_ID, request) } returns response

        val error = runCatching { repository.updatePairedDevice(DEVICE_ID, device, ACCOUNT_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DeviceApiException::class.java)
        assertThat((error as DeviceApiException).code).isEqualTo(401)
    }

    @Test
    fun `deletePairedDevice returns true on success`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns true
        coEvery { deviceApi.deletePairedDevice(DEVICE_ID) } returns response

        val result = repository.deletePairedDevice(DEVICE_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `deletePairedDevice throws DeviceApiException carrying the http code on failure`() = runTest {
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 404
        coEvery { deviceApi.deletePairedDevice(DEVICE_ID) } returns response

        val error = runCatching { repository.deletePairedDevice(DEVICE_ID) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DeviceApiException::class.java)
        assertThat((error as DeviceApiException).code).isEqualTo(404)
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

    // ── deviceExistsByMac (false) / deviceExistsByPeripheralId (true) ──────────

    @Test
    fun `deviceExistsByMac emits false when device not found`() = runTest {
        coEvery { deviceDao.getDeviceByMac(DEVICE_MAC, ACCOUNT_ID) } returns null

        val result = repository.deviceExistsByMac(DEVICE_MAC, ACCOUNT_ID).first()

        assertThat(result).isFalse()
    }

    @Test
    fun `deviceExistsByPeripheralId emits true when device found`() = runTest {
        coEvery { deviceDao.getDeviceByPeripheralId(PERIPHERAL_ID) } returns buildDeviceDetails()

        val result = repository.deviceExistsByPeripheralId(PERIPHERAL_ID).first()

        assertThat(result).isTrue()
    }

    // ── getDeviceBy* (non-null) ────────────────────────────────────────────────

    @Test
    fun `getDeviceByBroadcastId emits mapped device when found`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = DEVICE_ID)
        coEvery { deviceDao.getDeviceByBroadcastIdString(BROADCAST_ID, ACCOUNT_ID) } returns details
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getDeviceByBroadcastId(BROADCAST_ID, ACCOUNT_ID).first()

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getDeviceByMac emits mapped device when found`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = DEVICE_ID)
        coEvery { deviceDao.getDeviceByMac(DEVICE_MAC, ACCOUNT_ID) } returns details
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getDeviceByMac(DEVICE_MAC, ACCOUNT_ID).first()

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getDeviceByPeripheralId emits mapped device when found`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = DEVICE_ID)
        coEvery { deviceDao.getDeviceByPeripheralId(PERIPHERAL_ID) } returns details
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getDeviceByPeripheralId(PERIPHERAL_ID).first()

        assertThat(result).isEqualTo(device)
    }

    // ── getUnsyncedDevices (with entries) ──────────────────────────────────────

    @Test
    fun `getUnsyncedDevices returns mapped devices from dao`() = runTest {
        val details = buildDeviceDetails()
        val device = Device(id = DEVICE_ID)
        coEvery { deviceDao.getUnsyncedDevicesList() } returns listOf(details)
        every { details.toDeviceDomainModel() } returns device

        val result = repository.getUnsyncedDevices()

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(device)
    }

    // ── getScaleTokenFromApi (null token) ──────────────────────────────────────

    @Test
    fun `getScaleTokenFromApi isR4 false passes null param to api`() = runTest {
        val response = mockk<Response<ScaleTokenResponse>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns ScaleTokenResponse("xyz789")
        coEvery { deviceApi.getScaleToken(null) } returns response

        val result = repository.getScaleTokenFromApi(isR4 = false)

        assertThat(result).isEqualTo("xyz789")
        coVerify { deviceApi.getScaleToken(null) }
    }

    @Test
    fun `getScaleTokenFromApi throws when token in body is null`() = runTest {
        val response = mockk<Response<ScaleTokenResponse>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.getScaleToken(any()) } returns response

        try {
            repository.getScaleTokenFromApi(isR4 = true)
            error("Expected exception not thrown")
        } catch (e: Exception) {
            assertThat(e.message).contains("Token response is null")
        }
    }

    // ── saveScalePreferencesToApi ──────────────────────────────────────────────

    @Test
    fun `saveScalePreferencesToApi returns body on success`() = runTest {
        val preferences = buildScalePreferences()
        val response = mockk<Response<R4ScalePreferenceApiModel>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns preferences
        coEvery { deviceApi.saveScalePreferences(preferences) } returns response

        val result = repository.saveScalePreferencesToApi(preferences)

        assertThat(result).isEqualTo(preferences)
    }

    @Test
    fun `saveScalePreferencesToApi returns original preferences when body is null`() = runTest {
        val preferences = buildScalePreferences()
        val response = mockk<Response<R4ScalePreferenceApiModel>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns null
        coEvery { deviceApi.saveScalePreferences(preferences) } returns response

        val result = repository.saveScalePreferencesToApi(preferences)

        assertThat(result).isEqualTo(preferences)
    }

    @Test
    fun `saveScalePreferencesToApi throws on non-success response`() = runTest {
        val preferences = buildScalePreferences()
        val response = mockk<Response<R4ScalePreferenceApiModel>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 500
        coEvery { deviceApi.saveScalePreferences(preferences) } returns response

        try {
            repository.saveScalePreferencesToApi(preferences)
            error("Expected exception not thrown")
        } catch (e: Exception) {
            assertThat(e.message).contains("500")
        }
    }

    // ── saveScaleMetaDataToApi ─────────────────────────────────────────────────

    @Test
    fun `saveScaleMetaDataToApi returns true on success`() = runTest {
        val metaData = buildScaleMetaData()
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns true
        coEvery { deviceApi.updateScaleMetadata(DEVICE_ID, metaData) } returns response

        val result = repository.saveScaleMetaDataToApi(DEVICE_ID, metaData)

        assertThat(result).isTrue()
    }

    @Test
    fun `saveScaleMetaDataToApi throws on non-success response`() = runTest {
        val metaData = buildScaleMetaData()
        val response = mockk<Response<Unit>>()
        every { response.isSuccessful } returns false
        every { response.code() } returns 400
        coEvery { deviceApi.updateScaleMetadata(DEVICE_ID, metaData) } returns response

        try {
            repository.saveScaleMetaDataToApi(DEVICE_ID, metaData)
            error("Expected exception not thrown")
        } catch (e: Exception) {
            assertThat(e.message).contains("400")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    companion object {
        private const val ACCOUNT_ID = "account1"
        private const val DEVICE_ID = "device1"
        private const val DEVICE_MAC = "AA:BB:CC:DD"
        private const val BROADCAST_ID = "broadcast1"
        private const val PERIPHERAL_ID = "peripheral1"
    }

    private fun buildScalePreferences() = R4ScalePreferenceApiModel(
        scaleId = DEVICE_ID,
        displayName = null,
        displayMetrics = null,
        shouldFactoryReset = false,
        shouldMeasureImpedance = true,
        shouldMeasurePulse = false,
        timeFormat = null,
        tzOffset = null,
        wifiFotaScheduleTime = null,
    )

    private fun buildScaleMetaData() = ScaleMetaDataApiModel(
        modelNumber = null,
        serialNumber = null,
        firmwareRevision = "1.0.0",
        hardwareRevision = "rev1",
        softwareRevision = null,
        manufacturerName = null,
        systemId = null,
        latestVersion = null,
    )

    private fun buildDeviceDetails(isDeleted: Boolean = false): DeviceDetails {
        val entity = mockk<DeviceEntity>()
        every { entity.isDeleted } returns isDeleted
        val details = mockk<DeviceDetails>()
        every { details.device } returns entity
        return details
    }

    private fun buildDeviceApiModel(): DeviceApiModel = DeviceApiModel(
        id = DEVICE_ID,
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
