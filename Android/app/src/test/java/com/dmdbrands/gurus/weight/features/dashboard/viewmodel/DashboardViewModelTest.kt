package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.Progress
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
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
class DashboardViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var entryService: IEntryService

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var appNavigationService: IAppNavigationService

    @MockK(relaxUnitFun = true)
    lateinit var dashboardService: IDashboardService

    @MockK(relaxed = true)
    lateinit var healthConnectService: IHealthConnectService

    @MockK(relaxed = true)
    lateinit var goalService: IGoalService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: DashboardViewModel

    companion object {
        private const val LATEST_WEIGHT = 80.5
        private const val SCROLL_TARGET = 123.45
        private const val PAGER_INDEX = 2
        private const val ANCHOR_TIMESTAMP = 1700000000L
        private const val PROGRESS_COUNT = 5
        private const val PROGRESS_INIT_WEIGHT = 85.0
        private const val WEIGHTLESS_WEIGHT = 150f
        private const val TEST_ACCOUNT_12_ID = "test-12"
        private val TEST_DASHBOARD_KEY = DashboardKey.Metric(
            com.dmdbrands.gurus.weight.domain.enums.MetricKey.WEIGHT
        )
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccount } returns MutableStateFlow(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { dashboardService.visibleKeys } returns MutableStateFlow(emptyList())
        every { entryService.isEmpty } returns MutableStateFlow(false)
        every { entryService.progress } returns flowOf(Progress())
        every { entryService.isUpdating } returns MutableStateFlow(false)
        every { entryService.latestEntry } returns MutableStateFlow(null)
        every { healthConnectService.outOfSyncState } returns flowOf(false)
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.selectedSegment).isEqualTo(GraphSegment.WEEK)
        assertThat(state.isEmpty).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.data).isEmpty()
        assertThat(state.latestWeight).isNull()
        assertThat(state.selectedStat).isNull()
        assertThat(state.pagerState).isEqualTo(0)
        assertThat(state.scrollTarget).isNull()
        assertThat(state.isScrollTargetConsumed).isFalse()
        assertThat(state.isConsuming).isFalse()
    }

    @Test
    fun `initial state sets dashboardType from active account`() {
        val state = viewModel.state.value
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetData
    // -------------------------------------------------------------------------

    @Test
    fun `SetData replaces data list and preserves ordering`() {
        val data = listOf<PeriodBodyScaleSummary>(mockk(), mockk(), mockk())
        viewModel.handleIntent(DashboardIntent.SetData(data))
        assertThat(viewModel.state.value.data).isEqualTo(data)
        assertThat(viewModel.state.value.data).hasSize(3)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetSelectedSegment
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedSegment updates selectedSegment to MONTH`() {
        viewModel.handleIntent(DashboardIntent.SetSelectedSegment(GraphSegment.MONTH))
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.MONTH)
    }

    @Test
    fun `SetSelectedSegment with same segment does not change state`() {
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.WEEK)
        viewModel.handleIntent(DashboardIntent.SetSelectedSegment(GraphSegment.WEEK))
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.WEEK)
    }

    @Test
    fun `SetSelectedSegment with anchor sets scrollTarget`() {
        viewModel.handleIntent(DashboardIntent.SetSelectedSegment(GraphSegment.YEAR, ANCHOR_TIMESTAMP))
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.YEAR)
        assertThat(viewModel.state.value.scrollTarget).isEqualTo(ANCHOR_TIMESTAMP.toDouble())
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `SetLatestWeight updates latestWeight`() {
        viewModel.handleIntent(DashboardIntent.SetLatestWeight(LATEST_WEIGHT))
        assertThat(viewModel.state.value.latestWeight).isEqualTo(LATEST_WEIGHT)
    }

    @Test
    fun `SetLatestWeight with null clears latestWeight`() {
        viewModel.handleIntent(DashboardIntent.SetLatestWeight(LATEST_WEIGHT))
        viewModel.handleIntent(DashboardIntent.SetLatestWeight(null))
        assertThat(viewModel.state.value.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — UpdateIsEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsEmpty sets isEmpty to true`() {
        viewModel.handleIntent(DashboardIntent.UpdateIsEmpty(true))
        assertThat(viewModel.state.value.isEmpty).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetScrollTarget / SetIsScrollTargetConsumed
    // -------------------------------------------------------------------------

    @Test
    fun `SetScrollTarget updates scrollTarget`() {
        viewModel.handleIntent(DashboardIntent.SetScrollTarget(SCROLL_TARGET))
        assertThat(viewModel.state.value.scrollTarget).isEqualTo(SCROLL_TARGET)
    }

    @Test
    fun `SetIsScrollTargetConsumed one-shot pattern`() {
        viewModel.handleIntent(DashboardIntent.SetScrollTarget(SCROLL_TARGET))
        viewModel.handleIntent(DashboardIntent.SetIsScrollTargetConsumed(true))
        assertThat(viewModel.state.value.isScrollTargetConsumed).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetProgress / SetProgressUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `SetProgress updates progress`() {
        val progress = Progress(count = PROGRESS_COUNT, initWt = PROGRESS_INIT_WEIGHT)
        viewModel.handleIntent(DashboardIntent.SetProgress(progress))
        assertThat(viewModel.state.value.progress).isEqualTo(progress)
    }

    @Test
    fun `SetProgressUpdating updates isProgressUpdating`() {
        viewModel.handleIntent(DashboardIntent.SetProgressUpdating(true))
        assertThat(viewModel.state.value.isProgressUpdating).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetVisibleKeys
    // -------------------------------------------------------------------------

    @Test
    fun `SetVisibleKeys replaces visibleKeys`() {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(DashboardIntent.SetVisibleKeys(keys))
        assertThat(viewModel.state.value.visibleKeys).isEqualTo(keys)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetPagerState
    // -------------------------------------------------------------------------

    @Test
    fun `SetPagerState updates pagerState and sets selectedSegment`() = runTest {
        viewModel.handleIntent(DashboardIntent.SetPagerState(PAGER_INDEX))
        advanceUntilIdle()
        assertThat(viewModel.state.value.pagerState).isEqualTo(PAGER_INDEX)
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.entries[PAGER_INDEX])
    }

    @Test
    fun `SetPagerState with out-of-bounds index only updates pagerState`() {
        viewModel.handleIntent(DashboardIntent.SetPagerState(99))
        assertThat(viewModel.state.value.pagerState).isEqualTo(99)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetIsChartConsuming
    // -------------------------------------------------------------------------

    @Test
    fun `SetIsChartConsuming updates isConsuming`() {
        viewModel.handleIntent(DashboardIntent.SetIsChartConsuming(true))
        assertThat(viewModel.state.value.isConsuming).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — SetDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardType updates dashboardType`() {
        viewModel.handleIntent(DashboardIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS))
        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — UpdateIsRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsRefreshing updates flag`() {
        viewModel.handleIntent(DashboardIntent.UpdateIsRefreshing(true))
        assertThat(viewModel.state.value.isRefreshing).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents — UpdateWeightLess
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateWeightLess updates weightless`() {
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = WEIGHTLESS_WEIGHT)
        viewModel.handleIntent(DashboardIntent.UpdateWeightLess(weightless))
        assertThat(viewModel.state.value.weightless).isEqualTo(weightless)
    }

    // -------------------------------------------------------------------------
    // Async — Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh calls entryService syncOperations and dashboardService refreshDashboard`() = runTest {
        viewModel.handleIntent(DashboardIntent.Refresh)
        advanceUntilIdle()
        coVerify { entryService.syncOperations() }
        coVerify { dashboardService.refreshDashboard() }
        coVerify { accountService.refreshAccount() }
    }

    @Test
    fun `Refresh sets isRefreshing false after completion`() = runTest {
        viewModel.handleIntent(DashboardIntent.Refresh)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // Async — UpdateVisibleKeys
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateVisibleKeys calls dashboardService updateVisibleKeys`() = runTest {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(DashboardIntent.UpdateVisibleKeys(keys, DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        coVerify { dashboardService.updateVisibleKeys(keys = keys, dashboardType = DashboardType.DASHBOARD_4_METRICS) }
    }

    @Test
    fun `UpdateVisibleKeys shows loader and dismisses it`() = runTest {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(DashboardIntent.UpdateVisibleKeys(keys, DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `UpdateVisibleKeys clears selectedStat after success`() = runTest {
        val stat: Stat = mockk(relaxed = true)
        viewModel.handleIntent(DashboardIntent.SetSelectedStat(stat))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isEqualTo(stat)

        viewModel.handleIntent(DashboardIntent.UpdateVisibleKeys(emptyList(), DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isNull()
    }

    @Test
    fun `UpdateVisibleKeys dismisses loader on exception`() = runTest {
        coEvery { dashboardService.updateVisibleKeys(any(), any(), any()) } throws RuntimeException("fail")
        viewModel.handleIntent(DashboardIntent.UpdateVisibleKeys(emptyList(), DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Async — ResetDashboard
    // -------------------------------------------------------------------------

    @Test
    fun `ResetDashboard enqueues confirm dialog`() {
        viewModel.handleIntent(DashboardIntent.ResetDashboard { })
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ResetDashboard confirm callback calls dashboardService resetVisibleKeys`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(DashboardIntent.ResetDashboard { })
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { dashboardService.resetVisibleKeys(dashboardType = any()) }
    }

    @Test
    fun `ResetDashboard confirm callback invokes onConfirm lambda`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        var confirmCalled = false
        viewModel.handleIntent(DashboardIntent.ResetDashboard { confirmCalled = true })
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        assertThat(confirmCalled).isTrue()
    }

    @Test
    fun `ResetDashboard confirm shows loader and dismisses it`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(DashboardIntent.ResetDashboard { })
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `ResetDashboard confirm dismisses loader on exception`() = runTest {
        coEvery { dashboardService.resetVisibleKeys(any(), any()) } throws RuntimeException("fail")
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(DashboardIntent.ResetDashboard { })
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Navigation — OnConnectScale
    // -------------------------------------------------------------------------

    @Test
    fun `OnConnectScale navigates to AddEditScales`() = runTest {
        viewModel.handleIntent(DashboardIntent.OnConnectScale)
        advanceUntilIdle()
      coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyDevices) }
    }

    // -------------------------------------------------------------------------
    // SetSelectedStat
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedStat updates selectedStat and calls dashboardService`() = runTest {
        val stat: Stat = mockk()
        every { stat.key } returns TEST_DASHBOARD_KEY
        viewModel.handleIntent(DashboardIntent.SetSelectedStat(stat))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isEqualTo(stat)
        coVerify { dashboardService.setSelectedKey(TEST_DASHBOARD_KEY) }
    }

    @Test
    fun `SetSelectedStat with null clears stat and calls dashboardService`() = runTest {
        viewModel.handleIntent(DashboardIntent.SetSelectedStat(null))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isNull()
        coVerify { dashboardService.setSelectedKey(null) }
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeLatestWeight sets latestWeight from ScaleEntry`() = runTest {
        val entry = TestFixtures.weightEntry
        every { entryService.latestEntry } returns MutableStateFlow(entry)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isEqualTo(75.0)
    }

    @Test
    fun `subscribeLatestWeight sets null for non-ScaleEntry`() = runTest {
        every { entryService.latestEntry } returns MutableStateFlow(TestFixtures.bpmEntry)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeIsEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeIsEmpty updates isEmpty from entryService flow`() = runTest {
        every { entryService.isEmpty } returns MutableStateFlow(true)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isEmpty).isTrue()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeDashboardType updates to DASHBOARD_12_METRICS when account emits`() = runTest {
        val account12 = TestFixtures.anAccount(
            id = TEST_ACCOUNT_12_ID,
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(dashboardType = DashboardType.DASHBOARD_12_METRICS.value)

        val accountFlow = MutableStateFlow<Account?>(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        accountFlow.value = account12
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `subscribeDashboardType ignores null account`() = runTest {
        val accountFlow = MutableStateFlow<Account?>(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        accountFlow.value = null
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeMetrics updates visibleKeys after drop 1`() = runTest {
        val keysFlow = MutableStateFlow<List<DashboardKey>>(emptyList())
        every { dashboardService.visibleKeys } returns keysFlow

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        val newKeys = listOf(TEST_DASHBOARD_KEY)
        keysFlow.value = newKeys
        advanceUntilIdle()

        assertThat(viewModel.state.value.visibleKeys).isEqualTo(newKeys)
    }

    // -------------------------------------------------------------------------
    // Lifecycle — onResume
    // -------------------------------------------------------------------------

    @Test
    fun `onResume calls healthConnectOutOfSync when out of sync`() = runTest {
        every { healthConnectService.outOfSyncState } returns flowOf(true)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)

        viewModel.onResume(mockk())
        advanceUntilIdle()

        coVerify { healthConnectService.healthConnectOutOfSync() }
    }

    @Test
    fun `onResume does not call healthConnectOutOfSync when in sync`() = runTest {
        every { healthConnectService.outOfSyncState } returns flowOf(false)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)

        viewModel.onResume(mockk())
        advanceUntilIdle()

        coVerify(exactly = 0) { healthConnectService.healthConnectOutOfSync() }
    }

    // -------------------------------------------------------------------------
    // initLoadData — sets initial dashboard type, visible keys, and weightless
    // -------------------------------------------------------------------------

    @Test
    fun `initLoadData sets dashboard type from active account with 12 metrics`() = runTest {
        val account12 = TestFixtures.anAccount(
            id = TEST_ACCOUNT_12_ID,
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(dashboardType = DashboardType.DASHBOARD_12_METRICS.value)
        every { accountService.activeAccount } returns MutableStateFlow(account12)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `initLoadData sets visible keys from dashboardService`() = runTest {
        val keys = listOf(TEST_DASHBOARD_KEY)
        every { dashboardService.visibleKeys } returns MutableStateFlow(keys)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.visibleKeys).isEqualTo(keys)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeWeightLess
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeWeightLess updates weightless when account flow emits`() = runTest {
        val accountFlow = MutableStateFlow<Account?>(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        val accountWithWeightless = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(isWeightlessOn = true, weightlessWeight = WEIGHTLESS_WEIGHT)
        accountFlow.value = accountWithWeightless
        advanceUntilIdle()

        assertThat(viewModel.state.value.weightless).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeProgress
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeProgress updates progress from entryService flow`() = runTest {
        val progress = Progress(count = PROGRESS_COUNT, initWt = PROGRESS_INIT_WEIGHT)
        every { entryService.progress } returns flowOf(progress)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.progress).isEqualTo(progress)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeProgressUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeProgressUpdating updates isProgressUpdating from entryService flow`() = runTest {
        every { entryService.isUpdating } returns MutableStateFlow(true)

        viewModel = DashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dashboardService = dashboardService,
            healthConnectService = healthConnectService,
            goalService = goalService,
        ).initTestDependencies(navigationService = navigationService, dialogQueueService = dialogQueueService)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isProgressUpdating).isTrue()
    }

    // -------------------------------------------------------------------------
    // navigateTo
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo calls navigationService with given route`() = runTest {
        viewModel.navigateTo(AppRoute.AccountSettings.HelpScreen)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.HelpScreen) }
    }
}
