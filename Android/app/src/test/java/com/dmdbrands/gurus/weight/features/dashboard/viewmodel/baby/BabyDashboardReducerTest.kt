package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.BabyPercentileHelper
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BabyDashboardReducer], covering every Baby-specific intent branch, the
 * [BabyDashboardState.activePercentile] computed property, and the shared [BaseGraphIntent]
 * handling delegated to the base reducer.
 */
class BabyDashboardReducerTest {

  private lateinit var reducer: BabyDashboardReducer

  private val weightSeries = BabyPercentileHelper.PercentileSeries(
    xTimestamps = listOf(0.0, 1.0),
    p5 = listOf(1.0, 1.1),
    p10 = listOf(2.0, 2.1),
    p25 = listOf(3.0, 3.1),
    p50 = listOf(4.0, 4.1),
    p75 = listOf(5.0, 5.1),
    p90 = listOf(6.0, 6.1),
    p95 = listOf(7.0, 7.1),
  )
  private val heightSeries = BabyPercentileHelper.PercentileSeries(
    xTimestamps = listOf(0.0, 1.0),
    p5 = listOf(10.0, 10.1),
    p10 = listOf(20.0, 20.1),
    p25 = listOf(30.0, 30.1),
    p50 = listOf(40.0, 40.1),
    p75 = listOf(50.0, 50.1),
    p90 = listOf(60.0, 60.1),
    p95 = listOf(70.0, 70.1),
  )
  private val fakeProfile = BabyProfile(id = "b1", accountId = "a1", name = "Baby")

  @BeforeEach
  fun setUp() {
    reducer = BabyDashboardReducer()
  }

  // -------------------------------------------------------------------------
  // Default state
  // -------------------------------------------------------------------------

  @Test
  fun `default BabyDashboardState has expected initial values`() {
    val state = BabyDashboardState()

    assertThat(state.isEmpty).isFalse()
    assertThat(state.selectedMetric).isEqualTo(BabyMetric.WEIGHT)
    assertThat(state.weightPercentile).isNull()
    assertThat(state.heightPercentile).isNull()
    assertThat(state.babyProfile).isNull()
    assertThat(state.selectedSegment).isEqualTo(GraphSegment.WEEK)
    assertThat(state.segmentStates).isEmpty()
  }

  // -------------------------------------------------------------------------
  // SetIsEmpty
  // -------------------------------------------------------------------------

  @Test
  fun `SetIsEmpty true flags the state empty`() {
    val result = reducer.reduce(BabyDashboardState(isEmpty = false), BabyDashboardIntent.SetIsEmpty(true))

    assertThat(result?.isEmpty).isTrue()
  }

  @Test
  fun `SetIsEmpty false clears the empty flag`() {
    val result = reducer.reduce(BabyDashboardState(isEmpty = true), BabyDashboardIntent.SetIsEmpty(false))

    assertThat(result?.isEmpty).isFalse()
  }

  // ---------------------------------------------------------------------------
  // SetBabyWeightUnit / isMetric
  // ---------------------------------------------------------------------------

  @Test
  fun `default state is lb-oz and not metric`() {
    val state = BabyDashboardState()

    assertThat(state.weightUnit).isEqualTo(WeightUnit.LB_OZ)
    assertThat(state.isMetric).isFalse()
  }

  @Test
  fun `SetBabyWeightUnit KG makes the state metric`() {
    val result = reducer.reduce(BabyDashboardState(), BabyDashboardIntent.SetBabyWeightUnit(WeightUnit.KG))

    assertThat(result?.weightUnit).isEqualTo(WeightUnit.KG)
    assertThat(result?.isMetric).isTrue()
  }

  @Test
  fun `SetBabyWeightUnit LB stays imperial`() {
    val result = reducer.reduce(
      BabyDashboardState(weightUnit = WeightUnit.KG),
      BabyDashboardIntent.SetBabyWeightUnit(WeightUnit.LB),
    )

    assertThat(result?.weightUnit).isEqualTo(WeightUnit.LB)
    assertThat(result?.isMetric).isFalse()
  }

  // -------------------------------------------------------------------------
  // SetBabyProfile
  // -------------------------------------------------------------------------

  @Test
  fun `SetBabyProfile stores the profile`() {
    val result = reducer.reduce(BabyDashboardState(), BabyDashboardIntent.SetBabyProfile(fakeProfile))

    assertThat(result?.babyProfile).isEqualTo(fakeProfile)
  }

  // -------------------------------------------------------------------------
  // SetSelectedMetric
  // -------------------------------------------------------------------------

  @Test
  fun `SetSelectedMetric switches to HEIGHT`() {
    val result = reducer.reduce(
      BabyDashboardState(selectedMetric = BabyMetric.WEIGHT),
      BabyDashboardIntent.SetSelectedMetric(BabyMetric.HEIGHT),
    )

    assertThat(result?.selectedMetric).isEqualTo(BabyMetric.HEIGHT)
  }

  @Test
  fun `SetSelectedMetric switches back to WEIGHT`() {
    val result = reducer.reduce(
      BabyDashboardState(selectedMetric = BabyMetric.HEIGHT),
      BabyDashboardIntent.SetSelectedMetric(BabyMetric.WEIGHT),
    )

    assertThat(result?.selectedMetric).isEqualTo(BabyMetric.WEIGHT)
  }

  // -------------------------------------------------------------------------
  // SetWeightPercentile / SetHeightPercentile
  // -------------------------------------------------------------------------

  @Test
  fun `SetWeightPercentile stores the weight series`() {
    val result = reducer.reduce(
      BabyDashboardState(),
      BabyDashboardIntent.SetWeightPercentile(weightSeries),
    )

    assertThat(result?.weightPercentile).isEqualTo(weightSeries)
  }

  @Test
  fun `SetWeightPercentile with null clears the weight series`() {
    val result = reducer.reduce(
      BabyDashboardState(weightPercentile = weightSeries),
      BabyDashboardIntent.SetWeightPercentile(null),
    )

    assertThat(result?.weightPercentile).isNull()
  }

  @Test
  fun `SetHeightPercentile stores the height series`() {
    val result = reducer.reduce(
      BabyDashboardState(),
      BabyDashboardIntent.SetHeightPercentile(heightSeries),
    )

    assertThat(result?.heightPercentile).isEqualTo(heightSeries)
  }

  @Test
  fun `SetHeightPercentile with null clears the height series`() {
    val result = reducer.reduce(
      BabyDashboardState(heightPercentile = heightSeries),
      BabyDashboardIntent.SetHeightPercentile(null),
    )

    assertThat(result?.heightPercentile).isNull()
  }

  // -------------------------------------------------------------------------
  // activePercentile computed property
  // -------------------------------------------------------------------------

  @Test
  fun `activePercentile returns weight series when WEIGHT selected`() {
    val state = BabyDashboardState(
      selectedMetric = BabyMetric.WEIGHT,
      weightPercentile = weightSeries,
      heightPercentile = heightSeries,
    )

    assertThat(state.activePercentile).isEqualTo(weightSeries)
  }

  @Test
  fun `activePercentile returns height series when HEIGHT selected`() {
    val state = BabyDashboardState(
      selectedMetric = BabyMetric.HEIGHT,
      weightPercentile = weightSeries,
      heightPercentile = heightSeries,
    )

    assertThat(state.activePercentile).isEqualTo(heightSeries)
  }

  @Test
  fun `activePercentile is null when selected metric series absent`() {
    val state = BabyDashboardState(
      selectedMetric = BabyMetric.HEIGHT,
      weightPercentile = weightSeries,
      heightPercentile = null,
    )

    assertThat(state.activePercentile).isNull()
  }

  // -------------------------------------------------------------------------
  // Action intents — no state change
  // -------------------------------------------------------------------------

  @Test
  fun `Refresh does not change state`() {
    val state = BabyDashboardState(isEmpty = true, babyProfile = fakeProfile)

    val result = reducer.reduce(state, BabyDashboardIntent.Refresh)

    assertThat(result).isEqualTo(state)
  }

  @Test
  fun `OnConnectDevice does not change state`() {
    val state = BabyDashboardState(isEmpty = true)

    val result = reducer.reduce(state, BabyDashboardIntent.OnConnectDevice)

    assertThat(result).isEqualTo(state)
  }

  // -------------------------------------------------------------------------
  // Base intent delegation
  // -------------------------------------------------------------------------

  @Test
  fun `SetRefreshing delegates to base reducer`() {
    val result = reducer.reduce(BabyDashboardState(isRefreshing = false), BaseGraphIntent.SetRefreshing(true))

    assertThat(result?.isRefreshing).isTrue()
  }

  @Test
  fun `UpdateSegment delegates to base reducer`() {
    val result = reducer.reduce(
      BabyDashboardState(),
      BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(isEmptyGraph = true) },
    )

    assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.isEmptyGraph).isTrue()
  }

  @Test
  fun `UpdateMarkerIndex delegates to base reducer`() {
    val result = reducer.reduce(BabyDashboardState(), BaseGraphIntent.UpdateMarkerIndex(7.0))

    assertThat(result?.markerIndex).isEqualTo(7.0)
  }

  @Test
  fun `UpdateSeedYRange delegates to base reducer`() {
    val result = reducer.reduce(
      BabyDashboardState(),
      BaseGraphIntent.UpdateSeedYRange(GraphSegment.YEAR, minY = 2.0, maxY = 9.0),
    )

    val segment = result?.segmentStates?.get(GraphSegment.YEAR)
    assertThat(segment?.seedMinY).isEqualTo(2.0)
    assertThat(segment?.seedMaxY).isEqualTo(9.0)
  }

  @Test
  fun `ScrollRange delegates to base reducer preserving existing segment fields`() {
    val state = BabyDashboardState(
      segmentStates = mapOf(GraphSegment.WEEK to SegmentState(isEmptyGraph = true)),
    )

    val result = reducer.reduce(state, BaseGraphIntent.ScrollRange(GraphSegment.WEEK, min = 1L, max = 2L))

    val segment = result?.segmentStates?.get(GraphSegment.WEEK)
    assertThat(segment?.visibleMin).isEqualTo(1L)
    assertThat(segment?.visibleMax).isEqualTo(2L)
    assertThat(segment?.isEmptyGraph).isTrue()
  }
}
