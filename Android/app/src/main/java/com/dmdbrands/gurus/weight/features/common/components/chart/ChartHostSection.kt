package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.intervalCount
import com.dmdbrands.gurus.weight.features.common.model.chart.GraphPoint
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.fixed
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberTop
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import kotlin.math.roundToInt

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
  isUpdating: Boolean,
  selectedData: List<GraphPoint>,
  modelProducer: CartesianChartModelProducer,
  scrollState: VicoScrollState,
  horizontalItemPlacer: HorizontalAxis.ItemPlacer,
  decorations: Decoration,
  separators: List<Double>
) {
  key(segment) {
    val bottomAxis = bottomAxis(segment, separators, horizontalItemPlacer)

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
            size = BaseAxis.Size.fixed(40.dp),
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
                margins = insets(horizontal = 10.dp),
              ),
            tickLength = 0.dp,
          ),
        bottomAxis = bottomAxis,
        marker = emptyMarker(),
        decorations = listOf(decorations),
        markerVisibilityListener = markerListener,
        persistentMarkers =

          if (!isUpdating && selectedData.isNotEmpty() && markerIndex != null) {
            {
              defaultMarker at xLabels[markerIndex].value
            }
          } else {
            null
          },
        visibleLabelsCount = segment.intervalCount(),
        getXStep = {
          GraphUtil.calculateXStep(
            segment,
            xLabels.map { it.value.toDouble() },
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
