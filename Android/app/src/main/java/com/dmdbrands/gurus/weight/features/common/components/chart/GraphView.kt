package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.ONE_DAY_MILLIS
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.averageYValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.intervalCount
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.periodStarts
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphLine
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.helper.ImprovedNiceScaleCalculator.generateNiceScale
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
inline fun <T> rememberStable(
  value: T,
  crossinline areEqual: (T, T) -> Boolean = { a, b -> a == b }
): T {
  val state = remember { mutableStateOf(value) }
  if (!areEqual(state.value, value)) {
    state.value = value
  }
  return state.value
}

/**
 * Composable for displaying a graph/chart with interactive features.
 * Only UI logic and state management are present; all data transformation is delegated to GraphUtil.
 *
 * @param modifier Modifier for styling.
 * @param graphLines List of GraphLine data to display.
 * @param segment The segment of the graph (e.g., WEEK, MONTH).
 * @param placeHolder Optional placeholder text if no data is present.
 * @param selectedData List of selected GraphPoint(s) for marker display.
 * @param onScroll Callback for scroll events, returns formatted date range.
 * @param onSelected Callback for marker selection, returns selected GraphPoint(s).
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
  onMetricUpdate: (List<GraphPoint>) -> Unit = {},
  onScroll: (String?) -> Unit = {},
  onLabelUpdate: (String) -> Unit = {},
) {

  var selectedData: List<GraphPoint> by remember {
    mutableStateOf(listOf())
  }
  val stableGraphLines = rememberStable(graphLines)

  val stableSecondaryGraphLines by rememberUpdatedState(secondaryGraphLines)

  var point: Point? by remember { mutableStateOf(Point(0f, 0f)) }
  val xLabels = remember(stableGraphLines) {
    stableGraphLines.first().points.map { it.x }
  }
  val ySeries = remember(stableGraphLines) {
    stableGraphLines.map { it.points.map { point -> point.y } }
  }

  val timeStamp = stableGraphLines.flatMap { it.points.map { it.x.value.toDouble() }.sortedBy { it } }

  val scrollState = rememberVicoScrollState(
    scrollEnabled = segment != GraphSegment.TOTAL,
    initialScroll = Scroll.Absolute.End,
  )

  var minTarget by remember { mutableStateOf<Long?>(timeStamp.min().toLong()) }
  var maxTarget by remember { mutableStateOf<Long?>(timeStamp.max().toLong()) }
  var minYTarget by remember { mutableDoubleStateOf(0.0) }
  var secondaryMinYTarget by remember { mutableDoubleStateOf(0.0) }
  var maxYTarget by remember { mutableDoubleStateOf(220.0) }
  var secondaryMaxYTarget by remember { mutableDoubleStateOf(220.0) }

  val animatedMinTarget by animateIntAsState(
    targetValue = minYTarget.toInt(),
    animationSpec = tween(300),
  )
  val animatedSecondaryMinTarget by animateIntAsState(
    targetValue = secondaryMinYTarget.toInt(),
    animationSpec = tween(300),
  )
  val animatedMaxTarget by animateIntAsState(
    targetValue = maxYTarget.toInt(),
    animationSpec = tween(300),
  )
  val animatedSecondaryMaxTarget by animateIntAsState(
    targetValue = secondaryMaxYTarget.toInt(),
    animationSpec = tween(300),
  )
  var selectedTarget by remember { mutableStateOf<Long?>(null) }
  var markerIndex: Int? by remember(xLabels) { mutableStateOf(null) }
  var isUpdating by remember { mutableStateOf(false) }
  val modelProducer = remember { CartesianChartModelProducer() }
  val graphKey = remember(stableGraphLines) { stableGraphLines.hashCode() }
  // Remember the job outside LaunchedEffect
  var computationJob by remember { mutableStateOf<Job?>(null) }
  val minTargetState = rememberUpdatedState(minTarget)
  val maxTargetState = rememberUpdatedState(maxTarget)
  var stepSize by remember { mutableDoubleStateOf(10.0) }

  LaunchedEffect(graphKey, stableSecondaryGraphLines) {
    modelProducer.runTransaction {
      lineSeries {
        ySeries.forEach { y ->
          series(
            x = xLabels.map { it.value as Long },
            y = y.map { it.value },
          )
        }
      }
      if (stableSecondaryGraphLines != null && stableSecondaryGraphLines!!.points.isNotEmpty()) {
        lineSeries {
          series(
            x = stableSecondaryGraphLines!!.points.map { it.x.value as Long },
            y = stableSecondaryGraphLines!!.points.map { it.y.value },
          )
        }
        val secondaryYAxis = stableSecondaryGraphLines!!.points.map { it.y.value.toDouble() }
        val secondaryGraphMeta = generateNiceScale(
          floor(secondaryYAxis.min()),
          ceil(secondaryYAxis.max()),
          goalWeight = 80.0,
        )
        secondaryMinYTarget = secondaryGraphMeta.min
        secondaryMaxYTarget = secondaryGraphMeta.max
      }
    }

  }

  LaunchedEffect(segment) {
    isUpdating = true
    markerIndex = xLabels.lastIndex

    val target = selectedTarget ?: maxTarget
    val nearest = target?.let {
      xLabels.map { it.value.toLong() }
        .minByOrNull { abs(it - target) }
    }
    repeat(3) { withFrameNanos { } }
    if (nearest != null) {
      val endRange = GraphUtil.getEndRange(segment, nearest)
      if (endRange != null) {
        scrollState.scroll(Scroll.Absolute.x(endRange.toDouble(), 1.0f)) // ✅ suspend function}
      } else {
        scrollState.scroll(Scroll.Absolute.End)
      }
    }
    selectedTarget = null
    isUpdating = false
  }
  LaunchedEffect(selectedData) {
    // Cancel any existing computation job first
    computationJob?.cancel()

    if (selectedData.isEmpty()) {
      computationJob = launch(Dispatchers.Default) {
        val formattedRange = GraphUtil.formatDateRange(
          minTarget ?: 0L,
          maxTarget ?: 0L, segment,
        )
        onScroll(formattedRange)
        val subset = averageYValuesInRange(
          stableGraphLines,
          minTarget ?: 0L,
          maxTarget ?: 0L,
        )
        // Check if still active before updating
        if (isActive) {
          val joinedLabel = subset.values
            .filterNotNull()
            .joinToString(" / ") { it.label }
          onLabelUpdate(joinedLabel)
          val graphLines = filterXValuesInRange(
            stableGraphLines,
            minTarget ?: 0L,
            maxTarget ?: 0L,
          )
          onMetricUpdate(graphLines.flatMap { it.points })
        }

        // Clear the job reference when done
        computationJob = null
      }
    } else {
      onScroll(null)
      onMetricUpdate(
        listOf(
          selectedData.first(),
        ),
      )
      onLabelUpdate(
        selectedData.first().y.label,
      )
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow { minTargetState.value to maxTargetState.value }
      .debounce(500)
      .collect { (min, max) ->
        // Run your logic here after the debounce period
        computationJob = launch(Dispatchers.Default) {
          val formattedRange = GraphUtil.formatDateRange(
            min ?: 0L,
            max ?: 0L, segment,
          )
          onScroll(formattedRange)
          val subset = averageYValuesInRange(
            stableGraphLines,
            min ?: 0L,
            max ?: 0L,
          )
          selectedData = listOf()
          // Check if still active before updating
          val joinedLabel = subset.values
            .filterNotNull()
            .joinToString(" / ") { it.label }
          onLabelUpdate(joinedLabel)
          val graphLines = filterXValuesInRange(
            stableGraphLines,
            minTarget ?: 0L,
            maxTarget ?: 0L,
          )
          onMetricUpdate(graphLines.flatMap { it.points })
          val intervalCount = segment.intervalCount().div(2)
          val paddedMinTarget = minTarget?.minus(ONE_DAY_MILLIS * intervalCount)
          val paddedMaxTarget = maxTarget?.plus(ONE_DAY_MILLIS * intervalCount)
          val paddedGraphLines = filterXValuesInRange(
            stableGraphLines,
            paddedMinTarget ?: 0L,
            paddedMaxTarget ?: 0L,
          )
          val yAxis = paddedGraphLines.flatMap { graphLine -> graphLine.points.map { it.y.value as Double } }
          if (yAxis.isNotEmpty()) {
            var tempMax = 0.0
            var tempMin = 0.0
            tempMax = ceil(yAxis.max())
            tempMin = floor(yAxis.min())
            if (maxYTarget == minYTarget) {
              tempMax += 1
              tempMin -= 1
            }
            val graphMeta = generateNiceScale(
              tempMin,
              tempMax,
              goalWeight = 80.0,
            )
            minYTarget = graphMeta.min
            maxYTarget = graphMeta.max
            if (stableSecondaryGraphLines != null) {
              val paddedSecondaryGraphLines = filterXValuesInRange(
                listOf(stableSecondaryGraphLines!!),
                paddedMinTarget ?: 0L,
                paddedMaxTarget ?: 0L,
              )
              val secondaryYAxis =
                paddedSecondaryGraphLines.flatMap { graphLine -> graphLine.points.map { it.y.value.toDouble() } }
              if (secondaryYAxis.isNotEmpty()) {
                val secondaryGraphMeta = generateNiceScale(
                  floor(secondaryYAxis.min()),
                  ceil(secondaryYAxis.max()),
                  goalWeight = 80.0,
                )
                secondaryMinYTarget = secondaryGraphMeta.min
                secondaryMaxYTarget = secondaryGraphMeta.max
              }
            }
            stepSize = graphMeta.step
          }

          // Clear the job reference when done
          computationJob = null
        }
      }
  }
  val initialTimeStamp = xLabels.minOf { it.value as Long }
  val primaryLayer = primaryLayer(
    segment, animatedMinTarget, animatedMaxTarget,
    initialTimeStamp,
  )
  val secondaryLayer = secondaryLayer(
    segment = segment,
    minYTarget = animatedSecondaryMinTarget,
    maxYTarget = animatedSecondaryMaxTarget,
    initialTimeStamp = initialTimeStamp,
  )
  val todayMills = Calendar.getInstance().timeInMillis

  val startRangeX = GraphUtil.getStartRange(segment, initialTimeStamp)

  val endRangeX = GraphUtil.getEndRange(segment, todayMills)
  val separators = periodStarts(
    segment,
    startRangeX,
    endRangeX,

    )

  val defaultMarker = rememberDefaultMarker(xLabels, markerIndex, segment)
  val decorations = rememberHorizontalLine(goal = goal)

  val horizontalItemPlacer = horizontalItemPlacer(
    isEnabled = !isUpdating,
    segment = segment,
    onDestinationUpdate = { min, max ->
      minTarget = min
      maxTarget = max
    },
  )

  val markerListener = markerListener(
    stableGraphLines = stableGraphLines,
    point = point,
    xLabels = xLabels,
    onSelected = {
      selectedData = it
    },
    setMarkerIndex = { markerIndex = it },
    selectedData = selectedData,
    onDestinationUpdate = { selectedTarget = it },
  )

  val currentDeviceType = getDeviceType()
  val chartHeight = if (currentDeviceType == DeviceType.Tablet)
    400.dp else 300.dp

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
                  if (position != null && computationJob == null) {
                    point = (Point(position.x, position.y))
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
    stepSize = stepSize,
    primaryLayer = primaryLayer,
    secondaryLayer = secondaryLayer,
    markerListener = markerListener,
    defaultMarker = defaultMarker,
    decorations = decorations,
    xLabels = xLabels,
    markerIndex = markerIndex,
    isUpdating = isUpdating,
    selectedData = selectedData,
    modelProducer = modelProducer,
    scrollState = scrollState,
    horizontalItemPlacer = horizontalItemPlacer,
    separators = separators,
  )
}


