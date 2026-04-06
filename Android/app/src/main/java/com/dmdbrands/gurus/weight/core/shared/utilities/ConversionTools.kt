package com.dmdbrands.gurus.weight.core.shared.utilities

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
  fun convertStoredHeightToCm(stored: Int): Int {
    return (stored * CM_TO_INCH_FACTOR).toInt()
  }

  /**
   * Converts inches to stored height format.
   * @param inches Height in inches
   * @return Height in stored format
   */
  fun convertInchesToStoredHeight(inches: Double): Int {
    return (inches * STORED_HEIGHT_TO_INCHES_FACTOR).toInt()
  }

  // ========== Baby Weight/Length Conversions ==========

  private const val DECIGRAMS_PER_OZ = 283.495
  private const val OZ_PER_LB = 16
  private const val MM_PER_INCH = 25.4
  private const val DECIGRAMS_PER_KG = 10_000.0
  private const val DECIGRAMS_PER_GRAM = 10.0
  private const val MM_PER_CM = 10.0

  // Baby scale graduation thresholds (in grams, matching babyApp)
  private const val GRADUATION_THRESHOLD_18_LB_GRAMS = 8165
  private const val GRADUATION_THRESHOLD_25_LB_GRAMS = 11340

  // Conversion factors per SKU
  private const val GRAMS_TO_LBS_FACTOR_0220 = 2.2046
  private const val GRAMS_TO_LBS_FACTOR_0222 = 2.204623
  private const val CALIBRATION_NUMERATOR_0222 = 369874.0
  private const val CALIBRATION_DENOMINATOR_0222 = 1048576.0

  // ---- Imperial display (existing) ----

  /**
   * Converts baby weight from decigrams to pounds (whole number).
   * @param decigrams Weight in decigrams
   * @return Pounds component
   */
  fun convertDecigramsToLb(decigrams: Int): Int =
      (decigrams / DECIGRAMS_PER_OZ / OZ_PER_LB).toInt()

  /**
   * Converts baby weight from decigrams to remaining ounces (1 decimal).
   * @param decigrams Weight in decigrams
   * @return Ounces component rounded to 1 decimal place
   */
  fun convertDecigramsToOz(decigrams: Int): Double =
      round((decigrams / DECIGRAMS_PER_OZ) % OZ_PER_LB * 10.0) / 10.0

  /**
   * Converts baby length from millimeters to inches (1 decimal).
   * @param millimeters Length in millimeters
   * @return Inches rounded to 1 decimal place
   */
  fun convertMmToInches(millimeters: Int): Double =
      round(millimeters / MM_PER_INCH * 10.0) / 10.0

  // ---- Metric display ----

  /**
   * Converts baby weight from decigrams to kilograms (2 decimal places).
   * @param decigrams Weight in decigrams
   * @return Weight in kilograms
   */
  fun convertDecigramsToKg(decigrams: Int): Double =
      round(decigrams / DECIGRAMS_PER_KG * 100.0) / 100.0

  /**
   * Converts baby weight from decigrams to grams (1 decimal place).
   * @param decigrams Weight in decigrams
   * @return Weight in grams
   */
  fun convertDecigramsToGrams(decigrams: Int): Double =
      round(decigrams / DECIGRAMS_PER_GRAM * 10.0) / 10.0

  /**
   * Converts baby length from millimeters to centimeters (1 decimal place).
   * @param millimeters Length in millimeters
   * @return Length in centimeters
   */
  fun convertMmToCm(millimeters: Int): Double =
      round(millimeters / MM_PER_CM * 10.0) / 10.0

  // ---- Reverse / save conversions ----

  /**
   * Converts lb + oz input to decigrams for storage.
   * @param lbs Whole pounds
   * @param oz Ounces (fractional)
   * @return Weight in decigrams
   */
  fun convertLbOzToDecigrams(lbs: Int, oz: Double): Int {
    val totalOz = (lbs * OZ_PER_LB) + oz
    return round(totalOz * DECIGRAMS_PER_OZ).toInt()
  }

  /**
   * Converts kilograms to decigrams for storage.
   * @param kg Weight in kilograms
   * @return Weight in decigrams
   */
  fun convertKgToDecigrams(kg: Double): Int =
      round(kg * DECIGRAMS_PER_KG).toInt()

  /**
   * Converts inches to millimeters for storage.
   * @param inches Length in inches
   * @return Length in millimeters
   */
  fun convertInchesToMm(inches: Double): Int =
      round(inches * MM_PER_INCH).toInt()

  /**
   * Converts centimeters to millimeters for storage.
   * @param cm Length in centimeters
   * @return Length in millimeters
   */
  fun convertCmToMm(cm: Double): Int =
      round(cm * MM_PER_CM).toInt()

  // ========== Baby Scale SKU 0220 Graduation ==========

  /**
   * Converts decigrams to kg with 0220 scale graduation rounding.
   * <18 lb → 5 g steps, 18–25 lb → 10 g steps, ≥25 lb → 50 g steps.
   */
  fun convert0220DecigramsToKg(decigrams: Int): Double {
    val grams = decigrams / DECIGRAMS_PER_GRAM
    return when {
      grams >= GRADUATION_THRESHOLD_25_LB_GRAMS -> (round(grams / 50) * 50) / 1000.0
      grams >= GRADUATION_THRESHOLD_18_LB_GRAMS -> round(grams / 10) / 100.0
      else -> (round(grams / 5) * 5) / 1000.0
    }
  }

  /**
   * Converts decigrams to decimal lbs with 0220 scale graduation rounding.
   * <18 lb → 0.01 lb, 18–25 lb → 0.02 lb, ≥25 lb → 0.1 lb.
   */
  fun convert0220DecigramsToLbDecimal(decigrams: Int): Double {
    val grams = decigrams / DECIGRAMS_PER_GRAM
    val unroundedLbs = (grams / 1000.0) * GRAMS_TO_LBS_FACTOR_0220
    return when {
      grams >= GRADUATION_THRESHOLD_25_LB_GRAMS ->
        round(unroundedLbs * 10.0) / 10.0
      grams >= GRADUATION_THRESHOLD_18_LB_GRAMS ->
        round(unroundedLbs / 2.0 * 100.0) / 100.0 * 2.0
      else ->
        round(unroundedLbs * 100.0) / 100.0
    }
  }

  /**
   * Converts decigrams to [lbs, oz] with 0220 scale graduation rounding.
   * <18 lb → 0.1 oz, 18–25 lb → 0.2 oz, ≥25 lb → 2 oz.
   * @return Pair(pounds, ounces)
   */
  fun convert0220DecigramsToLbOz(decigrams: Int): Pair<Int, Double> {
    val grams = decigrams / DECIGRAMS_PER_GRAM
    val totalOz = when {
      grams >= GRADUATION_THRESHOLD_25_LB_GRAMS ->
        round(grams / 2.0 / 28.35) * 2.0
      grams >= GRADUATION_THRESHOLD_18_LB_GRAMS ->
        round(grams * 5.0 / 28.35) / 5.0
      else ->
        round(grams / 28.35 * 10.0) / 10.0
    }
    val lbs = (totalOz / OZ_PER_LB).toInt()
    val oz = if (lbs > 0) round((totalOz % OZ_PER_LB) * 10.0) / 10.0 else round(totalOz * 10.0) / 10.0
    return Pair(lbs, oz)
  }

  // ========== Baby Scale SKU 0222 Graduation ==========

  /**
   * Converts decigrams to decimal lbs with 0222 scale graduation rounding.
   * Uses precise 2.204623 conversion factor.
   */
  fun convert0222DecigramsToLbDecimal(decigrams: Int): Double {
    val grams = decigrams / DECIGRAMS_PER_GRAM
    val unroundedLbs = (grams / 1000.0) * GRAMS_TO_LBS_FACTOR_0222
    return when {
      grams >= GRADUATION_THRESHOLD_25_LB_GRAMS ->
        round(unroundedLbs * 10.0) / 10.0
      grams >= GRADUATION_THRESHOLD_18_LB_GRAMS ->
        round(unroundedLbs / 2.0 * 100.0) / 100.0 * 2.0
      else ->
        round(unroundedLbs * 100.0) / 100.0
    }
  }

  /**
   * Converts decigrams to [lbs, oz] with 0222 scale manufacturer calibration formula.
   * Uses Transtek transmission formula: weight * 369874 / 1048576.
   * @return Pair(pounds, ounces)
   */
  fun convert0222DecigramsToLbOz(decigrams: Int): Pair<Int, Double> {
    val transmissionWeight = decigrams / DECIGRAMS_PER_GRAM
    val converted = round(transmissionWeight * CALIBRATION_NUMERATOR_0222 / CALIBRATION_DENOMINATOR_0222)
    val lbs = (converted / 160).toInt()
    val rawOz = converted - (lbs * 160)

    val indexing = when {
      transmissionWeight >= GRADUATION_THRESHOLD_25_LB_GRAMS -> 50
      transmissionWeight >= GRADUATION_THRESHOLD_18_LB_GRAMS -> 10
      else -> 5
    }

    val adjustedOz = when (indexing) {
      50 -> round(rawOz / 20) * 20 / 10.0
      10 -> round(rawOz / 2) * 2 / 10.0
      else -> round(rawOz * 10.0 / 10.0) / 10.0
    }
    return Pair(lbs, round(adjustedOz * 10.0) / 10.0)
  }

  // ========== Baby Source-Aware Router ==========

  /**
   * Routes baby weight conversion based on scale source (SKU) and user unit preference.
   * For scale entries (0220/0222), applies graduation rounding.
   * For manual entries, uses simple conversion.
   *
   * @param decigrams Raw weight in decigrams
   * @param source Entry source (e.g., "0220", "0222", "manual")
   * @param isMetric Whether user prefers metric units
   * @return Pair(pounds, ounces) for imperial or Pair(kg-whole, kg-fraction) for metric
   */
  fun convertBabyWeightToDisplay(decigrams: Int, source: String?, isMetric: Boolean): String {
    if (source != null && (source.contains("0220") || source.contains("0222"))) {
      return if (isMetric) {
        val kg = convert0220DecigramsToKg(decigrams)
        formatKg(kg)
      } else {
        val (lbs, oz) = if (source.contains("0222")) {
          convert0222DecigramsToLbOz(decigrams)
        } else {
          convert0220DecigramsToLbOz(decigrams)
        }
        formatLbOz(lbs, oz)
      }
    }
    // Manual or unknown source — simple conversion
    return if (isMetric) {
      formatKg(convertDecigramsToKg(decigrams))
    } else {
      formatLbOz(convertDecigramsToLb(decigrams), convertDecigramsToOz(decigrams))
    }
  }

  /**
   * Routes baby length conversion based on user unit preference.
   * @param millimeters Raw length in millimeters
   * @param isMetric Whether user prefers metric units
   * @return Formatted length string
   */
  fun convertBabyLengthToDisplay(millimeters: Int, isMetric: Boolean): String {
    return if (isMetric) {
      "${String.format(Locale.US, "%.1f", convertMmToCm(millimeters))} cm"
    } else {
      "${String.format(Locale.US, "%.0f", convertMmToInches(millimeters))} in"
    }
  }

  private fun formatLbOz(lbs: Int, oz: Double): String =
      "$lbs lbs ${String.format(Locale.US, "%.1f", oz)} oz"

  private fun formatKg(kg: Double): String =
      "${String.format(Locale.US, "%.2f", kg)} kg"

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
