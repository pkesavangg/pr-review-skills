package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeUtil
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException

@OptIn(ExperimentalCoroutinesApi::class)
class UserSettingsServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val userSettingsRepository: IUserSettingsRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: UserSettingsService

    // --- Test Fixtures ---
    private val fakeTimestamp = "2026-03-17 10:00:00.000000+00:00"

    @Before
    fun setUp() {
        mockkObject(DateTimeUtil)
        every { DateTimeUtil.getCurrentTimestamp() } returns fakeTimestamp
        stubNetworkAvailable()
        service = createService()
    }

    @After
    fun tearDown() {
        unmockkObject(DateTimeUtil)
        clearAllMocks()
    }

    private fun createService() = UserSettingsService(
        userSettingsRepository,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
    private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()

    private fun stubUpdateStreakSettingSuccess() {
        coJustRun { userSettingsRepository.updateStreakSetting(any()) }
    }

    private fun stubUpdateStreakSettingOfflineSuccess() {
        coEvery { userSettingsRepository.updateStreakSettingOffline(any()) } returns null
    }

    private fun stubUpdateStreakSettingThrows(exception: Throwable) {
        coEvery { userSettingsRepository.updateStreakSetting(any()) } throws exception
    }

    private fun stubUpdateWeightlessSettingSuccess() {
        coJustRun { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    private fun stubUpdateWeightlessSettingOfflineSuccess() {
        coEvery { userSettingsRepository.updateWeightlessSettingOffline(any()) } returns null
    }

    private fun stubUpdateWeightlessSettingThrows(exception: Throwable) {
        coEvery { userSettingsRepository.updateWeightlessSetting(any()) } throws exception
    }

    // -------------------------------------------------------------------------
    // toggleStreakSetting
    // -------------------------------------------------------------------------

    @Test
    fun `toggleStreakSetting calls online repository when network available and streak on`() = runTest {
        // Arrange
        stubUpdateStreakSettingSuccess()

        // Act
        service.toggleStreakSetting(isStreakOn = true)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateStreakSetting(
                StreakRequest(isStreakOn = true, streakTimestamp = fakeTimestamp)
            )
        }
        coVerify(exactly = 0) { userSettingsRepository.updateStreakSettingOffline(any()) }
    }

    @Test
    fun `toggleStreakSetting calls online repository when network available and streak off`() = runTest {
        // Arrange
        stubUpdateStreakSettingSuccess()

        // Act
        service.toggleStreakSetting(isStreakOn = false)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateStreakSetting(
                StreakRequest(isStreakOn = false, streakTimestamp = fakeTimestamp)
            )
        }
    }

    @Test
    fun `toggleStreakSetting calls offline repository when network unavailable`() = runTest {
        // Arrange
        stubNetworkUnavailable()
        stubUpdateStreakSettingOfflineSuccess()

        // Act
        service.toggleStreakSetting(isStreakOn = true)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateStreakSettingOffline(
                StreakRequest(isStreakOn = true, streakTimestamp = fakeTimestamp)
            )
        }
        coVerify(exactly = 0) { userSettingsRepository.updateStreakSetting(any()) }
    }

    @Test
    fun `toggleStreakSetting rethrows HttpException from online call`() {
        // Arrange
        stubUpdateStreakSettingThrows(httpException(500))

        // Act & Assert
        assertThrows(HttpException::class.java) {
            runBlocking { service.toggleStreakSetting(isStreakOn = true) }
        }
    }

    @Test
    fun `toggleStreakSetting rethrows RuntimeException from online call`() {
        // Arrange
        stubUpdateStreakSettingThrows(RuntimeException("DB error"))

        // Act & Assert
        assertThrows(RuntimeException::class.java) {
            runBlocking { service.toggleStreakSetting(isStreakOn = true) }
        }
    }

    @Test
    fun `toggleStreakSetting rethrows exception from offline call`() {
        // Arrange
        stubNetworkUnavailable()
        coEvery { userSettingsRepository.updateStreakSettingOffline(any()) } throws RuntimeException("DB write failed")

        // Act & Assert
        assertThrows(RuntimeException::class.java) {
            runBlocking { service.toggleStreakSetting(isStreakOn = false) }
        }
    }

    // -------------------------------------------------------------------------
    // toggleWeightlessSetting
    // -------------------------------------------------------------------------

    @Test
    fun `toggleWeightlessSetting calls online repository when network available and weightless on`() = runTest {
        // Arrange
        stubUpdateWeightlessSettingSuccess()

        // Act
        service.toggleWeightlessSetting(isWeightlessOn = true, weightlessWeight = 150.0)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateWeightlessSetting(
                WeightlessRequest(
                    isWeightlessOn = true,
                    weightlessTimestamp = fakeTimestamp,
                    weightlessWeight = 150.0,
                )
            )
        }
        coVerify(exactly = 0) { userSettingsRepository.updateWeightlessSettingOffline(any()) }
    }

    @Test
    fun `toggleWeightlessSetting nulls timestamp and weight when weightless off`() = runTest {
        // Arrange
        stubUpdateWeightlessSettingSuccess()

        // Act
        service.toggleWeightlessSetting(isWeightlessOn = false, weightlessWeight = 150.0)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateWeightlessSetting(
                WeightlessRequest(
                    isWeightlessOn = false,
                    weightlessTimestamp = null,
                    weightlessWeight = null,
                )
            )
        }
    }

    @Test
    fun `toggleWeightlessSetting passes null weight when weightless on and weight is null`() = runTest {
        // Arrange
        stubUpdateWeightlessSettingSuccess()

        // Act
        service.toggleWeightlessSetting(isWeightlessOn = true, weightlessWeight = null)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateWeightlessSetting(
                WeightlessRequest(
                    isWeightlessOn = true,
                    weightlessTimestamp = fakeTimestamp,
                    weightlessWeight = null,
                )
            )
        }
    }

    @Test
    fun `toggleWeightlessSetting calls offline repository when network unavailable`() = runTest {
        // Arrange
        stubNetworkUnavailable()
        stubUpdateWeightlessSettingOfflineSuccess()

        // Act
        service.toggleWeightlessSetting(isWeightlessOn = true, weightlessWeight = 200.0)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateWeightlessSettingOffline(
                WeightlessRequest(
                    isWeightlessOn = true,
                    weightlessTimestamp = fakeTimestamp,
                    weightlessWeight = 200.0,
                )
            )
        }
        coVerify(exactly = 0) { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    @Test
    fun `toggleWeightlessSetting offline with weightless off nulls timestamp and weight`() = runTest {
        // Arrange
        stubNetworkUnavailable()
        stubUpdateWeightlessSettingOfflineSuccess()

        // Act
        service.toggleWeightlessSetting(isWeightlessOn = false, weightlessWeight = 100.0)

        // Assert
        coVerify(exactly = 1) {
            userSettingsRepository.updateWeightlessSettingOffline(
                WeightlessRequest(
                    isWeightlessOn = false,
                    weightlessTimestamp = null,
                    weightlessWeight = null,
                )
            )
        }
    }

    @Test
    fun `toggleWeightlessSetting rethrows HttpException from online call`() {
        // Arrange
        stubUpdateWeightlessSettingThrows(httpException(500))

        // Act & Assert
        assertThrows(HttpException::class.java) {
            runBlocking { service.toggleWeightlessSetting(isWeightlessOn = true, weightlessWeight = 150.0) }
        }
    }

    @Test
    fun `toggleWeightlessSetting rethrows RuntimeException from online call`() {
        // Arrange
        stubUpdateWeightlessSettingThrows(RuntimeException("Server error"))

        // Act & Assert
        assertThrows(RuntimeException::class.java) {
            runBlocking { service.toggleWeightlessSetting(isWeightlessOn = false) }
        }
    }

    @Test
    fun `toggleWeightlessSetting rethrows exception from offline call`() {
        // Arrange
        stubNetworkUnavailable()
        coEvery { userSettingsRepository.updateWeightlessSettingOffline(any()) } throws RuntimeException("DB write failed")

        // Act & Assert
        assertThrows(RuntimeException::class.java) {
            runBlocking { service.toggleWeightlessSetting(isWeightlessOn = true, weightlessWeight = 75.0) }
        }
    }
}
