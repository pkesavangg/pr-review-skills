package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import kotlinx.coroutines.FlowPreview
import android.icu.util.Calendar

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
  graphLines: List<GraphLine>,
  secondaryGraphLines: GraphLine? = null,
  segment: GraphSegment = GraphSegment.WEEK,
  placeHolder: String? = null,
  goal: Goal? = null,
  onRangeUpdate: (String?) -> Unit = {},
  onTargetsUpdate: (targets: List<Double>, fallbackValue: List<Double>) -> Unit = { _, _ -> },
  onWeightLabelUpdate: (String) -> Unit = {},
  viewModel: GraphViewModel = hiltViewModel(),
) {  // Scroll state

  val state by viewModel.state.collectAsState()

  // Store callbacks in ViewModel
  LaunchedEffect(Unit) {
    viewModel.setCallbacks(onTargetsUpdate, onRangeUpdate, onWeightLabelUpdate)
  }

  val initialStartX = GraphUtil.getStartRange(segment, state.endTimeStamp)?.toDouble()
    ?: Calendar.getInstance().timeInMillis.toDouble()

  val initialScroll = remember(initialStartX) {
    Scroll.Absolute.x(initialStartX)
  }
  val scrollState =
    rememberVicoScrollState(scrollEnabled = segment != GraphSegment.TOTAL, initialScroll = initialScroll)

  // Initialize graph when data changes
  LaunchedEffect(graphLines, secondaryGraphLines) {
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
    segment = segment,
    yRangeValues = state.primaryYAxis,
  )

  val secondaryLayer = secondaryLayer(
    segment = segment,
    yRangeValues = state.secondaryYAxis,
  )

  val defaultMarker = rememberDefaultMarker(segment) { fallbackValues ->
    val weightLabel =
      state.graphLines.first().points.firstOrNull { it.x.value.toDouble() == state.markerIndex?.toDouble() }?.y?.value?.toString()
    onWeightLabelUpdate(weightLabel ?: fallbackValues.first().average().toString())
    onTargetsUpdate(listOfNotNull(state.markerIndex), emptyList())
  }
  val goalMarker = rememberGoalMarker(goal = goal)

  val horizontalItemPlacer = horizontalItemPlacer(
    segment = segment,
  )

  val currentDeviceType = getDeviceType()
  val chartHeight = when (currentDeviceType) {
    DeviceType.Tablet -> 400.dp
    DeviceType.Phone -> 300.dp
    DeviceType.Fold -> 250.dp
  }


  ChartHostSection(
    modifier = modifier
      .height(chartHeight),
    segment = segment,
    primaryLayer = primaryLayer,
    secondaryLayer = secondaryLayer,
    defaultMarker = defaultMarker,
    goalMarker = goalMarker,
    xLabels = state.xLabels,
    markerIndex = state.markerIndex,
    state = state,
    modelProducer = state.modelProducer,
    scrollState = scrollState,
    handleIntent = viewModel::handleIntent,
    horizontalItemPlacer = horizontalItemPlacer,
  )
}


