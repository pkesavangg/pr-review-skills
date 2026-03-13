package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IDeviceInfoAPI
import com.dmdbrands.gurus.weight.domain.model.common.DeviceInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class DeviceInfoRepositoryTest {

    companion object {
        private const val FCM_TOKEN_DEFAULT = "fcm-token-abc"
        private const val FCM_TOKEN_123 = "test-fcm-token-123"
        private const val DEVICE_UUID = "device-uuid-123"
    }

    @MockK(relaxUnitFun = true)
    private lateinit var deviceApi: IDeviceInfoAPI

    private lateinit var repository: DeviceInfoRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = DeviceInfoRepository(deviceApi)
    }

    // ── updateDeviceInfo ───────────────────────────────────────────────────────

    @Test
    fun `updateDeviceInfo returns success response from api`() = runTest {
        val deviceInfo = buildDeviceInfo()
        val response = Response.success<Unit>(null)
        coEvery { deviceApi.updateDeviceInfo(deviceInfo) } returns response

        val result = repository.updateDeviceInfo(deviceInfo)

        assertThat(result.isSuccessful).isTrue()
    }

    @Test
    fun `updateDeviceInfo calls api with correct device info`() = runTest {
        val deviceInfo = buildDeviceInfo()
        coEvery { deviceApi.updateDeviceInfo(any()) } returns Response.success<Unit>(null)

        repository.updateDeviceInfo(deviceInfo)

        coVerify { deviceApi.updateDeviceInfo(deviceInfo) }
    }

    @Test
    fun `updateDeviceInfo returns error response when api returns 400`() = runTest {
        val deviceInfo = buildDeviceInfo()
        val errorResponse = Response.error<Unit>(400, "Bad Request".toResponseBody())
        coEvery { deviceApi.updateDeviceInfo(any()) } returns errorResponse

        val result = repository.updateDeviceInfo(deviceInfo)

        assertThat(result.isSuccessful).isFalse()
        assertThat(result.code()).isEqualTo(400)
    }

    @Test
    fun `updateDeviceInfo returns error response when api returns 401`() = runTest {
        val deviceInfo = buildDeviceInfo()
        val errorResponse = Response.error<Unit>(401, "Unauthorized".toResponseBody())
        coEvery { deviceApi.updateDeviceInfo(any()) } returns errorResponse

        val result = repository.updateDeviceInfo(deviceInfo)

        assertThat(result.isSuccessful).isFalse()
        assertThat(result.code()).isEqualTo(401)
    }

    @Test
    fun `updateDeviceInfo returns error response when api returns 500`() = runTest {
        val deviceInfo = buildDeviceInfo()
        val errorResponse = Response.error<Unit>(500, "Server Error".toResponseBody())
        coEvery { deviceApi.updateDeviceInfo(any()) } returns errorResponse

        val result = repository.updateDeviceInfo(deviceInfo)

        assertThat(result.isSuccessful).isFalse()
        assertThat(result.code()).isEqualTo(500)
    }

    @Test(expected = IOException::class)
    fun `updateDeviceInfo propagates IOException from api`() = runTest {
        coEvery { deviceApi.updateDeviceInfo(any()) } throws IOException("No internet")

        repository.updateDeviceInfo(buildDeviceInfo())
    }

    @Test(expected = RuntimeException::class)
    fun `updateDeviceInfo propagates RuntimeException from api`() = runTest {
        coEvery { deviceApi.updateDeviceInfo(any()) } throws RuntimeException("Unexpected error")

        repository.updateDeviceInfo(buildDeviceInfo())
    }

    @Test
    fun `updateDeviceInfo passes fcm token correctly`() = runTest {
        val deviceInfo = buildDeviceInfo(fcmToken = FCM_TOKEN_123)
        coEvery { deviceApi.updateDeviceInfo(any()) } returns Response.success<Unit>(null)

        repository.updateDeviceInfo(deviceInfo)

        coVerify { deviceApi.updateDeviceInfo(match { it.fcmToken == FCM_TOKEN_123 }) }
    }

    @Test
    fun `updateDeviceInfo works when fcm token is null`() = runTest {
        val deviceInfo = buildDeviceInfo(fcmToken = null)
        coEvery { deviceApi.updateDeviceInfo(any()) } returns Response.success<Unit>(null)

        val result = repository.updateDeviceInfo(deviceInfo)

        assertThat(result.isSuccessful).isTrue()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildDeviceInfo(fcmToken: String? = FCM_TOKEN_DEFAULT) = DeviceInfo(
        appVersion = "1.0.0",
        deviceManufacturer = "Google",
        deviceOSName = "Android",
        deviceOSVersion = "14",
        deviceUUID = DEVICE_UUID,
        deviceModel = "Pixel 8",
        fcmToken = fcmToken,
    )
}
