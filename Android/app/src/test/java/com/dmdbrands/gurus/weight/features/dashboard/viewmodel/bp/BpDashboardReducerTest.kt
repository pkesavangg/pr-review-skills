package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.bp

import com.dmdbrands.gurus.weight.domain.model.common.BpProgress
import com.dmdbrands.gurus.weight.domain.model.common.Streak
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBpmSummary
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BpDashboardReducer], covering every BP-specific intent branch plus the
 * shared [BaseGraphIntent] handling delegated to [com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphReducer].
 *
 * The reducer is a pure function `(BpDashboardState, BaseGraphIntent) -> BpDashboardState?`.
 */
class BpDashboardReducerTest {

  private lateinit var reducer: BpDashboardReducer

  companion object {
    private const val ANCHOR_TIMESTAMP = 1700000000.0
    private const val SEED_MIN_Y = 60.0
    private const val SEED_MAX_Y = 180.0

    // 2024-01-10 / 2024-01-20 start-of-day in system zone — only relative ordering matters.
    private val summaryEarly = PeriodBpmSummary(
      period = "2024-01-10",
      entryTimestamp = "2024-01-10",
      avgSystolic = 120,
      avgDiastolic = 80,
      avgPulse = 70,
    )
    private val summaryLate = PeriodBpmSummary(
      period = "2024-01-20",
      entryTimestamp = "2024-01-20",
      avgSystolic = 130,
      avgDiastolic = 85,
      avgPulse = 72,
    )
  }

  private val fakeProgress = BpProgress(streak = Streak(current = 3, longest = 7), count = 21)
  private val fakeLastReadings = BpLastReadings(
    entries = listOf(summaryEarly, summaryLate),
    averageSystolic = 125,
    averageDiastolic = 82,
    averagePulse = 71,
  )

  @BeforeEach
  fun setUp() {
    reducer = BpDashboardReducer()
  }

  // -------------------------------------------------------------------------
  // Default state
  // -------------------------------------------------------------------------

  @Test
  fun `default BpDashboardState has expected initial values`() {
    val state = BpDashboardState()

    assertThat(state.isEmpty).isFalse()
    assertThat(state.isRefreshing).isFalse()
    assertThat(state.markerIndex).isNull()
    assertThat(state.scrollTarget).isNull()
    assertThat(state.selectedSegment).isEqualTo(GraphSegment.WEEK)
    assertThat(state.segmentStates).isEmpty()
    assertThat(state.progress).isEqualTo(BpProgress())
    assertThat(state.lastReadings).isEqualTo(BpLastReadings())
  }

  @Test
  fun `default BpLastReadings has no averages and empty entries`() {
    val readings = BpLastReadings()

    assertThat(readings.entries).isEmpty()
    assertThat(readings.averageSystolic).isNull()
    assertThat(readings.averageDiastolic).isNull()
    assertThat(readings.averagePulse).isNull()
  }

  // -------------------------------------------------------------------------
  // SetIsEmpty
  // -------------------------------------------------------------------------

  @Test
  fun `SetIsEmpty true flags the state empty`() {
    val result = reducer.reduce(BpDashboardState(isEmpty = false), BpDashboardIntent.SetIsEmpty(true))

    assertThat(result?.isEmpty).isTrue()
  }

  @Test
  fun `SetIsEmpty false clears the empty flag`() {
    val result = reducer.reduce(BpDashboardState(isEmpty = true), BpDashboardIntent.SetIsEmpty(false))

    assertThat(result?.isEmpty).isFalse()
  }

  // -------------------------------------------------------------------------
  // SetProgress
  // -------------------------------------------------------------------------

  @Test
  fun `SetProgress updates progress field`() {
    val result = reducer.reduce(BpDashboardState(), BpDashboardIntent.SetProgress(fakeProgress))

    assertThat(result?.progress).isEqualTo(fakeProgress)
  }

  // -------------------------------------------------------------------------
  // SetLastReadings
  // -------------------------------------------------------------------------

  @Test
  fun `SetLastReadings stores readings and averages`() {
    val result = reducer.reduce(BpDashboardState(), BpDashboardIntent.SetLastReadings(fakeLastReadings))

    assertThat(result?.lastReadings).isEqualTo(fakeLastReadings)
    assertThat(result?.lastReadings?.averageSystolic).isEqualTo(125)
    assertThat(result?.lastReadings?.entries).containsExactly(summaryEarly, summaryLate).inOrder()
  }

  @Test
  fun `SetLastReadings with default clears previous readings`() {
    val result = reducer.reduce(
      BpDashboardState(lastReadings = fakeLastReadings),
      BpDashboardIntent.SetLastReadings(BpLastReadings()),
    )

    assertThat(result?.lastReadings).isEqualTo(BpLastReadings())
  }

  // -------------------------------------------------------------------------
  // Action intents — no state change
  // -------------------------------------------------------------------------

  @Test
  fun `Refresh does not change state`() {
    val state = BpDashboardState(isEmpty = true, progress = fakeProgress)

    val result = reducer.reduce(state, BpDashboardIntent.Refresh)

    assertThat(result).isEqualTo(state)
  }

  @Test
  fun `OnConnectDevice does not change state`() {
    val state = BpDashboardState(isEmpty = true)

    val result = reducer.reduce(state, BpDashboardIntent.OnConnectDevice)

    assertThat(result).isEqualTo(state)
  }

  // -------------------------------------------------------------------------
  // Base intent — SetRefreshing
  // -------------------------------------------------------------------------

  @Test
  fun `SetRefreshing true sets isRefreshing to true`() {
    val result = reducer.reduce(BpDashboardState(isRefreshing = false), BaseGraphIntent.SetRefreshing(true))

    assertThat(result?.isRefreshing).isTrue()
  }

  @Test
  fun `SetRefreshing false sets isRefreshing to false`() {
    val result = reducer.reduce(BpDashboardState(isRefreshing = true), BaseGraphIntent.SetRefreshing(false))

    assertThat(result?.isRefreshing).isFalse()
  }

  // -------------------------------------------------------------------------
  // Base intent — UpdateSegment
  // -------------------------------------------------------------------------

  @Test
  fun `UpdateSegment applies update to the targeted segment state`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(isEmptyGraph = true) },
    )

    assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.isEmptyGraph).isTrue()
  }

  @Test
  fun `UpdateSegment preserves other segments`() {
    val state = BpDashboardState(
      segmentStates = mapOf(GraphSegment.YEAR to SegmentState(isEmptyGraph = true)),
    )

    val result = reducer.reduce(
      state,
      BaseGraphIntent.UpdateSegment(GraphSegment.WEEK) { it.copy(isSingleWindow = true) },
    )

    assertThat(result?.segmentStates?.get(GraphSegment.YEAR)?.isEmptyGraph).isTrue()
    assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.isSingleWindow).isTrue()
  }

  // -------------------------------------------------------------------------
  // Base intent — SetSelectedSegment (marker auto-focus computation)
  // -------------------------------------------------------------------------

  @Test
  fun `SetSelectedSegment auto-focuses latest entry timestamp as markerIndex`() {
    val state = BpDashboardState(
      selectedSegment = GraphSegment.WEEK,
      segmentStates = mapOf(
        GraphSegment.MONTH to SegmentState(data = persistentListOf(summaryEarly, summaryLate)),
      ),
    )

    val result = reducer.reduce(state, BaseGraphIntent.SetSelectedSegment(GraphSegment.MONTH))

    assertThat(result?.selectedSegment).isEqualTo(GraphSegment.MONTH)
    assertThat(result?.markerIndex).isEqualTo(summaryLate.getTimeStamp().toDouble())
  }

  @Test
  fun `SetSelectedSegment with no segment data leaves markerIndex null`() {
    val state = BpDashboardState(selectedSegment = GraphSegment.WEEK, markerIndex = 5.0)

    val result = reducer.reduce(state, BaseGraphIntent.SetSelectedSegment(GraphSegment.YEAR))

    assertThat(result?.selectedSegment).isEqualTo(GraphSegment.YEAR)
    assertThat(result?.markerIndex).isNull()
  }

  @Test
  fun `SetSelectedSegment sets scrollTarget from anchorTimestamp`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.SetSelectedSegment(GraphSegment.MONTH, ANCHOR_TIMESTAMP),
    )

    assertThat(result?.scrollTarget).isEqualTo(ANCHOR_TIMESTAMP)
  }

  // -------------------------------------------------------------------------
  // Base intent — UpdateMarkerIndex
  // -------------------------------------------------------------------------

  @Test
  fun `UpdateMarkerIndex stores non-null marker`() {
    val result = reducer.reduce(BpDashboardState(), BaseGraphIntent.UpdateMarkerIndex(42.0))

    assertThat(result?.markerIndex).isEqualTo(42.0)
  }

  @Test
  fun `UpdateMarkerIndex with null clears marker`() {
    val result = reducer.reduce(BpDashboardState(markerIndex = 42.0), BaseGraphIntent.UpdateMarkerIndex(null))

    assertThat(result?.markerIndex).isNull()
  }

  // -------------------------------------------------------------------------
  // Base intent — ScrollRange
  // -------------------------------------------------------------------------

  @Test
  fun `ScrollRange stores visible min and max on the segment`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.ScrollRange(GraphSegment.WEEK, min = 100L, max = 200L),
    )

    val segment = result?.segmentStates?.get(GraphSegment.WEEK)
    assertThat(segment?.visibleMin).isEqualTo(100L)
    assertThat(segment?.visibleMax).isEqualTo(200L)
  }

  // -------------------------------------------------------------------------
  // Base intent — UpdateIsEmptyGraph
  // -------------------------------------------------------------------------

  @Test
  fun `UpdateIsEmptyGraph sets isEmptyGraph on the segment`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.UpdateIsEmptyGraph(GraphSegment.MONTH, isEmpty = true),
    )

    assertThat(result?.segmentStates?.get(GraphSegment.MONTH)?.isEmptyGraph).isTrue()
  }

  // -------------------------------------------------------------------------
  // Base intent — UpdateSegmentTarget (incl. out-of-range marker clear)
  // -------------------------------------------------------------------------

  @Test
  fun `UpdateSegmentTarget stores target entries on the segment`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, listOf(summaryEarly, summaryLate)),
    )

    assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.target)
      .containsExactly(summaryEarly, summaryLate).inOrder()
  }

  @Test
  fun `UpdateSegmentTarget keeps markerIndex when inside target range`() {
    val inRange = (summaryEarly.getTimeStamp() + summaryLate.getTimeStamp()) / 2.0
    val state = BpDashboardState(markerIndex = inRange)

    val result = reducer.reduce(
      state,
      BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, listOf(summaryEarly, summaryLate)),
    )

    assertThat(result?.markerIndex).isEqualTo(inRange)
  }

  @Test
  fun `UpdateSegmentTarget clears markerIndex when outside target range`() {
    val outOfRange = summaryLate.getTimeStamp().toDouble() + 1_000_000_000.0
    val state = BpDashboardState(markerIndex = outOfRange)

    val result = reducer.reduce(
      state,
      BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, listOf(summaryEarly, summaryLate)),
    )

    assertThat(result?.markerIndex).isNull()
  }

  @Test
  fun `UpdateSegmentTarget keeps null markerIndex with empty target`() {
    val state = BpDashboardState(markerIndex = null)

    val result = reducer.reduce(
      state,
      BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, emptyList()),
    )

    assertThat(result?.markerIndex).isNull()
    assertThat(result?.segmentStates?.get(GraphSegment.WEEK)?.target).isEmpty()
  }

  @Test
  fun `UpdateSegmentTarget keeps markerIndex when target is empty`() {
    val state = BpDashboardState(markerIndex = 99.0)

    val result = reducer.reduce(
      state,
      BaseGraphIntent.UpdateSegmentTarget(GraphSegment.WEEK, emptyList()),
    )

    assertThat(result?.markerIndex).isEqualTo(99.0)
  }

  // -------------------------------------------------------------------------
  // Base intent — UpdateSeedYRange
  // -------------------------------------------------------------------------

  @Test
  fun `UpdateSeedYRange stores seed min and max Y on the segment`() {
    val result = reducer.reduce(
      BpDashboardState(),
      BaseGraphIntent.UpdateSeedYRange(GraphSegment.MONTH, minY = SEED_MIN_Y, maxY = SEED_MAX_Y),
    )

    val segment = result?.segmentStates?.get(GraphSegment.MONTH)
    assertThat(segment?.seedMinY).isEqualTo(SEED_MIN_Y)
    assertThat(segment?.seedMaxY).isEqualTo(SEED_MAX_Y)
  }
}
