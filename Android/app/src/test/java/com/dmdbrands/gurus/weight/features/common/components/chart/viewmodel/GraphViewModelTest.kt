package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var dashboardService: IDashboardService

    @MockK(relaxed = true)
    lateinit var goalService: IGoalService

    @MockK(relaxed = true)
    lateinit var entryService: IEntryService

    @MockK(relaxed = true)
    lateinit var accountService: IAccountService

    private val mockAccount: Account = mockk(relaxed = true) {
        every { weightUnit } returns WeightUnit.LB
    }

    companion object {
        private const val MIN_TARGET = 1710000000L
        private const val MAX_TARGET = 1710086400L
        private const val ANCHOR_SCROLL = 1710050000.0
        private const val MARKER_INDEX = 3.5
        private const val Y_STEP = 5.0
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { entryService.daywiseBodyScaleAverages } returns MutableStateFlow(emptyList())
        every { entryService.monthlyBodyScaleAverages } returns MutableStateFlow(emptyList())
        every { dashboardService.selectedKey } returns MutableStateFlow(null)
        every { dashboardService.getCurrentSelectedKey() } returns null
        coEvery { goalService.getCurrentGoal() } returns flowOf(null)
        every { accountService.activeAccountFlow } returns flowOf(mockAccount)
        every { accountService.activeAccount } returns MutableStateFlow(mockAccount)
    }

    private fun createViewModel(
        segment: GraphSegment = GraphSegment.WEEK,
        anchoredScrollTarget: Double? = null,
    ): GraphViewModel {
        return GraphViewModel(
            segment = segment,
            anchoredScrollTarget = anchoredScrollTarget,
            dashboardService = dashboardService,
            goalService = goalService,
            entryService = entryService,
            accountService = accountService,
        ).initTestDependencies()
    }

    // -------------------------------------------------------------------------
    // Initialization — all 4 segments
    // -------------------------------------------------------------------------

    @Test
    fun `WEEK segment initializes with empty data`() = runTest {
        val vm = createViewModel(segment = GraphSegment.WEEK)
        advanceUntilIdle()
        assertThat(vm.state.value.data).isEmpty()
        assertThat(vm.state.value.isEmptyGraph).isTrue()
    }

    @Test
    fun `MONTH segment initializes with daywise data`() = runTest {
        val vm = createViewModel(segment = GraphSegment.MONTH)
        advanceUntilIdle()
        assertThat(vm.state.value.data).isEmpty()
    }

    @Test
    fun `YEAR segment uses monthly data flow`() = runTest {
        val vm = createViewModel(segment = GraphSegment.YEAR)
        advanceUntilIdle()
        assertThat(vm.state.value.data).isEmpty()
    }

    @Test
    fun `TOTAL segment uses monthly data flow`() = runTest {
        val vm = createViewModel(segment = GraphSegment.TOTAL)
        advanceUntilIdle()
        assertThat(vm.state.value.data).isEmpty()
    }

    @Test
    fun `init with anchored scroll target`() = runTest {
        val vm = createViewModel(anchoredScrollTarget = ANCHOR_SCROLL)
        advanceUntilIdle()
        assertThat(vm.state.value).isNotNull()
    }

    @Test
    fun `init sets weight unit from account`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.weightUnit).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `init with null account weight unit uses default`() = runTest {
        every { accountService.activeAccount } returns MutableStateFlow(null)
        every { accountService.activeAccountFlow } returns flowOf(null)
        val vm = createViewModel()
        advanceUntilIdle()
        // Default is KG from GraphState
        assertThat(vm.state.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // All intents via handleIntent
    // -------------------------------------------------------------------------

    @Test
    fun `SetScrollRange updates state`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.SetScrollRange(MIN_TARGET, MAX_TARGET))
        advanceUntilIdle()
        assertThat(vm.state.value.minTarget).isEqualTo(MIN_TARGET)
        assertThat(vm.state.value.maxTarget).isEqualTo(MAX_TARGET)
    }

    @Test
    fun `UpdateData sets data`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val mockData: List<PeriodBodyScaleSummary> = listOf(mockk(relaxed = true))
        vm.handleIntent(GraphIntent.UpdateData(mockData))
        assertThat(vm.state.value.data).hasSize(1)
    }

    @Test
    fun `UpdateGoal sets goal`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateGoal(mockk(relaxed = true)))
        assertThat(vm.state.value.goal).isNotNull()
    }

    @Test
    fun `UpdateGoal with null clears goal`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateGoal(null))
        assertThat(vm.state.value.goal).isNull()
    }

    @Test
    fun `UpdateTarget sets target`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateTarget(listOf(mockk(relaxed = true))))
        assertThat(vm.state.value.target).hasSize(1)
    }

    @Test
    fun `UpdatePrimaryYStep sets step`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdatePrimaryYStep(Y_STEP))
        assertThat(vm.state.value.primaryYStep).isEqualTo(Y_STEP)
    }

    @Test
    fun `UpdateMarkerIndex sets marker`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateMarkerIndex(MARKER_INDEX))
        assertThat(vm.state.value.markerIndex).isEqualTo(MARKER_INDEX)
    }

    @Test
    fun `UpdateIsUpdating sets flag`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateIsUpdating(true))
        assertThat(vm.state.value.isUpdating).isTrue()
    }

    @Test
    fun `UpdateIsLoading sets flag`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateIsLoading(true))
        assertThat(vm.state.value.isLoading).isTrue()
    }

    @Test
    fun `UpdateIsSingleWindow sets flag`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateIsSingleWindow(true))
        assertThat(vm.state.value.isSingleWindow).isTrue()
    }

    @Test
    fun `UpdateIsEmptyGraph sets flag`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateIsEmptyGraph(false))
        assertThat(vm.state.value.isEmptyGraph).isFalse()
    }

    @Test
    fun `UpdateWeightUnit sets unit`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.UpdateWeightUnit(WeightUnit.KG))
        assertThat(vm.state.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `ResetGraph clears targets, marker, flags`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.SetScrollRange(MIN_TARGET, MAX_TARGET))
        vm.handleIntent(GraphIntent.UpdateMarkerIndex(MARKER_INDEX))
        vm.handleIntent(GraphIntent.UpdateIsUpdating(true))
        vm.handleIntent(GraphIntent.ResetGraph)
        assertThat(vm.state.value.minTarget).isNull()
        assertThat(vm.state.value.maxTarget).isNull()
        assertThat(vm.state.value.markerIndex).isNull()
        assertThat(vm.state.value.isUpdating).isFalse()
        assertThat(vm.state.value.isSingleWindow).isFalse()
    }

    @Test
    fun `UpdatePrimaryYAxis sets yAxis and step`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        val yRange = mockk<com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues>(relaxed = true)
        vm.handleIntent(GraphIntent.UpdatePrimaryYAxis(yRange, Y_STEP))
        assertThat(vm.state.value.primaryYAxis).isEqualTo(yRange)
        assertThat(vm.state.value.primaryYStep).isEqualTo(Y_STEP)
    }

    @Test
    fun `SetSecondaryKey with null clears key`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.handleIntent(GraphIntent.SetSecondaryKey(null))
        assertThat(vm.state.value.secondaryKey).isNull()
    }
}
