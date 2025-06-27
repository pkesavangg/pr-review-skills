package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.enums.GraphSegment
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.averageYValuesInRange
import com.greatergoods.meapp.features.common.helper.graph.GraphUtil.filterXValuesInRange
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded
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
import kotlin.math.abs

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
    onMetricUpdate: (List<GraphPoint>) -> Unit = {},
    onScroll: (String?) -> Unit = {},
    onLabelUpdate: (String) -> Unit = {},
) {
    var selectedData: List<GraphPoint> by remember {
        mutableStateOf(listOf())
    }
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
    var minTarget by remember { mutableStateOf<Long?>(null) }
    var maxTarget by remember { mutableStateOf<Long?>(null) }
    var selectedTarget by remember { mutableStateOf<Long?>(null) }
    var markerIndex: Int? by remember(xLabels) { mutableStateOf(null) }
    var isUpdating by remember { mutableStateOf(false) }
    val modelProducer = remember { CartesianChartModelProducer() }
    val graphKey = remember(stableGraphLines) { stableGraphLines.hashCode() }
    // Remember the job outside LaunchedEffect
    var computationJob by remember { mutableStateOf<Job?>(null) }
    val minTargetState = rememberUpdatedState(minTarget)
    val maxTargetState = rememberUpdatedState(maxTarget)

    LaunchedEffect(graphKey, secondaryGraphLines) {
        modelProducer.runTransaction {
            lineSeries {
                ySeries.forEach { y ->
                    series(
                        x = xLabels.map { it.value as Long },
                        y = y.map { it.value },
                    )
                }
            }
            if (secondaryGraphLines != null && secondaryGraphLines.points.isNotEmpty()) {
                lineSeries {
                    series(
                        x = secondaryGraphLines.points.map { it.x.value as Long },
                        y = secondaryGraphLines.points.map { it.y.value },
                    )
                }
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
            scrollState.scroll(Scroll.Absolute.x(target.toDouble(), 0.5f)) // ✅ suspend function
        } else {
            scrollState.scroll(Scroll.Absolute.End)
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
                        .joinToString(" / ") { it.toDouble().rounded().toString() }
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
                selectedData.first().y.value.toDouble()
                    .rounded().toString(),
            )
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { minTargetState.value to maxTargetState.value }
            .debounce(500) // wait for 500ms of inactivity
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
                        .joinToString(" / ") { "%.1f".format(it) }
                    onLabelUpdate(joinedLabel)
                    val graphLines = filterXValuesInRange(
                        stableGraphLines,
                        minTarget ?: 0L,
                        maxTarget ?: 0L,
                    )
                    onMetricUpdate(graphLines.flatMap { it.points })

                    // Clear the job reference when done
                    computationJob = null
                }
            }
    }

    val primaryLayer = primaryLayer(segment)
    val defaultMarker = rememberDefaultMarker(xLabels, markerIndex, segment)
    val decorations = rememberHorizontalLine()

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

    ChartHostSection(
        modifier = modifier
            .height(300.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position
                        if (position != null) {
                            point = (Point(position.x, position.y))
                        }
                    }
                }
            },
        segment = segment,
        max = max,
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
        horizontalItemPlacer = horizontalItemPlacer,
    )
}


