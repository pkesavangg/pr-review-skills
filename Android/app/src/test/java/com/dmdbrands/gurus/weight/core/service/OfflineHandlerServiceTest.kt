package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHandlerServiceTest {

    // --- Mocks ---
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val bodyCompositionRepository: IBodyCompositionRepository = mockk(relaxed = true)
    private val deviceService: IDeviceService = mockk(relaxed = true)
    private val notificationRepository: INotificationRepository = mockk(relaxed = true)
    private val userSettingsRepository: IUserSettingsRepository = mockk(relaxed = true)
    private val goalRepository: IGoalRepository = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: OfflineHandlerService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"

    private val fakeAccount = Account(
        id = testAccountId,
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "john@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1700,
        activityLevel = "normal",
        isActiveAccount = true,
    )

    private val fakeAccountInfo = AccountInfo(
        id = testAccountId,
        email = "john@example.com",
        firstName = "John",
        lastName = "Doe",
        gender = "male",
        zipcode = "12345",
        weightUnit = "lb",
        isWeightlessOn = false,
        height = 1700,
        activityLevel = "normal",
        dob = "1990-01-01",
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
        dashboardMetrics = listOf("weight", "bmi"),
        goalWeight = 160f,
        initialWeight = 180f,
        shouldSendEntryNotifications = true,
        shouldSendWeightInEntryNotifications = false,
    )

    private val fakeAccountResponse = AccountResponse(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAt = "2099-01-01",
        account = fakeAccountInfo,
    )

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.w(any(), any()) } returns Unit
        every { AppLog.i(any(), any()) } returns Unit

        connectivityObserver.stubNetworkAvailable()

        // Default stubs — nothing unsynced
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns null
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns null
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns null
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns null
        coEvery { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() } returns null
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns null
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns null

        service = OfflineHandlerService(
            accountRepository = accountRepository,
            bodyCompositionRepository = bodyCompositionRepository,
            deviceService = deviceService,
            notificationRepository = notificationRepository,
            userSettingsRepository = userSettingsRepository,
            goalRepository = goalRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — network unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync returns early when network is unavailable`() = runTest {
        connectivityObserver.stubNetworkUnavailable()

        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.getUnsyncedActiveAccount() }
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — nothing to sync
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync completes when nothing to sync`() = runTest {
        service.handleOfflineSync()

        coVerify { accountRepository.getUnsyncedActiveAccount() }
        coVerify { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() }
        coVerify { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() }
        coVerify { goalRepository.getUnsyncedActiveGoalAccountFromDB() }
        coVerify { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() }
        coVerify { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() }
        coVerify { accountRepository.getUnsyncedActiveDashboardSettings() }
    }

    // -------------------------------------------------------------------------
    // syncProfileData — unsynced account
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs profile when unsynced account exists`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns fakeAccount

        service.handleOfflineSync()

        coVerify { accountRepository.updateProfile(any()) }
    }

    @Test
    fun `handleOfflineSync skips profile sync when no unsynced account`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns null

        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.updateProfile(any()) }
    }

    @Test
    fun `handleOfflineSync handles profile sync exception`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns fakeAccount
        coEvery { accountRepository.updateProfile(any()) } throws RuntimeException("Network error")

        service.handleOfflineSync()

        // Should continue with other syncs, not crash
        coVerify { deviceService.syncDevices() }
    }

    // -------------------------------------------------------------------------
    // syncBodyCompositionData — unsynced body comp
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs body composition when unsynced data exists`() = runTest {
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns fakeAccount
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } returns fakeAccountResponse

        service.handleOfflineSync()

        coVerify { bodyCompositionRepository.updateBodyCompInAPI(any()) }
        coVerify { bodyCompositionRepository.updateBodyCompInDB(any(), any()) }
    }

    @Test
    fun `handleOfflineSync skips body composition sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
    }

    @Test
    fun `handleOfflineSync handles body composition sync exception`() = runTest {
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns fakeAccount
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } throws RuntimeException("API error")

        service.handleOfflineSync()

        // Should continue with other syncs
        coVerify { deviceService.syncDevices() }
    }

    // -------------------------------------------------------------------------
    // syncDeviceData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync always syncs devices`() = runTest {
        service.handleOfflineSync()

        coVerify { deviceService.syncDevices() }
    }

    @Test
    fun `handleOfflineSync handles device sync exception`() = runTest {
        coEvery { deviceService.syncDevices() } throws RuntimeException("Device error")

        service.handleOfflineSync()

        // Should continue with other syncs
        coVerify { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() }
    }

    // -------------------------------------------------------------------------
    // syncNotificationData — unsynced notifications
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs notification settings when unsynced data exists`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { shouldSendEntryNotifications } returns true
            every { shouldSendWeightInEntryNotifications } returns false
        }
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns unsyncedAccount
        coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } returns fakeAccountResponse

        service.handleOfflineSync()

        coVerify { notificationRepository.updateNotificationSettingsInAPI(any()) }
        coVerify { notificationRepository.updateNotificationSettingsInDB(any(), any()) }
    }

    @Test
    fun `handleOfflineSync skips notification sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { notificationRepository.updateNotificationSettingsInAPI(any()) }
    }

    @Test
    fun `handleOfflineSync handles notification sync exception`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { shouldSendEntryNotifications } returns true
            every { shouldSendWeightInEntryNotifications } returns false
        }
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns unsyncedAccount
        coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } throws RuntimeException("API error")

        service.handleOfflineSync()

        // Should continue
        coVerify { goalRepository.getUnsyncedActiveGoalAccountFromDB() }
    }

    // -------------------------------------------------------------------------
    // syncGoalData — unsynced goal
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs goal data when unsynced data exists`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { goalWeight } returns 1600.0
            every { initialWeight } returns 1800.0
            every { goalType } returns "lose"
            every { metPreviousGoal } returns false
        }
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns unsyncedAccount

        service.handleOfflineSync()

        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `handleOfflineSync skips goal sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `handleOfflineSync handles goal sync exception`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { goalWeight } returns 1600.0
            every { initialWeight } returns 1800.0
            every { goalType } returns "lose"
            every { metPreviousGoal } returns false
        }
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns unsyncedAccount
        coEvery { goalRepository.updateGoalSetting(any()) } throws RuntimeException("API error")

        service.handleOfflineSync()

        // Should continue
        coVerify { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() }
    }

    // -------------------------------------------------------------------------
    // syncWeightlessSettings — unsynced weightless
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs weightless settings when unsynced data exists`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { isWeightlessOn } returns true
            every { weightlessTimestamp } returns "2024-01-01"
            every { weightlessWeight } returns 5f
        }
        coEvery { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() } returns unsyncedAccount

        service.handleOfflineSync()

        coVerify { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    @Test
    fun `handleOfflineSync skips weightless sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncStreakSettings — unsynced streak
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs streak settings when unsynced data exists`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { isStreakOn } returns true
            every { streakTimestamp } returns "2024-01-01"
        }
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns unsyncedAccount

        service.handleOfflineSync()

        coVerify { userSettingsRepository.updateStreakSetting(any()) }
    }

    @Test
    fun `handleOfflineSync skips streak sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { userSettingsRepository.updateStreakSetting(any()) }
    }

    @Test
    fun `handleOfflineSync handles streak sync exception`() = runTest {
        val unsyncedAccount: Account = mockk {
            every { id } returns testAccountId
            every { isStreakOn } returns true
            every { streakTimestamp } returns "2024-01-01"
        }
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns unsyncedAccount
        coEvery { userSettingsRepository.updateStreakSetting(any()) } throws RuntimeException("API error")

        service.handleOfflineSync()

        // Should continue
        coVerify { accountRepository.getUnsyncedActiveDashboardSettings() }
    }

    // -------------------------------------------------------------------------
    // syncDashboardData — unsynced dashboard
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs dashboard settings when unsynced data exists`() = runTest {
        val dashboardSettings = DashboardSettingsEntity(
            accountId = testAccountId,
            dashboardMetrics = listOf("weight", "bmi"),
            dashboardMilestones = listOf("streak"),
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            isSynced = false,
        )
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns dashboardSettings

        service.handleOfflineSync()

        coVerify { accountRepository.updateDashboardMetrics(listOf("weight", "bmi")) }
        coVerify { accountRepository.updateProgressMetrics(listOf("streak")) }
        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = testAccountId,
                dashboardMetrics = listOf("weight", "bmi"),
                dashboardMilestones = listOf("streak"),
                dashboardType = any(),
                isSynced = true,
            )
        }
    }

    @Test
    fun `handleOfflineSync skips dashboard sync when nothing unsynced`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.updateDashboardMetrics(any()) }
    }

    @Test
    fun `handleOfflineSync handles dashboard sync exception`() = runTest {
        val dashboardSettings = DashboardSettingsEntity(
            accountId = testAccountId,
            dashboardMetrics = listOf("weight"),
            dashboardMilestones = listOf("streak"),
            dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
            isSynced = false,
        )
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns dashboardSettings
        coEvery { accountRepository.updateDashboardMetrics(any()) } throws RuntimeException("API error")

        service.handleOfflineSync()

        // Should not crash
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — overall exception
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync handles overall exception gracefully`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveAccount() } throws RuntimeException("Fatal error")

        service.handleOfflineSync()

        // Should not crash
    }

    // -------------------------------------------------------------------------
    // interface conformance
    // -------------------------------------------------------------------------

    @Test
    fun `service implements IOfflineHandlerService`() {
        assertThat(service).isInstanceOf(com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService::class.java)
    }

    @Test
    fun `service extends BaseService`() {
        assertThat(service).isInstanceOf(BaseService::class.java)
    }
}
