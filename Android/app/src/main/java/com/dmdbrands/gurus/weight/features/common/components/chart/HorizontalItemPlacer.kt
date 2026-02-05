package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Internal helper to remember the horizontal item placer for the X axis.
 */
@Composable
internal fun rememberHorizontalAxisItemPlacer(
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
      return when (segment) {
        GraphSegment.YEAR ->
          fullXRange.monthStartTimestampsMillis()

        GraphSegment.MONTH ->
          monthAnchorStartOfDayMillisBetween(
            startMillis = fullXRange.start.toLong(),
            endMillis = fullXRange.endInclusive.toLong(),
          )

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
        GraphSegment.YEAR ->
          fullXRange.monthStartTimestampsMillis()

        GraphSegment.MONTH ->
          monthAnchorStartOfDayMillisBetween(
            startMillis = fullXRange.start.toLong(),
            endMillis = fullXRange.endInclusive.toLong(),
          )

        else -> defaultPlacer.getLabelValues(
          context, visibleXRange, fullXRange, maxLabelWidth,
        )
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

private val TARGET_DAYS = intArrayOf(1, 8, 15, 22, 29)

/**
 * Returns all matching LocalDate values within [startMillis, endMillis] (inclusive),
 * for days-of-month 1, 8, 15, 22, 29 (29 only if the month supports it).
 */
fun monthAnchorDatesBetween(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault()
): List<LocalDate> {
  if (startMillis > endMillis) return emptyList()

  val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
  val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

  var ym = YearMonth.from(startDate)
  val endYm = YearMonth.from(endDate)

  val out = ArrayList<LocalDate>(/* rough guess */ 6 * (endYm.year - ym.year + 1))

  while (!ym.isAfter(endYm)) {
    val maxDay = ym.lengthOfMonth()

    for (d in TARGET_DAYS) {
      if (d > maxDay) continue // e.g., Feb 29 on non-leap years
      val date = ym.atDay(d)
      if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
        out.add(date)
      }
    }

    ym = ym.plusMonths(1)
  }

  return out
}

/**
 * Same as monthAnchorDatesBetween, but returns timestamps (millis) at start-of-day in [zone].
 */
fun monthAnchorStartOfDayMillisBetween(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault()
): List<Double> =
  monthAnchorDatesBetween(startMillis, endMillis, zone)
    .map { it.atStartOfDay(zone).toInstant().toEpochMilli().toDouble() }
