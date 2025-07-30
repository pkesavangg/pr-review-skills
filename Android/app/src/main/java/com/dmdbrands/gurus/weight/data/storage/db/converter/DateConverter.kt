package com.dmdbrands.gurus.weight.data.storage.db.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for handling Date objects in Room database.
 */
class DateConverter {
    /**
     * Convert a timestamp to a Date object.
     *
     * @param timestamp The timestamp to convert
     * @return The Date object, or null if the timestamp is null
     */
    @TypeConverter
    fun fromTimestamp(timestamp: String?): Date? = timestamp?.let { Date(it.toLong()) }

    /**
     * Convert a Date object to a timestamp string.
     *
     * @param date The Date object to convert
     * @return The timestamp string, or null if the date is null
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): String? = date?.time?.toString()
}
