package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import com.greatergoods.meapp.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.fixed
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.roundToInt

@Composable
internal fun ChartHostSection(
    modifier: Modifier = Modifier,
    segment: GraphSegment,
    max: Double?,
    primaryLayer: LineCartesianLayer,
    markerListener: CartesianMarkerVisibilityListener,
    defaultMarker: CartesianMarker,
    xLabels: List<Label>,
    markerIndex: Int?,
    isUpdating: Boolean,
    selectedData: List<GraphPoint>,
    modelProducer: CartesianChartModelProducer,
    scrollState: VicoScrollState,
    horizontalItemPlacer: HorizontalAxis.ItemPlacer,
    decorations: Decoration,
) {
    key(segment) {
        val secondaryLayer =
            rememberLineCartesianLayer(
                lineProvider =
                    LineCartesianLayer.LineProvider.series(
                        listOf(MeTheme.colorScheme.secondaryAction).map {
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(fill(it)),
                                stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp),
                                pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
                                pointProvider =
                                    LineCartesianLayer.PointProvider.single(
                                        point =
                                            LineCartesianLayer.Point(
                                                rememberShapeComponent(
                                                    fill(it),
                                                    CorneredShape.Pill,
                                                    strokeThickness = 2.dp,
                                                ),
                                            ),
                                    ),
                            )
                        },
                    ),
                verticalAxisPosition = Axis.Position.Vertical.Start,
                pointSpacing = pointSpacing(segment, 10.dp),
            )
        val bottomAxis = bottomAxis(segment, horizontalItemPlacer)
        val primaryChart =
            rememberCartesianChart(
                primaryLayer,
                secondaryLayer,
                startAxis =
                    VerticalAxis.rememberStart(
                        label = null,
                        line = null,
                        guideline = null,
                        tickLength = 0.dp,
                    ),
                endAxis =
                    VerticalAxis.rememberEnd(
                        valueFormatter =
                            com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter { _, value, _ ->
                                value.roundToInt().toString()
                            },
                        itemPlacer =
                            VerticalAxis.ItemPlacer.step(
                                step = { max?.let { it / 5 } },
                            ),
                        size = BaseAxis.Size.fixed(40.dp),
                        line =
                            rememberAxisLineComponent(
                                fill = fill(MeTheme.colorScheme.iconSecondaryDisabled),
                                thickness = 1.dp,
                            ),
                        guideline =
                            rememberAxisLineComponent(
                                fill = fill(MeTheme.colorScheme.utility),
                                thickness = 1.dp,
                            ),
                        label =
                            rememberTextComponent(
                                color = MeTheme.colorScheme.textSubheading,
                                margins = insets(horizontal = 10.dp),
                            ),
                        verticalLabelPosition = Position.Vertical.Center,
                        tickLength = 0.dp,
                    ),
                bottomAxis = bottomAxis,
                marker = emptyMarker(),
                decorations = listOf(decorations),
                markerVisibilityListener = markerListener,
                persistentMarkers =
                    key(markerIndex) {
                        if (!isUpdating && selectedData.isNotEmpty() && markerIndex != null) {
                            {
                                defaultMarker at xLabels[markerIndex].value
                            }
                        } else {
                            null
                        }
                    },
                getXStep = {
                    com.greatergoods.meapp.features.common.helper.graph.GraphUtil.calculateXStep(
                        segment,
                        xLabels.map { it.value.toDouble() },
                    )
                },
            )
        CartesianChartHost(
            chart = primaryChart,
            modelProducer = modelProducer,
            modifier =
            modifier,
            animationSpec = null,
            scrollState = scrollState,
            consumeMoveEvents = true,
        )
    }
}
