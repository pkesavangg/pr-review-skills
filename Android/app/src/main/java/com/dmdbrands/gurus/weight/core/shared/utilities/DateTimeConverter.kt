package com.dmdbrands.gurus.weight.core.shared.utilities

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility class for converting between ISO date-time strings and timestamps.
 */
object DateTimeConverter {
  private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
  private val formatter = DateTimeFormatter.ofPattern(ISO_PATTERN)
  private val defaultZone = ZoneId.of("America/Los_Angeles")

  /**
   * Converts an ISO date-time string to a timestamp in milliseconds.
   *
   * @param isoString The ISO formatted date-time string
   * @return Timestamp in milliseconds since epoch, or null if conversion fails
   */
  fun isoToTimestamp(isoString: String?): Long {
    return try {
      if (isoString == null) {
        return 0L
      }
      ZonedDateTime.parse(isoString, this.formatter)
        .toInstant()
        .toEpochMilli()
    } catch (e: Exception) {
      AppLog.e("DateTimeConverter", "Failed to convert ISO string to timestamp", e.toString())
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
        defaultZone,
      ).format(formatter)
    } catch (e: Exception) {
      AppLog.e("DateTimeConverter", "Failed to convert timestamp to ISO string", e.toString())
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

  fun calculateAge(dobString: String): Int {
    val formatter = DateTimeFormatter.ISO_DATE_TIME
    val dob = ZonedDateTime.parse(dobString, formatter).toLocalDate()
    val today = LocalDate.now()
    return Period.between(dob, today).years
  }

  data class TimeRange(val start: Long, val end: Long)

  private val UTC: ZoneId = ZoneOffset.UTC
  private val END_OF_DAY_999: LocalTime = LocalTime.of(23, 59, 59, 999_000_000)

  private fun toUtcDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(UTC).toLocalDate()

  private fun startOfDayUtc(d: LocalDate): Long =
    d.atStartOfDay(UTC).toInstant().toEpochMilli()

  private fun endOfDayUtc(d: LocalDate): Long =
    d.atTime(END_OF_DAY_999).atZone(UTC).toInstant().toEpochMilli()

  fun getWeekRange(referenceMillis: Long): TimeRange {
    val d = toUtcDate(referenceMillis)
    val startDate = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val endDate = d.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    return TimeRange(startOfDayUtc(startDate), endOfDayUtc(endDate))
  }

  fun getMonthRange(referenceMillis: Long): TimeRange {
    val d = toUtcDate(referenceMillis)
    val ym = YearMonth.from(d)
    val startDate = ym.atDay(1)
    val endDate = ym.atEndOfMonth()
    return TimeRange(startOfDayUtc(startDate), endOfDayUtc(endDate))
  }

  fun getYearRange(referenceMillis: Long): TimeRange {
    val d = toUtcDate(referenceMillis)
    val startDate = d.with(java.time.temporal.TemporalAdjusters.firstDayOfYear())
    val endDate = d.with(java.time.temporal.TemporalAdjusters.lastDayOfYear())
    return TimeRange(startOfDayUtc(startDate), endOfDayUtc(endDate))
  }
}
