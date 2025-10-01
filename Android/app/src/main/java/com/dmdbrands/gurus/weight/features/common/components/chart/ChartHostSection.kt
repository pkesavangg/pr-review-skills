package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmdbrands.gurus.weight.R
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphIntent
import com.dmdbrands.gurus.weight.features.common.components.chart.viewmodel.GraphState
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.intervalCount
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberTop
import com.patrykandpatrick.vico.compose.cartesian.axis.scroll
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import java.util.Calendar
import kotlin.math.roundToInt
import android.graphics.Typeface

@Composable
internal fun ChartHostSection(
  modifier: Modifier = Modifier,
  primaryLayer: LineCartesianLayer,
  secondaryLayer: LineCartesianLayer,
  segment: GraphSegment,
  defaultMarker: CartesianMarker,
  xLabels: List<Label>,
  markerIndex: Double?,
  state: GraphState,
  modelProducer: CartesianChartModelProducer,
  scrollState: VicoScrollState,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  handleIntent: (GraphIntent) -> Unit,
  goalMarker: VerticalAxis.MarkerDecoration? = null,
) {
  val timeStamps = xLabels.map { it.value.toLong() }.sorted()
  val separators = GraphUtil.periodStarts(
    segment = segment,
    startMillis = if (timeStamps.isNotEmpty()) timeStamps.first() else null,
    endMillis = if (timeStamps.isNotEmpty()) timeStamps.last() else null,
  ).map { it.toDouble() }
  val bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer)
  buildList {
    add(primaryLayer)
    if (state.secondaryGraphLines != null) add(secondaryLayer)
  }

  val doubleVectorConverter = TwoWayConverter<Double, AnimationVector1D>(
    convertToVector = { AnimationVector1D(it.toFloat()) },
    convertFromVector = { it.value.toDouble() },
  )

  val animatedMarkerIndex = if (segment == GraphSegment.TOTAL && markerIndex != null && markerIndex != 0.0) {
    animateValueAsState<Double, AnimationVector1D>(
      targetValue = markerIndex,
      typeConverter = doubleVectorConverter,
      animationSpec = androidx.compose.animation.core.SpringSpec(),
      visibilityThreshold = null,
      label = "markerIndexAnimation",
      finishedListener = null,
    ).value
  } else {
    markerIndex ?: 0.0
  }
  val resources = LocalResources.current
  val openSans: Typeface = resources.getFont(R.font.open_sans_semi_bold)

  val primaryChart =
    rememberCartesianChart(
      primaryLayer,
      secondaryLayer,
      startAxis =
        VerticalAxis.rememberStart(
          label = null,
          size = BaseAxis.Size.scroll(8.dp, isLabelsScrollable = true),
          line = rememberAxisLineComponent(
            fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
            thickness = 1.dp,
          ),
          guideline = null,
          tickLength = 0.dp,
        ),
      topAxis =
        HorizontalAxis.rememberTop(
          label = null,
          line = rememberAxisLineComponent(
            fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
            thickness = 1.dp,
          ),
          guideline = null,
          tickLength = 0.dp,
        ),
      endAxis =
        VerticalAxis.rememberEnd(
          valueFormatter =
            CartesianValueFormatter { _, value, _ ->
              if (state.isEmptyGraph) " " else
                value.roundToInt().toString()
            },
          itemPlacer = remember(state.primaryYStep) {
            VerticalAxis.ItemPlacer.step(
              { state.primaryYStep },
            )
          },
          size = BaseAxis.Size.scroll(50.dp),
          line =
            rememberAxisLineComponent(
              fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
              thickness = 1.dp,
            ),
          markerDecoration = goalMarker,
          guideline = if (state.isEmptyGraph) null else
            rememberAxisLineComponent(
              fill = fill(MeTheme.colorScheme.utility.copy(0.5f)),
              thickness = 0.5.dp,
            ),
          label =
            rememberTextComponent(
              typeface = openSans,
              color = MeTheme.colorScheme.textSubheading,
              textSize = 14.sp,
            ),
          tickLength = 0.dp,
        ),
      bottomAxis = bottomAxis,
      marker = emptyMarker(),
      persistentMarkers =

        if (animatedMarkerIndex != 0.0) {
          {
            defaultMarker at animatedMarkerIndex
          }
        } else {
          null
        },
      visibleLabelsCount = if (segment == GraphSegment.TOTAL) GraphUtil.getTotalMonthsBetweenYears(
        xLabels.minOfOrNull { it.value.toLong() } ?: Calendar.getInstance().timeInMillis,
        Calendar.getInstance().timeInMillis,
      ).toDouble() else segment.intervalCount(),
      getXStep = {
        GraphUtil.calculateXStep(
          segment,
        )
      },

      onChartClick = { targets, click ->
        var markerIndex: Double? = null
        val max = xLabels.maxOfOrNull { it.value.toDouble() }
        val min = xLabels.minOfOrNull { it.value.toDouble() }

        val outOfBoundaryCondition = if (click != null && max != null && min != null) {
          click < min || click > max
        } else {
          false // or handle differently if null means "out of bounds"
        }

        if (click == null || outOfBoundaryCondition) {
          markerIndex = null
        } else {
          val targetMarkerIndex =
            getTargetPoints(
              scrollState.getVisibleAxisLabels(itemPlacer = horizontalItemPlacer),
              targets,
              click,
              segment,
            )
          if (targetMarkerIndex.isNotEmpty()) {
            markerIndex = targetMarkerIndex.first()
          }
        }
        handleIntent(GraphIntent.UpdateMarkerIndex(markerIndex))
      },
    )
  CartesianChartHost(
    chart = primaryChart,
    modelProducer = modelProducer,
    modifier = modifier,
    scrollState = scrollState,
    zoomState = rememberVicoZoomState(zoomEnabled = false),
    consumeMoveEvents = true,
    onScrollStopped = { range ->
      if (range != null) {
        val min = range.visibleXRange.start
        val max = range.visibleXRange.endInclusive
        handleIntent(GraphIntent.SetScrollRange(min.toLong(), max.toLong()))
      }
    },
  )
}

fun getTargetPoints(fullList: List<Double>, points: List<Double>, input: Double, segment: GraphSegment): List<Double> {
  if (points.isEmpty()) return emptyList()

  // For TOTAL segment, find nearest targets from click without considering visible labels
  if (segment == GraphSegment.TOTAL) {
    val nearestTarget = points.minByOrNull { kotlin.math.abs(it - input) }
    return listOfNotNull(nearestTarget)
  }

  // For other segments, use the original logic with visible labels
  if (fullList.isEmpty()) return emptyList()

  // find lower and upper bound from full list
  val lower = fullList.filter { it <= input }.maxOrNull()
  val upper = fullList.filter { it >= input }.minOrNull()

  // edge case: if input is outside range
  if (lower == null && upper == null) return emptyList()
  if (lower == null) return listOfNotNull(upper)
  if (upper == null) return listOfNotNull(lower)

  // filter targets within the upper and lower bound
  val filteredTargets = points.filter { it in lower..upper }

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
