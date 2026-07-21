package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.SeriesData
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import kotlinx.collections.immutable.toImmutableList
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.icu.util.Calendar

/**
 * Base ViewModel for all product dashboards. Provides:
 * - Shared segment range computation
 * - Shared producer transaction helper
 * - ScrollRange intent handling
 *
 * Producers are stable defaults inside the product [BaseDashboardState].
 * Each product VM subscribes to its own typed data flow, converts entries
 * to series inline, then calls [updateSegmentRanges] + [pushSeriesToProducer].
 */
abstract class BaseDashboardViewModel<S : BaseDashboardState, I : BaseGraphIntent>(
  reducer: IReducer<S, I>,
) : BaseIntentViewModel<S, I>(reducer) {

  companion object {
    private const val TAG = "BaseDashboardVM"
  }

  // ── Intent helpers ──

  @Suppress("UNCHECKED_CAST")
  protected fun updateSegmentState(segment: GraphSegment, update: (SegmentState) -> SegmentState) {
    super.handleIntent(BaseGraphIntent.UpdateSegment(segment, update) as I)
  }

  @Suppress("UNCHECKED_CAST")
  protected fun setRefreshing(isRefreshing: Boolean) {
    super.handleIntent(BaseGraphIntent.SetRefreshing(isRefreshing) as I)
  }

  // ── Shared: segment range computation ──

  /**
   * Compute segment ranges (minTarget, maxTarget, chartMinX, chartMaxX, etc.)
   * from entries and update segment states. Same logic for all products.
   *
   * @param getYValuesForSeed Optional per-product Y extractor. When provided, computes
   *   [SegmentState.seedMinY]/[SegmentState.seedMaxY] from the visible-window entries so
   *   the chart has the correct Y range on frame-0 — eliminating the axis jump on segment
   *   switch and first load. Pass null (default) to leave seed unchanged (e.g. Baby chart).
   */
  protected fun updateSegmentRanges(
    entries: List<PeriodSummary>,
    segments: List<GraphSegment>,
    goalWeight: Double = 0.0,
    isWeightlessMode: Boolean = false,
    getYValuesForSeed: ((List<PeriodSummary>) -> List<Double>)? = null,
  ) {
    if (entries.isEmpty()) {
      segments.forEach { segment ->
        updateSegmentState(segment) { it.copy(isEmptyGraph = true) }
      }
      return
    }

    val timestamps = entries.map { it.getTimeStamp() }.sorted()
    val initialTimeStamp = timestamps.minOrNull()
    val endTimeStamp = timestamps.maxOrNull()

    for (segment in segments) {
      val bounds = computeSegmentBounds(segment, initialTimeStamp, endTimeStamp)
      val filteredTarget = entries.filter { it.getTimeStamp() in bounds.startX..bounds.endX }
      val seed = computeSeedRange(
        entries = entries,
        startX = bounds.startX,
        endX = bounds.endX,
        goalWeight = goalWeight,
        isWeightlessMode = isWeightlessMode,
        getYValuesForSeed = getYValuesForSeed,
      )

      updateSegmentState(segment) {
        it.copy(
          data = entries.toImmutableList(),
          target = filteredTarget.toImmutableList(),
          chartMinX = bounds.chartMinX,
          chartMaxX = bounds.chartMaxX,
          isSingleWindow = bounds.isSingleWindow,
          isEmptyGraph = false,
          startTimestamp = initialTimeStamp,
          endTimestamp = endTimeStamp,
          visibleMin = it.visibleMin ?: bounds.startX,
          visibleMax = it.visibleMax ?: bounds.endX,
          seedMinY = seed?.first ?: it.seedMinY,
          seedMaxY = seed?.second ?: it.seedMaxY,
        )
      }
    }
  }

  // ── Shared: push series to producer ──

  protected suspend fun pushSeriesToProducer(
    producer: CartesianChartModelProducer,
    vararg seriesBlocks: List<SeriesData>,
  ) {
    withContext(Dispatchers.Main) {
      producer.runTransaction(animate = false) {
        seriesBlocks.forEach { block ->
          if (block.isNotEmpty()) {
            lineSeries {
              block.forEach { s -> series(x = s.xValues, y = s.yValues) }
            }
          }
        }
      }
    }
  }

  protected suspend fun pushEmptyProducer(producer: CartesianChartModelProducer) {
    withContext(Dispatchers.Main) {
      // Empty transaction → empty chart model, so no (0.0, 0.0) placeholder line is drawn. The
      // empty UI is shown by EmptyDashboardGraph via isEmptyGraph. (mirrors pushSeriesToProducer)
      producer.runTransaction(animate = false) {}
    }
  }

  // ── ScrollRange handling (shared) ──

  override fun handleIntent(intent: I) {
    if (intent is BaseGraphIntent.ScrollRange && _state.value.markerIndex == null) {
      val adjMin = GraphUtil.getRelativeStart(intent.segment, intent.min)
      val adjMax = GraphUtil.getRelativeEnd(intent.segment, intent.max)
      val ss = _state.value.forSegment(intent.segment)
      val filteredData = ss.data.filter { it.getTimeStamp() in adjMin..adjMax }
      if (filteredData.isNotEmpty()) {
        @Suppress("UNCHECKED_CAST")
        super.handleIntent(BaseGraphIntent.UpdateSegmentTarget(intent.segment, filteredData) as I)
      } else {
        @Suppress("UNCHECKED_CAST")
        super.handleIntent(BaseGraphIntent.UpdateSegmentTarget(intent.segment, emptyList()) as I)
        intent.onFallback()
      }
    }
    super.handleIntent(intent)
  }
}

/** Per-segment X-axis bounds computed from the entry time range. */
private data class SegmentBounds(
  val startX: Long,
  val endX: Long,
  val chartMinX: Double,
  val chartMaxX: Double,
  val isSingleWindow: Boolean,
)

/** Pure X-axis bounds computation for a single [segment] (extracted from updateSegmentRanges). */
private fun computeSegmentBounds(
  segment: GraphSegment,
  initialTimeStamp: Long?,
  endTimeStamp: Long?,
): SegmentBounds {
  val calendar = Calendar.getInstance()
  val isSingleWindow = GraphUtil.isSingleWindow(segment, initialTimeStamp, endTimeStamp)

  val (startX, endX) = if (segment == GraphSegment.TOTAL) {
    val start = (initialTimeStamp ?: calendar.timeInMillis).let {
      Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, -6) }.timeInMillis
    }
    val end = (endTimeStamp ?: calendar.timeInMillis).let {
      Calendar.getInstance().apply { timeInMillis = it; add(Calendar.MONTH, +6) }.timeInMillis
    }
    start to end
  } else {
    val start = GraphUtil.getRollingWindowStart(segment, endTimeStamp)
      ?: GraphUtil.getStartRange(segment, endTimeStamp)
      ?: calendar.timeInMillis
    val end = endTimeStamp ?: calendar.timeInMillis
    start to end
  }

  val chartMinX = if (segment == GraphSegment.TOTAL) {
    startX.toDouble()
  } else {
    GraphUtil.getStartRange(segment, initialTimeStamp)?.toDouble() ?: startX.toDouble()
  }
  val chartMaxX = if (segment == GraphSegment.TOTAL) {
    endX.toDouble()
  } else if (segment == GraphSegment.MONTH) {
    val paddedStart = GraphUtil.getStartRange(segment, calendar.timeInMillis) ?: calendar.timeInMillis
    Calendar.getInstance().apply { timeInMillis = paddedStart; add(Calendar.DAY_OF_YEAR, 30) }
      .timeInMillis.toDouble()
  } else {
    GraphUtil.getEndRange(segment, calendar.timeInMillis)?.toDouble() ?: endX.toDouble()
  }

  return SegmentBounds(startX, endX, chartMinX, chartMaxX, isSingleWindow)
}

/**
 * Pure seed Y-range computation (extracted from updateSegmentRanges). Returns null when no
 * [getYValuesForSeed] extractor is provided or no finite positive values fall in the window.
 *
 * Match ScrollAwareRangeProvider.computeVisibleEntries padding (paddingEntries=1): include 1
 * entry just before and 1 entry just after the visible window so the seed Y range matches the
 * runtime Y range exactly. Without this, an entry just outside the window with a different
 * weight would expand the runtime range and cause a frame-1 → frame-2 slide on initial load.
 */
private fun computeSeedRange(
  entries: List<PeriodSummary>,
  startX: Long,
  endX: Long,
  goalWeight: Double,
  isWeightlessMode: Boolean,
  getYValuesForSeed: ((List<PeriodSummary>) -> List<Double>)?,
): Pair<Double, Double>? {
  if (getYValuesForSeed == null) return null

  val sorted = entries.sortedBy { it.getTimeStamp() }
  val firstIdx = sorted.indexOfFirst { it.getTimeStamp() >= startX }
  val lastIdx = sorted.indexOfLast { it.getTimeStamp() <= endX }
  val seedSource = if (firstIdx < 0 || lastIdx < 0 || firstIdx > lastIdx) {
    entries.filter { it.getTimeStamp() in startX..endX }
  } else {
    val from = (firstIdx - 1).coerceAtLeast(0)
    val to = (lastIdx + 1).coerceAtMost(sorted.lastIndex)
    sorted.subList(from, to + 1)
  }

  val yValues = getYValuesForSeed(seedSource).filter { it.isFinite() && it > 0.0 }
  if (yValues.isEmpty()) return null

  val scale = generateNiceScale(
    minValue = yValues.min(),
    maxValue = yValues.max(),
    goalWeight = goalWeight,
    isWeightLessMode = isWeightlessMode,
    targetTickCount = 4,
  )
  return scale.min to scale.max
}
