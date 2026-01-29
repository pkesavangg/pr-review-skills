package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.SnapBehaviorConfig
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.InterpolationType
import com.patrykandpatrick.vico.core.cartesian.Scroll
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Composable for displaying a graph/chart with interactive features.
 * Uses GraphViewModel for state management following MVI pattern.
 *
 * @param modifier Modifier for styling.
 * @param graphLines List of GraphLine data to display.
 * @param secondaryGraphLines Optional secondary graph lines.
 * @param segment The segment of the graph (e.g., WEEK, MONTH).
 * @param placeHolder Optional placeholder text if no data is present.
 * @param goal Optional goal for reference.
 * @param onTargetsUpdate Callback for metric updates, returns list of GraphPoint(s).
 * @param onScroll Callback for scroll events, returns formatted date range.
 * @param onLabelUpdate Callback for label updates, returns updated label string.
 * @param viewModel The GraphViewModel instance (injected via Hilt).
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphView(
  modifier: Modifier = Modifier,
  state: GraphState,
  segment: GraphSegment = GraphSegment.WEEK,
  scrollTarget: Double? = null,
  placeHolder: String? = null,
  viewModel: GraphViewModel = hiltViewModel(),
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
  val initialScroll = remember(initialStartX) {
    Scroll.Absolute.x(initialStartX)
  }
  val snapToLabelFunction: ((Double?, Boolean, Boolean) -> Double)? = remember {
    { scrolledX, isDrag, isForward ->
      if (isDrag) {
        val snappedPosition = GraphSnapHelper.getSnappedPositionOnDrag(xLabel = scrolledX, segment = segment)
        snappedPosition
      } else {
        GraphSnapHelper.getSnapPositionOnFling(timeStamp = scrolledX, segment = segment, isForward = isForward)
      }
    }
  }

  val scrollState = rememberVicoScrollState(
    scrollEnabled = segment != GraphSegment.TOTAL,
    initialScroll = initialScroll,
    snapBehaviorConfig = SnapBehaviorConfig(
      snapToLabelFunction = snapToLabelFunction,
      animation = SnapBehaviorConfig.SnapAnimation(
        snapDurationMillis = 500,
      ),
    ),
  )

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
              interpolationType = InterpolationType.CUBIC,
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



  LaunchedEffect(scrollState.value) {
    if (state.markerIndex != null) {
      viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(null))
    }
  }

  LaunchedEffect(state.markerIndex) {
    if (state.markerIndex == null && state.minTarget != null && state.maxTarget != null) {
      onScrollUpdate(state.minTarget, state.maxTarget)
    }
  }

  // LaunchedEffect(scrollTarget) {
  //   if (scrollTarget != null) {
  //     val destinationTarget = GraphUtil.getStartRange(segment, scrollTarget.toLong())
  //     if (destinationTarget != null)
  //       scrollState.animateScroll(
  //         Scroll.Absolute.x(destinationTarget.toDouble()),
  //       )
  //   }
  // }

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
    handleIntent = viewModel::handleIntent,
    onChartClick = { targets, click ->
      if (click == null) return@rememberGraphChart
      scope.launch {
        var markerIndex: Double? = null
        val paddedMinCondition = state.getStartTimestamp() - GraphUtil.calculateXStep(segment = segment).div(2)
        val paddedMaxCondition = state.getEndTimestamp() + GraphUtil.calculateXStep(segment = segment).div(2)
        val outOfBoundaryCondition = click !in paddedMinCondition..paddedMaxCondition
        if (outOfBoundaryCondition) {
          markerIndex = null
        } else {
          val visibleLabels = scrollState.getVisibleAxisLabels(itemPlacer = horizontalItemPlacer)
          val targetMarkerIndex =
            getTargetPoints(
              visibleLabels,
              targets,
              click,
              segment,
              state.minTarget?.toDouble(),
              state.maxTarget?.toDouble(),
            )
          if (targetMarkerIndex.isNotEmpty()) {
            val targetIndex = targetMarkerIndex.first().toLong()
            markerIndex = if (targetIndex in state.getStartTimestamp()..state.getEndTimestamp())
              targetMarkerIndex.first()
            else if (targetIndex < state.getStartTimestamp())
              state.getStartTimestamp().toDouble()
            else if (targetIndex > state.getEndTimestamp())
              state.getEndTimestamp().toDouble()
            else
              null
          }
        }
        viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(markerIndex))
      }
    },
  )
  CartesianChartHost(
    chart = chart,
    modelProducer = state.modelProducer,
    modifier = modifier.height(chartHeight),
    scrollState = scrollState,
    animateIn = true,
    zoomState = rememberVicoZoomState(zoomEnabled = false),
    onScrollStopped = { range ->
      if (range != null) {
        val min = range.visibleXRange.start
        val max = range.visibleXRange.endInclusive
        onScrollUpdate(min.toLong(), max.toLong())
        if (!state.isEmptyGraph)
          viewModel.handleIntent(GraphIntent.UpdateIsEmptyGraph(min > state.getEndTimestamp()))
      }
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
    val pointsInRange = if (minWindow != null) {
      points.filter { it in minWindow..input }
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
      points.filter { it in input..maxWindow }
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
