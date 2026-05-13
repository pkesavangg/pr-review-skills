package com.dmdbrands.gurus.weight.core.shared.utilities

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.round

/**
 * Utility object for handling various unit conversions including height, weight, BMI calculations,
 * and protocol-specific conversions. Equivalent to the TypeScript ConversionTools service.
 */
object ConversionTools {

  // Constants for conversion calculations
  private const val KG_TO_LBS_FACTOR = 2.20462
  private const val STORED_TO_LBS_FACTOR = 10.0
  private const val STORED_TO_KG_FACTOR = 22.046
  private const val CM_TO_INCH_FACTOR = 0.254
  private const val INCHES_PER_FOOT = 12
  private const val STORED_HEIGHT_TO_INCHES_FACTOR = 10.0
  private const val BMI_CALCULATION_FACTOR = 100000
  private const val BLUETOOTH_CONVERSION_FACTOR = 1.1023

  // ========== Weight Conversions ==========

  /**
   * Converts stored weight format to pounds.
   * @param stored Weight in stored format
   * @return Weight in pounds (1 decimal place)
   */
  fun convertStoredToLbs(stored: Double): Double {
    return stored / STORED_TO_LBS_FACTOR
  }

  /**
   * Converts pounds to stored weight format.
   * @param lbs Weight in pounds
   * @return Weight in stored format
   */
  fun convertLbsToStored(lbs: Double): Double {
    return (lbs * STORED_TO_LBS_FACTOR)
  }

  /**
   * Converts kilograms to stored weight format.
   * @param kgs Weight in kilograms
   * @return Weight in stored format
   */
  fun convertKgToStored(kgs: Double): Double {
    return round((kgs * 2.2046) * 10) / 10.0
  }

  fun convertKgToStoredA3(kgs: Double): Double {
    val intermediate = round(kgs * 1.10231 * 10) / 10
    return intermediate * 2
  }

  /**
   * Converts stored weight format to kilograms.
   * @param stored Weight in stored format
   * @return Weight in kilograms (1 decimal place)
   */
  fun convertStoredToKg(stored: Double): Double {
    return (stored / STORED_TO_KG_FACTOR) * 10 / 10.0
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
   * Converts stored weight to display format based on unit preference.
   * @param stored Weight in stored format
   * @param isMetric User preference for metric units
   * @return Weight in display format
   */
  fun convertStoredToDisplay(stored: Double, isMetric: Boolean = false): Double {
    if (!isMetric) {
      val lbs = convertStoredToLbs(stored)
      return lbs
    }
    return convertStoredToKg(stored)
  }

  // ========== Height Conversions ==========
  /**
   * Converts stored height to centimeters.
   * @param stored Height in stored format (tenths of inches)
   * @return Height in centimeters
   */
  fun convertStoredHeightToCm(stored: Int): Double {
    return (stored * CM_TO_INCH_FACTOR)
  }

  /**
   * Converts inches to stored height format.
   * @param inches Height in inches
   * @return Height in stored format
   */
  fun convertInchesToStoredHeight(inches: Double): Int {
    return (inches * STORED_HEIGHT_TO_INCHES_FACTOR).toInt()
  }

  // ========== BMI Calculations ==========

  /**
   * Calculates BMI from weight and height.
   * @param weight Weight in stored format
   * @param height Height in stored format
   * @return BMI value rounded to nearest integer
   */
  fun calculateBMI(weight: Double, height: Int): Int {
    return ((weight / height / height) * BMI_CALCULATION_FACTOR).toInt()
  }

  /**
   * Calculates BMI from display units.
   * @param weightKg Weight in kilograms
   * @param heightCm Height in centimeters
   * @return BMI value rounded to 1 decimal place
   */
  fun calculateBMIFromMetric(weightKg: Double, heightCm: Int): Double {
    val heightM = heightCm / 100.0
    return ((weightKg / (heightM * heightM)) * 10) / 10.0
  }

  fun convertToUTC(input: String): String {
    val localDateTime = OffsetDateTime.parse(input)
    val utcDateTime = localDateTime.withOffsetSameInstant(ZoneOffset.UTC)
    return utcDateTime.format(DateTimeFormatter.ISO_INSTANT)
  }
}
