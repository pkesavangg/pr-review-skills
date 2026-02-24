package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Filters [values] to those inside [visibleXRange] with optional [paddingMillis] on each side.
 * If the result is empty, returns a single value at the visible start so the axis does not break.
 */
private fun filterToVisibleWithPadding(
  values: List<Double>,
  visibleXRange: ClosedFloatingPointRange<Double>,
  paddingMillis: Double,
): List<Double> {
  val start = visibleXRange.start - paddingMillis
  val end = visibleXRange.endInclusive + paddingMillis
  val filtered = values.filter { it in start..end }
  return if (filtered.isEmpty()) listOf(visibleXRange.start) else filtered
}

/**
 * Internal helper to remember the horizontal item placer for the X axis.
 * The placer is memoized by [segment] so the same instance is reused until the segment changes,
 * avoiding unnecessary chart redraws on recomposition.
 */
@Composable
internal fun rememberHorizontalAxisItemPlacer(
  segment: GraphSegment,
): HorizontalAxis.ItemPlacer {
  return remember(segment) {
    val defaultPlacer = HorizontalAxis.ItemPlacer.aligned()
    object : HorizontalAxis.ItemPlacer by defaultPlacer {
      override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
      ): List<Double> {
        return when (segment) {
          GraphSegment.YEAR -> {
            val all = fullXRange.monthStartTimestampsMillis()
            val padding = GraphUtil.calculateXStep(segment)
            filterToVisibleWithPadding(all, visibleXRange, padding)
          }

          GraphSegment.MONTH -> {
            val all = sundaysBetween(
              startMillis = fullXRange.start.toLong(),
              endMillis = fullXRange.endInclusive.toLong(),
            )
            val padding = GraphUtil.calculateXStep(segment)
            filterToVisibleWithPadding(all, visibleXRange, padding)
          }

          else -> defaultPlacer.getLabelValues(
            context, visibleXRange, fullXRange, maxLabelWidth,
          )
        }
      }

      override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float
      ): List<Double>? {
        return when (segment) {
          GraphSegment.YEAR -> {
            val all = fullXRange.monthStartTimestampsMillis()
            val padding = GraphUtil.calculateXStep(segment)
            filterToVisibleWithPadding(all, visibleXRange, padding)
          }

          GraphSegment.MONTH -> {
            val all = sundaysBetween(
              startMillis = fullXRange.start.toLong(),
              endMillis = fullXRange.endInclusive.toLong(),
            )
            val padding = GraphUtil.calculateXStep(segment)
            filterToVisibleWithPadding(all, visibleXRange, padding)
          }

          else -> defaultPlacer.getLabelValues(
            context, visibleXRange, fullXRange, maxLabelWidth,
          )
        }
      }
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

/**
 * @param startMillis inclusive
 * @param endMillis   inclusive
 * @param zone        pick the user/device zone, or ZoneId.of("Asia/Kolkata")
 */
fun sundaysBetween(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault()
): List<Double> {
  require(endMillis >= startMillis) { "endMillis must be >= startMillis" }

  val start = Instant.ofEpochMilli(startMillis).atZone(zone)
  val end = Instant.ofEpochMilli(endMillis).atZone(zone)

  // first Sunday on/after start date
  var cursorDate = start.toLocalDate()
    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

  val endDate = end.toLocalDate()

  val result = ArrayList<Long>()
  while (!cursorDate.isAfter(endDate)) {
    // Choose a stable time-of-day for the timestamp (start-of-day in that zone)
    val sundayMillis = cursorDate
      .atStartOfDay(zone)
      .toInstant()
      .toEpochMilli()

    // If you want strict timestamp-range filtering (not just date-range), keep this check:
    if (sundayMillis in startMillis..endMillis) {
      result.add(sundayMillis)
    }

    cursorDate = cursorDate.plusWeeks(1)
  }

  return result.map { it.toDouble() }
}
