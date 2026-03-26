package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHandlerServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val accountRepository: IAccountRepository = mockk()
    private val bodyCompositionRepository: IBodyCompositionRepository = mockk()
    private val deviceService: IDeviceService = mockk(relaxed = true)
    private val notificationRepository: INotificationRepository = mockk()
    private val userSettingsRepository: IUserSettingsRepository = mockk()
    private val goalRepository: IGoalRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: OfflineHandlerService

    // --- Test Fixtures ---
    private val fakeAccount = Account(
        id = "acc-1",
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
        goalType = "lose",
        goalWeight = 1600.0,
        initialWeight = 1800.0,
        metPreviousGoal = false,
        shouldSendEntryNotifications = true,
        shouldSendWeightInEntryNotifications = false,
        isStreakOn = true,
        streakTimestamp = "2026-01-01T00:00:00Z",
        isWeightlessOn = true,
        weightlessTimestamp = "2026-01-15T00:00:00Z",
        weightlessWeight = 170.5f,
    )

    private val fakeAccountInfo = AccountInfo(
        id = "acc-1",
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
        dashboardType = "dashboard_4_metrics",
        dashboardMetrics = listOf("weight"),
        shouldSendEntryNotifications = true,
        shouldSendWeightInEntryNotifications = false,
        goalWeight = null,
        initialWeight = null,
    )

    private val fakeAccountResponse = AccountResponse(
        accessToken = null,
        refreshToken = null,
        expiresAt = null,
        account = fakeAccountInfo,
    )

    private val fakeDashboardSettings = DashboardSettingsEntity(
        accountId = "acc-1",
        dashboardMetrics = listOf("weight", "bmi"),
        dashboardMilestones = listOf("goal_progress"),
        dashboardType = "dashboard_4_metrics",
        isSynced = false,
    )

    @Before
    fun setUp() {
        stubNetworkAvailable()
        stubAllUnsyncedNull()
        service = createService()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createService() = OfflineHandlerService(
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

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    private fun stubNetworkAvailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = true, unAvailable = false)
    }

    private fun stubNetworkUnavailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = false, unAvailable = true)
    }

    /** Default: all "getUnsynced" queries return null → no sync work to do. */
    private fun stubAllUnsyncedNull() {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns null
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns null
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns null
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns null
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns null
        coEvery { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() } returns null
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns null
    }

    private fun stubProfileSync(account: Account = fakeAccount) {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns account
        coEvery { accountRepository.updateProfile(any()) } returns Unit
    }

    private fun stubBodyCompSync(account: Account = fakeAccount) {
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns account
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } returns fakeAccountResponse
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), any()) } returns Unit
    }

    private fun stubNotificationSync(account: Account = fakeAccount) {
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns account
        coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } returns fakeAccountResponse
        coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeAccount
    }

    private fun stubGoalSync(account: Account = fakeAccount) {
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns account
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount
    }

    private fun stubStreakSync(account: Account = fakeAccount) {
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns account
        coEvery { userSettingsRepository.updateStreakSetting(any()) } returns Unit
    }

    private fun stubWeightlessSync(account: Account = fakeAccount) {
        coEvery { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() } returns account
        coEvery { userSettingsRepository.updateWeightlessSetting(any()) } returns Unit
    }

    private fun stubDashboardSync(settings: DashboardSettingsEntity = fakeDashboardSettings) {
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns settings
        coEvery { accountRepository.updateDashboardMetrics(any()) } returns Unit
        coEvery { accountRepository.updateProgressMetrics(any()) } returns Unit
        coEvery { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) } returns Unit
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — network gate
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync does nothing when offline`() = runTest {
        stubNetworkUnavailable()

        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.getUnsyncedActiveAccount() }
        coVerify(exactly = 0) { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() }
        coVerify(exactly = 0) { deviceService.syncDevices(any()) }
        coVerify(exactly = 0) { goalRepository.getUnsyncedActiveGoalAccountFromDB() }
    }

    @Test
    fun `handleOfflineSync does nothing when no unsynced data exists`() = runTest {
        service.handleOfflineSync()

        // All getUnsynced returned null → no API calls
        coVerify(exactly = 0) { accountRepository.updateProfile(any()) }
        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
        coVerify(exactly = 0) { notificationRepository.updateNotificationSettingsInAPI(any()) }
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
        coVerify(exactly = 0) { userSettingsRepository.updateStreakSetting(any()) }
        coVerify(exactly = 0) { userSettingsRepository.updateWeightlessSetting(any()) }
        coVerify(exactly = 0) { accountRepository.updateDashboardMetrics(any()) }
    }

    // -------------------------------------------------------------------------
    // syncProfileData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs profile when unsynced account exists`() = runTest {
        stubProfileSync()

        service.handleOfflineSync()

        coVerify {
            accountRepository.updateProfile(
                ProfileUpdateRequest(
                    id = fakeAccount.id,
                    firstName = fakeAccount.firstName,
                    lastName = fakeAccount.lastName,
                    email = fakeAccount.email,
                    dob = fakeAccount.dob,
                    gender = fakeAccount.gender,
                    zipcode = fakeAccount.zipcode,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips profile sync when no unsynced account`() = runTest {
        // Default stub returns null
        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.updateProfile(any()) }
    }

    @Test
    fun `handleOfflineSync catches profile sync exception and continues`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns fakeAccount
        coEvery { accountRepository.updateProfile(any()) } throws RuntimeException("API error")
        stubGoalSync() // Stub a later sync to verify continuation

        service.handleOfflineSync()

        // Profile failed but goal sync still ran
        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncBodyCompositionData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs body composition when unsynced data exists`() = runTest {
        stubBodyCompSync()

        service.handleOfflineSync()

        coVerify {
            bodyCompositionRepository.updateBodyCompInAPI(
                BodyCompUpdateRequest(
                    height = 1700,
                    activityLevel = "normal",
                    weightUnit = "lb",
                )
            )
        }
        coVerify {
            bodyCompositionRepository.updateBodyCompInDB(
                fakeAccount.id,
                WeightCompSettingsEntity(
                    accountId = fakeAccountInfo.id,
                    height = fakeAccountInfo.height,
                    activityLevel = fakeAccountInfo.activityLevel,
                    weightUnit = fakeAccountInfo.weightUnit,
                    isSynced = true,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync uses default height and activityLevel when null`() = runTest {
        val accountNullFields = fakeAccount.copy(height = null, activityLevel = null)
        stubBodyCompSync(accountNullFields)

        service.handleOfflineSync()

        coVerify {
            bodyCompositionRepository.updateBodyCompInAPI(
                BodyCompUpdateRequest(
                    height = 1700,
                    activityLevel = "normal",
                    weightUnit = "lb",
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips body composition sync when no unsynced data`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
    }

    @Test
    fun `handleOfflineSync catches body composition exception and continues`() = runTest {
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns fakeAccount
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } throws RuntimeException("API error")
        stubGoalSync()

        service.handleOfflineSync()

        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncDeviceData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs devices`() = runTest {
        service.handleOfflineSync()

        coVerify { deviceService.syncDevices() }
    }

    @Test
    fun `handleOfflineSync catches device sync exception and continues`() = runTest {
        coEvery { deviceService.syncDevices(any()) } throws RuntimeException("BLE error")
        stubGoalSync()

        service.handleOfflineSync()

        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncNotificationData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs notification settings when unsynced data exists`() = runTest {
        stubNotificationSync()

        service.handleOfflineSync()

        coVerify {
            notificationRepository.updateNotificationSettingsInAPI(
                NotificationSettingsRequest(
                    shouldSendEntryNotifications = true,
                    shouldSendWeightInEntryNotifications = false,
                )
            )
        }
        coVerify {
            notificationRepository.updateNotificationSettingsInDB(
                fakeAccount.id,
                NotificationSettingsEntity(
                    accountId = fakeAccountInfo.id,
                    shouldSendEntryNotifications = fakeAccountInfo.shouldSendEntryNotifications,
                    shouldSendWeightInEntryNotifications = fakeAccountInfo.shouldSendWeightInEntryNotifications,
                    isSynced = true,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync uses false defaults for null notification settings`() = runTest {
        val accountNullNotif = fakeAccount.copy(
            shouldSendEntryNotifications = null,
            shouldSendWeightInEntryNotifications = null,
        )
        stubNotificationSync(accountNullNotif)

        service.handleOfflineSync()

        coVerify {
            notificationRepository.updateNotificationSettingsInAPI(
                NotificationSettingsRequest(
                    shouldSendEntryNotifications = false,
                    shouldSendWeightInEntryNotifications = false,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips notification sync when no unsynced data`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { notificationRepository.updateNotificationSettingsInAPI(any()) }
    }

    @Test
    fun `handleOfflineSync catches notification exception and continues`() = runTest {
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns fakeAccount
        coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } throws RuntimeException("API error")
        stubGoalSync()

        service.handleOfflineSync()

        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncGoalData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs goal data when unsynced account exists`() = runTest {
        stubGoalSync()

        service.handleOfflineSync()

        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = 1600.0,
                    initialWeight = 1800.0,
                    type = "lose",
                    metPreviousGoal = false,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync uses defaults for null goal fields`() = runTest {
        val accountNullGoal = fakeAccount.copy(goalWeight = null, goalType = null)
        stubGoalSync(accountNullGoal)

        service.handleOfflineSync()

        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = 0.0,
                    initialWeight = 1800.0,
                    type = "maintain",
                    metPreviousGoal = false,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips goal sync when no unsynced data`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `handleOfflineSync catches goal sync exception and continues`() = runTest {
        coEvery { goalRepository.getUnsyncedActiveGoalAccountFromDB() } returns fakeAccount
        coEvery { goalRepository.updateGoalSetting(any()) } throws RuntimeException("API error")
        stubWeightlessSync()

        service.handleOfflineSync()

        coVerify { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncWeightlessSettings
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs weightless settings when unsynced data exists`() = runTest {
        stubWeightlessSync()

        service.handleOfflineSync()

        coVerify {
            userSettingsRepository.updateWeightlessSetting(
                WeightlessRequest(
                    isWeightlessOn = true,
                    weightlessTimestamp = "2026-01-15T00:00:00Z",
                    weightlessWeight = 170.5.toDouble(),
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync uses false default for null isWeightlessOn`() = runTest {
        val accountNullWeightless = fakeAccount.copy(isWeightlessOn = null, weightlessWeight = null)
        stubWeightlessSync(accountNullWeightless)

        service.handleOfflineSync()

        coVerify {
            userSettingsRepository.updateWeightlessSetting(
                WeightlessRequest(
                    isWeightlessOn = false,
                    weightlessTimestamp = fakeAccount.weightlessTimestamp,
                    weightlessWeight = null,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips weightless sync when no unsynced data`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { userSettingsRepository.updateWeightlessSetting(any()) }
    }

    @Test
    fun `handleOfflineSync catches weightless exception and continues`() = runTest {
        coEvery { userSettingsRepository.getUnsyncedActiveWeightlessAccountFromDB() } returns fakeAccount
        coEvery { userSettingsRepository.updateWeightlessSetting(any()) } throws RuntimeException("API error")
        stubStreakSync()

        service.handleOfflineSync()

        coVerify { userSettingsRepository.updateStreakSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // syncStreakSettings
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs streak settings when unsynced data exists`() = runTest {
        stubStreakSync()

        service.handleOfflineSync()

        coVerify {
            userSettingsRepository.updateStreakSetting(
                StreakRequest(
                    isStreakOn = true,
                    streakTimestamp = "2026-01-01T00:00:00Z",
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync uses false default for null isStreakOn`() = runTest {
        val accountNullStreak = fakeAccount.copy(isStreakOn = null)
        stubStreakSync(accountNullStreak)

        service.handleOfflineSync()

        coVerify {
            userSettingsRepository.updateStreakSetting(
                StreakRequest(
                    isStreakOn = false,
                    streakTimestamp = fakeAccount.streakTimestamp,
                )
            )
        }
    }

    @Test
    fun `handleOfflineSync skips streak sync when no unsynced data`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { userSettingsRepository.updateStreakSetting(any()) }
    }

    @Test
    fun `handleOfflineSync catches streak exception and continues`() = runTest {
        coEvery { userSettingsRepository.getUnsyncedActiveStreakAccountFromDB() } returns fakeAccount
        coEvery { userSettingsRepository.updateStreakSetting(any()) } throws RuntimeException("API error")
        stubDashboardSync()

        service.handleOfflineSync()

        coVerify { accountRepository.updateDashboardMetrics(any()) }
    }

    // -------------------------------------------------------------------------
    // syncDashboardData
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync syncs dashboard when unsynced settings exist`() = runTest {
        stubDashboardSync()

        service.handleOfflineSync()

        coVerify { accountRepository.updateDashboardMetrics(listOf("weight", "bmi")) }
        coVerify { accountRepository.updateProgressMetrics(listOf("goal_progress")) }
        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = "acc-1",
                dashboardMetrics = listOf("weight", "bmi"),
                dashboardMilestones = listOf("goal_progress"),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `handleOfflineSync uses DASHBOARD_4_METRICS as default when type is unknown`() = runTest {
        val settingsUnknownType = fakeDashboardSettings.copy(dashboardType = "unknown_type")
        stubDashboardSync(settingsUnknownType)

        service.handleOfflineSync()

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = "acc-1",
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_4_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `handleOfflineSync resolves DASHBOARD_12_METRICS type correctly`() = runTest {
        val settings12 = fakeDashboardSettings.copy(dashboardType = "dashboard_12_metrics")
        stubDashboardSync(settings12)

        service.handleOfflineSync()

        coVerify {
            accountRepository.updateDashboardSettings(
                accountId = "acc-1",
                dashboardMetrics = any(),
                dashboardMilestones = any(),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
                isSynced = true,
            )
        }
    }

    @Test
    fun `handleOfflineSync skips dashboard sync when no unsynced settings`() = runTest {
        service.handleOfflineSync()

        coVerify(exactly = 0) { accountRepository.updateDashboardMetrics(any()) }
        coVerify(exactly = 0) { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `handleOfflineSync catches dashboard exception and completes`() = runTest {
        coEvery { accountRepository.getUnsyncedActiveDashboardSettings() } returns fakeDashboardSettings
        coEvery { accountRepository.updateDashboardMetrics(any()) } throws RuntimeException("API error")

        // Should not throw — outer catch in handleOfflineSync or syncDashboardData catches it
        service.handleOfflineSync()
    }

    // -------------------------------------------------------------------------
    // Exception isolation — one failure does not stop subsequent syncs
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync continues all syncs even when multiple fail`() = runTest {
        // Profile: fails
        coEvery { accountRepository.getUnsyncedActiveAccount() } returns fakeAccount
        coEvery { accountRepository.updateProfile(any()) } throws RuntimeException("profile error")

        // BodyComp: fails
        coEvery { bodyCompositionRepository.getUnsyncedActiveBodyCompAccountFromDB() } returns fakeAccount
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } throws RuntimeException("bodycomp error")

        // Device: fails
        coEvery { deviceService.syncDevices(any()) } throws RuntimeException("device error")

        // Notification: fails
        coEvery { notificationRepository.getUnsyncedActiveNotificationAccountFromDB() } returns fakeAccount
        coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } throws RuntimeException("notif error")

        // Goal: succeeds
        stubGoalSync()

        // Weightless: succeeds
        stubWeightlessSync()

        // Streak: succeeds
        stubStreakSync()

        // Dashboard: succeeds
        stubDashboardSync()

        service.handleOfflineSync()

        // Despite 4 failures, the remaining 4 still ran
        coVerify { goalRepository.updateGoalSetting(any()) }
        coVerify { userSettingsRepository.updateWeightlessSetting(any()) }
        coVerify { userSettingsRepository.updateStreakSetting(any()) }
        coVerify { accountRepository.updateDashboardMetrics(any()) }
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — outer catch block
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync catches top-level exception gracefully`() = runTest {
        // Make the first call inside the try block throw a non-catchable error from the
        // getUnsyncedActiveAccount itself (not its inner catch)
        coEvery { accountRepository.getUnsyncedActiveAccount() } throws RuntimeException("fatal")

        // Should not throw
        service.handleOfflineSync()
    }

    // -------------------------------------------------------------------------
    // handleOfflineSync — full happy path
    // -------------------------------------------------------------------------

    @Test
    fun `handleOfflineSync runs all sync steps when all have unsynced data`() = runTest {
        stubProfileSync()
        stubBodyCompSync()
        stubNotificationSync()
        stubGoalSync()
        stubWeightlessSync()
        stubStreakSync()
        stubDashboardSync()

        service.handleOfflineSync()

        coVerify { accountRepository.updateProfile(any()) }
        coVerify { bodyCompositionRepository.updateBodyCompInAPI(any()) }
        coVerify { bodyCompositionRepository.updateBodyCompInDB(any(), any()) }
        coVerify { deviceService.syncDevices() }
        coVerify { notificationRepository.updateNotificationSettingsInAPI(any()) }
        coVerify { notificationRepository.updateNotificationSettingsInDB(any(), any()) }
        coVerify { goalRepository.updateGoalSetting(any()) }
        coVerify { userSettingsRepository.updateWeightlessSetting(any()) }
        coVerify { userSettingsRepository.updateStreakSetting(any()) }
        coVerify { accountRepository.updateDashboardMetrics(any()) }
        coVerify { accountRepository.updateProgressMetrics(any()) }
        coVerify { accountRepository.updateDashboardSettings(any(), any(), any(), any(), any()) }
    }
}
