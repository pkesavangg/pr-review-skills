package com.dmdbrands.gurus.weight.features.dashboard.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.common.Streak
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.dmdbrands.gurus.weight.features.common.model.Stat
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DashboardReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class DashboardReducerTest {

    private lateinit var reducer: DashboardReducer

    companion object {
        private const val TEST_WEIGHT = 180.5
        private const val TEST_SCROLL_TARGET = 1234.0
        private const val TEST_PAGER_STATE = 3
        private const val TEST_ANCHOR_TIMESTAMP = 1700000000L
    }

    private val fakeProgress = WeightProgress(streak = Streak(current = 5, longest = 10), count = 42)
    private val fakeStat: Stat = mockk(relaxed = true)
    private val fakeWeightless = Weightless(isWeightlessOn = true, weightlessWeight = 5.0f)
    private val fakeSummaryA: PeriodBodyScaleSummary = mockk(relaxed = true)
    private val fakeSummaryB: PeriodBodyScaleSummary = mockk(relaxed = true)
    private val fakeDashboardKey = DashboardKey.Metric(MetricKey.WEIGHT)

    @BeforeEach
    fun setUp() {
        reducer = DashboardReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default DashboardState has expected initial values`() {
        val state = DashboardState()

        assertThat(state.visibleKeys).isEmpty()
        assertThat(state.data).isEmpty()
        assertThat(state.latestWeight).isNull()
        assertThat(state.progress).isEqualTo(WeightProgress())
        assertThat(state.isProgressUpdating).isFalse()
        assertThat(state.selectedSegment).isEqualTo(GraphSegment.WEEK)
        assertThat(state.selectedStat).isNull()
        assertThat(state.pagerState).isEqualTo(0)
        assertThat(state.scrollTarget).isNull()
        assertThat(state.isScrollTargetConsumed).isFalse()
        assertThat(state.isEmpty).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.weightless).isNull()
        assertThat(state.isConsuming).isFalse()
        assertThat(state.dashboardType).isEqualTo(DashboardType.DASHBOARD_4_METRICS)
    }

    // -------------------------------------------------------------------------
    // UpdateIsRefreshing
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsRefreshing true sets isRefreshing to true`() {
        val state = DashboardState(isRefreshing = false)

        val result = reducer.reduce(state, DashboardIntent.UpdateIsRefreshing(true))

        assertThat(result?.isRefreshing).isTrue()
    }

    @Test
    fun `UpdateIsRefreshing false sets isRefreshing to false`() {
        val state = DashboardState(isRefreshing = true)

        val result = reducer.reduce(state, DashboardIntent.UpdateIsRefreshing(false))

        assertThat(result?.isRefreshing).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateIsEmpty
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsEmpty true sets isEmpty to true`() {
        val state = DashboardState(isEmpty = false)

        val result = reducer.reduce(state, DashboardIntent.UpdateIsEmpty(true))

        assertThat(result?.isEmpty).isTrue()
    }

    @Test
    fun `UpdateIsEmpty false sets isEmpty to false`() {
        val state = DashboardState(isEmpty = true)

        val result = reducer.reduce(state, DashboardIntent.UpdateIsEmpty(false))

        assertThat(result?.isEmpty).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetVisibleKeys
    // -------------------------------------------------------------------------

    @Test
    fun `SetVisibleKeys stores keys as immutable list`() {
        val state = DashboardState()
        val keys = listOf(fakeDashboardKey)

        val result = reducer.reduce(state, DashboardIntent.SetVisibleKeys(keys))

        assertThat(result?.visibleKeys).containsExactly(fakeDashboardKey)
    }

    @Test
    fun `SetVisibleKeys with empty list clears previous keys`() {
        val state = DashboardState(visibleKeys = persistentListOf(fakeDashboardKey))

        val result = reducer.reduce(state, DashboardIntent.SetVisibleKeys(emptyList()))

        assertThat(result?.visibleKeys).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetProgress
    // -------------------------------------------------------------------------

    @Test
    fun `SetProgress updates progress field`() {
        val state = DashboardState()

        val result = reducer.reduce(state, DashboardIntent.SetProgress(fakeProgress))

        assertThat(result?.progress).isEqualTo(fakeProgress)
    }

    // -------------------------------------------------------------------------
    // SetProgressUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `SetProgressUpdating true sets isProgressUpdating to true`() {
        val state = DashboardState(isProgressUpdating = false)

        val result = reducer.reduce(state, DashboardIntent.SetProgressUpdating(true))

        assertThat(result?.isProgressUpdating).isTrue()
    }

    @Test
    fun `SetProgressUpdating false sets isProgressUpdating to false`() {
        val state = DashboardState(isProgressUpdating = true)

        val result = reducer.reduce(state, DashboardIntent.SetProgressUpdating(false))

        assertThat(result?.isProgressUpdating).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetSelectedSegment
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedSegment changes segment and sets scrollTarget from anchorTimestamp`() {
        val state = DashboardState(selectedSegment = GraphSegment.WEEK)

        val result = reducer.reduce(
            state,
            DashboardIntent.SetSelectedSegment(GraphSegment.MONTH, TEST_ANCHOR_TIMESTAMP),
        )

        assertThat(result?.selectedSegment).isEqualTo(GraphSegment.MONTH)
        assertThat(result?.scrollTarget).isEqualTo(TEST_ANCHOR_TIMESTAMP.toDouble())
    }

    @Test
    fun `SetSelectedSegment with null anchorTimestamp sets scrollTarget to null`() {
        val state = DashboardState(selectedSegment = GraphSegment.WEEK, scrollTarget = 999.0)

        val result = reducer.reduce(
            state,
            DashboardIntent.SetSelectedSegment(GraphSegment.YEAR, anchorTimestamp = null),
        )

        assertThat(result?.selectedSegment).isEqualTo(GraphSegment.YEAR)
        assertThat(result?.scrollTarget).isNull()
    }

    @Test
    fun `SetSelectedSegment with same segment returns same state`() {
        val state = DashboardState(selectedSegment = GraphSegment.MONTH, scrollTarget = 500.0)

        val result = reducer.reduce(
            state,
            DashboardIntent.SetSelectedSegment(GraphSegment.MONTH, TEST_ANCHOR_TIMESTAMP),
        )

        // When same segment is selected, state is returned unchanged
        assertThat(result).isSameInstanceAs(state)
        assertThat(result?.scrollTarget).isEqualTo(500.0)
    }

    // -------------------------------------------------------------------------
    // SetIsChartConsuming
    // -------------------------------------------------------------------------

    @Test
    fun `SetIsChartConsuming true sets isConsuming to true`() {
        val state = DashboardState(isConsuming = false)

        val result = reducer.reduce(state, DashboardIntent.SetIsChartConsuming(true))

        assertThat(result?.isConsuming).isTrue()
    }

    @Test
    fun `SetIsChartConsuming false sets isConsuming to false`() {
        val state = DashboardState(isConsuming = true)

        val result = reducer.reduce(state, DashboardIntent.SetIsChartConsuming(false))

        assertThat(result?.isConsuming).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetSelectedStat
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedStat updates selectedStat`() {
        val state = DashboardState(selectedStat = null)

        val result = reducer.reduce(state, DashboardIntent.SetSelectedStat(fakeStat))

        assertThat(result?.selectedStat).isEqualTo(fakeStat)
    }

    @Test
    fun `SetSelectedStat with null clears selectedStat`() {
        val state = DashboardState(selectedStat = fakeStat)

        val result = reducer.reduce(state, DashboardIntent.SetSelectedStat(null))

        assertThat(result?.selectedStat).isNull()
    }

    // -------------------------------------------------------------------------
    // SetData
    // -------------------------------------------------------------------------

    @Test
    fun `SetData stores data as immutable list`() {
        val state = DashboardState()

        val result = reducer.reduce(
            state,
            DashboardIntent.SetData(listOf(fakeSummaryA, fakeSummaryB)),
        )

        assertThat(result?.data).containsExactly(fakeSummaryA, fakeSummaryB).inOrder()
    }

    @Test
    fun `SetData with empty list clears previous data`() {
        val state = DashboardState(data = persistentListOf(fakeSummaryA))

        val result = reducer.reduce(state, DashboardIntent.SetData(emptyList()))

        assertThat(result?.data).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetPagerState
    // -------------------------------------------------------------------------

    @Test
    fun `SetPagerState updates pagerState`() {
        val state = DashboardState(pagerState = 0)

        val result = reducer.reduce(state, DashboardIntent.SetPagerState(TEST_PAGER_STATE))

        assertThat(result?.pagerState).isEqualTo(TEST_PAGER_STATE)
    }

    // -------------------------------------------------------------------------
    // SetScrollTarget
    // -------------------------------------------------------------------------

    @Test
    fun `SetScrollTarget sets scrollTarget`() {
        val state = DashboardState(scrollTarget = null)

        val result = reducer.reduce(state, DashboardIntent.SetScrollTarget(TEST_SCROLL_TARGET))

        assertThat(result?.scrollTarget).isEqualTo(TEST_SCROLL_TARGET)
    }

    @Test
    fun `SetScrollTarget with null clears scrollTarget`() {
        val state = DashboardState(scrollTarget = TEST_SCROLL_TARGET)

        val result = reducer.reduce(state, DashboardIntent.SetScrollTarget(null))

        assertThat(result?.scrollTarget).isNull()
    }

    // -------------------------------------------------------------------------
    // SetDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `SetDashboardType updates dashboardType`() {
        val state = DashboardState(dashboardType = DashboardType.DASHBOARD_4_METRICS)

        val result = reducer.reduce(
            state,
            DashboardIntent.SetDashboardType(DashboardType.DASHBOARD_12_METRICS),
        )

        assertThat(result?.dashboardType).isEqualTo(DashboardType.DASHBOARD_12_METRICS)
    }

    // -------------------------------------------------------------------------
    // SetLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `SetLatestWeight updates latestWeight`() {
        val state = DashboardState(latestWeight = null)

        val result = reducer.reduce(state, DashboardIntent.SetLatestWeight(TEST_WEIGHT))

        assertThat(result?.latestWeight).isEqualTo(TEST_WEIGHT)
    }

    @Test
    fun `SetLatestWeight with null clears latestWeight`() {
        val state = DashboardState(latestWeight = TEST_WEIGHT)

        val result = reducer.reduce(state, DashboardIntent.SetLatestWeight(null))

        assertThat(result?.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateWeightLess
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateWeightLess updates weightless`() {
        val state = DashboardState(weightless = null)

        val result = reducer.reduce(state, DashboardIntent.UpdateWeightLess(fakeWeightless))

        assertThat(result?.weightless).isEqualTo(fakeWeightless)
    }

    @Test
    fun `UpdateWeightLess with null clears weightless`() {
        val state = DashboardState(weightless = fakeWeightless)

        val result = reducer.reduce(state, DashboardIntent.UpdateWeightLess(null))

        assertThat(result?.weightless).isNull()
    }

    // -------------------------------------------------------------------------
    // SetIsScrollTargetConsumed
    // -------------------------------------------------------------------------

    @Test
    fun `SetIsScrollTargetConsumed true sets isScrollTargetConsumed to true`() {
        val state = DashboardState(isScrollTargetConsumed = false)

        val result = reducer.reduce(state, DashboardIntent.SetIsScrollTargetConsumed(true))

        assertThat(result?.isScrollTargetConsumed).isTrue()
    }

    @Test
    fun `SetIsScrollTargetConsumed false sets isScrollTargetConsumed to false`() {
        val state = DashboardState(isScrollTargetConsumed = true)

        val result = reducer.reduce(state, DashboardIntent.SetIsScrollTargetConsumed(false))

        assertThat(result?.isScrollTargetConsumed).isFalse()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `Refresh returns state unchanged`() {
        val state = DashboardState(isRefreshing = true, isEmpty = false)

        val result = reducer.reduce(state, DashboardIntent.Refresh)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ResetDashboard returns state unchanged`() {
        val state = DashboardState()

        val result = reducer.reduce(state, DashboardIntent.ResetDashboard(onConfirm = {}))

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnConnectScale returns state unchanged`() {
        val state = DashboardState()

        val result = reducer.reduce(state, DashboardIntent.OnConnectScale)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `UpdateVisibleKeys returns state unchanged by reducer`() {
        val state = DashboardState()

        val result = reducer.reduce(
            state,
            DashboardIntent.UpdateVisibleKeys(
                keys = listOf(fakeDashboardKey),
                dashboardType = DashboardType.DASHBOARD_12_METRICS,
            ),
        )

        assertThat(result).isEqualTo(state)
    }
}
