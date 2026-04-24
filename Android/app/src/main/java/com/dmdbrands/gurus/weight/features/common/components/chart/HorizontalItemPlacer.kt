package com.dmdbrands.gurus.weight.features.common.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private const val TAG = "HorizontalItemPlacer"

// Upper bound on ticks returned from a single call. Ticks are generated over the visible window
// (typically <20), so this cap is a defensive safety net: even under unexpected upstream state
// the placer cannot return a runaway list and trigger the OOM class fixed in MA-3841.
private const val MAX_TICKS_PER_CALL = 64

/**
 * Builds x-axis label / grid-line positions for the scrollable segments (WEEK/MONTH/YEAR)
 * bounded by the *visible* range. Ticks are generated directly inside
 * `[visibleXRange ± padding]` clamped to `fullXRange`, so allocation scales with the viewport
 * rather than the data domain (MA-3841).
 *
 * TOTAL is non-scrollable and is called only on layout / a handful of times per session, so it
 * keeps Vico's default placer — its adaptive density produces nicer label spacing for the wide
 * TOTAL view without the per-frame allocation pressure that makes the scrollable segments risky.
 */
private fun computeTicks(
  segment: GraphSegment,
  visibleXRange: ClosedFloatingPointRange<Double>,
  fullXRange: ClosedFloatingPointRange<Double>,
): List<Double> {
  val padding = GraphUtil.calculateXStep(segment)
  val rangeStart = maxOf(fullXRange.start, visibleXRange.start - padding)
  val rangeEnd = minOf(fullXRange.endInclusive, visibleXRange.endInclusive + padding)
  if (rangeEnd < rangeStart) return listOf(visibleXRange.start)

  val ticks = when (segment) {
    GraphSegment.WEEK -> dayStartsInRange(rangeStart.toLong(), rangeEnd.toLong())
    GraphSegment.MONTH -> sundaysInRange(rangeStart.toLong(), rangeEnd.toLong())
    GraphSegment.YEAR -> monthStartsInRange(rangeStart.toLong(), rangeEnd.toLong())
    GraphSegment.TOTAL -> error("TOTAL is delegated to Vico's default placer")
  }
  if (ticks.isEmpty()) return listOf(visibleXRange.start)
  if (ticks.size > MAX_TICKS_PER_CALL) {
    AppLog.w(TAG, "tick cap hit: segment=$segment size=${ticks.size}, truncating to $MAX_TICKS_PER_CALL")
    return ticks.take(MAX_TICKS_PER_CALL)
  }
  return ticks
}

/**
 * Remembers a horizontal-axis item placer for [segment]. Memoised on [segment] so scrolling
 * within one segment reuses the instance; switching segments rebuilds it.
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
      ): List<Double> =
        if (segment == GraphSegment.TOTAL) {
          defaultPlacer.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)
        } else {
          computeTicks(segment, visibleXRange, fullXRange)
        }

      override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
      ): List<Double>? =
        if (segment == GraphSegment.TOTAL) {
          defaultPlacer.getLineValues(context, visibleXRange, fullXRange, maxLabelWidth)
        } else {
          computeTicks(segment, visibleXRange, fullXRange)
        }
    }
  }
}

/**
 * Month-start (day 1 at 00:00 local) timestamps inside `[startMillis, endMillis]`.
 * Iterates month-by-month over the clamped range, not the full data domain.
 */
internal fun monthStartsInRange(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault(),
): List<Double> {
  if (endMillis < startMillis) return emptyList()
  val startZdt = Instant.ofEpochMilli(startMillis).atZone(zone)
  val endZdt = Instant.ofEpochMilli(endMillis).atZone(zone)

  var cursor = ZonedDateTime.of(startZdt.year, startZdt.monthValue, 1, 0, 0, 0, 0, zone)
  val endExclusive = endZdt.plusNanos(1)

  val result = ArrayList<Double>()
  while (cursor.isBefore(endExclusive)) {
    val ts = cursor.toInstant().toEpochMilli()
    if (ts in startMillis..endMillis) result.add(ts.toDouble())
    cursor = cursor.plusMonths(1)
  }
  return result
}

/**
 * Sunday start-of-day timestamps inside `[startMillis, endMillis]`.
 * Walks one week at a time from the first Sunday on/after [startMillis].
 */
internal fun sundaysInRange(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault(),
): List<Double> {
  if (endMillis < startMillis) return emptyList()
  val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
  val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

  var cursor = startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
  val result = ArrayList<Double>()
  while (!cursor.isAfter(endDate)) {
    val ts = cursor.atStartOfDay(zone).toInstant().toEpochMilli()
    if (ts in startMillis..endMillis) result.add(ts.toDouble())
    cursor = cursor.plusWeeks(1)
  }
  return result
}

/**
 * Day start-of-day timestamps inside `[startMillis, endMillis]`. Used by WEEK.
 */
internal fun dayStartsInRange(
  startMillis: Long,
  endMillis: Long,
  zone: ZoneId = ZoneId.systemDefault(),
): List<Double> {
  if (endMillis < startMillis) return emptyList()
  val startDate: LocalDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
  val endDate: LocalDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

  var cursor = startDate
  val result = ArrayList<Double>()
  while (!cursor.isAfter(endDate)) {
    val ts = cursor.atStartOfDay(zone).toInstant().toEpochMilli()
    if (ts in startMillis..endMillis) result.add(ts.toDouble())
    cursor = cursor.plusDays(1)
  }
  return result
}
