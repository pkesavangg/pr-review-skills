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
   * Returns visible-window padding in xStep units (start, end) for the given segment.
   * Only start padding is used (iOS-style); end padding is 0. Start = 0.2 xStep.
   * TOTAL has no visible padding (non-scrollable).
   *
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @return Pair of (visibleStartPaddingXStep, visibleEndPaddingXStep). Use 0 for TOTAL.
   */
  fun getVisiblePaddingXStepForSegment(segment: GraphSegment): Pair<Double, Double> =
    when (segment) {
      GraphSegment.WEEK -> 0.4 to 0.0
      GraphSegment.MONTH -> 0.3 to 0.0

      GraphSegment.YEAR -> 0.25 to 0.0
      GraphSegment.TOTAL -> 0.0 to 0.0
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
   * Gets the snapped position for drag events based on the graph segment.
   *
   * @param xLabel The x-coordinate timestamp to snap.
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @return The snapped timestamp position.
   */
  fun getSnappedPositionOnDrag(xLabel: Double?, segment: GraphSegment): Double {
    return when (segment) {
      GraphSegment.WEEK -> snapToDayBoundary(xLabel)
      GraphSegment.YEAR -> snapToMonthBoundary(xLabel)
      GraphSegment.MONTH -> xLabel ?: 0.0
      GraphSegment.TOTAL -> 0.0
    }
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
      set(Calendar.MINUTE, 0)
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
   *
   * @param timeStamp The timestamp to snap.
   * @param segment The graph segment type (WEEK, MONTH, YEAR, TOTAL).
   * @param isForward True if flinging forward, false if backward.
   * @return The snapped timestamp position.
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
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

        GraphSegment.MONTH -> {
          set(Calendar.DAY_OF_MONTH, 1)
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }

        GraphSegment.YEAR -> {
          set(Calendar.DAY_OF_YEAR, 1)
          set(Calendar.HOUR_OF_DAY, 0)
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

    return snapped.timeInMillis.toDouble()
  }
}
