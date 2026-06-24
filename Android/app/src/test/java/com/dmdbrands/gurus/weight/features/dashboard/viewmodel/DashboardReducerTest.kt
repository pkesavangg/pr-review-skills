package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.common.Streak
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardReducer
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WeightDashboardReducer].
 *
 * The reducer is a pure function `(WeightDashboardState, BaseGraphIntent) -> WeightDashboardState?`.
 * Weight-specific intents ([WeightDashboardIntent]) update product fields directly; shared
 * chart intents ([BaseGraphIntent]) delegate to the base reducer and operate on the
 * per-segment [SegmentState] map.
 */
class DashboardReducerTest {

    private lateinit var reducer: WeightDashboardReducer

    companion object {
        private const val TEST_WEIGHT = 180.5
        private const val TEST_ANCHOR_TIMESTAMP = 1700000000.0
    }

    private val fakeProgress = WeightProgress(streak = Streak(current = 5, longest = 10), count = 42)
    private val fakeStat: Stat = mockk(relaxed = true)
    private val fakeWeightless = Weightless(isWeightlessOn = true, weightlessWeight = 5.0f)
    private val fakeSummaryA: PeriodBodyScaleSummary = mockk(relaxed = true)
    private val fakeSummaryB: PeriodBodyScaleSummary = mockk(relaxed = true)
    private val fakeDashboardKey = DashboardKey.Metric(MetricKey.WEIGHT)

    @BeforeEach
    fun setUp() {
        reducer = WeightDashboardReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default WeightDashboardState has expected initial values`() {
        val state = WeightDashboardState()

        assertThat(state.visibleKeys).isEmpty()
        assertThat(state.data).isEmpty()
        assertThat(state.latestWeight).isNull()
        assertThat(state.progress).isEqualTo(WeightProgress())
        assertThat(state.isProgressUpdating).isFalse()
        assertThat(state.selectedSegment).isEqualTo(GraphSegment.WEEK)
        assertThat(state.selectedStat).isNull()
        assertThat(state.scrollTarget).isNull()
        assertThat(state.markerIndex).isNull()
        assertThat(state.isEmpty).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.weightless).isNull()
        assertThat(state.goal).isNull()
        assertThat(state.secondaryKey).isNull()
        assertThat(state.weightUnit).isEqualTo(WeightUnit.LB)
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
        assertThat(state.segmentStates).isEmpty()
        assertThat(state.resetSignal).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // ResetComplete — one-shot signal (MOB-445)
    // -------------------------------------------------------------------------

    @Test
    fun `ResetComplete increments resetSignal`() {
        val state = WeightDashboardState(resetSignal = 0)

        val result = reducer.reduce(state, WeightDashboardIntent.ResetComplete)

        assertThat(result?.resetSignal).isEqualTo(1)
    }

    @Test
    fun `ResetComplete increments resetSignal on each emission`() {
        val state = WeightDashboardState(resetSignal = 3)

        val result = reducer.reduce(state, WeightDashboardIntent.ResetComplete)

        assertThat(result?.resetSignal).isEqualTo(4)
    }

    // -------------------------------------------------------------------------
    // Base intent — SetRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `SetRefreshing true sets isRefreshing to true`() {
        val state = WeightDashboardState(isRefreshing = false)

        val result = reducer.reduce(state, BaseGraphIntent.SetRefreshing(true))

        assertThat(result?.isRefreshing).isTrue()
    }

    @Test
    fun `SetRefreshing false sets isRefreshing to false`() {
        val state = WeightDashboardState(isRefreshing = true)

        val result = reducer.reduce(state, BaseGraphIntent.SetRefreshing(false))

        assertThat(result?.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetIsEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `SetIsEmpty true sets isEmpty to true`() {
        val state = WeightDashboardState(isEmpty = false)

        val result = reducer.reduce(state, WeightDashboardIntent.SetIsEmpty(true))

        assertThat(result?.isEmpty).isTrue()
    }

    @Test
    fun `SetIsEmpty false sets isEmpty to false`() {
        val state = WeightDashboardState(isEmpty = true)

        val result = reducer.reduce(state, WeightDashboardIntent.SetIsEmpty(false))

        assertThat(result?.isEmpty).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetVisibleKeys
    // -------------------------------------------------------------------------

    @Test
    fun `SetVisibleKeys stores keys as immutable list`() {
        val state = WeightDashboardState()
        val keys = listOf(fakeDashboardKey)

        val result = reducer.reduce(state, WeightDashboardIntent.SetVisibleKeys(keys))

        assertThat(result?.visibleKeys).containsExactly(fakeDashboardKey)
    }

    @Test
    fun `SetVisibleKeys with empty list clears previous keys`() {
        val state = WeightDashboardState(visibleKeys = persistentListOf(fakeDashboardKey))

        val result = reducer.reduce(state, WeightDashboardIntent.SetVisibleKeys(emptyList()))

        assertThat(result?.visibleKeys).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetProgress
    // -------------------------------------------------------------------------

    @Test
    fun `SetProgress updates progress field`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(state, WeightDashboardIntent.SetProgress(fakeProgress))

        assertThat(result?.progress).isEqualTo(fakeProgress)
    }

    // -------------------------------------------------------------------------
    // SetProgressUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `SetProgressUpdating true sets isProgressUpdating to true`() {
        val state = WeightDashboardState(isProgressUpdating = false)

        val result = reducer.reduce(state, WeightDashboardIntent.SetProgressUpdating(true))

        assertThat(result?.isProgressUpdating).isTrue()
    }

    @Test
    fun `SetProgressUpdating false sets isProgressUpdating to false`() {
        val state = WeightDashboardState(isProgressUpdating = true)

        val result = reducer.reduce(state, WeightDashboardIntent.SetProgressUpdating(false))

        assertThat(result?.isProgressUpdating).isFalse()
    }

    // -------------------------------------------------------------------------
    // Base intent — SetSelectedSegment
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedSegment changes segment and sets scrollTarget from anchorTimestamp`() {
        val state = WeightDashboardState(selectedSegment = GraphSegment.WEEK)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.SetSelectedSegment(GraphSegment.MONTH, TEST_ANCHOR_TIMESTAMP),
        )

        assertThat(result?.selectedSegment).isEqualTo(GraphSegment.MONTH)
        assertThat(result?.scrollTarget).isEqualTo(TEST_ANCHOR_TIMESTAMP)
    }

    @Test
    fun `SetSelectedSegment with null anchorTimestamp clears scrollTarget`() {
        val state = WeightDashboardState(selectedSegment = GraphSegment.WEEK, scrollTarget = 999.0)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.SetSelectedSegment(GraphSegment.YEAR, anchorTimestamp = null),
        )

        assertThat(result?.selectedSegment).isEqualTo(GraphSegment.YEAR)
        assertThat(result?.scrollTarget).isNull()
    }

    @Test
    fun `SetSelectedSegment clears markerIndex`() {
        val state = WeightDashboardState(selectedSegment = GraphSegment.WEEK, markerIndex = 42.0)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.SetSelectedSegment(GraphSegment.MONTH),
        )

        assertThat(result?.markerIndex).isNull()
    }

    // -------------------------------------------------------------------------
    // Base intent — UpdateMarkerIndex
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMarkerIndex stores non-null marker index`() {
        val state = WeightDashboardState(markerIndex = null)

        val result = reducer.reduce(state, BaseGraphIntent.UpdateMarkerIndex(42.0))

        assertThat(result?.markerIndex).isEqualTo(42.0)
    }

    @Test
    fun `UpdateMarkerIndex with null clears marker index`() {
        val state = WeightDashboardState(markerIndex = 42.0)

        val result = reducer.reduce(state, BaseGraphIntent.UpdateMarkerIndex(null))

        assertThat(result?.markerIndex).isNull()
    }

    // -------------------------------------------------------------------------
    // SetSelectedStat
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedStat updates selectedStat`() {
        val state = WeightDashboardState(selectedStat = null)

        val result = reducer.reduce(state, WeightDashboardIntent.SetSelectedStat(fakeStat))

        assertThat(result?.selectedStat).isEqualTo(fakeStat)
    }

    @Test
    fun `SetSelectedStat with null clears selectedStat`() {
        val state = WeightDashboardState(selectedStat = fakeStat)

        val result = reducer.reduce(state, WeightDashboardIntent.SetSelectedStat(null))

        assertThat(result?.selectedStat).isNull()
    }

    // -------------------------------------------------------------------------
    // SetData
    // -------------------------------------------------------------------------

    @Test
    fun `SetData stores data as immutable list`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            WeightDashboardIntent.SetData(listOf(fakeSummaryA, fakeSummaryB)),
        )

        assertThat(result?.data).containsExactly(fakeSummaryA, fakeSummaryB).inOrder()
    }

    @Test
    fun `SetData with empty list clears previous data`() {
        val state = WeightDashboardState(data = persistentListOf(fakeSummaryA))

        val result = reducer.reduce(state, WeightDashboardIntent.SetData(emptyList()))

        assertThat(result?.data).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardType updates dashboardType`() {
        val state = WeightDashboardState(dashboardType = DashboardType.DASHBOARD_4_METRICS)

        val result = reducer.reduce(
            state,
            WeightDashboardIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS),
        )

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // SetLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `SetLatestWeight updates latestWeight`() {
        val state = WeightDashboardState(latestWeight = null)

        val result = reducer.reduce(state, WeightDashboardIntent.SetLatestWeight(TEST_WEIGHT))

        assertThat(result?.latestWeight).isEqualTo(TEST_WEIGHT)
    }

    @Test
    fun `SetLatestWeight with null clears latestWeight`() {
        val state = WeightDashboardState(latestWeight = TEST_WEIGHT)

        val result = reducer.reduce(state, WeightDashboardIntent.SetLatestWeight(null))

        assertThat(result?.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // SetWeightUnit
    // -------------------------------------------------------------------------

    @Test
    fun `SetWeightUnit updates weightUnit`() {
        val state = WeightDashboardState(weightUnit = WeightUnit.LB)

        val result = reducer.reduce(state, WeightDashboardIntent.SetWeightUnit(WeightUnit.KG))

        assertThat(result?.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // SetGoal
    // -------------------------------------------------------------------------

    @Test
    fun `SetGoal with null clears goal`() {
        val state = WeightDashboardState(goal = mockk(relaxed = true))

        val result = reducer.reduce(state, WeightDashboardIntent.SetGoal(null))

        assertThat(result?.goal).isNull()
    }

    // -------------------------------------------------------------------------
    // SetSecondaryKey
    // -------------------------------------------------------------------------

    @Test
    fun `SetSecondaryKey stores the key`() {
        val state = WeightDashboardState(secondaryKey = null)

        val result = reducer.reduce(state, WeightDashboardIntent.SetSecondaryKey(fakeDashboardKey))

        assertThat(result?.secondaryKey).isEqualTo(fakeDashboardKey)
    }

    @Test
    fun `SetSecondaryKey with null clears the key`() {
        val state = WeightDashboardState(secondaryKey = fakeDashboardKey)

        val result = reducer.reduce(state, WeightDashboardIntent.SetSecondaryKey(null))

        assertThat(result?.secondaryKey).isNull()
    }

    // -------------------------------------------------------------------------
    // SetWeightless
    // -------------------------------------------------------------------------

    @Test
    fun `SetWeightless updates weightless`() {
        val state = WeightDashboardState(weightless = null)

        val result = reducer.reduce(state, WeightDashboardIntent.SetWeightless(fakeWeightless))

        assertThat(result?.weightless).isEqualTo(fakeWeightless)
    }

    @Test
    fun `SetWeightless with null clears weightless`() {
        val state = WeightDashboardState(weightless = fakeWeightless)

        val result = reducer.reduce(state, WeightDashboardIntent.SetWeightless(null))

        assertThat(result?.weightless).isNull()
    }

    // -------------------------------------------------------------------------
    // Base intent — UpdateSegment
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSegment applies update to the targeted segment state`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(isEmptyGraph = true) },
        )

        assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.isEmptyGraph).isTrue()
    }

    // -------------------------------------------------------------------------
    // Base intent — UpdateIsEmptyGraph
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsEmptyGraph sets isEmptyGraph on the segment`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateIsEmptyGraph(GraphSegment.MONTH, isEmpty = true),
        )

        assertThat(result?.segmentStates?.get(GraphSegment.MONTH)?.isEmptyGraph).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh returns state unchanged`() {
        val state = WeightDashboardState(isRefreshing = true, isEmpty = false)

        val result = reducer.reduce(state, WeightDashboardIntent.Refresh)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ResetDashboard returns state unchanged`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(state, WeightDashboardIntent.ResetDashboard)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnConnectScale returns state unchanged`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(state, WeightDashboardIntent.OnConnectScale)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `NavigateToGoal returns state unchanged`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(state, WeightDashboardIntent.NavigateToGoal)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `UpdateVisibleKeys returns state unchanged by reducer`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            WeightDashboardIntent.UpdateVisibleKeys(
                keys = listOf(fakeDashboardKey),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
            ),
        )

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Base intent — ScrollRange
    // -------------------------------------------------------------------------

    @Test
    fun `ScrollRange stores visible range on the segment`() {
        val result = reducer.reduce(
            WeightDashboardState(),
            BaseGraphIntent.ScrollRange(GraphSegment.WEEK, min = 100L, max = 200L),
        )

        val segment = result?.segmentStates?.get(GraphSegment.WEEK)
        assertThat(segment?.visibleMin).isEqualTo(100L)
        assertThat(segment?.visibleMax).isEqualTo(200L)
    }

    // -------------------------------------------------------------------------
    // Base intent — UpdateSegmentTarget
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSegmentTarget stores target on the segment`() {
        val result = reducer.reduce(
            WeightDashboardState(),
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, listOf(fakeSummaryA, fakeSummaryB)),
        )

        assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.target)
            .containsExactly(fakeSummaryA, fakeSummaryB).inOrder()
    }

    @Test
    fun `UpdateSegmentTarget keeps null markerIndex with empty target`() {
        val result = reducer.reduce(
            WeightDashboardState(markerIndex = null),
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, emptyList()),
        )

        assertThat(result?.markerIndex).isNull()
    }

    // -------------------------------------------------------------------------
    // Base intent — UpdateSeedYRange
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSeedYRange stores seed Y range on the segment`() {
        val result = reducer.reduce(
            WeightDashboardState(),
            BaseGraphIntent.UpdateSeedYRange(GraphSegment.MONTH, minY = 50.0, maxY = 250.0),
        )

        val segment = result?.segmentStates?.get(GraphSegment.MONTH)
        assertThat(segment?.seedMinY).isEqualTo(50.0)
        assertThat(segment?.seedMaxY).isEqualTo(250.0)
    }
}
