package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.ITokenManager
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0220
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper.SKU_0663
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
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.dmdbrands.library.ggbluetooth.enums.GGAppType
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var appRepository: IAppRepository
    @MockK(relaxed = true) lateinit var entryService: IEntryService
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

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
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
        coEvery { entryService.latestEntry } returns MutableStateFlow(null)
    }

    private fun createViewModel(): AppViewModel =
        AppViewModel(
            appRepository = appRepository,
            entryService = entryService,
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
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() = runTest {
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
    fun `SetScaleDiscovered true updates isScaleDiscovered`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))

        assertThat(viewModel.state.value.isScaleDiscovered).isTrue()
        assertThat(viewModel.state.value.scaleDiscoveredTimestamp).isNotNull()
    }

    @Test
    fun `SetScaleDiscovered false clears timestamp`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        viewModel.handleIntent(AppIntent.SetScaleDiscovered(false))

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
        assertThat(viewModel.state.value.scaleDiscoveredTimestamp).isNull()
    }

    @Test
    fun `SetSku updates sku in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetSku("0500"))

        assertThat(viewModel.state.value.sku).isEqualTo("0500")
    }

    @Test
    fun `SetScanStatus updates hasScanStarted`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScanStatus(true))

        assertThat(viewModel.state.value.hasScanStarted).isTrue()
    }

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetUnreadFeedCount(5))

        assertThat(viewModel.state.value.unreadFeedCount).isEqualTo(5)
    }

    @Test
    fun `SetShowUnreadFeedIndication updates showUnreadFeedIndication`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetShowUnreadFeedIndication(true))

        assertThat(viewModel.state.value.showUnreadFeedIndication).isTrue()
    }

    // -------------------------------------------------------------------------
    // OnPopUpDismiss
    // -------------------------------------------------------------------------

    @Test
    fun `OnPopUpDismiss sets isScaleDiscovered to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        assertThat(viewModel.state.value.isScaleDiscovered).isTrue()

        viewModel.handleIntent(AppIntent.OnPopUpDismiss)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    @Test
    fun `OnPopUpDismiss skips device in ggDeviceService`() = runTest {
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
    fun `OnPopUpConnect sets isScaleDiscovered to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.SetScaleDiscovered(true))
        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isScaleDiscovered).isFalse()
    }

    @Test
    fun `OnPopUpConnect clears dialog queue`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppIntent.OnPopUpConnect)
        advanceUntilIdle()

        verify { dialogQueueService.clear() }
    }

    @Test
    fun `OnPopUpConnect with baby scale SKU navigates to BabyScaleSetup with WAKEUP step`() = runTest {
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
    fun `OnPopUpConnect with LCBT SKU navigates to LcbtScaleSetup`() = runTest {
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
    fun `OnPopUpConnect with BPM SKU navigates to LcbtScaleSetup`() = runTest {
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
                match<AppRoute.ScaleSetup.LcbtScaleSetup> {
                    it.sku == SKU_0663 && it.initialStep == LcbtScaleSetupStep.CONNECTING_BLUETOOTH
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Auth State — LoggedOut
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState LoggedOut with active account clears dialog queue`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        authEventFlow.emit(AuthState.LoggedOut(isActiveAccount = true, isLastAccount = true))
        advanceUntilIdle()

        verify { dialogQueueService.clear() }
    }

    @Test
    fun `AuthState LoggedOut with last account navigates to landing`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        authEventFlow.emit(AuthState.LoggedOut(isActiveAccount = true, isLastAccount = true))
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    @Test
    fun `AuthState LoggedOut with other logged-in accounts navigates to MultiAccountLanding`() = runTest {
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
    fun `AuthState AccountSwitched with showToast true shows toast`() = runTest {
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
    fun `AuthState AccountSwitched with showToast false does not show toast`() = runTest {
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

    // -------------------------------------------------------------------------
    // Auth State — AccountDeleted
    // -------------------------------------------------------------------------

    @Test
    fun `AuthState AccountDeleted with active account navigates and resets dashboard`() = runTest {
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
    fun `AuthState UnauthorizedLogout navigates to MultiAccountLanding when active account found`() = runTest {
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
    fun `AuthState LoggedInFromLoading resets dashboard selected key`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        authEventFlow.emit(AuthState.LoggedInFromLoading(account = TestFixtures.activeAccount))
        advanceUntilIdle()

        coVerify { dashboardService.setSelectedKey(null) }
    }

    @Test
    fun `scan starts with ME_HEALTH app type and active account profile on login`() = runTest {
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
    fun `init loads all tokens into TokenManager`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { tokenManager.loadAllTokens() }
        coVerify { tokenManager.getCurrentAccountID() }
    }

    @Test
    fun `init cleans up old logs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { logManager.cleanupOldLogs(5) }
    }
}
