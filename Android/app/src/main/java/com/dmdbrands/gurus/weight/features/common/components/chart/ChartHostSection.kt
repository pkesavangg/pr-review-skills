package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.roundToInt
import android.text.Layout

@Composable
internal fun ChartHostSection(
  modifier: Modifier = Modifier,
  segment: GraphSegment,
  primaryLayer: LineCartesianLayer,
  secondaryLayer: LineCartesianLayer,
  markerListener: CartesianMarkerVisibilityListener?,
  stepSize: Double,
  defaultMarker: CartesianMarker,
  xLabels: List<Label>,
  markerIndex: Int?,
  state: GraphState,
  modelProducer: CartesianChartModelProducer,
  scrollState: VicoScrollState,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  isEmpty: Boolean = false,
  decorations: Decoration? = null,
  separators: List<Double>
) {

  key(segment, isEmpty) {
    val visibleCount = if (segment == GraphSegment.TOTAL) GraphUtil.calculateTotalIntervalCount(
      startTime = state.startRangeX,
      endTime = state.endRangeX,
      segment = segment,
    ) else segment.intervalCount()
    val bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer)
    val fill = fill(Color(0xFF458239))

    val primaryChart =
      rememberCartesianChart(
        primaryLayer,
        secondaryLayer,
        startAxis =
          VerticalAxis.rememberStart(
            label = null,
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
                value.roundToInt().toString()
              },
            itemPlacer =
              VerticalAxis.ItemPlacer.step(
                step = { stepSize },
              ),
            tickLength = 0.dp,
            line =
              rememberAxisLineComponent(
                fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
                thickness = 1.dp,
              ),
            guideline =
              rememberAxisLineComponent(
                fill = fill(MeTheme.colorScheme.utility.copy(0.5f)),
                thickness = 0.5.dp,
              ),
            label =
              rememberTextComponent(
                color = MeTheme.colorScheme.textSubheading,
                minWidth = TextComponent.MinWidth.fixed(40f),
                textSize = 14.sp,
                textAlignment = Layout.Alignment.ALIGN_CENTER,
                background =
                  shapeComponent(
                    fill,
                    shape = CorneredShape.Pill,
                  ),
              ),
          ),
        bottomAxis = bottomAxis,
        marker = emptyMarker(),
        decorations = listOfNotNull(decorations),
        markerVisibilityListener = markerListener,
        persistentMarkers =

          if (markerIndex != null && markerIndex < xLabels.size) {
            {
              defaultMarker at xLabels[markerIndex].value
            }
          } else {
            null
          },
        visibleLabelsCount = visibleCount,
        getXStep = {
          GraphUtil.calculateXStep(
            segment,
          )
        },
      )
    CartesianChartHost(
      chart = primaryChart,
      modelProducer = modelProducer,
      modifier = modifier,
      animationSpec = null,
      animateIn = false,
      scrollState = scrollState,
      zoomState = rememberVicoZoomState(zoomEnabled = false),
      consumeMoveEvents = true,
    )
  }
}
