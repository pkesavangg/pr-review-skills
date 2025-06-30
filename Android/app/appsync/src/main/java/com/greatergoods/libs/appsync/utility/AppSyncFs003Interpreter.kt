package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.libs.appsync.strings.AppSyncStrings
import android.util.Log

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
     * @return [AppSyncResult] with decoded measurement values, or null if the
     *         bit array is empty or contains invalid data
     */
    fun interpret(bits: IntArray): AppSyncResult? {
        // Validate input
        if (bits.isEmpty()) {
            Log.w(TAG, AppSyncStrings.EmptyBitArrayReceived)
            return null
        }

        // Decode Hamming-encoded data and extract measurements
        val (data, errorsFound) = decodeHammingData(bits)
        val measurements = extractMeasurements(data, errorsFound)

        // Validate the extracted measurements
        if (isInvalidScan(measurements)) {
            Log.w(TAG, AppSyncStrings.InvalidScanDetected)
            return null
        }

        // Create and return the final result
        return createResult(measurements, errorsFound)
    }

    /**
     * Decodes Hamming-encoded data from the bit array.
     *
     * @param bits Raw bit array from detector
     * @return Pair of decoded data array and error flags array
     */
    private fun decodeHammingData(bits: IntArray): Pair<IntArray, IntArray> {
        val data = IntArray(AppSyncConstants.DATA_ARRAY_SIZE)
        val errorsFound = IntArray(AppSyncConstants.DATA_ARRAY_SIZE)

        var dataIndex = 0
        var bitIndex = 0

        while (dataIndex < AppSyncConstants.DATA_ARRAY_SIZE &&
            bitIndex + AppSyncConstants.HAMMING_BLOCK_SIZE <= bits.size
        ) {
            var hammingCode = 0
            for (k in 0 until AppSyncConstants.HAMMING_BLOCK_SIZE) {
                val bitValue = if (bitIndex < bits.size && bits[bitIndex] == 1) 1 else 0
                hammingCode = hammingCode or (bitValue shl k)
                bitIndex++
            }

            data[dataIndex] = AppSyncHammingDecoder.extractData(hammingCode)
            errorsFound[dataIndex] = if (AppSyncHammingDecoder.wasErrorFoundInLastCorrection()) 1 else 0
            dataIndex++
        }

        return Pair(data, errorsFound)
    }

    /**
     * Extracts measurement values from decoded data.
     *
     * @param data Decoded data array
     * @param errorsFound Error flags array
     * @return MeasurementData object with all extracted values
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
     * Extracts weight value from data array.
     */
    private fun extractWeight(
        data: IntArray,
        errorsFound: IntArray,
    ): MeasurementValue {
        var weight = data[2]
        weight = weight or (data[1] shl AppSyncConstants.WEIGHT_SHIFT_1)
        weight = weight or (data[0] shl AppSyncConstants.WEIGHT_SHIFT_2)
        weight = weight ushr AppSyncConstants.WEIGHT_RIGHT_SHIFT

        val value = weight * 1f / AppSyncConstants.WEIGHT_DIVISOR
        val errors = errorsFound[1] + errorsFound[2] + errorsFound[0]

        return MeasurementValue(
            value = if (value > 0) value else null,
            errors = errors,
        )
    }

    /**
     * Extracts fat percentage from data array.
     */
    private fun extractFat(
        data: IntArray,
        errorsFound: IntArray,
    ): MeasurementValue {
        var fat = data[5]
        fat = fat or (data[4] shl AppSyncConstants.FAT_SHIFT_1)
        fat = fat or (data[3] shl AppSyncConstants.FAT_SHIFT_2)
        fat = fat or (data[2] shl AppSyncConstants.FAT_SHIFT_3)
        fat = fat ushr AppSyncConstants.FAT_RIGHT_SHIFT
        fat = fat and AppSyncConstants.FAT_MASK

        var value = fat * 1f / AppSyncConstants.FAT_DIVISOR
        if (fat == AppSyncConstants.FAT_INVALID_VALUE || fat == 0) {
            value = -1f
        }

        val errors = errorsFound[2] + errorsFound[3] + errorsFound[4] + errorsFound[5]

        return MeasurementValue(
            value = if (value > 0) value else null,
            errors = errors,
        )
    }

    /**
     * Extracts muscle percentage from data array.
     */
    private fun extractMuscle(
        data: IntArray,
        errorsFound: IntArray,
    ): MeasurementValue {
        var muscle = data[7]
        muscle = muscle or (data[6] shl AppSyncConstants.MUSCLE_SHIFT_1)
        muscle = muscle or (data[5] shl AppSyncConstants.MUSCLE_SHIFT_2)
        muscle = muscle ushr AppSyncConstants.MUSCLE_RIGHT_SHIFT
        muscle = muscle and AppSyncConstants.MUSCLE_MASK

        var value = AppSyncConstants.MUSCLE_BASE_VALUE + muscle * 1f / AppSyncConstants.MUSCLE_DIVISOR
        if (muscle == AppSyncConstants.MUSCLE_INVALID_VALUE || muscle == 0) {
            value = -1f
        }

        val errors = errorsFound[7] + errorsFound[6] + errorsFound[5]

        return MeasurementValue(
            value = if (value > 0) value else null,
            errors = errors,
        )
    }

    /**
     * Extracts water percentage from data array.
     */
    private fun extractWater(
        data: IntArray,
        errorsFound: IntArray,
    ): MeasurementValue {
        var water = data[9] shr AppSyncConstants.WATER_RIGHT_SHIFT_1
        water = water or (data[8] shl AppSyncConstants.WATER_SHIFT_1)
        water = water or (data[7] shl AppSyncConstants.WATER_SHIFT_2)
        water = water and AppSyncConstants.WATER_MASK

        var value = AppSyncConstants.WATER_BASE_VALUE + water.toFloat() / AppSyncConstants.WATER_DIVISOR
        if (water == AppSyncConstants.WATER_INVALID_VALUE || water == 0) {
            value = -1f
        }

        val errors = errorsFound[9] + errorsFound[8] + errorsFound[7]

        return MeasurementValue(
            value = if (value > 0) value else null,
            errors = errors,
        )
    }

    /**
     * Extracts measurement mode from data array.
     */
    private fun extractMode(
        data: IntArray,
        errorsFound: IntArray,
    ): MeasurementValue {
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
     * Checks if the scan result is invalid.
     */
    private fun isInvalidScan(measurements: MeasurementData): Boolean =
        measurements.weight.value == 0.0f &&
            measurements.fat.value == -1f &&
            measurements.muscle.value == -1f &&
            measurements.water.value == -1f

    /**
     * Creates AppSyncResult from measurement data.
     */
    private fun createResult(
        measurements: MeasurementData,
        errorsFound: IntArray,
    ): AppSyncResult {
        var totalErrors = errorsFound.sum()

        // Special case for invalid scans
        if (measurements.weight.value == 0.0f &&
            measurements.fat.value == 0.0f &&
            measurements.muscle.value == 14.9f
        ) {
            totalErrors = AppSyncConstants.MAX_ERRORS_FOR_INVALID_SCAN
        }

        return AppSyncResult(
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
            zoom = AppSyncConstants.DEFAULT_ZOOM,
            canceled = false,
            manual = false,
        )
    }

    /**
     * Data class to hold measurement values and their error counts.
     */
    private data class MeasurementValue(
        val value: Float?,
        val stringValue: String? = null,
        val errors: Int = 0,
    )

    /**
     * Data class to hold all measurement data.
     */
    private data class MeasurementData(
        val weight: MeasurementValue,
        val fat: MeasurementValue,
        val muscle: MeasurementValue,
        val water: MeasurementValue,
        val mode: MeasurementValue,
    )
}
