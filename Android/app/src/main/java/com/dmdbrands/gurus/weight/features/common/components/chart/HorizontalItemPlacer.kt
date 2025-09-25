package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Internal helper to remember the horizontal item placer for the X axis.
 */
@Composable
internal fun horizontalItemPlacer(
  segment: GraphSegment,
): HorizontalAxis.ItemPlacer {
  val defaultPlacer = HorizontalAxis.ItemPlacer.aligned()
  return object : HorizontalAxis.ItemPlacer by defaultPlacer {
    override fun getLabelValues(
      context: CartesianDrawingContext,
      visibleXRange: ClosedFloatingPointRange<Double>,
      fullXRange: ClosedFloatingPointRange<Double>,
      maxLabelWidth: Float,
    ): List<Double> {
      return if (segment == GraphSegment.YEAR)
        fullXRange.monthStartTimestampsMillis()
      else defaultPlacer.getLabelValues(
        context, visibleXRange, fullXRange, maxLabelWidth,
      )
    }

    override fun getLineValues(
      context: CartesianDrawingContext,
      visibleXRange: ClosedFloatingPointRange<Double>,
      fullXRange: ClosedFloatingPointRange<Double>,
      maxLabelWidth: Float
    ): List<Double>? {
      return if (segment == GraphSegment.YEAR)
        fullXRange.monthStartTimestampsMillis()
      else defaultPlacer.getLabelValues(
        context, visibleXRange, fullXRange, maxLabelWidth,
      )
    }
  }
}

fun ClosedFloatingPointRange<Double>.monthStartTimestampsMillis(): List<Double> {
  // Convert timestamps (millis) to ZonedDateTime in local timezone
  val localZone = ZoneId.systemDefault()
  val startZdt = Instant.ofEpochMilli(start.toLong()).atZone(localZone)
  val endZdt = Instant.ofEpochMilli(endInclusive.toLong()).atZone(localZone)

  val startYear = startZdt.year
  val endYear = endZdt.year

  val timestamps = mutableListOf<Double>()

  for (year in startYear..endYear) {
    for (month in 1..12) {
      val zdt = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, localZone)
      val ts = zdt.toInstant().toEpochMilli().toDouble()

      // include only months between actual start and end timestamps
      if (ts in start..endInclusive) {
        timestamps.add(ts)
      }
    }
  }

  return timestamps
}
