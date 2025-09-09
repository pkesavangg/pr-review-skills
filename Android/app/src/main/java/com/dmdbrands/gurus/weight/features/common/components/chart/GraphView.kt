package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  viewModel: GraphViewModel = hiltViewModel(),
) {

  val state by viewModel.state.collectAsState()
  var point: VicoPoint? by remember { mutableStateOf(null) }

  // Scroll state
  val scrollState = rememberVicoScrollState(
    scrollEnabled = segment != GraphSegment.TOTAL,
    initialScroll = Scroll.Absolute.End,
  )

  // Store callbacks in ViewModel
  DisposableEffect(Unit) {
    viewModel.setCallbacks(onMetricUpdate, onScroll, onLabelUpdate) { target ->
      // Store the target for processing in LaunchedEffect
      viewModel.handleIntent(GraphIntent.SetScrollTarget(target))
    }
    onDispose {
      viewModel.handleIntent(GraphIntent.SetScrollTarget(null))
    }
  }

  // Initialize graph when data changes
  LaunchedEffect(secondaryGraphLines) {
    viewModel.handleIntent(
      GraphIntent.InitializeGraph(
        graphLines = graphLines,
        secondaryGraphLines = secondaryGraphLines,
        goal = goal,
      ),
    )
  }

  // Chart layers and components
  val primaryLayer = primaryLayer(
    segment,
    state.primaryYAxis?.min?.toInt(),
    state.primaryYAxis?.max?.toInt(),
    state.initialTimeStamp,
  )

  val secondaryLayer = secondaryLayer(
    segment = segment,
    minYTarget = state.secondaryYAxis?.min?.toInt(),
    maxYTarget = state.secondaryYAxis?.max?.toInt(),
    initialTimeStamp = state.initialTimeStamp,
  )

  val defaultMarker = rememberDefaultMarker(state.xLabels, state.markerIndex, segment)
  val decorations = rememberHorizontalLine(goal = goal)

  val horizontalItemPlacer = horizontalItemPlacer(
    isEnabled = !state.isUpdating,
    segment = segment,
    onDestinationUpdate = { min, max ->
      viewModel.handleIntent(GraphIntent.SetScrollRange(min, max))
    },
  )

  val markerListener = markerListener(
    point = point,
    xLabels = state.xLabels,
    setMarkerIndex = { markerIndex ->
      if (markerIndex != null) {
        if (markerIndex > 0) {
          viewModel.handleIntent(GraphIntent.UpdateMarkerIndex(markerIndex))
        }
      }
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
                    point = VicoPoint(position.x, position.y)
                  }
                } else {
                  point = null
                }
                isScrollInProgress = false
              }
            }
          }
        }
      },
    segment = segment,
    primaryLayer = primaryLayer,
    secondaryLayer = secondaryLayer,
    markerListener = markerListener,
    defaultMarker = defaultMarker,
    decorations = decorations,
    xLabels = state.xLabels,
    markerIndex = state.markerIndex,
    state = state,
    modelProducer = state.modelProducer,
    scrollState = scrollState,
    horizontalItemPlacer = horizontalItemPlacer,
    separators = state.separators,
  )
}


