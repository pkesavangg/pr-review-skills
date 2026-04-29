package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.bottomAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.endAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.startAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.axis.topAxis
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.visibleLabelsCount
import com.patrykandpatrick.vico.compose.cartesian.data.rememberScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.FadingEdges
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import java.util.Calendar

@Composable
fun rememberGraphChart(
  state: GraphState,
  defaultMarker: CartesianMarker,
  segment: GraphSegment,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  fadingEdges: FadingEdges? = null,
  onChartClick: ((List<Double>, Double?) -> Unit)? = null,
): CartesianChart {
  val isWeightlessOn = state.goal?.account?.isWeightlessOn ?: false
  val goalWeight = state.goal?.goalWeight ?: 0.0
  val goalMarker = rememberGoalMarker(goal = state.goal, isWeightlessOn = isWeightlessOn)
  val markerIndex = state.markerIndex
  val timeStamps = state.data.map { DateTimeConverter.isoToTimestamp(it.entryTimestamp) }.sorted()

  val visibleLabelsCount = if (segment != GraphSegment.TOTAL) {
    remember(segment) { segment.visibleLabelsCount() }
  } else {
    remember(state.minTarget, state.maxTarget) {
      getIntervalCount(
        startTimeStamp = state.minTarget ?: Calendar.getInstance().timeInMillis,
        endTimeStamp = state.maxTarget ?: Calendar.getInstance().timeInMillis,
      )
    }
  }
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = if (timeStamps.isNotEmpty()) timeStamps.first() else null,
    endMillis = if (timeStamps.isNotEmpty()) timeStamps.last() else null,
  ).map { it.toDouble() }

  // Synchronous chart-wide X bounds — matches MA-3287's chartMinX/chartMaxX computed in
  // BaseDashboardViewModel.updateSegmentRanges, and the per-segment padding from release
  // 5.0.0's setupChartData. Used for both primary's scroll-aware provider and secondary's
  // X-only fixed provider, so both layers share the same X scale even before the VM's
  // UpdatePrimaryYAxis dispatch lands.
  //
  // TOTAL gets ±6 months padding around the data extents so the chart visibly extends past
  // the first/last entries (matches release 5.0.0).
  val chartXBounds = remember(state.data) {
    val firstTs = state.data.minOfOrNull {
      DateTimeConverter.isoToTimestamp(it.entryTimestamp)
    }
    val lastTs = state.data.maxOfOrNull {
      DateTimeConverter.isoToTimestamp(it.entryTimestamp)
    }
    val now = Calendar.getInstance().timeInMillis
    val minX: Double? = when (segment) {
      GraphSegment.TOTAL -> firstTs?.let { ts ->
        Calendar.getInstance().apply {
          timeInMillis = ts; add(Calendar.MONTH, -6)
        }.timeInMillis.toDouble()
      }

      else -> GraphUtil.getStartRange(segment, firstTs)?.toDouble()
    }
    val maxX: Double? = when (segment) {
      GraphSegment.TOTAL -> lastTs?.let { ts ->
        Calendar.getInstance().apply {
          timeInMillis = ts; add(Calendar.MONTH, +6)
        }.timeInMillis.toDouble()
      }

      GraphSegment.MONTH -> {
        val paddedStart = GraphUtil.getStartRange(segment, now) ?: now
        Calendar.getInstance().apply {
          timeInMillis = paddedStart; add(Calendar.DAY_OF_YEAR, 30)
        }.timeInMillis.toDouble()
      }

      else -> GraphUtil.getEndRange(segment, now)?.toDouble()
    }
    minX to maxX
  }

  // Seed Y range comes from VM (`state.seedMinY/seedMaxY`), computed using the SAME bracketing
  // window the provider's `onVisibleEntries` callback uses (visible + 1 entry each side, matching
  // `paddingEntries = 1`). Frame-0 seed and frame-1 callback produce identical Y bounds, so
  // there's no snap on segment switch. Mirrors MA-3287's SegmentState.seedMinY/seedMaxY pattern.
  val scrollAwareRange = rememberScrollAwareRangeProvider(
    minX = chartXBounds.first ?: Double.NaN,
    maxX = chartXBounds.second ?: Double.NaN,
    seedMinY = state.seedMinY ?: Double.NaN,
    seedMaxY = state.seedMaxY ?: Double.NaN,
  ) { visibleEntries, _ ->
    // Single-series chart: take entries from the first (and only) series.
    val yValues = visibleEntries.firstOrNull()?.map { it.second }?.filter { it.isFinite() } ?: emptyList()
    if (yValues.isEmpty()) {
      0.0..1.0 to emptyList()
    } else {
      val nice = generateNiceScale(
        minValue = yValues.min(),
        maxValue = yValues.max(),
        goalWeight = goalWeight,
        isWeightLessMode = isWeightlessOn,
        targetTickCount = 4,
      )
      val ticks = mutableListOf<Double>()
      var t = nice.min
      while (t <= nice.max + nice.step * 0.01) {
        ticks.add(t)
        t += nice.step
      }
      (nice.min..nice.max) to ticks
    }
  }

  val primaryLayer = primaryLayer(segment = segment, rangeProvider = scrollAwareRange)

  // Secondary layer is created ONLY when a secondary metric is selected. Matches MA-3287's
  // pattern (conditional inclusion via config.hasSecondaryLayer) — avoids registering a
  // data-less layer when no metric is active, which can cause cache + transform side effects.
  // Secondary uses its own X-only fixed range provider keyed on the synchronous chartXBounds
  // so it shares primary's X scale even on frame-0; `alwaysUseLiveRange = true` (inside
  // `secondaryLayer`) skips the cached drawing model so yTransform output reaches screen.
  val secondaryLayer = if (state.secondaryKey != null) {
    val secondaryRangeProvider =
      remember(chartXBounds.first, chartXBounds.second) {
        CartesianLayerRangeProvider.fixed(
          minX = chartXBounds.first,
          maxX = chartXBounds.second,
        )
      }
    secondaryLayer(
      segment = segment,
      rangeProvider = secondaryRangeProvider,
      yTransform = { series, yRange, visibleXRange ->
        normalizeSecondaryEntriesToWeightRange(
          series = series,
          weightMin = yRange.minY,
          weightMax = yRange.maxY,
          minX = visibleXRange.start.toLong(),
          maxX = visibleXRange.endInclusive.toLong(),
        )
      },
    )
  } else null

  val layers = if (secondaryLayer != null) {
    arrayOf(primaryLayer, secondaryLayer)
  } else {
    arrayOf(primaryLayer)
  }

  return rememberCartesianChart(
    *layers,
    topAxis = topAxis(),
    startAxis = startAxis(segment, state.isSingleWindow),
    endAxis = endAxis(
      ticksProvider = { scrollAwareRange.currentTicks },
      isEmptyGraph = state.isEmptyGraph,
      markerDecoration = goalMarker,
    ),
    bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer),
    marker = emptyMarker(),
    persistentMarkers = if (markerIndex != null) {
      { defaultMarker at markerIndex }
    } else {
      null
    },
    fadingEdges = fadingEdges,
    visibleLabelsCount = visibleLabelsCount,
    getXStep = { GraphUtil.calculateXStep(segment) },
    onChartClick = onChartClick,
  )
}

fun getIntervalCount(startTimeStamp: Long, endTimeStamp: Long): Double =
  GraphUtil.getTotalMonthsBetweenYears(startTimeStamp, endTimeStamp)
    .toDouble()
    .coerceAtLeast(1.0)

/**
 * Render-time normalization of raw secondary metric values into the primary Y range. Mirrors
 * MA-3287's `GraphUtil.normalizeYValues` exactly — single-pass classification of entries into
 * prev/visible/next sentinel buckets (assumes input is sorted by X, which vico guarantees).
 * Returns `null` when the visible window is empty so vico falls back to `entry.y` instead of
 * applying a stale transform.
 */
private fun normalizeSecondaryEntriesToWeightRange(
  series: List<LineCartesianLayerModel.Entry>,
  weightMin: Double,
  weightMax: Double,
  minX: Long,
  maxX: Long,
): DoubleArray? {
  if (series.isEmpty() || !weightMin.isFinite() || !weightMax.isFinite() || weightMin >= weightMax) {
    return null
  }

  val yAxisSpan = weightMax - weightMin

  // Single pass, sentinel-based: prevY = LAST y before window, nextY = FIRST y after window.
  val visibleY = mutableListOf<Double>()
  var prevY: Double? = null
  var nextY: Double? = null
  for (entry in series) {
    val x = entry.x.toLong()
    val y = entry.y
    if (!y.isFinite()) continue
    when {
      x < minX -> prevY = y
      x > maxX -> if (nextY == null) nextY = y
      else -> visibleY.add(y)
    }
  }
  val metricValuesForRange = buildList {
    prevY?.let { add(it) }
    addAll(visibleY)
    nextY?.let { add(it) }
  }
  if (metricValuesForRange.isEmpty()) return null

  val metricMin = metricValuesForRange.min()
  val metricMax = metricValuesForRange.max()
  val metricRange = metricMax - metricMin
  val isSingle = metricRange < 0.01
  val effMin: Double
  val effMax: Double
  if (isSingle) {
    effMin = metricMin - 1.0
    effMax = metricMax + 1.0
  } else {
    val padding = metricRange * 0.05
    effMin = metricMin - padding
    effMax = metricMax + padding
  }
  val metricSpan = effMax - effMin
  if (metricSpan <= 0) return null

  val epsilon = yAxisSpan * 0.001
  val safeMin = weightMin + epsilon
  val safeMax = weightMax - epsilon
  val fallback = (weightMin + weightMax) / 2.0

  val result = DoubleArray(series.size)
  for (i in series.indices) {
    val y = series[i].y
    if (!y.isFinite()) {
      result[i] = if (fallback.isFinite()) fallback else weightMin
      continue
    }
    if (isSingle) {
      result[i] = weightMin + yAxisSpan * 0.7
    } else {
      val clamped = y.coerceIn(effMin, effMax)
      val normalized = weightMin + (clamped - effMin) * yAxisSpan / metricSpan
      result[i] = normalized.coerceIn(safeMin, safeMax)
    }
  }
  return result
}
