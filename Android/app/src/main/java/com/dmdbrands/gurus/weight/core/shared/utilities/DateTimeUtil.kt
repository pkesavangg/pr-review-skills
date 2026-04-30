package com.dmdbrands.gurus.weight.core.shared.utilities

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility object for date and time formatting operations.
 * Provides similar functionality to the Angular DatetimeToolsService.
 */
object DateTimeUtil {
  /**
   * Generates a timestamp in the format 'YYYY-MM-DD HH:mm:ss.SSSSSSZ'
   * Example: '2022-08-22 14:26:38.954039+05:30'
   */
  fun getCurrentTimestamp(): String {
    val now = ZonedDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX")
    return now.format(formatter)
  }

  /**
   * Gets minimum birthday offset for date picker (13 years ago)
   */
  fun getMinBirthdayOffsetForDatePicker(): Long {
    val minDate =
      LocalDateTime
        .now()
        .minusYears(13)
        .toLocalDate()
        .atTime(23, 59, 59, 999_000_000)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    return minDate
  }
}
