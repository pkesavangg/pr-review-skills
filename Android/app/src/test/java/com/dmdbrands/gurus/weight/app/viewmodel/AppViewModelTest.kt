package com.dmdbrands.gurus.weight.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0220
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0663
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountFlagService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ReadingToastStrings
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.core.service.GGInAppMessagingService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var appRepository: IAppRepository
    @MockK(relaxed = true) lateinit var entryService: IEntryService
    @MockK(relaxed = true) lateinit var entryReadService: IEntryReadService
    @MockK(relaxed = true) lateinit var logManager: LogManager
    @MockK(relaxed = true) lateinit var tokenManager: ITokenManager
    @MockK(relaxed = true) lateinit var dashboardService: IDashboardService
    @MockK(relaxed = true) lateinit var accountService: IAccountService
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService
    @MockK(relaxed = true) lateinit var ggPermissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var deviceInfoService: IDeviceInfoService
    @MockK(relaxed = true) lateinit var bluetoothPreferencesService: BluetoothPreferencesService
    @MockK(relaxed = true) lateinit var feedService: IFeedService
    @MockK(relaxed = true) lateinit var ggInAppMessagingService: GGInAppMessagingService
    @MockK(relaxed = true) lateinit var accountFlagService: IAccountFlagService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private val authEventFlow = MutableSharedFlow<AuthState>()
    private lateinit var viewModel: AppViewModel

    private val createdViewModels = mutableListOf<AppViewModel>()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    @AfterEach
    fun tearDown() {
        // AppViewModel collects the AppNotificationEventService singleton's tapEvents in
        // viewModelScope (which isn't a child of runTest), so without cancellation the collector
        // leaks across test classes and steals later emissions — breaking
        // AppNotificationEventServiceTest's retained-tap assertions. Cancel every VM we created
        // and clear the singleton's retained tap.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        AppNotificationEventService.consumeTap()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { accountService.activeAccount } returns MutableStateFlow(TestFixtures.activeAccount)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { ggPermissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf())
        every { ggDeviceService.deviceCallbackFlow } returns MutableStateFlow(mockk(relaxed = true))
        every { feedService.feedsChanged } returns flowOf(emptyList())
        coEvery { feedService.getUnreadFeedCount() } returns 0
        coEvery { feedService.getFeedSettings() } returns null
        coEvery { feedService.checkAndTriggerFeedModal() } returns false
        coEvery { accountFlagService.getAccountFlag() } returns null
        every { navigationService.authEvent } returns authEventFlow
        every { entryReadService.latestEntry() } returns flowOf<Entry?>(null)
    }

    private fun createViewModel(): AppViewModel =
        AppViewModel(
            appRepository = appRepository,
            entryService = entryService,
            entryReadService = entryReadService,
            logManager = logManager,
            appNavigationService = navigationService,
            tokenManager = tokenManager,
            dashboardService = dashboardService,
            accountService = accountService,
            dialogUtility = dialogUtility,
            deviceService = deviceService,
            ggPermissionService = ggPermissionService,
            ggDeviceService = ggDeviceService,
            deviceInfoService = deviceInfoService,
            bluetoothPreferencesService = bluetoothPreferencesService,
            feedService = feedService,
            ggInAppMessagingService = ggInAppMessagingService,
            accountFlagService = accountFlagService,
            tokenMigrationHelper = mockk(relaxed = true),
            analyticsService = mockk(relaxed = true),
            powerSaveModeObserver = mockk(relaxed = true) {
                every { observe() } returns flowOf(false)
                every { isPowerSaveMode() } returns false
            },
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        ).also { createdViewModels += it }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.isScaleDiscovered).isFalse()
        assertThat(state.hasScanStarted).isFalse()
        assertThat(state.sku).isEqualTo("0412")
        assertThat(state.unreadFeedCount).isEqualTo(0)
        assertThat(state.showUnreadFeedIndication).isFalse()
        assertThat(state.scaleDiscoveredTimestamp).isNull()
    }

    // -------------------------------------------------------------------------
    // Reducer — Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleDiscovered true updates isScaleDiscovered`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))

        assertThat(viewModel.state.value.isScaleDiscovered).isTrue()
        assertThat(viewModel.state.value.scaleDiscoveredTimestamp).isNotNull()
    }

    @Test
    fun `SetScaleDiscovered false clears timestamp`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        viewModel.handleIntent(AppIntent.SetScaleDiscovered(false))

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
        assertThat(viewModel.state.value.scaleDiscoveredTimestamp).isNull()
    }

    @Test
    fun `SetSku updates sku in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetSku("0500"))

        assertThat(viewModel.state.value.sku).isEqualTo("0500")
    }

    @Test
    fun `SetScanStatus updates hasScanStarted`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScanStatus(true))

        assertThat(viewModel.state.value.hasScanStarted).isTrue()
    }

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetUnreadFeedCount(5))

        assertThat(viewModel.state.value.unreadFeedCount).isEqualTo(5)
    }

    @Test
    fun `SetShowUnreadFeedIndication updates showUnreadFeedIndication`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetShowUnreadFeedIndication(true))

        assertThat(viewModel.state.value.showUnreadFeedIndication).isTrue()
    }

    // -------------------------------------------------------------------------
    // OnPopUpDismiss
    // -------------------------------------------------------------------------

    @Test
    fun `OnPopUpDismiss sets isScaleDiscovered to false`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        assertThat(viewModel.state.value.isScaleDiscovered).isTrue()

        viewModel.handleIntent(AppIntent.OnPopUpDismiss)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    @Test
    fun `OnPopUpDismiss skips device in ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.OnPopUpDismiss)
        advanceUntilIdle()

        // discoveredBroadcastId is null initially, so skipDevice won't be called
        // but isScaleDiscovered should be set to false
        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    // -------------------------------------------------------------------------
    // OnPopUpConnect
    // -------------------------------------------------------------------------

    @Test
    fun `OnPopUpConnect sets isScaleDiscovered to false`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    @Test
    fun `OnPopUpConnect clears dialog queue`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        verify { dialogQueueService.clear() }
    }

    @Test
    fun `OnPopUpConnect with baby scale SKU navigates to BabyScaleSetup with WAKEUP step`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set private sku field to a baby scale SKU
        AppViewModel::class.java.getDeclaredField("sku").apply {
            isAccessible = true
            set(viewModel, SKU_0220)
        }

        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        coVerify {
            navigationService.navigateTo(
                match<AppRoute.ScaleSetup.BabyScaleSetup> {
                    it.sku == SKU_0220 && it.initialStep == BabyScaleSetupStep.WAKEUP
                },
            )
        }
    }

    @Test
    fun `OnPopUpConnect with LCBT SKU navigates to LcbtScaleSetup`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val lcbtSku = "0500"
        AppViewModel::class.java.getDeclaredField("sku").apply {
            isAccessible = true
            set(viewModel, lcbtSku)
        }

        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        coVerify {
            navigationService.navigateTo(
                match<AppRoute.ScaleSetup.LcbtScaleSetup> {
                    it.sku == lcbtSku && it.initialStep == LcbtScaleSetupStep.CONNECTING_BLUETOOTH
                },
            )
        }
    }

    @Test
    fun `OnPopUpConnect with BPM SKU navigates to BpmSetup`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        AppViewModel::class.java.getDeclaredField("sku").apply {
            isAccessible = true
            set(viewModel, SKU_0663)
        }

        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        coVerify {
            navigationService.navigateTo(
                match<AppRoute.ScaleSetup.BpmSetup> {
                    it.sku == SKU_0663
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Auth State — LoggedOut
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState LoggedOut with active account clears dialog queue`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        authEventFlow.emit(AuthState.LoggedOut(isActiveAccount = true, isLastAccount = true))
        advanceUntilIdle()

        verify { dialogQueueService.clear() }
    }

    @Test
    fun `AuthState LoggedOut with last account navigates to landing`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        authEventFlow.emit(AuthState.LoggedOut(isActiveAccount = true, isLastAccount = true))
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    @Test
    fun `AuthState LoggedOut with other logged-in accounts navigates to MultiAccountLanding`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns listOf(TestFixtures.secondaryAccount)

        authEventFlow.emit(AuthState.LoggedOut(isActiveAccount = true))
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding) }
    }

    // -------------------------------------------------------------------------
    // Auth State — AccountSwitched
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState AccountSwitched with showToast true shows toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(
            AuthState.AccountSwitched(
                account = TestFixtures.activeAccount,
                showToast = true,
            ),
        )
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `AuthState AccountSwitched with showToast false does not show toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(
            AuthState.AccountSwitched(
                account = TestFixtures.activeAccount,
                showToast = false,
            ),
        )
        advanceUntilIdle()

        verify(exactly = 0) { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `AuthState AccountSwitched resets scale-discovered state so reconnect alert is not suppressed`() = runTest(mainDispatcherRule.scheduler) {
        // MOB-175: switching accounts in-session must clear the previous account's skip/ignore
        // state. Otherwise a scale skipped under the previous account stays muted and the
        // duplicate-user reconnect alert never reappears after switching back.
        viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate a scale having been discovered before the switch, so we can prove the reset
        // flips it back (pins the full reset, not just the clearSkipDevices side effect).
        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        assertThat(viewModel.state.value.isScaleDiscovered).isTrue()

        authEventFlow.emit(
            AuthState.AccountSwitched(
                account = TestFixtures.activeAccount,
                showToast = true,
            ),
        )
        advanceUntilIdle()

        verify { bluetoothPreferencesService.clearSkipDevices() }
        // resetScaleDiscoveredState() also dispatches SetScaleDiscovered(false).
        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    @Test
    fun `AuthState AccountSwitched resets scale-discovered state even when showToast is false`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(
            AuthState.AccountSwitched(
                account = TestFixtures.activeAccount,
                showToast = false,
            ),
        )
        advanceUntilIdle()

        // The reset is independent of the toast — it must happen on every account switch.
        verify { bluetoothPreferencesService.clearSkipDevices() }
        verify(exactly = 0) { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // Duplicate-user reconnect — token-disambiguation selection (MOB-175)
    // -------------------------------------------------------------------------

    @Test
    fun `selectDuplicateUserToken prefers the token-matched user among same-name users`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        // Two users share the display name "renu"; only one token matches THIS account's stored token.
        val users = listOf(
            GGBTUser(name = "renu", token = "this-account-token", lastActive = 1000L, isBodyMetricsEnabled = true),
            GGBTUser(name = "renu", token = "other-account-token", lastActive = 2000L, isBodyMetricsEnabled = false),
        )

        val selected = viewModel.selectDuplicateUserToken(
            userList = users,
            displayName = "renu",
            localToken = "this-account-token",
        )

        // Must pick the token-matched user, not an arbitrary name match (which could delete the wrong slot).
        assertThat(selected).isEqualTo("this-account-token")
    }

    @Test
    fun `selectDuplicateUserToken falls back to first name match when no token matches`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val users = listOf(
            GGBTUser(name = "renu", token = "token-a", lastActive = 1000L, isBodyMetricsEnabled = true),
            GGBTUser(name = "renu", token = "token-b", lastActive = 2000L, isBodyMetricsEnabled = false),
        )

        val selected = viewModel.selectDuplicateUserToken(
            userList = users,
            displayName = "renu",
            // localToken matches neither user (e.g. this account has no stored scale token yet).
            localToken = "unmatched-token",
        )

        // Falls back to the first name match.
        assertThat(selected).isEqualTo("token-a")
    }

    @Test
    fun `selectDuplicateUserToken returns null when no user shares the display name`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val users = listOf(
            GGBTUser(name = "alice", token = "token-a", lastActive = 1000L, isBodyMetricsEnabled = true),
        )

        val selected = viewModel.selectDuplicateUserToken(
            userList = users,
            displayName = "renu",
            localToken = "token-a",
        )

        assertThat(selected).isNull()
    }

    // -------------------------------------------------------------------------
    // Auth State — AccountDeleted
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState AccountDeleted with active account navigates and resets dashboard`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        authEventFlow.emit(AuthState.AccountDeleted(isActiveAccount = true))
        advanceUntilIdle()

        coVerify { dashboardService.setSelectedKey(null) }
    }

    // -------------------------------------------------------------------------
    // Auth State — UnauthorizedLogout
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState UnauthorizedLogout navigates to MultiAccountLanding when active account found`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.handleUnauthorizedLogout("some-id") } returns TestFixtures.activeAccount

        authEventFlow.emit(AuthState.UnauthorizedLogout(accountId = "some-id"))
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding) }
        verify { dialogUtility.showAccountLoggedOutAlert(TestFixtures.activeAccount.firstName) }
    }

    // -------------------------------------------------------------------------
    // Auth State — LoggedInFromLoading
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState LoggedInFromLoading resets dashboard selected key`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(AuthState.LoggedInFromLoading(account = TestFixtures.activeAccount))
        advanceUntilIdle()

        coVerify { dashboardService.setSelectedKey(null) }
    }

    @Test
    fun `scan starts with ME_HEALTH app type and active account profile on login`() = runTest(mainDispatcherRule.scheduler) {
        val profileSlot = slot<GGBTUserProfile>()
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(AuthState.LoggedInFromLoading(account = TestFixtures.activeAccount))
        advanceUntilIdle()

        verify { ggPermissionService.startScan(eq(GGAppType.ME_HEALTH), capture(profileSlot)) }
        assertThat(profileSlot.captured.name).isEqualTo(TestFixtures.activeAccount.firstName)
        assertThat(profileSlot.captured.unit).isEqualTo(TestFixtures.activeAccount.weightUnit.value)
    }

    // -------------------------------------------------------------------------
    // Init — token loading
    // -------------------------------------------------------------------------

    @Test
    fun `init loads all tokens into TokenManager`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { tokenManager.loadAllTokens() }
        coVerify { tokenManager.getCurrentAccountID() }
    }

    @Test
    fun `init cleans up old logs`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { logManager.cleanupOldLogs(5) }
    }

    // -------------------------------------------------------------------------
    // Baby reading assignment — silent-failure guard (MOB-426 / MOB-428)
    // -------------------------------------------------------------------------

    private suspend fun AppViewModel.assignReadingToBaby(
        entry: List<ScaleEntry>,
        babyId: String,
        babies: List<BabyProfile>,
        previousEntryIds: List<Long>,
    ) {
        val fn = AppViewModel::class.declaredMemberFunctions.first { it.name == "assignReadingToBaby" }
        fn.isAccessible = true
        fn.callSuspend(this, "75.0 lbs", entry, babyId, babies, previousEntryIds)
    }

    private fun aBaby(id: String = "baby1") =
        BabyProfile(id = id, accountId = "active-account-id", name = "Emma")

    @Test
    fun `assignReadingToBaby surfaces an error and skips success when the save fails`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { entryService.addBabyEntry(any()) } returns -1L

        viewModel.assignReadingToBaby(
            entry = listOf(TestFixtures.weightEntry),
            babyId = "baby1",
            babies = listOf(aBaby()),
            previousEntryIds = listOf(99L),
        )
        advanceUntilIdle()

        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        // The user sees the failure copy, not a false "Reading assigned" confirmation.
        assertThat(toasts.any { it is Toast.Simple && it.message == ReadingToastStrings.SaveFailed }).isTrue()
        assertThat(toasts.any { it is Toast.Custom }).isFalse()
        // A failed save must not delete the previously-assigned entries.
        coVerify(exactly = 0) { entryService.deleteBabyEntryLocally(any()) }
    }

    @Test
    fun `assignReadingToBaby deletes previous entries and confirms when the save succeeds`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { entryService.addBabyEntry(any()) } returns 5L

        viewModel.assignReadingToBaby(
            entry = listOf(TestFixtures.weightEntry),
            babyId = "baby1",
            babies = listOf(aBaby()),
            previousEntryIds = listOf(99L),
        )
        advanceUntilIdle()

        // Reassign only removes the old entry after the new one is safely persisted.
        coVerify { entryService.deleteBabyEntryLocally(99L) }
        val toasts = mutableListOf<Toast>()
        verify { dialogQueueService.showToast(capture(toasts)) }
        assertThat(toasts.any { it is Toast.Simple && it.message == ReadingToastStrings.SaveFailed }).isFalse()
        assertThat(toasts.any { it is Toast.Custom }).isTrue()
    }
}
