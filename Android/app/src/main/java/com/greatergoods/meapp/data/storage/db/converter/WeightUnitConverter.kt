package com.greatergoods.meapp.data.storage.db.converter

import androidx.room.TypeConverter
import com.greatergoods.meapp.domain.model.common.WeightUnit

/**
 * Type converter for handling WeightUnit enum in Room database.
 */
class WeightUnitConverter {
    /**
     * Convert a string to a WeightUnit enum.
     *
     * @param value The string value to convert
     * @return The WeightUnit enum, or LB if the value is null or unknown
     */
    @TypeConverter
    fun fromString(value: String?): WeightUnit = WeightUnit.from(value)

    /**
     * Convert a WeightUnit enum to its string value.
     *
     * @param unit The WeightUnit enum to convert
     * @return The string value, or null if the unit is null
     */
    @TypeConverter
    fun toString(unit: WeightUnit?): String? = unit?.value
}
