package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis

/**
 * Internal helper to remember the horizontal item placer for the X axis.
 */
@Composable
internal fun horizontalItemPlacer(
    isEnabled: Boolean,
    segment: GraphSegment,
    onDestinationUpdate: (Long, Long) -> Unit,
): HorizontalAxis.ItemPlacer {
    val defaultPlacer = HorizontalAxis.ItemPlacer.aligned()
    return remember(segment, isEnabled) {

        object : HorizontalAxis.ItemPlacer by defaultPlacer {
            override fun getLabelValues(
                context: CartesianDrawingContext,
                visibleXRange: ClosedFloatingPointRange<Double>,
                fullXRange: ClosedFloatingPointRange<Double>,
                maxLabelWidth: Float,
            ): List<Double> {
                if (isEnabled) {
                    val (min, max) = if (segment != GraphSegment.TOTAL) {
                        visibleXRange.start.toLong() to visibleXRange.endInclusive.toLong()
                    } else {
                        with(context.model.models.first()) {
                            minX.toLong() to maxX.toLong()
                        }
                    }

                    // Only trigger onDestinationUpdate if not the first time for this segment
                    onDestinationUpdate(min, max)
                }

                return defaultPlacer.getLabelValues(
                    context, visibleXRange, fullXRange, maxLabelWidth,
                )
            }
        }
    }
}
