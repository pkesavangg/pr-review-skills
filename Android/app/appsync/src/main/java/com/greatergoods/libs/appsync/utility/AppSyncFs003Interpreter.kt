package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.AppSyncLogger
import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.strings.AppSyncStrings

/**
 * Interpreter for FS003 protocol data from smart scales.
 *
 * This object is responsible for decoding the raw bit arrays received from the native
 * detector into meaningful measurement values. The FS003 protocol is a proprietary
 * protocol used by certain smart scales to encode weight, body fat, muscle, and water
 * measurements in a specific bit pattern.
 *
 * The interpretation process involves:
 * 1. Hamming code decoding to correct transmission errors
 * 2. Bit extraction and assembly for each measurement type
 * 3. Value conversion using protocol-specific constants
 * 4. Validation of extracted values
 * 5. Error counting and result creation
 *
 * The interpreter handles various edge cases including invalid measurements,
 * transmission errors, and protocol-specific data formats.
 */
object AppSyncFs003Interpreter {
  private const val TAG = "AppSyncFs003Interpreter"

  /**
   * Interprets a bit array from the native detector into measurement values.
   *
   * This is the main entry point for FS003 protocol interpretation. It takes
   * the raw bit array detected by the native image processing code and converts
   * it into a structured [AppSyncResult] containing all the extracted measurements.
   *
   * The interpretation process includes error correction using Hamming codes,
   * extraction of individual measurement values, validation of the results,
   * and creation of the final result object.
   *
   * @param bits Array of bits (0s and 1s) from the native detector representing
   *             the FS003 protocol data pattern
   * @param currentZoom The zoom level the camera was at when this frame was captured.
   *                    Reported back on the result so callers can persist the user's
   *                    last-used zoom (MA-1178). Clamped to [AppSyncConstants.MIN_ZOOM]..[AppSyncConstants.MAX_ZOOM].
   * @return [AppSyncResult] with decoded measurement values, or null if the
   *         bit array is empty or contains invalid data
   */
  fun interpret(bits: IntArray, currentZoom: Int = AppSyncConstants.DEFAULT_ZOOM): AppSyncResult? {
    // Validate input
    if (bits.isEmpty()) {
      AppSyncLogger.w(TAG, AppSyncStrings.EmptyBitArrayReceived)
      return null
    }

    // Decode Hamming-encoded data and extract measurements
    val (data, errorsFound) = decodeHammingData(bits)
    val measurements = extractMeasurements(data, errorsFound)

    // Validate the extracted measurements
    if (isInvalidScan(measurements)) {
      AppSyncLogger.w(TAG, AppSyncStrings.InvalidScanDetected)
      return null
    }

    // Create and return the final result using the factory pattern
    return createResult(measurements, errorsFound, currentZoom)
  }

  /**
   * Decodes Hamming-encoded data from the raw bit array.
   *
   * Processes the input bits in blocks of [AppSyncConstants.HAMMING_BLOCK_SIZE],
   * padding with zeros if the last block is incomplete. Always returns arrays of size [AppSyncConstants.DATA_ARRAY_SIZE].
   *
   * @param bits Raw bit array from the native detector
   * @return Pair of:
   *   - Decoded data array
   *   - Error flags array
   */
  private fun decodeHammingData(bits: IntArray): Pair<IntArray, IntArray> {
    val data = IntArray(AppSyncConstants.DATA_ARRAY_SIZE)
    val errorsFound = IntArray(AppSyncConstants.DATA_ARRAY_SIZE)
    var bitIndex = 0

    for (dataIndex in 0 until AppSyncConstants.DATA_ARRAY_SIZE) {
      var hammingCode = 0
      for (k in 0 until AppSyncConstants.HAMMING_BLOCK_SIZE) {
        val bitValue = if (bitIndex < bits.size && bits[bitIndex] == 1) 1 else 0
        hammingCode = hammingCode or (bitValue shl k)
        bitIndex++
      }
      data[dataIndex] = AppSyncHammingDecoder.extractData(hammingCode)
      errorsFound[dataIndex] = if (AppSyncHammingDecoder.wasErrorFoundInLastCorrection()) 1 else 0
    }
    return Pair(data, errorsFound)
  }

  /**
   * Extracts all measurement values from the decoded data array.
   *
   * This function coordinates the extraction of all measurement types from
   * the decoded data array. Each measurement type has its own extraction
   * function that handles the specific bit patterns and conversion formulas
   * defined by the FS003 protocol.
   *
   * @param data Decoded data array containing raw measurement values
   * @param errorsFound Array indicating which data blocks had transmission errors
   * @return [MeasurementData] object containing all extracted measurement values
   */
  private fun extractMeasurements(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementData {
    val weight = extractWeight(data, errorsFound)
    val fat = extractFat(data, errorsFound)
    val muscle = extractMuscle(data, errorsFound)
    val water = extractWater(data, errorsFound)
    val mode = extractMode(data, errorsFound)

    return MeasurementData(weight, fat, muscle, water, mode)
  }

  /**
   * Extracts weight value from the decoded data array.
   *
   * Weight is stored across multiple data positions in the FS003 protocol.
   * This function assembles the weight value by combining bits from positions
   * 0, 1, and 2 using specific shift operations and applies the protocol's
   * conversion formula.
   *
   * @param data Decoded data array
   * @param errorsFound Array indicating transmission errors
   * @return [MeasurementValue] containing the weight in kilograms or null if invalid
   */
  private fun extractWeight(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementValue {
    // Assemble weight from multiple data positions
    var weight = data[2]
    weight = weight or (data[1] shl AppSyncConstants.WEIGHT_SHIFT_1)
    weight = weight or (data[0] shl AppSyncConstants.WEIGHT_SHIFT_2)
    weight = weight ushr AppSyncConstants.WEIGHT_RIGHT_SHIFT

    // Convert to actual weight value using protocol divisor
    val value = weight * 1f / AppSyncConstants.WEIGHT_DIVISOR

    // Count errors from all weight-related data positions
    val errors = errorsFound[1] + errorsFound[2] + errorsFound[0]

    return MeasurementValue(
      value = if (value > 0) value else null,
      errors = errors,
    )
  }

  /**
   * Extracts body fat percentage from the decoded data array.
   *
   * Body fat is stored across multiple data positions and includes special
   * handling for invalid values. The extraction process involves bit shifting,
   * masking, and validation against protocol-specific constants.
   *
   * @param data Decoded data array
   * @param errorsFound Array indicating transmission errors
   * @return [MeasurementValue] containing the body fat percentage or null if invalid
   */
  private fun extractFat(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementValue {
    // Assemble fat value from multiple data positions
    var fat = data[5]
    fat = fat or (data[4] shl AppSyncConstants.FAT_SHIFT_1)
    fat = fat or (data[3] shl AppSyncConstants.FAT_SHIFT_2)
    fat = fat or (data[2] shl AppSyncConstants.FAT_SHIFT_3)
    fat = fat ushr AppSyncConstants.FAT_RIGHT_SHIFT
    fat = fat and AppSyncConstants.FAT_MASK

    // Convert to percentage and handle invalid values
    var value = fat * 1f / AppSyncConstants.FAT_DIVISOR
    if (fat == AppSyncConstants.FAT_INVALID_VALUE || fat == 0) {
      value = -1f
    }

    // Count errors from all fat-related data positions
    val errors = errorsFound[2] + errorsFound[3] + errorsFound[4] + errorsFound[5]

    return MeasurementValue(
      value = if (value > 0) value else null,
      errors = errors,
    )
  }

  /**
   * Extracts muscle percentage from the decoded data array.
   *
   * Muscle percentage uses a base value plus an offset from the protocol data.
   * The extraction process involves bit shifting, masking, and validation
   * against protocol-specific constants.
   *
   * @param data Decoded data array
   * @param errorsFound Array indicating transmission errors
   * @return [MeasurementValue] containing the muscle percentage or null if invalid
   */
  private fun extractMuscle(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementValue {
    // Assemble muscle value from multiple data positions
    var muscle = data[7]
    muscle = muscle or (data[6] shl AppSyncConstants.MUSCLE_SHIFT_1)
    muscle = muscle or (data[5] shl AppSyncConstants.MUSCLE_SHIFT_2)
    muscle = muscle ushr AppSyncConstants.MUSCLE_RIGHT_SHIFT
    muscle = muscle and AppSyncConstants.MUSCLE_MASK

    // Convert to percentage using base value and divisor
    var value = AppSyncConstants.MUSCLE_BASE_VALUE + muscle * 1f / AppSyncConstants.MUSCLE_DIVISOR
    if (muscle == AppSyncConstants.MUSCLE_INVALID_VALUE || muscle == 0) {
      value = -1f
    }

    // Count errors from all muscle-related data positions
    val errors = errorsFound[7] + errorsFound[6] + errorsFound[5]

    return MeasurementValue(
      value = if (value > 0) value else null,
      errors = errors,
    )
  }

  /**
   * Extracts water percentage from the decoded data array.
   *
   * Water percentage uses a base value plus an offset from the protocol data.
   * The extraction process involves bit shifting, masking, and validation
   * against protocol-specific constants.
   *
   * @param data Decoded data array
   * @param errorsFound Array indicating transmission errors
   * @return [MeasurementValue] containing the water percentage or null if invalid
   */
  private fun extractWater(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementValue {
    // Assemble water value from multiple data positions
    var water = data[9] shr AppSyncConstants.WATER_RIGHT_SHIFT_1
    water = water or (data[8] shl AppSyncConstants.WATER_SHIFT_1)
    water = water or (data[7] shl AppSyncConstants.WATER_SHIFT_2)
    water = water and AppSyncConstants.WATER_MASK

    // Convert to percentage using base value and divisor
    var value = AppSyncConstants.WATER_BASE_VALUE + water.toFloat() / AppSyncConstants.WATER_DIVISOR
    if (water == AppSyncConstants.WATER_INVALID_VALUE || water == 0) {
      value = -1f
    }

    // Count errors from all water-related data positions
    val errors = errorsFound[9] + errorsFound[8] + errorsFound[7]

    return MeasurementValue(
      value = if (value > 0) value else null,
      errors = errors,
    )
  }

  /**
   * Extracts measurement mode from the decoded data array.
   *
   * The measurement mode indicates the type of measurement being performed
   * (e.g., "kg", "lb", etc.). This is stored as a string value rather than
   * a numeric value.
   *
   * @param data Decoded data array
   * @param errorsFound Array indicating transmission errors
   * @return [MeasurementValue] containing the mode string or null if invalid
   */
  private fun extractMode(
    data: IntArray,
    errorsFound: IntArray,
  ): MeasurementValue {
    // Extract mode bits and look up corresponding string
    val unit = data[9] and AppSyncConstants.MODE_MASK
    val modeStr = AppSyncConstants.Modes.VALID_MODES.getOrNull(unit)
    val errors = errorsFound[9]

    return MeasurementValue(
      value = null, // Mode is a string, not a float
      stringValue = modeStr,
      errors = errors,
    )
  }

  /**
   * Checks if the scan result represents an invalid measurement.
   *
   * This function validates the extracted measurements against known
   * invalid patterns. An invalid scan typically has zero weight and
   * negative values for body composition measurements.
   *
   * @param measurements The extracted measurement data
   * @return true if the scan is invalid, false otherwise
   */
  private fun isInvalidScan(measurements: MeasurementData): Boolean =
    measurements.weight.value == 0.0f &&
      measurements.fat.value == -1f &&
      measurements.muscle.value == -1f &&
      measurements.water.value == -1f

  /**
   * Creates the final [AppSyncResult] from extracted measurement data using the factory pattern.
   *
   * This function assembles all the extracted measurements and error counts
   * into the final result object using [AppSyncResultFactory.createSuccessResult()].
   * It includes special handling for certain invalid scan patterns and sets
   * appropriate default values.
   *
   * @param measurements The extracted measurement data
   * @param errorsFound Array indicating transmission errors
   * @param currentZoom The zoom level used during the scan (reported back on the result).
   * @return Complete [AppSyncResult] object ready for delivery
   */
  private fun createResult(
    measurements: MeasurementData,
    errorsFound: IntArray,
    currentZoom: Int,
  ): AppSyncResult {
    var totalErrors = errorsFound.sum()

    // Special case for invalid scans - set maximum error count
    if (measurements.weight.value == 0.0f &&
      measurements.fat.value == 0.0f &&
      measurements.muscle.value == 14.9f
    ) {
      totalErrors = AppSyncConstants.MAX_ERRORS_FOR_INVALID_SCAN
    }

    // Use the factory pattern to create the result object
    return AppSyncResultFactory.createSuccessResult(
      weight = measurements.weight.value,
      fat = measurements.fat.value,
      muscle = measurements.muscle.value,
      water = measurements.water.value,
      mode = measurements.mode.stringValue,
      weightErrors = measurements.weight.errors,
      fatErrors = measurements.fat.errors,
      muscleErrors = measurements.muscle.errors,
      waterErrors = measurements.water.errors,
      modeErrors = measurements.mode.errors,
      errors = totalErrors,
      zoom = currentZoom.coerceIn(AppSyncConstants.MIN_ZOOM.toInt(), AppSyncConstants.MAX_ZOOM.toInt()),
    )
  }

  /**
   * Data class to hold measurement values and their associated metadata.
   *
   * This class encapsulates a single measurement value along with its
   * error count and optional string representation (for modes).
   *
   * @param value The numeric measurement value, or null if invalid
   * @param stringValue Optional string representation (used for modes)
   * @param errors Number of transmission errors detected for this measurement
   */
  private data class MeasurementValue(
    val value: Float?,
    val stringValue: String? = null,
    val errors: Int = 0,
  )

  /**
   * Data class to hold all extracted measurement data.
   *
   * This class groups together all the individual measurement values
   * extracted from the FS003 protocol data for easier processing.
   *
   * @param weight Weight measurement in kilograms
   * @param fat Body fat percentage
   * @param muscle Muscle percentage
   * @param water Water percentage
   * @param mode Measurement mode (e.g., "kg", "lb")
   */
  private data class MeasurementData(
    val weight: MeasurementValue,
    val fat: MeasurementValue,
    val muscle: MeasurementValue,
    val water: MeasurementValue,
    val mode: MeasurementValue,
  )
}
