package com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel

import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.features.common.model.DashboardKey
import com.patrykandpatrick.vico.core.cartesian.data.CartesianRangeValues
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GraphReducer].
 *
 * The reducer returns [GraphState]? — all successful branches return a non-null copy.
 * Complex Vico/domain types are constructed via mockk(relaxed = true) where needed.
 */
class GraphReducerTest {

    private lateinit var reducer: GraphReducer

    private fun makeState(
        weightUnit: WeightUnit = WeightUnit.LB,
        primaryYStep: Double? = null,
        markerIndex: Double? = null,
        isUpdating: Boolean = false,
        isLoading: Boolean = false,
        isSingleWindow: Boolean = false,
        isEmptyGraph: Boolean = false,
        goal: Goal? = null,
        minTarget: Long? = null,
        maxTarget: Long? = null,
        secondaryKey: DashboardKey? = null,
    ): GraphState = GraphState(
        weightUnit = weightUnit,
        data = persistentListOf(),
        target = persistentListOf(),
        primaryYStep = primaryYStep,
        markerIndex = markerIndex,
        isUpdating = isUpdating,
        isLoading = isLoading,
        isSingleWindow = isSingleWindow,
        isEmptyGraph = isEmptyGraph,
        goal = goal,
        minTarget = minTarget,
        maxTarget = maxTarget,
        secondaryKey = secondaryKey,
    )

    @BeforeEach
    fun setUp() {
        reducer = GraphReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default GraphState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.isUpdating).isFalse()
        assertThat(state.isSingleWindow).isFalse()
        assertThat(state.isEmptyGraph).isFalse()
        assertThat(state.goal).isNull()
        assertThat(state.markerIndex).isNull()
        assertThat(state.minTarget).isNull()
        assertThat(state.maxTarget).isNull()
        assertThat(state.secondaryKey).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateGoal
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateGoal with non-null goal stores the goal`() {
        val state = makeState(goal = null)
        val goal: Goal = mockk(relaxed = true)

        val result = reducer.reduce(state, GraphIntent.UpdateGoal(goal))

        assertThat(result).isNotNull()
        assertThat(result!!.goal).isSameInstanceAs(goal)
    }

    @Test
    fun `UpdateGoal with null clears the goal`() {
        val goal: Goal = mockk(relaxed = true)
        val state = makeState(goal = goal)

        val result = reducer.reduce(state, GraphIntent.UpdateGoal(null))

        assertThat(result).isNotNull()
        assertThat(result!!.goal).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateData
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateData stores provided data list`() {
        val state = makeState()
        val item: PeriodBodyScaleSummary = mockk(relaxed = true)

        val result = reducer.reduce(state, GraphIntent.UpdateData(listOf(item)))

        assertThat(result).isNotNull()
        assertThat(result!!.data).hasSize(1)
        assertThat(result.data[0]).isSameInstanceAs(item)
    }

    @Test
    fun `UpdateData with empty list clears data`() {
        val state = makeState()

        val result = reducer.reduce(state, GraphIntent.UpdateData(emptyList()))

        assertThat(result).isNotNull()
        assertThat(result!!.data).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetSecondaryKey
    // -------------------------------------------------------------------------

    @Test
    fun `SetSecondaryKey with a key stores the key`() {
        val state = makeState(secondaryKey = null)
        val key = DashboardKey.Metric(MetricKey.BMI)

        val result = reducer.reduce(state, GraphIntent.SetSecondaryKey(key))

        assertThat(result).isNotNull()
        assertThat(result!!.secondaryKey).isEqualTo(key)
    }

    @Test
    fun `SetSecondaryKey with null clears the secondary key`() {
        val state = makeState(secondaryKey = DashboardKey.Metric(MetricKey.BODY_FAT))

        val result = reducer.reduce(state, GraphIntent.SetSecondaryKey(null))

        assertThat(result).isNotNull()
        assertThat(result!!.secondaryKey).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateTarget
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateTarget stores provided target list`() {
        val state = makeState()
        val item: PeriodBodyScaleSummary = mockk(relaxed = true)

        val result = reducer.reduce(state, GraphIntent.UpdateTarget(listOf(item)))

        assertThat(result).isNotNull()
        assertThat(result!!.target).hasSize(1)
        assertThat(result.target[0]).isSameInstanceAs(item)
    }

    @Test
    fun `UpdateTarget with empty list clears target`() {
        val state = makeState()

        val result = reducer.reduce(state, GraphIntent.UpdateTarget(emptyList()))

        assertThat(result).isNotNull()
        assertThat(result!!.target).isEmpty()
    }

    // -------------------------------------------------------------------------
    // UpdatePrimaryYStep
    // -------------------------------------------------------------------------

    @Test
    fun `UpdatePrimaryYStep stores provided step value`() {
        val state = makeState(primaryYStep = null)

        val result = reducer.reduce(state, GraphIntent.UpdatePrimaryYStep(5.0))

        assertThat(result).isNotNull()
        assertThat(result!!.primaryYStep).isEqualTo(5.0)
    }

    @Test
    fun `UpdatePrimaryYStep replaces existing step value`() {
        val state = makeState(primaryYStep = 2.5)

        val result = reducer.reduce(state, GraphIntent.UpdatePrimaryYStep(10.0))

        assertThat(result).isNotNull()
        assertThat(result!!.primaryYStep).isEqualTo(10.0)
    }

    // -------------------------------------------------------------------------
    // UpdatePrimaryYAxis — yStep provided
    // -------------------------------------------------------------------------

    @Test
    fun `UpdatePrimaryYAxis with explicit yStep stores both yAxis and yStep`() {
        val state = makeState(primaryYStep = 2.0)
        val yRange: CartesianRangeValues = mockk(relaxed = true)

        val result = reducer.reduce(state, GraphIntent.UpdatePrimaryYAxis(yRange, yStep = 8.0))

        assertThat(result).isNotNull()
        assertThat(result!!.primaryYAxis).isSameInstanceAs(yRange)
        assertThat(result.primaryYStep).isEqualTo(8.0)
    }

    @Test
    fun `UpdatePrimaryYAxis with null yStep falls back to existing primaryYStep`() {
        val state = makeState(primaryYStep = 3.0)
        val yRange: CartesianRangeValues = mockk(relaxed = true)

        val result = reducer.reduce(state, GraphIntent.UpdatePrimaryYAxis(yRange, yStep = null))

        assertThat(result).isNotNull()
        assertThat(result!!.primaryYAxis).isSameInstanceAs(yRange)
        assertThat(result.primaryYStep).isEqualTo(3.0)
    }

    // -------------------------------------------------------------------------
    // UpdateMarkerIndex
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMarkerIndex stores non-null marker index`() {
        val state = makeState(markerIndex = null)

        val result = reducer.reduce(state, GraphIntent.UpdateMarkerIndex(42.0))

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isEqualTo(42.0)
    }

    @Test
    fun `UpdateMarkerIndex with null clears marker index`() {
        val state = makeState(markerIndex = 42.0)

        val result = reducer.reduce(state, GraphIntent.UpdateMarkerIndex(null))

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateIsUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsUpdating true sets isUpdating to true`() {
        val state = makeState(isUpdating = false)

        val result = reducer.reduce(state, GraphIntent.UpdateIsUpdating(true))

        assertThat(result).isNotNull()
        assertThat(result!!.isUpdating).isTrue()
    }

    @Test
    fun `UpdateIsUpdating false sets isUpdating to false`() {
        val state = makeState(isUpdating = true)

        val result = reducer.reduce(state, GraphIntent.UpdateIsUpdating(false))

        assertThat(result).isNotNull()
        assertThat(result!!.isUpdating).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateIsLoading
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsLoading true sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, GraphIntent.UpdateIsLoading(true))

        assertThat(result).isNotNull()
        assertThat(result!!.isLoading).isTrue()
    }

    @Test
    fun `UpdateIsLoading false sets isLoading to false`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, GraphIntent.UpdateIsLoading(false))

        assertThat(result).isNotNull()
        assertThat(result!!.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateIsSingleWindow
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsSingleWindow true sets isSingleWindow to true`() {
        val state = makeState(isSingleWindow = false)

        val result = reducer.reduce(state, GraphIntent.UpdateIsSingleWindow(true))

        assertThat(result).isNotNull()
        assertThat(result!!.isSingleWindow).isTrue()
    }

    @Test
    fun `UpdateIsSingleWindow false sets isSingleWindow to false`() {
        val state = makeState(isSingleWindow = true)

        val result = reducer.reduce(state, GraphIntent.UpdateIsSingleWindow(false))

        assertThat(result).isNotNull()
        assertThat(result!!.isSingleWindow).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateIsEmptyGraph
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateIsEmptyGraph true sets isEmptyGraph to true`() {
        val state = makeState(isEmptyGraph = false)

        val result = reducer.reduce(state, GraphIntent.UpdateIsEmptyGraph(true))

        assertThat(result).isNotNull()
        assertThat(result!!.isEmptyGraph).isTrue()
    }

    @Test
    fun `UpdateIsEmptyGraph false sets isEmptyGraph to false`() {
        val state = makeState(isEmptyGraph = true)

        val result = reducer.reduce(state, GraphIntent.UpdateIsEmptyGraph(false))

        assertThat(result).isNotNull()
        assertThat(result!!.isEmptyGraph).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateWeightUnit
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateWeightUnit to KG stores KG`() {
        val state = makeState(weightUnit = WeightUnit.LB)

        val result = reducer.reduce(state, GraphIntent.UpdateWeightUnit(WeightUnit.KG))

        assertThat(result).isNotNull()
        assertThat(result!!.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `UpdateWeightUnit to LB stores LB`() {
        val state = makeState(weightUnit = WeightUnit.KG)

        val result = reducer.reduce(state, GraphIntent.UpdateWeightUnit(WeightUnit.LB))

        assertThat(result).isNotNull()
        assertThat(result!!.weightUnit).isEqualTo(WeightUnit.LB)
    }

    // -------------------------------------------------------------------------
    // ResetGraph
    // -------------------------------------------------------------------------

    @Test
    fun `ResetGraph clears minTarget and maxTarget`() {
        val state = makeState(minTarget = 1000L, maxTarget = 2000L)

        val result = reducer.reduce(state, GraphIntent.ResetGraph)

        assertThat(result).isNotNull()
        assertThat(result!!.minTarget).isNull()
        assertThat(result.maxTarget).isNull()
    }

    @Test
    fun `ResetGraph clears markerIndex`() {
        val state = makeState(markerIndex = 5.0)

        val result = reducer.reduce(state, GraphIntent.ResetGraph)

        assertThat(result).isNotNull()
        assertThat(result!!.markerIndex).isNull()
    }

    @Test
    fun `ResetGraph sets isUpdating to false`() {
        val state = makeState(isUpdating = true)

        val result = reducer.reduce(state, GraphIntent.ResetGraph)

        assertThat(result).isNotNull()
        assertThat(result!!.isUpdating).isFalse()
    }

    @Test
    fun `ResetGraph sets isSingleWindow to false`() {
        val state = makeState(isSingleWindow = true)

        val result = reducer.reduce(state, GraphIntent.ResetGraph)

        assertThat(result).isNotNull()
        assertThat(result!!.isSingleWindow).isFalse()
    }

    @Test
    fun `ResetGraph preserves weightUnit`() {
        val state = makeState(weightUnit = WeightUnit.KG, isSingleWindow = true, isUpdating = true)

        val result = reducer.reduce(state, GraphIntent.ResetGraph)

        assertThat(result).isNotNull()
        assertThat(result!!.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // SetScrollRange
    // -------------------------------------------------------------------------

    @Test
    fun `SetScrollRange stores min and max target values`() {
        val state = makeState(minTarget = null, maxTarget = null)

        val result = reducer.reduce(state, GraphIntent.SetScrollRange(min = 1000L, max = 9000L))

        assertThat(result).isNotNull()
        assertThat(result!!.minTarget).isEqualTo(1000L)
        assertThat(result.maxTarget).isEqualTo(9000L)
    }

    @Test
    fun `SetScrollRange replaces previous min and max values`() {
        val state = makeState(minTarget = 500L, maxTarget = 1500L)

        val result = reducer.reduce(state, GraphIntent.SetScrollRange(min = 2000L, max = 8000L))

        assertThat(result).isNotNull()
        assertThat(result!!.minTarget).isEqualTo(2000L)
        assertThat(result.maxTarget).isEqualTo(8000L)
    }

    @Test
    fun `SetScrollRange preserves other state fields`() {
        val state = makeState(isLoading = true, isUpdating = true, weightUnit = WeightUnit.KG)

        val result = reducer.reduce(state, GraphIntent.SetScrollRange(min = 0L, max = 1L))

        assertThat(result).isNotNull()
        assertThat(result!!.isLoading).isTrue()
        assertThat(result.isUpdating).isTrue()
        assertThat(result.weightUnit).isEqualTo(WeightUnit.KG)
    }
}
