package com.dmdbrands.gurus.weight.features.home.viewmodel

import android.app.Activity
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.AccountFlagService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.AppReviewManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAppSyncService
import com.dmdbrands.gurus.weight.domain.services.IFeedService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.home.reducer.HomeIntent
import com.greatergoods.libs.appsync.startAppSyncScan
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.ggInAppMessaging.domain.services.IInAppMessagingService
import com.greatergoods.libs.appsync.model.AppSyncResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    companion object {
        private const val TEST_WEIGHT = 75.0f
        private const val TEST_WEIGHT_MODE = "kg"
        private const val UNREAD_FEED_COUNT = 3
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var deviceService: IDeviceService
    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var ggPermissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var appSyncService: IAppSyncService
    @MockK(relaxed = true) lateinit var accountService: IAccountService
    @MockK(relaxed = true) lateinit var feedService: IFeedService
    @MockK(relaxed = true) lateinit var accountFlagService: AccountFlagService
    @MockK(relaxed = true) lateinit var appReviewManager: AppReviewManager
    @MockK(relaxed = true) lateinit var ggInAppMessagingService: IInAppMessagingService
    @MockK(relaxed = true) lateinit var healthConnectService: IHealthConnectService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun stubDefaultFlows() {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { ggPermissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf<String, String>())
        every { accountService.activeAccount } returns MutableStateFlow(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { feedService.feedsChanged } returns flowOf(emptyList())
        coEvery { feedService.getUnreadFeedCount() } returns 0
        coEvery { feedService.getFeedSettings() } returns null
        coEvery { feedService.checkAndTriggerFeedModal() } returns false
        coEvery { accountFlagService.getAccountFlag() } returns null
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            deviceService = deviceService,
            ggDeviceService = ggDeviceService,
            ggPermissionService = ggPermissionService,
            dialogUtility = dialogUtility,
            appSyncService = appSyncService,
            accountService = accountService,
            feedService = feedService,
            accountFlagService = accountFlagService,
            appReviewManager = appReviewManager,
            ggInAppMessagingService = ggInAppMessagingService,
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.showAppsync).isFalse()
        assertThat(state.isAppSyncPermissionsEnabled).isFalse()
        assertThat(state.showWeightOnlyModeBottomSheet).isFalse()
        assertThat(state.openWeightOnlyModePopup).isFalse()
        assertThat(state.isWeightOnlyModeDismissed).isFalse()
        assertThat(state.showUnreadFeedIndicator).isFalse()
        assertThat(state.shouldAskForReview).isFalse()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowAppsync updates showAppsync`() {
        viewModel.handleIntent(HomeIntent.SetShowAppsync(true))
        assertThat(viewModel.state.value.showAppsync).isTrue()
    }

    @Test
    fun `isAppSyncPermissionsEnabled updates state`() {
        viewModel.handleIntent(HomeIntent.isAppSyncPermissionsEnabled(true))
        assertThat(viewModel.state.value.isAppSyncPermissionsEnabled).isTrue()
    }

    @Test
    fun `SetShowWeightOnlyModeBottomSheet updates state`() {
        viewModel.handleIntent(HomeIntent.SetShowWeightOnlyModeBottomSheet(true))
        assertThat(viewModel.state.value.showWeightOnlyModeBottomSheet).isTrue()
    }

    @Test
    fun `OpenWeightOnlyModePopup updates state`() {
        viewModel.handleIntent(HomeIntent.OpenWeightOnlyModePopup(true))
        assertThat(viewModel.state.value.openWeightOnlyModePopup).isTrue()
    }

    @Test
    fun `SetWeightOnlyModeDismissed updates state`() {
        viewModel.handleIntent(HomeIntent.SetWeightOnlyModeDismissed(true))
        assertThat(viewModel.state.value.isWeightOnlyModeDismissed).isTrue()
    }

    @Test
    fun `SetShowUnreadFeedIndicator updates state`() {
        viewModel.handleIntent(HomeIntent.SetShowUnreadFeedIndicator(true))
        assertThat(viewModel.state.value.showUnreadFeedIndicator).isTrue()
    }

    @Test
    fun `SetShouldAskForReview updates state`() {
        viewModel.handleIntent(HomeIntent.SetShouldAskForReview(true))
        assertThat(viewModel.state.value.shouldAskForReview).isTrue()
    }

    // -------------------------------------------------------------------------
    // HandleAppSyncResult — manual entry
    // -------------------------------------------------------------------------

    @Test
    fun `HandleAppSyncResult with manual navigates to ManualEntry`() = runTest(mainDispatcherRule.scheduler) {
        val result = AppSyncResult(weight = null, fat = null, muscle = null, water = null, mode = null, manual = true, canceled = false)
        viewModel.handleIntent(HomeIntent.HandleAppSyncResult(result))
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home) }
    }

    @Test
    fun `HandleAppSyncResult with canceled does not navigate`() = runTest(mainDispatcherRule.scheduler) {
        val result = AppSyncResult(weight = null, fat = null, muscle = null, water = null, mode = null, manual = false, canceled = true)
        viewModel.handleIntent(HomeIntent.HandleAppSyncResult(result))
        advanceUntilIdle()
        coVerify(exactly = 0) { navigationService.navigateTo(any(), any()) }
    }

    @Test
    fun `HandleAppSyncResult with weight shows entry sync popup`() = runTest(mainDispatcherRule.scheduler) {
        val result = AppSyncResult(weight = TEST_WEIGHT, fat = null, muscle = null, water = null, mode = TEST_WEIGHT_MODE, manual = false, canceled = false)
        viewModel.handleIntent(HomeIntent.HandleAppSyncResult(result))
        advanceUntilIdle()
        verify { dialogUtility.showEntrySyncPopup(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // OnWeightOnlyModeEnable
    // -------------------------------------------------------------------------

    @Test
    fun `OnWeightOnlyModeEnable with no connected scales dismisses loader`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        viewModel = createViewModel()

        viewModel.handleIntent(HomeIntent.OnWeightOnlyModeEnable)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // OnWeightOnlyModeAlertDismiss
    // -------------------------------------------------------------------------

    @Test
    fun `OnWeightOnlyModeAlertDismiss calls deviceService weightOnlyModeDismissAlert`() {
        viewModel.handleIntent(HomeIntent.OnWeightOnlyModeAlertDismiss)
        verify { deviceService.weightOnlyModeDismissAlert(any()) }
    }

    // -------------------------------------------------------------------------
    // LaunchAppReview
    // -------------------------------------------------------------------------

    @Test
    fun `LaunchAppReview calls appReviewManager launchInAppReview`() = runTest(mainDispatcherRule.scheduler) {
        val activity: Activity = mockk(relaxed = true)
        viewModel.handleIntent(HomeIntent.LaunchAppReview(activity))
        advanceUntilIdle()
        coVerify { appReviewManager.launchInAppReview(activity) }
    }

    @Test
    fun `LaunchAppReview resets shouldAskForReview to false`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HomeIntent.SetShouldAskForReview(true))
        assertThat(viewModel.state.value.shouldAskForReview).isTrue()

        val activity: Activity = mockk(relaxed = true)
        viewModel.handleIntent(HomeIntent.LaunchAppReview(activity))
        advanceUntilIdle()

        assertThat(viewModel.state.value.shouldAskForReview).isFalse()
    }

    // -------------------------------------------------------------------------
    // CheckAndRequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `CheckAndRequestPermission when already enabled invokes callback with true`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HomeIntent.isAppSyncPermissionsEnabled(true))

        var result: Boolean? = null
        viewModel.handleIntent(HomeIntent.CheckAndRequestPermission { result = it })
        advanceUntilIdle()

        assertThat(result).isTrue()
    }

    @Test
    fun `CheckAndRequestPermission when not enabled shows permission alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(HomeIntent.CheckAndRequestPermission { })
        advanceUntilIdle()
        verify { dialogUtility.permissionAlert(any(), any(), any(), any()) }
    }

    @Test
    fun `CheckAndRequestPermission dismisses the loader when permission status never loads`() = runTest(mainDispatcherRule.scheduler) {
        // Loader must always be dismissed, even if the flow never emits (times out) or the
        // coroutine is cancelled mid-await — the try/finally guard. (PR #2093 review)
        val permFlow = MutableStateFlow(mutableMapOf<String, String>())
        every { ggPermissionService.permissionCallBackFlow } returns permFlow
        viewModel = createViewModel()
        mockkObject(AppPermissionsHelper)
        every { AppPermissionsHelper.areRequiredPermissionsEnabled(any(), any()) } returns false

        try {
            viewModel.handleIntent(HomeIntent.CheckAndRequestPermission { })
            advanceUntilIdle() // advances past PERMISSION_LOAD_TIMEOUT_MS

            verify { dialogQueueService.showLoader(any()) }
            verify { dialogQueueService.dismissLoader() }
        } finally {
            unmockkObject(AppPermissionsHelper)
        }
    }

    @Test
    fun `CheckAndRequestPermission with empty-then-granted flow opens scanner without prompting`() = runTest(mainDispatcherRule.scheduler) {
        // MOB-710 regression: the permission flow holds an empty map until its first poll, then
        // emits the real (granted) status. The tap must wait for that status and proceed —
        // NOT prompt off the stale empty map. (PR #2093 review)
        val permFlow = MutableStateFlow(mutableMapOf<String, String>())
        every { ggPermissionService.permissionCallBackFlow } returns permFlow
        viewModel = createViewModel()
        mockkObject(AppPermissionsHelper)
        // areRequiredPermissionsEnabled has 4 params (map, sku, setupType, requiredTypes) — match all.
        every { AppPermissionsHelper.areRequiredPermissionsEnabled(any(), any(), any(), any()) } returns true

        try {
            var result: Boolean? = null
            viewModel.handleIntent(HomeIntent.CheckAndRequestPermission { result = it })
            // Status loads after the tap (empty -> granted).
            permFlow.value = mutableMapOf("android.permission.CAMERA" to "granted")
            advanceUntilIdle()

            assertThat(result).isTrue()
            verify(exactly = 0) { dialogUtility.permissionAlert(any(), any(), any(), any()) }
        } finally {
            unmockkObject(AppPermissionsHelper)
        }
    }

    @Test
    fun `StartAppSyncScan resets isScanning to false when the scan fails`() = runTest(mainDispatcherRule.scheduler) {
        // The scan runs on viewModelScope with a finally that always clears the flag; a failed
        // scan must leave isScanning = false, otherwise the AppSync icon stays stuck. (PR #2093 review)
        mockkStatic(::startAppSyncScan)
        try {
            coEvery { startAppSyncScan(any(), any(), any(), any()) } throws RuntimeException("scan failed")
            val activity = mockk<android.app.Activity>(relaxed = true)

            viewModel.handleIntent(HomeIntent.StartAppSyncScan(activity))
            advanceUntilIdle()

            assertThat(viewModel.state.value.isScanning).isFalse()
        } finally {
            unmockkStatic(::startAppSyncScan)
        }
    }

    // -------------------------------------------------------------------------
    // handleWeightOnlyDismiss
    // -------------------------------------------------------------------------

    @Test
    fun `handleWeightOnlyDismiss closes popup and bottom sheet`() {
        viewModel.handleIntent(HomeIntent.OpenWeightOnlyModePopup(true))
        viewModel.handleIntent(HomeIntent.SetShowWeightOnlyModeBottomSheet(true))

        viewModel.handleWeightOnlyDismiss()

        assertThat(viewModel.state.value.openWeightOnlyModePopup).isFalse()
        assertThat(viewModel.state.value.showWeightOnlyModeBottomSheet).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — feed indicator
    // -------------------------------------------------------------------------

    @Test
    fun `init updates unread feed indicator when count greater than 0`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { feedService.getUnreadFeedCount() } returns UNREAD_FEED_COUNT
        coEvery { feedService.getFeedSettings() } returns null // defaults to showBadge = true

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.showUnreadFeedIndicator).isTrue()
    }

    @Test
    fun `init sets showUnreadFeedIndicator false when count is 0`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { feedService.getUnreadFeedCount() } returns 0

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.showUnreadFeedIndicator).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkHealthConnectPermission — exercised via init
    // -------------------------------------------------------------------------

    @Test
    fun `init calls healthConnectService checkHealthConnectPermissionDisabled`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { healthConnectService.checkHealthConnectPermissionDisabled() }
    }

    @Test
    fun `checkHealthConnectPermission does not crash when service throws`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { healthConnectService.checkHealthConnectPermissionDisabled() } throws RuntimeException("fail")

        viewModel = createViewModel()
        advanceUntilIdle()

        // Should not crash — exception is caught internally
        coVerify { healthConnectService.checkHealthConnectPermissionDisabled() }
    }

    // -------------------------------------------------------------------------
    // observeAppSyncStatus — exercised via init
    // -------------------------------------------------------------------------

    @Test
    fun `observeAppSyncStatus subscribes to pairedScales flow`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // pairedScales flow is observed in init
        verify { deviceService.pairedScales }
    }

    @Test
    fun `observeAppSyncStatus sets showAppsync false with empty scales`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.showAppsync).isFalse()
    }

    // -------------------------------------------------------------------------
    // observePermissions — exercised via init
    // -------------------------------------------------------------------------

    @Test
    fun `observePermissions subscribes to permissionCallBackFlow`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        verify { ggPermissionService.permissionCallBackFlow }
    }

    @Test
    fun `observePermissions handles non-Map type without crash`() = runTest(mainDispatcherRule.scheduler) {
        every { ggPermissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf<String, String>())

        viewModel = createViewModel()
        advanceUntilIdle()

        // Should not crash — non-Map types are ignored
        assertThat(viewModel.state.value.isAppSyncPermissionsEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `init sets account id on ggInAppMessagingService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { ggInAppMessagingService.setAccountId(any()) }
    }

    @Test
    fun `init checks feed modal trigger`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { feedService.checkAndTriggerFeedModal() }
    }

    @Test
    fun `init checks account flags when feed modal is not triggered`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { feedService.checkAndTriggerFeedModal() } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { accountFlagService.getAccountFlag() }
    }

    @Test
    fun `init skips account flag check when feed modal is triggered`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { feedService.checkAndTriggerFeedModal() } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        // checkAccountFlags is only called when isModalTriggered is false
        coVerify(exactly = 0) { accountFlagService.checkAccountFlag(any()) }
    }

    // -------------------------------------------------------------------------
    // subscribeToWeightOnlyModeAlertDismissed
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeToWeightOnlyModeAlertDismissed updates state from deviceService`() = runTest(mainDispatcherRule.scheduler) {
        val alertFlow = MutableStateFlow(false)
        every { deviceService.isWeightOnlyModeAlertShown } returns alertFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        alertFlow.value = true
        advanceUntilIdle()

        assertThat(viewModel.state.value.isWeightOnlyModeDismissed).isTrue()
    }

    // -------------------------------------------------------------------------
    // enableSessionImpedence
    // -------------------------------------------------------------------------

    @Test
    fun `enableSessionImpedence calls ggDeviceService updateSettings`() = runTest(mainDispatcherRule.scheduler) {
        val device: com.dmdbrands.gurus.weight.domain.model.storage.Device = mockk(relaxed = true)
        viewModel.enableSessionImpedence(device)
        advanceUntilIdle()

        // enableSessionImpedence launches on Dispatchers.IO which is not replaced by the test dispatcher,
        // so use coVerify with a timeout to allow the IO coroutine to complete
        coVerify(timeout = 1000) { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // HandleAppSyncResult — additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `HandleAppSyncResult with null weight and not manual is no-op`() = runTest(mainDispatcherRule.scheduler) {
        val result = AppSyncResult(weight = null, fat = null, muscle = null, water = null, mode = null, manual = false, canceled = false)
        viewModel.handleIntent(HomeIntent.HandleAppSyncResult(result))
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.navigateTo(any(), any()) }
        verify(exactly = 0) { dialogUtility.showEntrySyncPopup(any(), any(), any(), any()) }
    }
}
