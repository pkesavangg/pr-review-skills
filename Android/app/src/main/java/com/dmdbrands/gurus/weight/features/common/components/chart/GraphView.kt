package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.common.Point as VicoPoint

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
 * @param onMetricUpdate Callback for metric updates, returns list of GraphPoint(s).
 * @param onScroll Callback for scroll events, returns formatted date range.
 * @param onLabelUpdate Callback for label updates, returns updated label string.
 * @param viewModel The GraphViewModel instance (injected via Hilt).
 */
@Composable
fun GraphView(
  modifier: Modifier = Modifier,
  graphLines: List<GraphLine>,
  secondaryGraphLines: GraphLine? = null,
  segment: GraphSegment = GraphSegment.WEEK,
  placeHolder: String? = null,
  goal: Goal? = null,
  onMetricUpdate: (List<GraphPoint>) -> Unit = {},
  onScroll: (String?) -> Unit = {},
  onLabelUpdate: (String) -> Unit = {},
  viewModel: GraphViewModel = hiltViewModel()
) {
  val state by viewModel.state.collectAsState()

  // Store callbacks in ViewModel
  LaunchedEffect(Unit) {
    viewModel.setCallbacks(onMetricUpdate, onScroll, onLabelUpdate)
  }

  // Handle segment changes
  LaunchedEffect(segment) {
    viewModel.handleIntent(GraphIntent.UpdateSegment(segment))
  }

  // Animated values for smooth transitions
  val animatedMinTarget by animateIntAsState(
    targetValue = state.minYTarget.toInt(),
    animationSpec = tween(300),
    label = "minTarget",
  )

  val animatedSecondaryMinTarget by animateIntAsState(
    targetValue = state.secondaryMinYTarget.toInt(),
    animationSpec = tween(300),
    label = "secondaryMinTarget",
  )

  val animatedMaxTarget by animateIntAsState(
    targetValue = state.maxYTarget.toInt(),
    animationSpec = tween(300),
    label = "maxTarget",
  )

  val animatedSecondaryMaxTarget by animateIntAsState(
    targetValue = state.secondaryMaxYTarget.toInt(),
    animationSpec = tween(300),
    label = "secondaryMaxTarget",
  )

  // Scroll state
  val scrollState = rememberVicoScrollState(
    scrollEnabled = segment != GraphSegment.TOTAL,
    initialScroll = Scroll.Absolute.End,
  )

  // Initialize graph when data changes
  LaunchedEffect(graphLines, secondaryGraphLines) {
    viewModel.handleIntent(
      GraphIntent.InitializeGraph(
        graphLines = graphLines,
        secondaryGraphLines = secondaryGraphLines,
        segment = segment,
        goal = goal,
      ),
    )
  }

  // Chart layers and components
  val primaryLayer = primaryLayer(
    segment,
    animatedMinTarget,
    animatedMaxTarget,
    state.initialTimeStamp,
  )

  val secondaryLayer = secondaryLayer(
    segment = segment,
    minYTarget = animatedSecondaryMinTarget,
    maxYTarget = animatedSecondaryMaxTarget,
    initialTimeStamp = state.initialTimeStamp,
  )

  val startRangeX = GraphUtil.getStartRange(segment, state.initialTimeStamp)
  val endRangeX = GraphUtil.getEndRange(segment, state.todayMills)
  val separators = GraphUtil.periodStarts(segment, startRangeX, endRangeX)

  val defaultMarker = rememberDefaultMarker(state.xLabels, state.markerIndex, segment)
  val decorations = rememberHorizontalLine(goal = goal)

  val horizontalItemPlacer = horizontalItemPlacer(
    isEnabled = !state.isUpdating,
    segment = segment,
    onDestinationUpdate = { min, max ->
      viewModel.handleIntent(GraphIntent.HandleScroll(min, max))
    },
  )

  val markerListener = markerListener(
    stableGraphLines = state.graphLines,
    point = state.point,
    xLabels = state.xLabels,
    onSelected = { selectedData ->
      viewModel.handleIntent(GraphIntent.HandleMarkerSelection(selectedData))
    },
    setMarkerIndex = { markerIndex ->
      viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(markerIndex))
    },
    selectedData = state.selectedData,
    onDestinationUpdate = { selectedTarget ->
      viewModel.handleIntent(GraphIntent.UpdateSelectedTarget(selectedTarget))
    },
  )

  val currentDeviceType = getDeviceType()
  val chartHeight = if (currentDeviceType == DeviceType.Tablet) 400.dp else 300.dp

  ChartHostSection(
    modifier = modifier
      .height(chartHeight)
      .pointerInput(Unit) {
        var isScrollInProgress = false
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
              PointerEventType.Press -> {
                isScrollInProgress = false
              }

              PointerEventType.Move -> {
                isScrollInProgress = true
              }

              PointerEventType.Release -> {
                if (!isScrollInProgress) {
                  val position = event.changes.firstOrNull()?.position
                  if (position != null && state.computationJob == null) {
                    viewModel.handleIntent(
                      GraphIntent.UpdatePoint(VicoPoint(position.x, position.y)),
                    )
                  }
                } else {
                  viewModel.handleIntent(GraphIntent.UpdatePoint(null))
                }
                isScrollInProgress = false
              }
            }
          }
        }
      },
    segment = segment,
    stepSize = state.stepSize,
    primaryLayer = primaryLayer,
    secondaryLayer = secondaryLayer,
    markerListener = markerListener,
    defaultMarker = defaultMarker,
    decorations = decorations,
    xLabels = state.xLabels,
    markerIndex = state.markerIndex,
    isUpdating = state.isUpdating,
    selectedData = state.selectedData,
    modelProducer = state.modelProducer,
    scrollState = scrollState,
    horizontalItemPlacer = horizontalItemPlacer,
    separators = separators,
    isEmpty = state.isEmpty,
  )
}


