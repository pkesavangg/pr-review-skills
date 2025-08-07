package com.dmdbrands.gurus.weight.core.shared.utilities

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
   * Gets today's date formatted as "September 4, 1986"
   */
  fun getTodayFormattedDate(): String {
    val today = LocalDate.now()
    return today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
  }

  /**
   * Gets formatted date from ISO string as "September 4, 1986"
   */
  fun getFormattedDate(dateString: String): String =
    try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    } catch (e: Exception) {
      "Invalid Date"
    }

  /**
   * Gets formatted day with time as "Aug 22, 4:38pm"
   */
  fun getFormattedDayWithTime(dateString: String): String =
    try {
      val dateTime = LocalDateTime.parse(dateString)
      dateTime.format(DateTimeFormatter.ofPattern("MMM d, h:mma", Locale.getDefault()))
    } catch (e: Exception) {
      "Invalid DateTime"
    }

  /**
   * Gets formatted month and day as "Sept 4"
   */
  fun getFormattedMonthDay(dateString: String): String =
    try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    } catch (e: Exception) {
      "Invalid Date"
    }

  /**
   * Gets formatted time as "8:30 PM"
   */
  fun getFormattedTime(timeString: String): String =
    try {
      val dateTime = LocalDateTime.parse(timeString)
      dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    } catch (e: Exception) {
      "Invalid Time"
    }

  /**
   * Gets month abbreviation as "Jan"
   */
  fun getMonth(dateString: String): String {
    if (dateString.isBlank()) return "---"

    return try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    } catch (e: Exception) {
      "---"
    }
  }

  /**
   * Gets month and year as "January 2024"
   */
  fun getMonthYear(dateString: String): String {
    if (dateString.isBlank()) return "----"

    return try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    } catch (e: Exception) {
      "----"
    }
  }

  /**
   * Gets month, day and year as "January 4, 2024"
   */
  fun getMonthDayYear(dateString: String): String {
    if (dateString.isBlank()) return "----"

    return try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    } catch (e: Exception) {
      "----"
    }
  }

  /**
   * Gets year as "1970"
   */
  fun getYear(dateString: String): String {
    if (dateString.isBlank()) return "----"

    return try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.year.toString()
    } catch (e: Exception) {
      "----"
    }
  }

  /**
   * Gets day of week as number (1=Monday, 7=Sunday)
   */
  fun getDay(dateString: String): Int {
    if (dateString.isBlank()) return -1

    return try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString).toLocalDate()
        } else {
          LocalDate.parse(dateString)
        }
      date.dayOfWeek.value
    } catch (e: Exception) {
      -1
    }
  }

  /**
   * Gets current datetime as ISO string
   */
  fun getCurrentDatetimeIsoString(): String = Instant.now().toString()

  /**
   * Converts date string to ISO format
   */
  fun getDatetimeIsoString(dateString: String): String =
    try {
      val dateTime = LocalDateTime.parse(dateString)
      dateTime.atZone(ZoneId.systemDefault()).toInstant().toString()
    } catch (e: Exception) {
      getCurrentDatetimeIsoString()
    }

  /**
   * Gets current time with timezone as "2022-08-22 14:26:38.954039+05:30"
   */
  fun getCurrentTimeWithTimeZone(): String {
    val now = LocalDateTime.now()
    val offset = ZoneId.systemDefault().rules.getOffset(now)
    return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")) + offset.toString()
  }

  /**
   * Gets datetime ISO string for interval days ago from start date
   */
  fun getIntervalDatetimeIsoString(
    interval: Int,
    start: String? = null,
  ): String {
    val startDate =
      if (start != null) {
        try {
          LocalDateTime.parse(start)
        } catch (e: Exception) {
          LocalDateTime.now()
        }
      } else {
        LocalDateTime.now()
      }

    val intervalDate =
      startDate
        .minusDays(interval.toLong())
        .withHour(0)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)

    return intervalDate.atZone(ZoneId.systemDefault()).toInstant().toString()
  }

  /**
   * Gets date string formatted for date picker as "2024-01-01T00:00:00"
   */
  fun getDateStringFormattedForDatePicker(dateString: String): String =
    try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString)
        } else {
          LocalDate.parse(dateString).atStartOfDay()
        }
      date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    } catch (e: Exception) {
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
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
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    return minDate
  }

  /**
   * Gets maximum birthday offset for date picker (120 years ago - oldest allowed)
   */
  fun getMaxBirthdayOffsetForDatePicker(): String {
    val maxDate =
      LocalDateTime
        .now()
        .minusYears(120)
        .withHour(0)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)

    return maxDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
  }

  /**
   * Gets birthday formatted string in UTC
   */
  fun getBirthdayFormattedString(dateString: String): String =
    try {
      val date =
        if (dateString.contains('T')) {
          LocalDateTime.parse(dateString)
        } else {
          LocalDate.parse(dateString).atStartOfDay()
        }
      date.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    } catch (e: Exception) {
      LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }

  /**
   * Gets user's timezone ID
   */
  fun getUserTimezone(): String = ZoneId.systemDefault().id

  /**
   * Gets user's timezone offset in minutes
   */
  fun getUserTimezoneOffset(): Int {
    val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
    return offset.totalSeconds / 60
  }

  /**
   * Gets timezone offset in minutes
   */
  fun getTimeZoneInMinutes(): Int {
    val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
    return offset.totalSeconds / 60
  }

  /**
   * Formats a timestamp (milliseconds) to YYYY-MM-DD format for API requests.
   * Similar to moment(timestamp).format('Y-MM-DD')
   */
  fun getDateFormatFromMilliseconds(timestampMillis: Long): String =
    try {
      val date =
        Instant
          .ofEpochMilli(timestampMillis)
          .atZone(ZoneId.systemDefault())
          .toLocalDate()
      date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } catch (e: Exception) {
      ""
    }

  /**
   * Converts a date string to epoch milliseconds with custom time zone.
   *
   * @param dateString Date string in format "yyyy-MM-dd"
   * @param zoneId Time zone ID (defaults to system default)
   * @return Epoch milliseconds at start of day in specified timezone
   */
  fun getEpochMillisFromDateString(
    dateString: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Long =
    try {
      LocalDate
        .parse(dateString)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
    } catch (e: Exception) {
      System.currentTimeMillis()
    }
}
