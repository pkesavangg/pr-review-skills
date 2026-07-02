package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.GraphData
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardViewModel
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
    lateinit var entryReadService: IEntryReadService

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxUnitFun = true)
    lateinit var dashboardService: IDashboardService

    @MockK(relaxed = true)
    lateinit var healthConnectService: IHealthConnectService

    @MockK(relaxed = true)
    lateinit var goalService: IGoalService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: WeightDashboardViewModel

    companion object {
        private const val LATEST_WEIGHT = 80.5
        private const val PROGRESS_COUNT = 5
        private const val PROGRESS_INIT_WEIGHT = 85.0
        private const val WEIGHTLESS_WEIGHT = 150f
        private const val TEST_ACCOUNT_12_ID = "test-12"
        private val TEST_DASHBOARD_KEY = DashboardKey.Metric(
            com.dmdbrands.gurus.weight.domain.enums.MetricKey.WEIGHT,
        )
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun createViewModel(): WeightDashboardViewModel =
        WeightDashboardViewModel(
            entryService = entryService,
            accountService = accountService,
            dashboardService = dashboardService,
            goalService = goalService,
            healthConnectService = healthConnectService,
            entryReadService = entryReadService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    private fun stubDefaultFlows() {
        every { accountService.activeAccount } returns MutableStateFlow(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { dashboardService.visibleKeys } returns MutableStateFlow(emptyList())
        every { dashboardService.selectedKey } returns MutableStateFlow(null)
        every { entryReadService.isWeightEmpty() } returns flowOf(false)
        every { entryReadService.weightProgress() } returns flowOf(WeightProgress())
        every { entryReadService.latestEntry() } returns flowOf(null)
        every { entryReadService.getDailyGraphData(any()) } returns flowOf(GraphData.Weight(emptyList()))
        every { entryReadService.getMonthlyGraphData(any()) } returns flowOf(GraphData.Weight(emptyList()))
        every { entryService.isUpdating } returns MutableStateFlow(false)
        every { healthConnectService.outOfSyncState } returns flowOf(false)
        coEvery { goalService.getCurrentGoal() } returns flowOf(null)
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
        assertThat(state.scrollTarget).isNull()
        assertThat(state.markerIndex).isNull()
    }

    @Test
    fun `initLoadData sets dashboardType from active account`() {
        val state = viewModel.state.value
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (routed through reducer via handleIntent)
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedSegment updates selectedSegment to MONTH`() {
        viewModel.handleIntent(BaseGraphIntent.SetSelectedSegment(GraphSegment.MONTH))
        assertThat(viewModel.state.value.selectedSegment).isEqualTo(GraphSegment.MONTH)
    }

    @Test
    fun `SetLatestWeight updates latestWeight`() {
        viewModel.handleIntent(WeightDashboardIntent.SetLatestWeight(LATEST_WEIGHT))
        assertThat(viewModel.state.value.latestWeight).isEqualTo(LATEST_WEIGHT)
    }

    @Test
    fun `SetIsEmpty sets isEmpty to true`() {
        viewModel.handleIntent(WeightDashboardIntent.SetIsEmpty(true))
        assertThat(viewModel.state.value.isEmpty).isTrue()
    }

    @Test
    fun `SetProgress updates progress`() {
        val progress = WeightProgress(count = PROGRESS_COUNT, initWt = PROGRESS_INIT_WEIGHT)
        viewModel.handleIntent(WeightDashboardIntent.SetProgress(progress))
        assertThat(viewModel.state.value.progress).isEqualTo(progress)
    }

    @Test
    fun `SetProgressUpdating updates isProgressUpdating`() {
        viewModel.handleIntent(WeightDashboardIntent.SetProgressUpdating(true))
        assertThat(viewModel.state.value.isProgressUpdating).isTrue()
    }

    @Test
    fun `SetVisibleKeys replaces visibleKeys`() {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(WeightDashboardIntent.SetVisibleKeys(keys))
        assertThat(viewModel.state.value.visibleKeys).isEqualTo(keys)
    }

    @Test
    fun `SetDashboardType updates dashboardType`() {
        viewModel.handleIntent(WeightDashboardIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS))
        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `SetWeightless updates weightless`() {
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = WEIGHTLESS_WEIGHT)
        viewModel.handleIntent(WeightDashboardIntent.SetWeightless(weightless))
        assertThat(viewModel.state.value.weightless).isEqualTo(weightless)
    }

    // -------------------------------------------------------------------------
    // Async — Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh calls entryService syncOperations and dashboardService refreshDashboard`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(WeightDashboardIntent.Refresh)
        advanceUntilIdle()
        coVerify { entryService.syncOperations() }
        coVerify { dashboardService.refreshDashboard() }
        coVerify { accountService.refreshAccount() }
    }

    @Test
    fun `Refresh sets isRefreshing false after completion`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(WeightDashboardIntent.Refresh)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // Async — UpdateVisibleKeys
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateVisibleKeys calls dashboardService updateVisibleKeys`() = runTest(mainDispatcherRule.scheduler) {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(WeightDashboardIntent.UpdateVisibleKeys(keys, DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        coVerify { dashboardService.updateVisibleKeys(keys = keys, dashboardType = DashboardType.DASHBOARD_4_METRICS) }
    }

    @Test
    fun `UpdateVisibleKeys shows loader and dismisses it`() = runTest(mainDispatcherRule.scheduler) {
        val keys = listOf(TEST_DASHBOARD_KEY)
        viewModel.handleIntent(WeightDashboardIntent.UpdateVisibleKeys(keys, DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `UpdateVisibleKeys clears selectedStat after success`() = runTest(mainDispatcherRule.scheduler) {
        val stat: Stat = mockk(relaxed = true)
        viewModel.handleIntent(WeightDashboardIntent.SetSelectedStat(stat))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isEqualTo(stat)

        viewModel.handleIntent(WeightDashboardIntent.UpdateVisibleKeys(emptyList(), DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isNull()
    }

    @Test
    fun `UpdateVisibleKeys dismisses loader on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { dashboardService.updateVisibleKeys(any(), any(), any()) } throws RuntimeException("fail")
        viewModel.handleIntent(WeightDashboardIntent.UpdateVisibleKeys(emptyList(), DashboardType.DASHBOARD_4_METRICS))
        advanceUntilIdle()
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Async — ResetDashboard
    // -------------------------------------------------------------------------

    @Test
    fun `ResetDashboard enqueues confirm dialog`() {
        viewModel.handleIntent(WeightDashboardIntent.ResetDashboard)
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ResetDashboard confirm callback calls dashboardService resetVisibleKeys`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(WeightDashboardIntent.ResetDashboard)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { dashboardService.resetVisibleKeys(dashboardType = any()) }
    }

    @Test
    fun `ResetDashboard confirm shows loader and dismisses it`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(WeightDashboardIntent.ResetDashboard)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `ResetDashboard confirm dismisses loader on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { dashboardService.resetVisibleKeys(any(), any()) } throws RuntimeException("fail")
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(WeightDashboardIntent.ResetDashboard)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Navigation — OnConnectScale / NavigateToGoal
    // -------------------------------------------------------------------------

    @Test
    fun `OnConnectScale navigates to MyDevices`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(WeightDashboardIntent.OnConnectScale)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyDevices) }
    }

    @Test
    fun `NavigateToGoal navigates to Goal`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(WeightDashboardIntent.NavigateToGoal)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.Goal) }
    }

    // -------------------------------------------------------------------------
    // SetSelectedStat
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedStat updates selectedStat and calls dashboardService`() = runTest(mainDispatcherRule.scheduler) {
        val stat: Stat = mockk()
        every { stat.key } returns TEST_DASHBOARD_KEY
        viewModel.handleIntent(WeightDashboardIntent.SetSelectedStat(stat))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isEqualTo(stat)
        coVerify { dashboardService.setSelectedKey(TEST_DASHBOARD_KEY) }
    }

    @Test
    fun `SetSelectedStat with null clears stat and calls dashboardService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(WeightDashboardIntent.SetSelectedStat(null))
        advanceUntilIdle()
        assertThat(viewModel.state.value.selectedStat).isNull()
        coVerify { dashboardService.setSelectedKey(null) }
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeLatestWeight sets latestWeight from ScaleEntry`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.latestEntry() } returns flowOf(TestFixtures.weightEntry)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isEqualTo(75.0)
    }

    @Test
    fun `subscribeLatestWeight sets null for non-ScaleEntry`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.latestEntry() } returns flowOf(TestFixtures.bpmEntry)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeIsEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeIsEmpty updates isEmpty from entryReadService flow`() = runTest(mainDispatcherRule.scheduler) {
        every { entryReadService.isWeightEmpty() } returns flowOf(true)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isEmpty).isTrue()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeDashboardType updates to DASHBOARD_12_METRICS when account emits`() = runTest(mainDispatcherRule.scheduler) {
        val account12 = TestFixtures.anAccount(
            id = TEST_ACCOUNT_12_ID,
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(dashboardType = DashboardType.DASHBOARD_12_METRICS.value)

        val accountFlow = MutableStateFlow<Account?>(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        accountFlow.value = account12
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `subscribeDashboardType ignores null account`() = runTest(mainDispatcherRule.scheduler) {
        val accountFlow = MutableStateFlow<Account?>(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        accountFlow.value = null
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeMetrics updates visibleKeys after drop 1`() = runTest(mainDispatcherRule.scheduler) {
        val keysFlow = MutableStateFlow<List<DashboardKey>>(emptyList())
        every { dashboardService.visibleKeys } returns keysFlow

        viewModel = createViewModel()
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
    fun `onResume calls healthConnectOutOfSync when out of sync`() = runTest(mainDispatcherRule.scheduler) {
        every { healthConnectService.outOfSyncState } returns flowOf(true)

        viewModel = createViewModel()

        viewModel.onResume(mockk())
        advanceUntilIdle()

        coVerify { healthConnectService.healthConnectOutOfSync() }
    }

    @Test
    fun `onResume does not call healthConnectOutOfSync when in sync`() = runTest(mainDispatcherRule.scheduler) {
        every { healthConnectService.outOfSyncState } returns flowOf(false)

        viewModel = createViewModel()

        viewModel.onResume(mockk())
        advanceUntilIdle()

        coVerify(exactly = 0) { healthConnectService.healthConnectOutOfSync() }
    }

    // -------------------------------------------------------------------------
    // initLoadData — initial dashboard type and visible keys
    // -------------------------------------------------------------------------

    @Test
    fun `initLoadData sets dashboard type from active account with 12 metrics`() = runTest(mainDispatcherRule.scheduler) {
        val account12 = TestFixtures.anAccount(
            id = TEST_ACCOUNT_12_ID,
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(dashboardType = DashboardType.DASHBOARD_12_METRICS.value)
        every { accountService.activeAccount } returns MutableStateFlow(account12)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    @Test
    fun `initLoadData sets visible keys from dashboardService`() = runTest(mainDispatcherRule.scheduler) {
        val keys = listOf(TEST_DASHBOARD_KEY)
        every { dashboardService.visibleKeys } returns MutableStateFlow(keys)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.visibleKeys).isEqualTo(keys)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeWeightless
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeWeightless updates weightless when account flow emits`() = runTest(mainDispatcherRule.scheduler) {
        val accountWithWeightless = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        ).copy(isWeightlessOn = true, weightlessWeight = WEIGHTLESS_WEIGHT)
        every { accountService.activeAccountFlow } returns flowOf(accountWithWeightless)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.weightless).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeProgress
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeProgress updates progress from entryReadService flow`() = runTest(mainDispatcherRule.scheduler) {
        val progress = WeightProgress(count = PROGRESS_COUNT, initWt = PROGRESS_INIT_WEIGHT)
        every { entryReadService.weightProgress() } returns flowOf(progress)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.progress).isEqualTo(progress)
    }

    // -------------------------------------------------------------------------
    // Flow Subscriptions — subscribeProgressUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeProgressUpdating updates isProgressUpdating from entryService flow`() = runTest(mainDispatcherRule.scheduler) {
        every { entryService.isUpdating } returns MutableStateFlow(true)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isProgressUpdating).isTrue()
    }
}
