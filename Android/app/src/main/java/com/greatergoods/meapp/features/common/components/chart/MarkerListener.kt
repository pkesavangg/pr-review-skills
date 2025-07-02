package com.greatergoods.meapp.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.greatergoods.meapp.features.common.model.chart.GraphLine
import com.greatergoods.meapp.features.common.model.chart.GraphPoint
import com.greatergoods.meapp.features.common.model.chart.Label
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Point
import kotlin.math.abs

/**
 * Internal helper to remember the marker listener for the graph.
 */
@Composable
internal fun markerListener(
    stableGraphLines: List<GraphLine>,
    point: Point?,
    xLabels: List<Label>,
    onSelected: (List<GraphPoint>) -> Unit,
    selectedData: List<GraphPoint>,
    setMarkerIndex: (Int?) -> Unit,
    onDestinationUpdate: (Long?) -> Unit,
): CartesianMarkerVisibilityListener = remember(point) {
    object : CartesianMarkerVisibilityListener {
        override fun onShown(
            marker: CartesianMarker,
            targets: List<CartesianMarker.Target>,
        ) {
            val targetCanvasX = targets.first().canvasX
            val targetCanvasY = (targets.first() as LineCartesianLayerMarkerTarget).points.first().canvasY
            val dx = abs(targetCanvasX - (point?.x ?: 0.0f))
            val dy = abs(targetCanvasY - (point?.y ?: 0.0f))
            val isInRange = dx <= 50.0f || dy <= 50.0f
            if (!isInRange) {
                onSelected(listOf())
                onDestinationUpdate(null)
                setMarkerIndex(null)
                return
            }

            val idx = xLabels.indexOfFirst { it.value == targets.first().x.toLong() }
            setMarkerIndex(idx)
            val selectedPoints = stableGraphLines.map { it.points[idx] }
            if (selectedPoints != selectedData) {
                onSelected(selectedPoints)
                onDestinationUpdate(targets.first().x.toLong())
            }
        }

        override fun onHidden(marker: CartesianMarker) {
        }

        override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            onShown(marker, targets)
        }
    }
}
