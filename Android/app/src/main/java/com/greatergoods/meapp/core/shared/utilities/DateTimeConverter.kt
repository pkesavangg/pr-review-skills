package com.greatergoods.meapp.core.shared.utilities

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import java.time.Instant
import java.time.ZoneId
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
    fun isoToTimestamp(isoString: String): Long {
        return try {
            ZonedDateTime.parse(isoString, formatter)
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
}
