package com.greatergoods.meapp.features.common.helper

import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

/**
 * Utility object for handling various unit conversions including height, weight, BMI calculations,
 * and protocol-specific conversions. Equivalent to the TypeScript ConversionTools service.
 */
object ConversionTools {

    // Constants for conversion calculations
    private const val KG_TO_LBS_FACTOR = 2.20462
    private const val LBS_TO_KG_FACTOR = 0.453592
    private const val STORED_TO_LBS_FACTOR = 10.0
    private const val STORED_TO_KG_FACTOR = 22.046
    private const val CM_TO_INCH_FACTOR = 0.254
    private const val INCHES_PER_FOOT = 12
    private const val STORED_HEIGHT_TO_INCHES_FACTOR = 10.0
    private const val BMI_CALCULATION_FACTOR = 100000.0
    private const val BLUETOOTH_CONVERSION_FACTOR = 1.1023

    /**
     * Converts stored height to formatted string representation.
     * @param height Height in stored format (tenths of inches)
     * @param forceMetric Force metric display regardless of user preference
     * @param isMetric User preference for metric units
     * @return Formatted height string (e.g., "5' 10\"" or "178 cm")
     */
    fun convertToFormattedHeight(
        height: Int,
        forceMetric: Boolean = false,
        isMetric: Boolean = false
    ): String {
        return if (isMetric || forceMetric) {
            val cm = convertStoredHeightToCm(height)
            "$cm cm"
        } else {
            val feetInches = convertStoredHeightToFeet(height)
            "${feetInches.first}' ${feetInches.second}\""
        }
    }

    // ========== Weight Conversions ==========

    /**
     * Converts stored weight format to pounds.
     * @param stored Weight in stored format
     * @return Weight in pounds (1 decimal place)
     */
    fun convertStoredToLbs(stored: Double): Double {
        return round((stored / STORED_TO_LBS_FACTOR) * 10) / 10.0
    }

    /**
     * Converts pounds to stored weight format.
     * @param lbs Weight in pounds
     * @return Weight in stored format
     */
    fun convertLbsToStored(lbs: Double): Double {
        return round((lbs * STORED_TO_LBS_FACTOR) * 10) / 10.0
    }

    /**
     * Converts stored weight format to kilograms.
     * @param stored Weight in stored format
     * @return Weight in kilograms (1 decimal place)
     */
    fun convertStoredToKg(stored: Double): Double {
        return round((stored / STORED_TO_KG_FACTOR) * 10) / 10.0
    }

    /**
     * AppSync scales need more precision for kg to lbs conversion.
     * @param display Display weight value
     * @return Weight in stored format with higher precision
     */
    fun convertAppSyncDisplayToStored(display: Double): Double {
        return round((display * KG_TO_LBS_FACTOR) * 10) / 10.0 * 10
    }

    /**
     * Converts kilograms to stored weight format.
     * @param kgs Weight in kilograms
     * @return Weight in stored format
     */
    fun convertKgToStored(kgs: Double): Double {
        return round((kgs * KG_TO_LBS_FACTOR) * 10) / 10.0
    }

    /**
     * Converts display weight to stored format based on unit preference.
     * @param display Display weight value
     * @param forceMetric Force metric conversion regardless of preference
     * @param isMetric User preference for metric units
     * @return Weight in stored format
     */
    fun convertDisplayToStored(
        display: Double,
        forceMetric: Boolean = false,
        isMetric: Boolean = false
    ): Double {
        return if (isMetric || forceMetric) {
            convertKgToStored(display) * 10
        } else {
            convertLbsToStored(display)
        }
    }

    /**
     * Converts stored weight to display format based on unit preference.
     * @param stored Weight in stored format
     * @param isMetric User preference for metric units
     * @return Weight in display format
     */
    fun convertStoredToDisplay(stored: Double, isMetric: Boolean = false): Double {
        return if (isMetric) {
            convertStoredToKg(stored)
        } else {
            convertStoredToLbs(stored)
        }
    }

    /**
     * Converts Bluetooth scale reading to stored format.
     * Bluetooth scales have a resolution of .2 lbs, requiring a specific formula.
     * @param btKg Weight from Bluetooth scale in kg
     * @return Weight in stored format
     */
    fun convertBluetoothToStored(btKg: Double): Double {
        return round((btKg * BLUETOOTH_CONVERSION_FACTOR) * 10) / 10.0 * 2
    }

    // ========== Height Conversions ==========

    /**
     * Converts stored height to feet and inches.
     * @param stored Height in stored format (tenths of inches)
     * @return Pair of (feet, inches)
     */
    fun convertStoredHeightToFeet(stored: Int): Pair<Int, Int> {
        val inches = stored / STORED_HEIGHT_TO_INCHES_FACTOR
        val feet = floor(inches / INCHES_PER_FOOT).toInt()
        val remainingInches = floor(inches % INCHES_PER_FOOT).toInt()
        return Pair(feet, remainingInches)
    }

    /**
     * Converts stored height to centimeters.
     * @param stored Height in stored format (tenths of inches)
     * @return Height in centimeters
     */
    fun convertStoredHeightToCm(stored: Int): Int {
        return round(stored * CM_TO_INCH_FACTOR).toInt()
    }

    /**
     * Converts centimeters to stored height format.
     * @param cm Height in centimeters
     * @return Height in stored format
     */
    fun convertCmToStoredHeight(cm: Int): Int {
        return round(cm / CM_TO_INCH_FACTOR).toInt()
    }

    /**
     * Converts inches to stored height format.
     * @param inches Height in inches
     * @return Height in stored format
     */
    fun convertInchesToStoredHeight(inches: Double): Int {
        return (inches * STORED_HEIGHT_TO_INCHES_FACTOR).toInt()
    }

    /**
     * Converts feet and inches to stored height format.
     * @param feet Height in feet
     * @param inches Additional inches
     * @return Height in stored format
     */
    fun convertFeetInchesToStoredHeight(feet: Int, inches: Int): Int {
        val totalInches = (feet * INCHES_PER_FOOT) + inches
        return convertInchesToStoredHeight(totalInches.toDouble())
    }

    // ========== BMI Calculations ==========

    /**
     * Calculates BMI from weight and height.
     * @param weight Weight in stored format
     * @param height Height in stored format
     * @return BMI value rounded to nearest integer
     */
    fun calculateBMI(weight: Double, height: Int): Int {
        return round((weight / height / height) * BMI_CALCULATION_FACTOR).toInt()
    }

    /**
     * Calculates BMI from display units.
     * @param weightKg Weight in kilograms
     * @param heightCm Height in centimeters
     * @return BMI value rounded to 1 decimal place
     */
    fun calculateBMIFromMetric(weightKg: Double, heightCm: Int): Double {
        val heightM = heightCm / 100.0
        return round((weightKg / (heightM * heightM)) * 10) / 10.0
    }

    /**
     * Calculates BMI from imperial units.
     * @param weightLbs Weight in pounds
     * @param heightInches Height in inches
     * @return BMI value rounded to 1 decimal place
     */
    fun calculateBMIFromImperial(weightLbs: Double, heightInches: Double): Double {
        return round((703 * weightLbs / (heightInches * heightInches)) * 10) / 10.0
    }

    // ========== Value Parsing ==========

    /**
     * Parses various string values to appropriate types.
     * @param value String value to parse
     * @return Parsed value of appropriate type
     */
    fun parseValue(value: String): Any? {
        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            "null" -> null
            "undefined" -> null
            "nan" -> Double.NaN
            "infinity" -> Double.POSITIVE_INFINITY
            "-infinity" -> Double.NEGATIVE_INFINITY
            else -> {
                // Try to parse as number
                value.toDoubleOrNull() ?: value
            }
        }
    }

    // ========== Protocol Conversions ==========

    /**
     * Protocol types for hex conversion.
     */
    enum class ProtocolType {
        R4,
        OTHER
    }

    /**
     * Converts integer to hex string for scale protocols.
     * Scales' broadcast IDs and passwords are stored as integers and need to be
     * converted to a Hex string before being sent to the app.
     * @param value Integer value to convert
     * @param protocolType Type of protocol (R4 or OTHER)
     * @return Hex string formatted for the protocol
     */
    fun convertIntToHex(value: Long, protocolType: ProtocolType): String {
        return try {
            var convertedValue = value.toString(16)

            // Apply padding based on protocol type
            convertedValue = when (protocolType) {
                ProtocolType.R4 -> {
                    "000000000000$convertedValue".takeLast(12)
                }
                ProtocolType.OTHER -> {
                    when {
                        convertedValue.length < 8 -> {
                            "0000000$convertedValue".takeLast(8)
                        }
                        convertedValue.length > 8 && convertedValue.length < 12 -> {
                            "0000000$convertedValue".takeLast(12)
                        }
                        else -> convertedValue
                    }
                }
            }

            // Reverse byte order and convert to uppercase
            convertedValue.chunked(2)
                .reversed()
                .joinToString("")
                .uppercase()

        } catch (e: Exception) {
            AppLog.e("ConversionTools", "Error converting int to hex", e.toString())
            ""
        }
    }

    // ========== Utility Functions ==========

    /**
     * Rounds a double to specified decimal places.
     * @param value Value to round
     * @param decimals Number of decimal places
     * @return Rounded value
     */
    fun roundToDecimals(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals.toDouble())
        return round(value * factor) / factor
    }

    /**
     * Safely converts string to double with fallback.
     * @param value String value to convert
     * @param fallback Fallback value if conversion fails
     * @return Converted double or fallback
     */
    fun safeStringToDouble(value: String?, fallback: Double = 0.0): Double {
        return value?.toDoubleOrNull() ?: fallback
    }

    /**
     * Safely converts string to int with fallback.
     * @param value String value to convert
     * @param fallback Fallback value if conversion fails
     * @return Converted int or fallback
     */
    fun safeStringToInt(value: String?, fallback: Int = 0): Int {
        return value?.toIntOrNull() ?: fallback
    }
}
