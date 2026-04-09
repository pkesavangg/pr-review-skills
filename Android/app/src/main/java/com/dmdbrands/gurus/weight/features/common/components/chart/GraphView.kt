package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodSummary
import com.dmdbrands.gurus.weight.features.common.components.chart.config.ChartConfig
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseDashboardState
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.BaseGraphIntent
import com.dmdbrands.gurus.weight.features.dashboard.viewmodel.base.SegmentState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.InterpolationType
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.SnapBehaviorConfig
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberScrubMarkerController
import com.patrykandpatrick.vico.compose.cartesian.rememberChartSnapFlingBehavior
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.Calendar
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog

private const val SCROLL_DELAY_AFTER_LAYOUT_MS = 50L

/**
 * Composable for displaying a chart with interactive scroll/snap/marker features.
 * Product-agnostic: driven by [ChartConfig] and [SegmentState].
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphView(
  modifier: Modifier = Modifier,
  state: BaseDashboardState,
  segmentState: SegmentState,
  chartConfig: ChartConfig,
  modelProducer: CartesianChartModelProducer,
  segment: GraphSegment = GraphSegment.WEEK,
  scrollTarget: Double? = null,
  canScrollToAnchor: Boolean = false,
  handleGraphIntent: (BaseGraphIntent) -> Unit,
  createFallbackEntry: (timestamp: Long, yValues: List<Double>, segment: GraphSegment) -> PeriodSummary? = { _, _, _ -> null },
  onScrollTargetConsumed: (Boolean) -> Unit = {},
  chartFillsHeight: Boolean = false,
) {
  val scope = rememberCoroutineScope()
  val currentDeviceType = getDeviceType()
  val chartHeight = remember(currentDeviceType) {
    when (currentDeviceType) {
      DeviceType.Tablet -> 400.dp
      DeviceType.Phone -> 300.dp
      DeviceType.Fold -> 250.dp
    }
  }

  val endTs = segmentState.endTimestamp
  val initialStartX = GraphUtil.getRollingWindowStart(segment, endTs)?.toDouble()
    ?: GraphUtil.getStartRange(segment, endTs)?.toDouble()
    ?: Calendar.getInstance().timeInMillis.toDouble()

  val (startPaddingXStep, _) = remember(segmentState.isEmptyGraph, segment) {
    if (!segmentState.isEmptyGraph || segment != GraphSegment.TOTAL)
      GraphSnapHelper.getVisiblePaddingXStepForSegment(segment)
    else
      0.0 to 0.0
  }
  val fadingEdges = rememberFadingEdges(
    startWidth = 0.dp,
    endWidth = 0.dp,
    startPaddingXStep = startPaddingXStep.takeIf { it > 0.0 },
    visibilityEasing = LinearEasing,
  )
  val initialScroll = remember(initialStartX, startPaddingXStep) {
    Scroll.Absolute.xWithPadding(initialStartX, startPaddingXStep)
  }

  val scrollState = rememberVicoScrollState(
    scrollEnabled = segment != GraphSegment.TOTAL && !segmentState.isSingleWindow,
    initialScroll = initialScroll,
    key = segment,
  )

  val snapConfig = remember(segment, startPaddingXStep) {
    SnapBehaviorConfig(
      snapToLabel = { currentXLabel, projectedXLabel, isDrag, isForward ->
        if (isDrag) {
          GraphSnapHelper.getSnappedPositionOnDrag(xLabel = currentXLabel, segment = segment)
        } else {
          GraphSnapHelper.getSnapPositionOnFling(
            timeStamp = projectedXLabel,
            segment = segment,
            isForward = isForward,
          )
        }
      },
      scrollPaddingXStep = startPaddingXStep,
      animation = SnapBehaviorConfig.SnapAnimation(snapDurationMillis = 500),
    )
  }
  val flingBehavior = rememberChartSnapFlingBehavior(scrollState = scrollState, config = snapConfig)
  val horizontalItemPlacer = rememberHorizontalAxisItemPlacer(segment = segment)

  fun onScrollUpdate(min: Long, max: Long) {
    scope.launch {
      handleGraphIntent(
        BaseGraphIntent.ScrollRange(segment, min, max) {
          // Fallback: no data in visible range — interpolate from chart lines
          val dataStart = segmentState.startTimestamp
          val dataEnd = segmentState.endTimestamp
          val visibleRange = scrollState.visibleXRange

          // Outside data range entirely → empty, no interpolation needed
          if (dataStart == null || dataEnd == null || visibleRange == null ||
            visibleRange.endInclusive < dataStart.toDouble() || visibleRange.start > dataEnd.toDouble()
          ) {
            handleGraphIntent(BaseGraphIntent.UpdateSegmentTarget(segment, emptyList()))
            return@ScrollRange
          }

          // Within data range — interpolate
          val visibleLabels = scrollState.getVisibleAxisLabels(horizontalItemPlacer).filter {
            it in visibleRange && it in dataStart.toDouble()..dataEnd.toDouble()
          }
          if (visibleLabels.isNotEmpty()) {
            val fallbackValues = scrollState.getInterpolatedYValues(
              xValues = visibleLabels,
              interpolationType = InterpolationType.MONOTONE,
            )
            val fallbackData = visibleLabels.mapIndexedNotNull { index, x ->
              val ts = x.toLong()
              val yValues = fallbackValues.mapNotNull { series -> series.getOrNull(index)?.toDouble() }
              if (yValues.isEmpty()) return@mapIndexedNotNull null
              createFallbackEntry(ts, yValues, segment)
            }
            handleGraphIntent(BaseGraphIntent.UpdateSegmentTarget(segment, fallbackData))
          }
        },
      )
    }
  }

  LaunchedEffect(segment) {
    if (scrollTarget == null || !canScrollToAnchor || segmentState.isEmptyGraph) return@LaunchedEffect
    val updatedScrollTarget = GraphUtil.getRelativeStart(segment, scrollTarget.toLong())
    val anchoredTarget = GraphUtil.getStartOnAnchored(segment, updatedScrollTarget)
    delay(SCROLL_DELAY_AFTER_LAYOUT_MS)
    scrollState.animateScroll(
      Scroll.Absolute.xWithPadding(
        anchoredTarget.toDouble(),
        GraphSnapHelper.getVisiblePaddingXStepForSegment(segment).first,
      ),
      animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
    )
    onScrollTargetConsumed(true)
  }

  val defaultMarker = rememberDefaultMarker(
    segmentState = segmentState,
    segment = segment,
    markerIndex = state.markerIndex,
    createFallbackEntry = createFallbackEntry,
    onTargetsUpdate = { entries ->
      if (entries.isNotEmpty()) {
        handleGraphIntent(BaseGraphIntent.UpdateSegmentTarget(segment, entries))
      }
    },
  )

  val scrubController = rememberScrubMarkerController(
    scrollState = scrollState,
    onMarkerIndexChanged = { clickX, targets ->
      if (clickX == null || segmentState.isEmptyGraph) {
        handleGraphIntent(BaseGraphIntent.UpdateMarkerIndex(null))
        return@rememberScrubMarkerController null
      }
      val visibleRange = scrollState.visibleXRange
      val visibleLabels = scrollState.getVisibleAxisLabels(itemPlacer = horizontalItemPlacer).filter {
        visibleRange != null && it in visibleRange
      }
      var markerIndex: Double? = null
      val startTs = segmentState.startTimestamp
      val endTs = segmentState.endTimestamp
      if (startTs != null && endTs != null) {
        val paddedMinCondition = startTs - GraphUtil.calculateXStep(segment = segment)
        val paddedMaxCondition = endTs + GraphUtil.calculateXStep(segment = segment)
        val outOfBoundaryCondition = clickX !in paddedMinCondition..paddedMaxCondition
        if (!outOfBoundaryCondition) {
          val targetMarkerIndex = getTargetPoints(
            visibleLabels, targets, clickX, segment, paddedMinCondition, paddedMaxCondition,
          )
          if (targetMarkerIndex.isNotEmpty()) {
            val targetIndex = targetMarkerIndex.first().toLong()
            markerIndex = when {
              targetIndex in startTs..endTs -> targetMarkerIndex.first()
              targetIndex < startTs -> startTs.toDouble()
              targetIndex > endTs -> endTs.toDouble()
              else -> null
            }
          }
        }
      }
      if (state.markerIndex != markerIndex) handleGraphIntent(BaseGraphIntent.UpdateMarkerIndex(markerIndex))
      markerIndex
    },
  )

  LaunchedEffect(state.markerIndex == null) {
    val vMin = segmentState.visibleMin
    val vMax = segmentState.visibleMax
    if (state.markerIndex == null && vMin != null && vMax != null) {
      delay(50)
      if (!scrollState.isScrolling) onScrollUpdate(vMin, vMax)
    }
  }

  val chart = rememberProductChart(
    config = chartConfig,
    graphState = state,
    segmentState = segmentState,
    defaultMarker = defaultMarker,
    segment = segment,
    horizontalItemPlacer = horizontalItemPlacer,
    fadingEdges = fadingEdges,
    scrubController = scrubController,
  )

  LaunchedEffect(scrollState) {
    snapshotFlow { scrollState.value }
      .debounce(100)
      .collect {
        if (segment == GraphSegment.TOTAL) return@collect
        val range = scrollState.visibleXRange ?: return@collect
        val min = range.start.toLong()
        val max = range.endInclusive.toLong()
        val relativeMin = GraphUtil.getRelativeStart(segment, min)
        val relativeMax = GraphUtil.getRelativeEnd(segment, max)
        val clipRange = GraphUtil.clipRangeForGraph(segment, relativeMin, relativeMax)
        onScrollUpdate(clipRange.startMillis, clipRange.endMillis)
        if (!segmentState.isEmptyGraph) {
          val endTs = segmentState.endTimestamp
          if (endTs != null) handleGraphIntent(BaseGraphIntent.UpdateIsEmptyGraph(segment, relativeMin > endTs))
        }
      }
  }

  CartesianChartHost(
    chart = chart,
    modelProducer = modelProducer,
    modifier = if (chartFillsHeight) modifier else modifier.height(chartHeight),
    scrollState = scrollState,
    animateIn = false,
    zoomState = rememberVicoZoomState(zoomEnabled = false),
    flingBehavior = flingBehavior,
  )
}

// ── getTargetPoints (unchanged) ──

fun getTargetPoints(
  fullList: List<Double>, points: List<Double>, input: Double, segment: GraphSegment,
  minWindow: Double? = null, maxWindow: Double? = null,
): List<Double> {
  if (segment == GraphSegment.TOTAL) {
    return listOfNotNull(points.minByOrNull { kotlin.math.abs(it - input) })
  }
  if (fullList.isEmpty()) return emptyList()
  val lower = fullList.filter { it <= input }.maxOrNull()
  val upper = fullList.filter { it >= input }.minOrNull()
  if (lower == null) {
    val pointsInRange =
      if (minWindow != null && upper != null) points.filter { it in minWindow..upper } else points.filter { it <= input }
    return listOfNotNull(pointsInRange.minByOrNull { kotlin.math.abs(it - input) })
  }
  if (upper == null) {
    val pointsInRange =
      if (maxWindow != null) points.filter { it in lower..maxWindow } else points.filter { it >= input }
    return listOfNotNull(pointsInRange.minByOrNull { kotlin.math.abs(it - input) })
  }
  val searchRange = if (minWindow != null && maxWindow != null) kotlin.math.max(minWindow, lower)..kotlin.math.min(
    maxWindow,
    upper,
  ) else lower..upper
  val filteredTargets = points.filter { it in searchRange }
  return when {
    filteredTargets.isEmpty() -> if (input < (lower + upper) / 2.0) listOf(lower) else listOf(upper)
    filteredTargets.size == 1 -> {
      val target = filteredTargets.first()
      val halfway = (upper - lower) / 2.0
      if (kotlin.math.abs(target - input) < halfway) listOf(target) else if (target > input) listOf(lower) else listOf(
        upper,
      )
    }

    else -> listOfNotNull(filteredTargets.minByOrNull { kotlin.math.abs(it - input) })
  }
}
