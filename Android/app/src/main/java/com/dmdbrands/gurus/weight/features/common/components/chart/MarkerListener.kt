package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.features.common.model.chart.Label
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
  point: Point?,
  xLabels: List<Label>,
  setMarkerIndex: (Int?) -> Unit,
): CartesianMarkerVisibilityListener? = remember(point) {
  if (point == null) {
    return@remember null
  }
  object : CartesianMarkerVisibilityListener {
    override fun onShown(
      marker: CartesianMarker,
      targets: List<CartesianMarker.Target>,
    ) {
      val targetCanvasX = targets.first().canvasX
      val targetCanvasY = (targets.first() as LineCartesianLayerMarkerTarget).points.first().canvasY
      val dx = abs(targetCanvasX - (point.x))
      val dy = abs(targetCanvasY - (point.y))
      val isInRange = dx <= 50.0f || dy <= 50.0f
      if (!isInRange) {
        setMarkerIndex(null)
        return
      }

      val idx = xLabels.indexOfFirst { it.value == targets.first().x.toLong() }
      setMarkerIndex(idx)
    }

    override fun onHidden(marker: CartesianMarker) {
    }

    override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
      onShown(marker, targets)
    }
  }
}
