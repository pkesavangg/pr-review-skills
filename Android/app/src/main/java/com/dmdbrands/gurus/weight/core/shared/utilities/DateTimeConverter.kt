package com.dmdbrands.gurus.weight.core.shared.utilities

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Utility class for converting between ISO date-time strings and timestamps.
 */
object DateTimeConverter {
  private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
  private const val SIMPLE_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
  private val formatter = DateTimeFormatter.ofPattern(ISO_PATTERN)
  private val simpleDateTimeFormatter = DateTimeFormatter.ofPattern(SIMPLE_DATETIME_PATTERN)
  private val defaultZone = ZoneId.systemDefault()

  /**
   * Converts an ISO date-time string to a timestamp in milliseconds.
   * Supports multiple date-time formats:
   * - ISO format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
   * - Simple format: "yyyy-MM-dd HH:mm:ss"
   *
   * @param isoString The date-time string in various formats
   * @return Timestamp in milliseconds since epoch, or 0L if conversion fails
   */
  fun isoToTimestamp(isoString: String?): Long {
    return try {
      if (isoString == null) {
        return 0L
      }

      val zonedDateTime = when {
        // Try ISO format first (with T and timezone)
        isoString.contains('T') && (isoString.contains('+') || isoString.contains('Z')) -> {
          ZonedDateTime.parse(isoString, formatter)
        }
        // Try simple datetime format (space instead of T, no timezone)
        isoString.contains(' ') && !isoString.contains('T') -> {
          val localDateTime = java.time.LocalDateTime.parse(isoString, simpleDateTimeFormatter)
          localDateTime.atZone(defaultZone)
        }
        // Try ISO format without timezone (assume system timezone)
        isoString.contains('T') -> {
          val localDateTime = java.time.LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
          localDateTime.atZone(defaultZone)
        }
        // Try simple date format (assume start of day in system timezone)
        else -> {
          val localDate = LocalDate.parse(isoString, DateTimeFormatter.ISO_DATE)
          localDate.atStartOfDay(defaultZone)
        }
      }
      zonedDateTime.toInstant().toEpochMilli()
    } catch (e: Exception) {
      AppLog.e("DateTimeConverter", "Failed to convert date string to timestamp: $isoString", e.toString())
      0L
    }
  }

  /**
   * Converts a timestamp to an ISO date-time string.
   *
   * @param timestamp Timestamp in milliseconds since epoch
   * @return ISO formatted date-time string, or null if conversion fails
   */
  fun timestampToIso(timestamp: Long): String {
    return try {
      ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneOffset.UTC,
      ).format(formatter)
    } catch (e: Exception) {
      AppLog.e("DateTimeConverter", "Failed to convert timestamp to ISO string", e)
      ""
    }
  }

  fun isValidIsoTimestamp(timestamp: String): Boolean {
    return try {
      Instant.parse(timestamp) // For full ISO-8601 (with Z or time zone)
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Calculates age from a date of birth string.
   * Supports multiple date formats:
   * - ISO date-time format: "yyyy-MM-ddTHH:mm:ss..."
   * - Simple datetime format: "yyyy-MM-dd HH:mm:ss"
   * - ISO date format: "yyyy-MM-dd"
   *
   * @param dobString Date of birth string in various formats
   * @return Age in years, or 0 if parsing fails
   */
  fun calculateAge(dobString: String): Int {
    return try {
      val dob = when {
        // Handle ISO date-time format with timezone (e.g., "2025-08-21T10:15:30+00:00")
        dobString.contains('T') && (dobString.contains('+') || dobString.contains('Z')) -> {
          ZonedDateTime.parse(dobString, formatter).toLocalDate()
        }
        // Handle simple datetime format (e.g., "2025-08-21 10:15:30")
        dobString.contains(' ') && !dobString.contains('T') -> {
          java.time.LocalDateTime.parse(dobString, simpleDateTimeFormatter).toLocalDate()
        }
        // Handle ISO date-time format without timezone (e.g., "2025-08-21T10:15:30")
        dobString.contains('T') -> {
          java.time.LocalDateTime.parse(dobString, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        }
        // Handle ISO date format (e.g., "2025-08-21")
        else -> {
          LocalDate.parse(dobString, DateTimeFormatter.ISO_DATE)
        }
      }
      val today = LocalDate.now()
      Period.between(dob, today).years
    } catch (e: Exception) {
      AppLog.e("DateTimeConverter", "Failed to calculate age from date: $dobString", e.toString())
      0
    }
  }

  /**
   * Gets the start of the week (Sunday) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return Start of week timestamp in milliseconds
   */
  fun getWeekStart(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /**
   * Gets the end of the week (Saturday) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return End of week timestamp in milliseconds
   */
  fun getWeekEnd(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /**
   * Gets the start of the month (1st day) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return Start of month timestamp in milliseconds
   */
  fun getMonthStart(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /**
   * Gets the end of the month (last day) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return End of month timestamp in milliseconds
   */
  fun getMonthEnd(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /**
   * Gets the start of the year (January 1st) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return Start of year timestamp in milliseconds
   */
  fun getYearStart(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_YEAR, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }

  /**
   * Gets the end of the year (December 31st) for the given timestamp.
   * @param referenceMillis Timestamp in milliseconds
   * @return End of year timestamp in milliseconds
   */
  fun getYearEnd(referenceMillis: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = referenceMillis
    calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
  }
}
