package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.ChartInteractionEvent
import com.patrykandpatrick.vico.compose.cartesian.SnapBehaviorConfig
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.InterpolationType
import com.patrykandpatrick.vico.core.cartesian.Scroll
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Calendar
import android.util.Log

/**
 * Composable for displaying a graph/chart with interactive features.
 * Uses GraphViewModel for state management following MVI pattern.
 *
 * @param modifier Modifier for styling.
 * @param segment The segment of the graph (e.g., WEEK, MONTH).
 * @param isCurrentPage True when this page is the one currently visible in the pager;
 *   used to trigger a reset-to-latest (scroll to default range + auto-select latest entry) on every tab switch.
 * @param state The current [GraphState] from the view model.
 * @param placeHolder Optional placeholder text if no data is present.
 * @param viewModel The GraphViewModel instance (injected via Hilt).
 * @param onChartConsuming Invoked while the chart is internally settling after a tab switch.
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphView(
  modifier: Modifier = Modifier,
  state: GraphState,
  segment: GraphSegment = GraphSegment.WEEK,
  isCurrentPage: Boolean = false,
  placeHolder: String? = null,
  viewModel: GraphViewModel = hiltViewModel(),
  onChartConsuming: (Boolean) -> Unit = {},
) {

  val scope = rememberCoroutineScope()
  val currentDeviceType = getDeviceType()
  val chartHeight = remember(state.markerIndex) {
    when (currentDeviceType) {
      DeviceType.Tablet -> 400.dp
      DeviceType.Phone -> 300.dp
      DeviceType.Fold -> 250.dp
    }
  }

  val initialStartX = GraphUtil.getRollingWindowStart(segment, state.getEndTimestamp())?.toDouble()
    ?: GraphUtil.getStartRange(segment, state.getEndTimestamp())?.toDouble()
    ?: Calendar.getInstance().timeInMillis.toDouble()

  val (startPaddingXStep, _) = remember(state.isEmptyGraph, segment) {
    if (!state.isEmptyGraph || segment != GraphSegment.TOTAL)
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

  val snapToLabelFunction: ((Double?, Boolean, Boolean) -> Double)? = remember {
    { scrolledX, isDrag, isForward ->
      if (isDrag) {
        GraphSnapHelper.getSnappedPositionOnDrag(xLabel = scrolledX, segment = segment)
      } else {
        GraphSnapHelper.getSnapPositionOnFling(timeStamp = scrolledX, segment = segment, isForward = isForward)
      }
    }
  }

  // Bumped on every page activation so VicoScrollState is rebuilt fresh
  // (initialScrollHandled = false). Plain `remember` (not rememberSaveable) avoids
  // any saver restoring a stale scroll/initialScrollHandled across tab returns.
  var resetEpoch by remember(segment) { mutableIntStateOf(0) }
  LaunchedEffect(isCurrentPage, segment) {
    if (isCurrentPage) resetEpoch++
  }

  val scrollState = remember(segment, resetEpoch) {
    VicoScrollState(
      scrollEnabled = segment != GraphSegment.TOTAL && !state.isSingleWindow,
      initialScroll = initialScroll,
      autoScroll = initialScroll,
      autoScrollCondition = AutoScrollCondition.Never,
      autoScrollAnimationSpec = spring(),
      snapBehaviorConfig = SnapBehaviorConfig(
        snapToLabelFunction = snapToLabelFunction,
        animation = SnapBehaviorConfig.SnapAnimation(
          snapDurationMillis = 500,
        ),
      ),
      scrollStartPaddingXStep = startPaddingXStep,
    )
  }
  val horizontalItemPlacer =
    rememberHorizontalAxisItemPlacer(
      segment = segment,
    )

  fun onScrollUpdate(min: Long, max: Long) {
    scope.launch {
      viewModel.handleIntent(
        GraphIntent.SetScrollRange(min, max) {
          val visibleLabels = scrollState.getVisibleAxisLabels(horizontalItemPlacer).filter {
            it.toLong() in min..max
          }
          if (visibleLabels.isNotEmpty()) {
            val fallbackValues = scrollState.getInterpolatedYValues(
              xValues = visibleLabels,
              interpolationType = InterpolationType.MONOTONE,
            )
            val fallbackData = state.createFallBackData(
              segment = segment,
              timeStamps = visibleLabels.map { it.toLong() },
              fallbackValues = fallbackValues.map { it.map { it.toDouble() } },
            )
            viewModel.handleIntent(GraphIntent.UpdateTarget(fallbackData))
          }
        },
      )
    }
  }
  LaunchedEffect(isCurrentPage) {
    if (!isCurrentPage) {
      viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(null))
    }
  }

  LaunchedEffect(resetEpoch) {
    if (resetEpoch == 0 || !isCurrentPage || state.isEmptyGraph || state.data.isEmpty()) {
      return@LaunchedEffect
    }
    val latestEntry = state.data.maxByOrNull { it.getTimeStamp() } ?: return@LaunchedEffect
    val latestTimeStamp = latestEntry.getTimeStamp()
    if (segment != GraphSegment.TOTAL) {
      val windowStart = GraphUtil.getRollingWindowStart(segment, latestTimeStamp)
        ?: GraphUtil.getStartRange(segment, latestTimeStamp)
      if (windowStart != null) {
        onScrollUpdate(windowStart, latestTimeStamp)
      }
    }
    viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(latestTimeStamp.toDouble()))
    viewModel.handleIntent(GraphIntent.UpdateTarget(listOf(latestEntry)))
    onChartConsuming(false)
  }

  LaunchedEffect(state.markerIndex == null) {
    if (state.markerIndex == null && state.minTarget != null && state.maxTarget != null) {
      onScrollUpdate(state.minTarget, state.maxTarget)
    }
  }

  LaunchedEffect(scrollState) {
    scrollState.interactionEvents
      .filter { it is ChartInteractionEvent.DragStarted }
      .collect {
        viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(null))
      }
  }

  val defaultMarker = rememberDefaultMarker(
    state = state,
    segment = segment,
    onTargetsUpdate = {
      if (it.isNotEmpty())
        viewModel.handleIntent(GraphIntent.UpdateTarget(it))
    },
  )

  val chart = rememberGraphChart(
    state = state,
    defaultMarker = defaultMarker,
    segment = segment,
    horizontalItemPlacer = horizontalItemPlacer,
    fadingEdges = fadingEdges,
    handleIntent = viewModel::handleIntent,
    onChartClick = { targets, click ->
      if (click == null || state.isEmptyGraph) {
        return@rememberGraphChart
      }
      val currentInteractionEvent = scrollState.interactionEvents.value
      if (currentInteractionEvent is ChartInteractionEvent.MarkerScrubbing || currentInteractionEvent is ChartInteractionEvent.MarkerSelectionStarted || currentInteractionEvent is ChartInteractionEvent.Stable) {
        val visibleLabels =
          scrollState.getVisibleAxisLabels(itemPlacer = horizontalItemPlacer).filter {
            if (state.minTarget != null && state.maxTarget != null)
              it.toLong() in state.minTarget..state.maxTarget
            else
              true
          }
        var markerIndex: Double? = null
        val paddedMinCondition = state.getStartTimestamp() - GraphUtil.calculateXStep(segment = segment)
        val paddedMaxCondition = state.getEndTimestamp() + GraphUtil.calculateXStep(segment = segment)
        val outOfBoundaryCondition = click !in paddedMinCondition..paddedMaxCondition
        if (!outOfBoundaryCondition) {
          val targetMarkerIndex =
            getTargetPoints(
              visibleLabels,
              targets,
              click,
              segment,
              paddedMinCondition,
              paddedMaxCondition,
            )
          if (targetMarkerIndex.isNotEmpty()) {
            val targetIndex = targetMarkerIndex.first().toLong()
            markerIndex = when {
              targetIndex in state.getStartTimestamp()..state.getEndTimestamp() -> targetMarkerIndex.first()
              targetIndex < state.getStartTimestamp() -> state.getStartTimestamp().toDouble()
              targetIndex > state.getEndTimestamp() -> state.getEndTimestamp().toDouble()
              else -> null
            }
          }
        }
        if (state.markerIndex != markerIndex) {
          viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(markerIndex))
        }
      }
    },
  )

  CartesianChartHost(
    chart = chart,
    modelProducer = state.modelProducer,
    modifier = modifier.height(chartHeight),
    scrollState = scrollState,
    consumeMoveEvents = true,
    zoomState = rememberVicoZoomState(zoomEnabled = false),
    onScrollStopped = { range ->
      if (range != null && segment != GraphSegment.TOTAL) {
        val min = range.visibleXRange.start.toLong()
        val max = range.visibleXRange.endInclusive.toLong()
        val relativeMin = GraphUtil.getRelativeStart(segment, min)
        val relativeMax = GraphUtil.getRelativeEnd(segment, max)
        val clipRange = GraphUtil.clipRangeForGraph(segment, relativeMin, relativeMax)
        onScrollUpdate(clipRange.startMillis, clipRange.endMillis)
        if (!state.isEmptyGraph)
          viewModel.handleIntent(GraphIntent.UpdateIsEmptyGraph(relativeMin > state.getEndTimestamp()))
      }
      onChartConsuming(false)
    },
  )
}

/**
 * Gets target points based on visible labels, available points, and current window bounds.
 *
 * @param fullList List of visible axis labels (from scrollState).
 * @param points List of all available target points.
 * @param input The clicked position value.
 * @param segment The graph segment type.
 * @param minWindow Optional minimum x value of current window from state.
 * @param maxWindow Optional maximum x value of current window from state.
 * @return List of target points that match the criteria.
 */
fun getTargetPoints(
  fullList: List<Double>,
  points: List<Double>,
  input: Double,
  segment: GraphSegment,
  minWindow: Double? = null,
  maxWindow: Double? = null,
): List<Double> {

  // For TOTAL segment, find nearest targets from click without considering visible labels
  if (segment == GraphSegment.TOTAL) {
    val nearestTarget = points.minByOrNull { kotlin.math.abs(it - input) }
    return listOfNotNull(nearestTarget)
  }

  // For other segments, use the original logic with visible labels
  if (fullList.isEmpty()) return emptyList()

  // find lower and upper bound from full list (visible labels)
  val lower = fullList.filter { it <= input }.maxOrNull()
  val upper = fullList.filter { it >= input }.minOrNull()

  // Handle edge cases where input is outside fullList range
  // Use window bounds from state to find points within current window
  if (lower == null) {
    // Input is below fullList range, find points within window bounds
    val pointsInRange = if (minWindow != null && upper != null) {
      points.filter { it in minWindow..upper }
    } else {
      points.filter { it <= input }
    }
    return if (pointsInRange.isNotEmpty()) {
      // Return the nearest point to input, not just the max
      val nearestTarget = pointsInRange.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    } else {
      emptyList()
    }
  }

  if (upper == null) {
    // Input is above fullList range, find points within window bounds
    val pointsInRange = if (maxWindow != null) {
      points.filter { it in lower..maxWindow }
    } else {
      points.filter { it >= input }
    }
    return if (pointsInRange.isNotEmpty()) {
      // Return the nearest point to input, not just the min
      val nearestTarget = pointsInRange.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    } else {
      emptyList()
    }
  }

  // Both lower and upper exist, proceed with original logic
  // Filter targets within the window bounds if available, otherwise use lower..upper
  val searchRange = if (minWindow != null && maxWindow != null) {
    // Use intersection of visible labels range and window bounds
    kotlin.math.max(minWindow, lower)..kotlin.math.min(maxWindow, upper)
  } else {
    lower..upper
  }

  val filteredTargets = points.filter { it in searchRange }

  return when {
    filteredTargets.isEmpty() -> {
      val halfway = (lower + upper) / 2.0

      // check halfway condition to return lower or upper
      if (input < halfway) {
        listOf(lower)
      } else {
        listOf(upper)
      }
    }

    filteredTargets.size == 1 -> {
      val target = filteredTargets.first()
      val halfway = (upper - lower) / 2.0

      // check if rounding of the point meets the target
      if (kotlin.math.abs(target - input) < halfway) {
        listOf(target)
      } else if (target > input) {
        listOf(lower)
      } else {
        listOf(upper)
      }
    }

    else -> {
      // return the nearest target to the point
      val nearestTarget = filteredTargets.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    }
  }
}
