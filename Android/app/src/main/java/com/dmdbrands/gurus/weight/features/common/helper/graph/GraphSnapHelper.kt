package com.dmdbrands.gurus.weight.features.common.helper.graph

import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import java.util.Calendar
import java.util.TimeZone

/**
 * Helper object for graph snapping functionality.
 * Provides functions to snap graph positions to appropriate boundaries based on time segments.
 */
object GraphSnapHelper {

  /**
   * Gets the padding amount in milliseconds for the given segment.
   * Padding is applied before snapped positions to ensure visibility.
   *
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @return Padding amount in milliseconds.
   */
  fun getPaddingForSegment(segment: GraphSegment): Long {
    return when (segment) {
      GraphSegment.WEEK -> 5 * 60 * 60 * 1000L // 5 hours
      GraphSegment.MONTH -> 5 * 60 * 60 * 1000L // 5 hours
      GraphSegment.YEAR -> 5 * 24 * 60 * 60 * 1000L // 5 days
      GraphSegment.TOTAL -> 0L // No padding for total
    }
  }

  /**
   * Checks if a timestamp matches a snapped boundary for the given segment.
   * Used to detect if scroll range came from snapping.
   *
   * @param timestamp The timestamp to check.
   * @param segment The graph segment type.
   * @return True if the timestamp matches a snapped boundary.
   */
  fun isSnappedBoundary(timestamp: Long, segment: GraphSegment): Boolean {
    val localTimeZone = TimeZone.getDefault()
    val calendar = Calendar.getInstance(localTimeZone)
    calendar.timeInMillis = timestamp

    return when (segment) {
      GraphSegment.WEEK -> {
        // Check if it's at day boundary (midnight)
        calendar.get(Calendar.HOUR_OF_DAY) == 0 &&
          calendar.get(Calendar.MINUTE) == 0 &&
          calendar.get(Calendar.SECOND) == 0
      }

      GraphSegment.MONTH -> {
        // Check if it's at month start (day 1, hour 0 or 1)
        calendar.get(Calendar.DAY_OF_MONTH) == 1 &&
          (calendar.get(Calendar.HOUR_OF_DAY) == 0 || calendar.get(Calendar.HOUR_OF_DAY) == 1) &&
          calendar.get(Calendar.MINUTE) <= 1
      }

      GraphSegment.YEAR -> {
        // Check if it's at year start (day 1 of year, hour 0 or 1)
        calendar.get(Calendar.DAY_OF_YEAR) == 1 &&
          (calendar.get(Calendar.HOUR_OF_DAY) == 0 || calendar.get(Calendar.HOUR_OF_DAY) == 1) &&
          calendar.get(Calendar.MINUTE) <= 1
      }

      GraphSegment.TOTAL -> false
    }
  }

  /**
   * Applies padding to a scroll range if it matches snapped boundaries.
   * This ensures padding is visible when snapping occurs.
   *
   * @param min The minimum timestamp of the scroll range.
   * @param max The maximum timestamp of the scroll range.
   * @param segment The graph segment type.
   * @return Pair of (adjustedMin, adjustedMax) with padding applied if needed.
   */
  fun applyPaddingToScrollRange(min: Long, max: Long, segment: GraphSegment): Pair<Long, Long> {
    if (segment == GraphSegment.TOTAL) {
      return min to max
    }

    val padding = getPaddingForSegment(segment)
    val isMinSnapped = isSnappedBoundary(min, segment)
    isSnappedBoundary(max, segment)

    // Apply padding before min if it's a snapped boundary
    val adjustedMin = if (isMinSnapped && padding > 0) {
      min - padding
    } else {
      min
    }

    // Keep max as is (padding is only applied at the start)
    val adjustedMax = max

    return adjustedMin to adjustedMax
  }

  /**
   * Gets the snapped position for drag events based on the graph segment.
   * Applies padding before the snapped position to ensure visibility.
   *
   * @param xLabel The x-coordinate timestamp to snap.
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @return The snapped timestamp position with padding applied (padding is subtracted to show space before).
   */
  fun getSnappedPositionOnDrag(xLabel: Double?, segment: GraphSegment): Double {
    val snappedPosition = when (segment) {
      GraphSegment.WEEK -> snapToDayBoundary(xLabel)
      GraphSegment.YEAR -> snapToMonthBoundary(xLabel)
      GraphSegment.MONTH -> xLabel ?: 0.0
      GraphSegment.TOTAL -> 0.0
    }

    // Apply padding: subtract padding from snapped position so there's space before it
    val padding = getPaddingForSegment(segment)
    return snappedPosition - padding
  }

  /**
   * Snaps a timestamp to the nearest day boundary in local time.
   * If the time is after 12:00 PM, it snaps to the next day at midnight.
   * Otherwise, it snaps to the current day at midnight.
   *
   * @param timestamp The timestamp to snap.
   * @return The snapped timestamp at day boundary.
   */
  fun snapToDayBoundary(timestamp: Double?): Double {
    timestamp ?: return 0.0

    val localTimeZone = TimeZone.getDefault()
    val calendar = Calendar.getInstance(localTimeZone)
    calendar.timeInMillis = timestamp.toLong()

    val startOfDay = Calendar.getInstance(localTimeZone).apply {
      timeInMillis = timestamp.toLong()
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    return if (calendar.get(Calendar.HOUR_OF_DAY) >= 12) {
      // Snap to next day 12:00 AM
      startOfDay.apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis.toDouble()
    } else {
      // Snap to current day 12:00 AM
      startOfDay.timeInMillis.toDouble()
    }
  }

  /**
   * Snaps a timestamp to the nearest month boundary in local time.
   * If the day is after the 15th or it's the 15th after 12:00 PM, it snaps to the next month.
   * Otherwise, it snaps to the current month.
   *
   * @param timestamp The timestamp to snap.
   * @return The snapped timestamp at month boundary.
   */
  fun snapToMonthBoundary(timestamp: Double?): Double {
    timestamp ?: return 0.0

    val localTimeZone = TimeZone.getDefault()
    val calendar = Calendar.getInstance(localTimeZone)
    calendar.timeInMillis = timestamp.toLong()

    val startOfMonth = Calendar.getInstance(localTimeZone).apply {
      timeInMillis = timestamp.toLong()
      set(Calendar.DAY_OF_MONTH, 1)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 1)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    return if (calendar.get(Calendar.DAY_OF_MONTH) > 15 ||
      (calendar.get(Calendar.DAY_OF_MONTH) == 15 && calendar.get(Calendar.HOUR_OF_DAY) >= 12)
    ) {
      // Next month midnight
      startOfMonth.apply { add(Calendar.MONTH, 1) }.timeInMillis.toDouble()
    } else {
      // Current month midnight
      startOfMonth.timeInMillis.toDouble()
    }
  }

  /**
   * Gets the snap position for fling events based on the graph segment and direction.
   * Applies padding before the snapped position to ensure visibility.
   *
   * @param timeStamp The timestamp to snap.
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @param isForward True if flinging forward, false if backward.
   * @return The snapped timestamp position with padding applied (padding is subtracted to show space before).
   */
  fun getSnapPositionOnFling(timeStamp: Double?, segment: GraphSegment, isForward: Boolean): Double {
    timeStamp ?: return 0.0

    val localTimeZone = TimeZone.getDefault()
    val calendar = Calendar.getInstance(localTimeZone)
    calendar.timeInMillis = timeStamp.toLong()

    val startOfCurrent = Calendar.getInstance(localTimeZone).apply {
      timeInMillis = timeStamp.toLong()
      when (segment) {
        GraphSegment.WEEK -> {
          val firstDayOfWeek = firstDayOfWeek
          val currentDayOfWeek = get(Calendar.DAY_OF_WEEK)
          val delta = ((currentDayOfWeek - firstDayOfWeek) + 7) % 7
          add(Calendar.DAY_OF_MONTH, -delta)
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 1)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

        GraphSegment.MONTH -> {
          set(Calendar.DAY_OF_MONTH, 1)
          set(Calendar.HOUR_OF_DAY, 1)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

        GraphSegment.YEAR -> {
          set(Calendar.DAY_OF_YEAR, 1)
          set(Calendar.HOUR_OF_DAY, 1)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

        GraphSegment.TOTAL -> {
          return 0.0
        }
      }
    }

    val snapped = Calendar.getInstance(localTimeZone).apply {
      timeInMillis = startOfCurrent.timeInMillis
      when (segment) {
        GraphSegment.WEEK -> if (isForward) add(Calendar.WEEK_OF_YEAR, 1) else { /* stay at current week start */
        }

        GraphSegment.MONTH -> if (isForward) add(Calendar.MONTH, 1) else { /* stay at current month start */
        }

        GraphSegment.YEAR -> if (isForward) add(Calendar.YEAR, 1) else { /* stay at current year start */
        }

        GraphSegment.TOTAL -> { /* no change */
        }
      }
    }

    val snappedPosition = snapped.timeInMillis.toDouble()

    // Apply padding: subtract padding from snapped position so there's space before it
    val padding = getPaddingForSegment(segment)
    return snappedPosition - padding
  }
}
