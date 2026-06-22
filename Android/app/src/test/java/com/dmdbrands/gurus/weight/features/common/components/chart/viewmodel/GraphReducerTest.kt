package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardReducer
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.weight.WeightDashboardState
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.toImmutableList
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for the shared chart reducer logic.
 *
 * The standalone `GraphReducer` / `GraphState` / `GraphIntent` were removed in the
 * Phase 2 dashboard refactor. The chart is now driven by [BaseGraphIntent] reduced
 * by `BaseGraphReducer`, with per-segment chart state held in a
 * [SegmentState] map on the product state. These tests exercise that base reducer
 * through its only public concretion, [WeightDashboardReducer], focusing on the
 * shared chart intents (segment data/target, marker, empty-graph, scroll range,
 * seed Y range, selected segment).
 *
 * Deleted (behaviour no longer exists in production):
 * - UpdateGoal / UpdateData / UpdateTarget / UpdatePrimaryYStep / UpdatePrimaryYAxis /
 *   UpdateWeightUnit / UpdateIsUpdating / UpdateIsLoading / UpdateIsSingleWindow /
 *   SetSecondaryKey(on GraphState) / ResetGraph / SetScrollRange — these flat
 *   GraphState fields/intents were replaced by SegmentState + BaseGraphIntent.
 *   The equivalent product-level intents are covered in DashboardReducerTest.
 */
class GraphReducerTest {

    private lateinit var reducer: WeightDashboardReducer

    private fun summaryAt(timestamp: Long): PeriodSummary =
        mockk(relaxed = true) {
            every { getTimeStamp() } returns timestamp
        }

    @BeforeEach
    fun setUp() {
        reducer = WeightDashboardReducer()
    }

    // -------------------------------------------------------------------------
    // Default segment state
    // -------------------------------------------------------------------------

    @Test
    fun `default SegmentState has expected initial values`() {
        val state = SegmentState()

        assertThat(state.data).isEmpty()
        assertThat(state.target).isEmpty()
        assertThat(state.chartMinX).isNull()
        assertThat(state.chartMaxX).isNull()
        assertThat(state.isEmptyGraph).isFalse()
        assertThat(state.isSingleWindow).isFalse()
        assertThat(state.startTimestamp).isNull()
        assertThat(state.endTimestamp).isNull()
        assertThat(state.visibleMin).isNull()
        assertThat(state.visibleMax).isNull()
        assertThat(state.seedMinY).isNull()
        assertThat(state.seedMaxY).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateSegment
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSegment applies the update function to the targeted segment`() {
        val state = WeightDashboardState()
        val item = summaryAt(1000L)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(data = listOf(item).toImmutable()) },
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.WEEK]?.data).containsExactly(item)
    }

    @Test
    fun `UpdateSegment leaves other segments untouched`() {
        val existing = SegmentState(isSingleWindow = true)
        val state = WeightDashboardState(segmentStates = mapOf(GraphSegment.MONTH to existing))

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(isEmptyGraph = true) },
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.MONTH]).isSameInstanceAs(existing)
        assertThat(result.segmentStates[GraphSegment.WEEK]?.isEmptyGraph).isTrue()
    }

    // -------------------------------------------------------------------------
    // UpdateIsEmptyGraph
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsEmptyGraph true sets isEmptyGraph on the segment`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateIsEmptyGraph(GraphSegment.WEEK, isEmpty = true),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.WEEK]?.isEmptyGraph).isTrue()
    }

    @Test
    fun `UpdateIsEmptyGraph false clears isEmptyGraph on the segment`() {
        val state = WeightDashboardState(
            segmentStates = mapOf(GraphSegment.WEEK to SegmentState(isEmptyGraph = true)),
        )

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateIsEmptyGraph(GraphSegment.WEEK, isEmpty = false),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.WEEK]?.isEmptyGraph).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateSegmentTarget
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSegmentTarget stores provided target on the segment`() {
        val state = WeightDashboardState()
        val item = summaryAt(2000L)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.MONTH, listOf(item)),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.MONTH]?.target).containsExactly(item)
    }

    @Test
    fun `UpdateSegmentTarget with empty list clears target`() {
        val state = WeightDashboardState(
            segmentStates = mapOf(GraphSegment.MONTH to SegmentState(target = listOf(summaryAt(1L)).toImmutable())),
        )

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.MONTH, emptyList()),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.MONTH]?.target).isEmpty()
    }

    @Test
    fun `UpdateSegmentTarget clears markerIndex when marker falls outside new target range`() {
        val state = WeightDashboardState(markerIndex = 50.0)
        val target = listOf(summaryAt(1000L), summaryAt(2000L))

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, target),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isNull()
    }

    @Test
    fun `UpdateSegmentTarget keeps markerIndex when marker is within new target range`() {
        val state = WeightDashboardState(markerIndex = 1500.0)
        val target = listOf(summaryAt(1000L), summaryAt(2000L))

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, target),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isEqualTo(1500.0)
    }

    // -------------------------------------------------------------------------
    // UpdateMarkerIndex
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMarkerIndex stores non-null marker index`() {
        val state = WeightDashboardState(markerIndex = null)

        val result = reducer.reduce(state, BaseGraphIntent.UpdateMarkerIndex(42.0))

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isEqualTo(42.0)
    }

    @Test
    fun `UpdateMarkerIndex with null clears marker index`() {
        val state = WeightDashboardState(markerIndex = 42.0)

        val result = reducer.reduce(state, BaseGraphIntent.UpdateMarkerIndex(null))

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isNull()
    }

    // -------------------------------------------------------------------------
    // ScrollRange — stores visible min/max on the segment
    // -------------------------------------------------------------------------

    @Test
    fun `ScrollRange stores visible min and max on the segment`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            BaseGraphIntent.ScrollRange(GraphSegment.WEEK, min = 1000L, max = 9000L),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.WEEK]?.visibleMin).isEqualTo(1000L)
        assertThat(result.segmentStates[GraphSegment.WEEK]?.visibleMax).isEqualTo(9000L)
    }

    // -------------------------------------------------------------------------
    // UpdateSeedYRange
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSeedYRange stores seed min and max Y on the segment`() {
        val state = WeightDashboardState()

        val result = reducer.reduce(
            state,
            BaseGraphIntent.UpdateSeedYRange(GraphSegment.YEAR, minY = 140.0, maxY = 200.0),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.segmentStates[GraphSegment.YEAR]?.seedMinY).isEqualTo(140.0)
        assertThat(result.segmentStates[GraphSegment.YEAR]?.seedMaxY).isEqualTo(200.0)
    }

    // -------------------------------------------------------------------------
    // SetSelectedSegment
    // -------------------------------------------------------------------------

    @Test
    fun `SetSelectedSegment updates selected segment and scroll target`() {
        val state = WeightDashboardState(selectedSegment = GraphSegment.WEEK)

        val result = reducer.reduce(
            state,
            BaseGraphIntent.SetSelectedSegment(GraphSegment.TOTAL, anchorTimestamp = 12345.0),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.selectedSegment).isEqualTo(GraphSegment.TOTAL)
        assertThat(result.scrollTarget).isEqualTo(12345.0)
    }

    private fun List<PeriodSummary>.toImmutable() = this.toImmutableList()
}
