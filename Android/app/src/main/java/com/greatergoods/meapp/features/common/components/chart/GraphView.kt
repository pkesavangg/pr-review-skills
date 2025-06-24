package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enum.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import com.greatergoods.meapp.theme.MeTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.fixed
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Point
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.abs
import kotlin.math.roundToInt
import android.util.Log

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
@Composable
fun GraphView(
    modifier: Modifier = Modifier,
    graphLines: List<GraphLine>,
    segment: GraphSegment = GraphSegment.WEEK,
    placeHolder: String? = null,
    selectedData: List<GraphPoint> = listOf(),
    onScroll: (String?) -> Unit = {},
    onSelected: (List<GraphPoint>) -> Unit,
) {
    val stableGraphLines = rememberStable(graphLines)

    if (stableGraphLines.isEmpty() || stableGraphLines.all { it.points.isEmpty() }) {
        GraphEmptyState(modifier = modifier, placeHolder = placeHolder)
        return
    }

    var point: Point? by remember { mutableStateOf(Point(0f, 0f)) }

    val xLabels = remember(stableGraphLines) {
        stableGraphLines.first().points.map { it.x }
    }

    val ySeries = remember(stableGraphLines) {
        stableGraphLines.map { it.points.map { point -> point.y } }
    }

    val max = remember(ySeries) {
        ySeries.flatten().maxOfOrNull { it.value.toDouble() }
    }

    val scrollState = rememberVicoScrollState(
        scrollEnabled = segment != GraphSegment.TOTAL,
        initialScroll = Scroll.Absolute.End,
    )

    var markerIndex by remember(xLabels) { mutableIntStateOf(xLabels.lastIndex) }
    var isUpdating by remember { mutableStateOf(false) }

    val modelProducer = remember { CartesianChartModelProducer() }

    val valueFormatter = rememberValueFormatter(xLabels, markerIndex)

    val graphKey = remember(stableGraphLines) { stableGraphLines.hashCode() }

    LaunchedEffect(graphKey, segment) {
        isUpdating = true
        markerIndex = xLabels.lastIndex
        modelProducer.runTransaction {
            lineSeries {
                ySeries.forEach { y ->
                    series(
                        x = xLabels.map { it.value as Long },
                        y = y.map { it.value },
                    )
                }
            }
        }
        onSelected(listOf())
        withFrameNanos {}
        scrollState.scroll(Scroll.Absolute.End)
        isUpdating = false
    }

    val primaryLayer = rememberPrimaryLayer(segment)
    val defaultMarker = rememberDefaultMarker(valueFormatter)
    val decorations = rememberHorizontalLine()

    val segmentedPlacer = HorizontalAxis.ItemPlacer.aligned()

    val horizontalItemPlacer = rememberHorizontalItemPlacer(
        segmentedPlacer = segmentedPlacer,
        isEnabled = true,
        segment = segment,
        onScroll = onScroll,
    )

    val markerListener = rememberMarkerListener(
        stableGraphLines = stableGraphLines,
        point = point,
        xLabels = xLabels,
        onSelected = onSelected,
        segment = segment,
        setMarkerIndex = { markerIndex = it },
        setIsUpdating = { isUpdating = it },
    )

    ChartHostSection(
        segment = segment,
        max = max,
        modifier = modifier,
        primaryLayer = primaryLayer,
        markerListener = markerListener,
        defaultMarker = defaultMarker,
        decorations = decorations,
        xLabels = xLabels,
        markerIndex = markerIndex,
        isUpdating = isUpdating,
        selectedData = selectedData,
        modelProducer = modelProducer,
        scrollState = scrollState,
        onSelected = onSelected,
        horizontalItemPlacer = horizontalItemPlacer,
        onPointUpdate = { point = it },

        )
}

/**
 * Internal composable for displaying the empty state of the graph.
 */
@Composable
internal fun GraphEmptyState(modifier: Modifier, placeHolder: String?) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = placeHolder ?: "You haven't added \n any entries.",
            modifier = Modifier.padding(16.dp),
            color = MeTheme.colorScheme.textBody,
            style = MeTheme.typography.heading5,
            textAlign = TextAlign.Center,
            minLines = 2,
        )
    }
}

/**
 * Internal helper to remember the value formatter for the marker.
 */
@Composable
internal fun rememberValueFormatter(
    xLabels: List<Label>,
    markerIndex: Int
): DefaultCartesianMarker.ValueFormatter =
    remember(xLabels, markerIndex) {
        object : DefaultCartesianMarker.ValueFormatter {
            override fun format(
                context: CartesianDrawingContext,
                targets: List<CartesianMarker.Target>,
            ) = xLabels[markerIndex].label
        }
    }

/**
 * Internal helper to remember the primary layer for the graph.
 */
@Composable
internal fun rememberPrimaryLayer(segment: GraphSegment): LineCartesianLayer {
    return rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            listOf(MeTheme.colorScheme.primaryAction).map {
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(it)),
                    stroke = LineCartesianLayer.LineStroke.continuous(thickness = 3.dp),
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
        pointSpacing = rememberPointSpacing(segment, 10.dp),
    )
}

/**
 * Internal helper to remember the marker listener for the graph.
 */
@Composable
internal fun rememberMarkerListener(
    stableGraphLines: List<GraphLine>,
    point: Point?,
    xLabels: List<Label>,
    onSelected: (List<GraphPoint>) -> Unit,
    segment: GraphSegment,
    setMarkerIndex: (Int) -> Unit,
    setIsUpdating: (Boolean) -> Unit
): CartesianMarkerVisibilityListener = remember(point, segment) {
    object : CartesianMarkerVisibilityListener {
        override fun onShown(
            marker: CartesianMarker,
            targets: List<CartesianMarker.Target>,
        ) {
            setIsUpdating(true)
            if (segment != GraphSegment.TOTAL) {
                val targetCanvasX = targets.first().canvasX
                val targetCanvasY = (targets.first() as LineCartesianLayerMarkerTarget).points.first().canvasY
                val dx = abs(targetCanvasX - (point?.x ?: 0.0f))
                val dy = abs(targetCanvasY - (point?.y ?: 0.0f))
                Log.d("CHECKING", "point: $point")
                Log.d("CHECKING", "targetCanvasX: $targetCanvasX, targetCanvasY: $targetCanvasY")
                val isInRange = dx <= 50.0f || dy <= 50.0f
                if (!isInRange) {
                    onSelected(listOf())
                    return
                }
            }

            val idx = xLabels.indexOfFirst { it.value == targets.first().x.toLong() }
            setMarkerIndex(idx)
            val selectedPoints = stableGraphLines.map { it.points[idx] }
            onSelected(selectedPoints)
        }

        override fun onHidden(marker: CartesianMarker) {
            setIsUpdating(false)
        }

        override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            onShown(marker, targets)
        }
    }
}

/**
 * Calculates the point spacing for the graph based on the segment and axis padding.
 * UI-only logic, not business/data transformation.
 */
@Composable
internal fun rememberPointSpacing(
    segment: GraphSegment,
    axisPadding: androidx.compose.ui.unit.Dp = 0.dp
): androidx.compose.ui.unit.Dp {
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val density = androidx.compose.ui.platform.LocalDensity.current
    val intervalCount = remember(segment) {
        when (segment) {
            GraphSegment.WEEK -> 7
            GraphSegment.MONTH -> 6
            GraphSegment.YEAR -> 12
            else -> 32
        }
    }
    return remember(segment, screenWidthPx, intervalCount, axisPadding) {
        with(density) { (screenWidthPx / intervalCount).toDp() - axisPadding }
    }
}

/**
 * Internal helper to remember the horizontal item placer for the X axis.
 */
@Composable
internal fun rememberHorizontalItemPlacer(
    segmentedPlacer: HorizontalAxis.ItemPlacer,
    isEnabled: Boolean,
    segment: GraphSegment,
    onScroll: (String?) -> Unit,
): HorizontalAxis.ItemPlacer {
    return remember(segment) {
        object : HorizontalAxis.ItemPlacer by segmentedPlacer {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                visibleXRange: ClosedFloatingPointRange<Double>,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float,
            ): List<Double> {

                // Update pointer position and call onScroll with formatted range
                val formattedRange = if (segment != GraphSegment.TOTAL) {
                    val min = visibleXRange.start.toLong()
                    val max = visibleXRange.endInclusive.toLong()
                    GraphUtil.formatDateRange(min, max, segment)
                } else {
                    GraphUtil.formatDateRange(
                        context.model.models.first().minX.toLong(),
                        context.model.models.first().maxX.toLong(),
                        segment,
                    )
                }
                if (isEnabled)
                    onScroll(formattedRange)
                return segmentedPlacer.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)
            }
        }
    }
}

/**
 * Internal composable for the chart host and axis setup.
 */
@Composable
internal fun ChartHostSection(
    segment: GraphSegment,
    max: Double?,
    primaryLayer: LineCartesianLayer,
    markerListener: CartesianMarkerVisibilityListener,
    defaultMarker: CartesianMarker,
    xLabels: List<Label>,
    markerIndex: Int,
    isUpdating: Boolean,
    selectedData: List<GraphPoint>,
    modelProducer: CartesianChartModelProducer,
    scrollState: VicoScrollState,
    onSelected: (List<GraphPoint>) -> Unit,
    horizontalItemPlacer: HorizontalAxis.ItemPlacer,
    decorations: Decoration,
    modifier: Modifier,
    onPointUpdate: (Point) -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    windowInfo.containerSize.width
    key(segment) {
        val bottomAxis = rememberBottomAxis(segment, horizontalItemPlacer)
        val chart = rememberCartesianChart(
            primaryLayer,
            endAxis =
                VerticalAxis.rememberEnd(
                    valueFormatter =
                        CartesianValueFormatter { _, value, _ ->
                            value.roundToInt().toString()
                        },
                    itemPlacer =
                        VerticalAxis.ItemPlacer.step(
                            step = { max?.let { it / 5 } },
                        ),
                    size = BaseAxis.Size.fixed(40.dp),
                    line = rememberAxisLineComponent(),
                    guideline = rememberAxisLineComponent(),
                    label = rememberTextComponent(
                        color = MeTheme.colorScheme.textSubheading,
                        margins = insets(horizontal = 10.dp),
                    ),
                    verticalLabelPosition = Position.Vertical.Center,
                    tickLength = 0.dp,
                ),
            bottomAxis = bottomAxis,
            marker = if (segment != GraphSegment.TOTAL) rememberCartesianMarker() else defaultMarker,
            decorations = listOf(decorations),
            markerVisibilityListener = markerListener,
            persistentMarkers =
                if (!isUpdating && selectedData.isNotEmpty()) {
                    { defaultMarker at xLabels[markerIndex].value }
                } else {
                    null
                },
            getXStep = {
                GraphUtil.calculateXStep(segment, xLabels.map { it.value.toDouble() })
            },
        )
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier =
                modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position
                            if (position != null) {
                                onPointUpdate(Point(position.x, position.y))
                            }
                        }
                    }
                },
            animationSpec = null,
            scrollState = scrollState,
            consumeMoveEvents = true,
        )
    }
}

/**
 * Internal helper to remember the bottom axis for the chart.
 */
@Composable
internal fun rememberBottomAxis(
    segment: GraphSegment,
    horizontalItemPlacer: HorizontalAxis.ItemPlacer
): Axis<Axis.Position.Horizontal.Bottom> {
    return if (segment != GraphSegment.TOTAL)
        HorizontalAxis.rememberBottom(
            valueFormatter =
                CartesianValueFormatter { _, value, _ ->
                    if (value.toInt() != 0) GraphUtil.formatTimestampForSegment(value.toLong(), segment) else " "
                },
            itemPlacer = horizontalItemPlacer,
            guideline = rememberAxisGuidelineComponent(),
            label = rememberTextComponent(color = MeTheme.colorScheme.textSubheading),
            tickLength = 0.dp,
            line = rememberAxisLineComponent(),
        )
    else
        HorizontalAxis.rememberBottom(
            guideline = null,
            itemPlacer = horizontalItemPlacer,
            label = null,
            tickLength = 0.dp,
            line = rememberAxisLineComponent(),
        )
}

@Composable
internal fun rememberCartesianMarker(): CartesianMarker {
    val emptyFormatter = rememberEmptyFormatter()
    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MeTheme.colorScheme.textSubheading),
        valueFormatter = emptyFormatter,
        indicator = null,
    )
}

/**
 * Internal helper to remember the empty value formatter for the marker.
 */
@Composable
internal fun rememberEmptyFormatter(): DefaultCartesianMarker.ValueFormatter =
    remember {
        object : DefaultCartesianMarker.ValueFormatter {
            override fun format(
                context: CartesianDrawingContext,
                targets: List<CartesianMarker.Target>,
            ) = ""
        }
    }
