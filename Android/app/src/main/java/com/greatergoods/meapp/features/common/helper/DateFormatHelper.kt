package com.greatergoods.meapp.features.common.helper

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatHelper {
    /**
     * Formats a date string (ISO8601, Zulu, or local) to 'MMMM d, yyyy' or returns 'Invalid Date'.
     */
    fun formatDisplayDate(dateString: String): String =
        try {
            val date = when {
                dateString.contains('T') && dateString.endsWith("Z") -> {
                    ZonedDateTime.parse(dateString).toLocalDate()
                }
                dateString.contains('T') -> {
                    LocalDateTime.parse(dateString).toLocalDate()
                }
                else -> {
                    LocalDate.parse(dateString)
                }
            }
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        } catch (e: Exception) {
            "Invalid Date"
        }
} 