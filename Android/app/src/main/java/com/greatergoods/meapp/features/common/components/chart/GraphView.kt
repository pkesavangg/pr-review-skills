package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.theme.MeAppTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.fixed
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val LegendLabelKey = ExtraStore.Key<List<Double>>()

@Composable
fun GraphView(
    modifier: Modifier = Modifier,
    graphLines: List<GraphLine>,
    placeHolder: String? = null,
    selectedData: List<GraphPoint>? = null,
    labelContent: (@Composable () -> Unit)? = null,
    onSelected: (List<GraphPoint>) -> Unit
) {
    if (graphLines.isEmpty() || graphLines.all { it.points.isEmpty() }) {
        Box(
            modifier = modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = placeHolder ?: "No data",
                modifier = Modifier.padding(16.dp),
                color = MeAppTheme.colorScheme.body,
                style = MeAppTheme.typography.heading5,
            )
        }
        return
    }
    val xLabels = remember(graphLines) {
        graphLines.first().points.map { it.x }

    }
    val ySeries =
        remember(graphLines) {
            graphLines.map { graphLine ->
                graphLine.points.map { graphPoint ->
                    graphPoint.y
                }
            }
        }

    val minY = remember(ySeries) {
        ySeries.flatten().minOfOrNull { it.value.toDouble() }
    }
    val max = remember(ySeries) {
        ySeries.flatten().maxOfOrNull { it.value.toDouble() }
    }

    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        autoScrollCondition = AutoScrollCondition.OnModelGrowth,
    )
    var markerIndex by remember(xLabels) { mutableIntStateOf(xLabels.lastIndex) }
    var isUpdating by remember { mutableStateOf(false) }

    val modelProducer = remember { CartesianChartModelProducer() }
    val valueFormatter =
        object : DefaultCartesianMarker.ValueFormatter {
            override fun format(
                context: CartesianDrawingContext,
                targets: List<CartesianMarker.Target>
            ) =
                xLabels[1].label
        }

    val markerListener = remember(graphLines) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                isUpdating = true
                markerIndex = targets.first().x.toInt()
                val selectedPoints = graphLines.mapNotNull {
                    val point = it.points.getOrNull(targets.first().x.toInt())
                    point
                }
                onSelected(
                    selectedPoints,
                )
            }

            override fun onHidden(marker: CartesianMarker) {
                isUpdating = false
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                onShown(marker, targets)
            }
        }
    }
    // Update chart on graphPoints change
    LaunchedEffect(graphLines) {
        isUpdating = true
        markerIndex = xLabels.lastIndex
        isUpdating = false

        withContext(Dispatchers.IO) {
            modelProducer.runTransaction {
                lineSeries {
                    ySeries.forEach { y ->
                        series(
                            x = xLabels.map { label ->
                                label.value
                            },
                            y.map { label ->
                                label.value
                            },
                        )
                    }
                }
                extras { extraStore ->
                    extraStore[LegendLabelKey] = ySeries.flatMap { y ->
                        y.map { label ->
                            label.value.toDouble()
                        }
                    }
                }
            }
        }
    }

    val primaryLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            listOf(Color(0xFF1565C0)).map {
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(it)),
                    stroke = LineCartesianLayer.LineStroke.continuous(
                        thickness = 3.dp,
                    ),
                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.5f),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        point = LineCartesianLayer.Point(
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
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minY = minY ?: 0.0,
            maxY = max ?: 0.0,
        ),
    )

    val layeredMarker = rememberMarker(
        valueFormatter = valueFormatter,
        decorations = listOf(rememberHorizontalLine(LegendLabelKey, markerIndex)),
    )

    val chart = rememberCartesianChart(
        primaryLayer,
        endAxis = VerticalAxis.rememberEnd(
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                if (value.toInt() != 0) value.roundToInt().toString() else " "
            },
            itemPlacer = VerticalAxis.ItemPlacer.step(
                step = {
                    max?.let {
                        it / 5
                    }
                },
            ),
            size = BaseAxis.Size.fixed(20.dp),
            line = null,
            guideline = rememberAxisGuidelineComponent(),
            label = rememberTextComponent(color = Color(0xFF7B726E)),
            verticalLabelPosition = Position.Vertical.Top,
            tickLength = 0.dp,
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            guideline = null,
            tickLength = 0.dp,
            label = rememberTextComponent(color = Color(0xFF7B726E)),
            line = rememberAxisGuidelineComponent(),
        ),
        marker = layeredMarker,
        markerVisibilityListener = markerListener,
        persistentMarkers = if (!isUpdating) {
            { layeredMarker at markerIndex }
        } else null,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        labelContent?.invoke()
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxSize(),
            animationSpec = tween(1000),
            scrollState = scrollState,
        )
    }
}
