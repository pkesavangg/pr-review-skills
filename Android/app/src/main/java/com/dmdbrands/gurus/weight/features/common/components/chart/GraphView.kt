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
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.features.common.components.chart.config.ChartConfig
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphViewModel
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.ProductGraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.DeviceType
import com.dmdbrands.gurus.weight.features.common.helper.getDeviceType
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphSnapHelper
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
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
 * Composable for displaying a graph/chart with interactive features.
 * Product-agnostic: receives [productState] for per-product data and
 * [graphState] for shared fields (goal, weightUnit).
 */
@OptIn(FlowPreview::class)
@Composable
fun GraphView(
  modifier: Modifier = Modifier,
  graphState: GraphState,
  productState: ProductGraphState,
  chartConfig: ChartConfig,
  productType: ProductType,
  segment: GraphSegment = GraphSegment.WEEK,
  scrollTarget: Double? = null,
  canScrollToAnchor: Boolean = false,
  viewModel: GraphViewModel = hiltViewModel(),
  onChartConsuming: (Boolean) -> Unit = {},
  onScrollTargetConsumed: (Boolean) -> Unit = {},
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

  val initialStartX = GraphUtil.getRollingWindowStart(segment, productState.getEndTimestamp())?.toDouble()
    ?: GraphUtil.getStartRange(segment, productState.getEndTimestamp())?.toDouble()
    ?: Calendar.getInstance().timeInMillis.toDouble()

  val (startPaddingXStep, _) = remember(productState.isEmptyGraph, segment) {
    if (!productState.isEmptyGraph || segment != GraphSegment.TOTAL)
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
    scrollEnabled = segment != GraphSegment.TOTAL && !productState.isSingleWindow,
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
      animation = SnapBehaviorConfig.SnapAnimation(
        snapDurationMillis = 500,
      ),
    )
  }
  val flingBehavior = rememberChartSnapFlingBehavior(
    scrollState = scrollState,
    config = snapConfig,
  )

  val horizontalItemPlacer = rememberHorizontalAxisItemPlacer(segment = segment)

  fun onScrollUpdate(min: Long, max: Long) {
    scope.launch {
      viewModel.handleIntent(
        GraphIntent.SetProductScrollRange(productType, min, max) {
          val visibleLabels = scrollState.getVisibleAxisLabels(horizontalItemPlacer).filter {
            it.toLong() in min..max
          }
          if (visibleLabels.isNotEmpty()) {
            val fallbackValues = scrollState.getInterpolatedYValues(
              xValues = visibleLabels,
              interpolationType = InterpolationType.MONOTONE,
            )
            val fallbackData = graphState.createFallBackData(
              productType = productType,
              segment = segment,
              timeStamps = visibleLabels.map { it.toLong() },
              fallbackValues = fallbackValues.map { list -> list.mapNotNull { it?.toDouble() } },
            )
            viewModel.handleIntent(GraphIntent.UpdateProductTarget(productType, fallbackData))
          }
        },
      )
    }
  }

  LaunchedEffect(segment) {
    if (scrollTarget == null || !canScrollToAnchor || productState.isEmptyGraph) return@LaunchedEffect
    val updatedScrollTarget = GraphUtil.getRelativeStart(segment, scrollTarget.toLong())
    val anchoredTarget = GraphUtil.getStartOnAnchored(segment, updatedScrollTarget)
    delay(SCROLL_DELAY_AFTER_LAYOUT_MS)
    scrollState.animateScroll(
      Scroll.Absolute.xWithPadding(
        anchoredTarget.toDouble(),
        GraphSnapHelper.getVisiblePaddingXStepForSegment(segment).first,
      ),
      animationSpec = tween(
        durationMillis = 150,
        easing = LinearOutSlowInEasing,
      ),
    )
    onScrollTargetConsumed(true)
  }

  val defaultMarker = rememberDefaultMarker(
    state = graphState,
    productState = productState,
    segment = segment,
    onTargetsUpdate = {
      if (it.isNotEmpty())
        viewModel.handleIntent(GraphIntent.UpdateProductTarget(productType, it))
    },
  )

  val scrubController = rememberScrubMarkerController(
    scrollState = scrollState,
    onMarkerIndexChanged = { clickX, targets ->
      if (clickX == null || productState.isEmptyGraph) {
        viewModel.handleIntent(GraphIntent.UpdateProductMarkerIndex(productType, null))
        return@rememberScrubMarkerController null
      }
      val visibleLabels =
        scrollState.getVisibleAxisLabels(itemPlacer = horizontalItemPlacer).filter {
          if (productState.minTarget != null && productState.maxTarget != null)
            it.toLong() in productState.minTarget..productState.maxTarget
          else
            true
        }
      var markerIndex: Double? = null
      val paddedMinCondition = productState.getStartTimestamp() - GraphUtil.calculateXStep(segment = segment)
      val paddedMaxCondition = productState.getEndTimestamp() + GraphUtil.calculateXStep(segment = segment)
      val outOfBoundaryCondition = clickX !in paddedMinCondition..paddedMaxCondition
      if (!outOfBoundaryCondition) {
        val targetMarkerIndex =
          getTargetPoints(
            visibleLabels,
            targets,
            clickX,
            segment,
            paddedMinCondition,
            paddedMaxCondition,
          )
        if (targetMarkerIndex.isNotEmpty()) {
          val targetIndex = targetMarkerIndex.first().toLong()
          markerIndex = when {
            targetIndex in productState.getStartTimestamp()..productState.getEndTimestamp() -> targetMarkerIndex.first()
            targetIndex < productState.getStartTimestamp() -> productState.getStartTimestamp().toDouble()
            targetIndex > productState.getEndTimestamp() -> productState.getEndTimestamp().toDouble()
            else -> null
          }
        }
      }
      if (productState.markerIndex != markerIndex)
        viewModel.handleIntent(GraphIntent.UpdateProductMarkerIndex(productType, markerIndex))
      markerIndex
    },
  )

  LaunchedEffect(productState.markerIndex == null) {
    if (productState.markerIndex == null && productState.minTarget != null && productState.maxTarget != null) {
      delay(50)
      if (!scrollState.isScrolling) {
        onScrollUpdate(productState.minTarget, productState.maxTarget)
      }
    }
  }

  val chart = rememberProductChart(
    config = chartConfig,
    graphState = graphState,
    productState = productState,
    defaultMarker = defaultMarker,
    segment = segment,
    horizontalItemPlacer = horizontalItemPlacer,
    fadingEdges = fadingEdges,
    handleIntent = viewModel::handleIntent,
    scrubController = scrubController,
  )

  LaunchedEffect(scrollState, segment) {
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
        AppLog.d(
          "GraphView",
          "start : " + DateTimeConverter.timestampToIso(min) + " end : " + DateTimeConverter.timestampToIso(max),
        )
        onScrollUpdate(clipRange.startMillis, clipRange.endMillis)
        if (!productState.isEmptyGraph)
          viewModel.handleIntent(
            GraphIntent.UpdateProductIsEmptyGraph(productType, relativeMin > productState.getEndTimestamp()),
          )
        onChartConsuming(false)
      }
  }

  CartesianChartHost(
    chart = chart,
    modelProducer = productState.modelProducer,
    modifier = modifier.height(chartHeight),
    scrollState = scrollState,
    animateIn = false,
    zoomState = rememberVicoZoomState(zoomEnabled = false),
    flingBehavior = flingBehavior,
  )
}

/**
 * Gets target points based on visible labels, available points, and current window bounds.
 */
fun getTargetPoints(
  fullList: List<Double>,
  points: List<Double>,
  input: Double,
  segment: GraphSegment,
  minWindow: Double? = null,
  maxWindow: Double? = null,
): List<Double> {
  if (segment == GraphSegment.TOTAL) {
    val nearestTarget = points.minByOrNull { kotlin.math.abs(it - input) }
    return listOfNotNull(nearestTarget)
  }
  if (fullList.isEmpty()) return emptyList()
  val lower = fullList.filter { it <= input }.maxOrNull()
  val upper = fullList.filter { it >= input }.minOrNull()

  if (lower == null) {
    val pointsInRange = if (minWindow != null && upper != null) {
      points.filter { it in minWindow..upper }
    } else {
      points.filter { it <= input }
    }
    return if (pointsInRange.isNotEmpty()) {
      val nearestTarget = pointsInRange.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    } else emptyList()
  }

  if (upper == null) {
    val pointsInRange = if (maxWindow != null) {
      points.filter { it in lower..maxWindow }
    } else {
      points.filter { it >= input }
    }
    return if (pointsInRange.isNotEmpty()) {
      val nearestTarget = pointsInRange.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    } else emptyList()
  }

  val searchRange = if (minWindow != null && maxWindow != null) {
    kotlin.math.max(minWindow, lower)..kotlin.math.min(maxWindow, upper)
  } else {
    lower..upper
  }
  val filteredTargets = points.filter { it in searchRange }

  return when {
    filteredTargets.isEmpty() -> {
      val halfway = (lower + upper) / 2.0
      if (input < halfway) listOf(lower) else listOf(upper)
    }
    filteredTargets.size == 1 -> {
      val target = filteredTargets.first()
      val halfway = (upper - lower) / 2.0
      if (kotlin.math.abs(target - input) < halfway) listOf(target)
      else if (target > input) listOf(lower) else listOf(upper)
    }
    else -> {
      val nearestTarget = filteredTargets.minByOrNull { kotlin.math.abs(it - input) }
      listOfNotNull(nearestTarget)
    }
  }
}
