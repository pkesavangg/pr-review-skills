package com.greatergoods.libs.appsync.config

/**
 * Constants used throughout the AppSync module.
 *
 * This object contains all the configuration constants used by the AppSync library,
 * including camera settings, FS003 protocol parameters, error thresholds, and
 * measurement modes. These constants are organized into logical groups for better
 * maintainability and understanding.
 *
 * The constants are used across various components of the library to ensure
 * consistent behavior and configuration.
 */
object AppSyncConstants {
    // ============================================================================
    // Camera focus settings
    // ============================================================================

    /**
     * Half-extent (per axis, as a fraction of the sensor active array) of the centre
     * AF/AE metering region used while scanning.
     *
     * MOB-869: focus/exposure are driven continuously and biased onto the centre of the
     * frame, where the targeting overlay frames the scale display. 0.15 yields a central
     * box covering ~30% of each axis — large enough to tolerate framing wobble, small
     * enough to keep the bright display (rather than the background) the metering subject.
     */
    const val CENTER_REGION_HALF_FRACTION = 0.15f

    // ============================================================================
    // Camera zoom settings
    // ============================================================================

    /**
     * Minimum zoom level allowed for the camera.
     *
     * This represents the closest zoom level (1.0x magnification).
     */
    const val MIN_ZOOM = 1.0f

    /**
     * Maximum zoom level allowed for the camera.
     *
     * This represents the farthest zoom level (5.0x magnification).
     */
    const val MAX_ZOOM = 5.0f

    /**
     * Step size for zoom level changes.
     *
     * When the user adjusts zoom, it changes by this increment.
     */
    const val ZOOM_STEP = 0.5f

    /**
     * Default zoom level when starting a scan.
     *
     * This is the initial zoom level used when no specific zoom is requested.
     */
    const val DEFAULT_ZOOM = 1

    // ============================================================================
    // Zoom animation settings
    // ============================================================================

    /**
     * Duration of zoom animation in milliseconds.
     *
     * This controls how long the zoom transition animation takes.
     */
    const val ZOOM_ANIMATION_DURATION = 300L // 300ms animation

    /**
     * Number of steps used for smooth zoom animation.
     *
     * Higher values result in smoother animations but more processing.
     */
    const val ZOOM_ANIMATION_STEPS = 30 // 30 steps for smooth animation

    /**
     * Minimum change in zoom level to trigger animation.
     *
     * Small changes below this threshold are applied immediately without animation.
     */
    const val ZOOM_CHANGE_THRESHOLD = 0.01f // Minimum change to trigger animation

    // ============================================================================
    // FS003 protocol constants
    // ============================================================================

    /**
     * Size of Hamming code blocks used in FS003 protocol.
     *
     * This defines the block size for error correction in the protocol.
     */
    const val HAMMING_BLOCK_SIZE = 7

    /**
     * Size of the data array in FS003 protocol.
     *
     * This defines the number of data elements in the protocol message.
     */
    const val DATA_ARRAY_SIZE = 10

    // Weight extraction constants

    /**
     * First shift value for weight extraction from FS003 data.
     */
    const val WEIGHT_SHIFT_1 = 4

    /**
     * Second shift value for weight extraction from FS003 data.
     */
    const val WEIGHT_SHIFT_2 = 8

    /**
     * Right shift value for weight extraction from FS003 data.
     */
    const val WEIGHT_RIGHT_SHIFT = 1

    /**
     * Divisor used to convert raw weight data to kilograms.
     */
    const val WEIGHT_DIVISOR = 10f

    // Fat extraction constants

    /**
     * First shift value for fat extraction from FS003 data.
     */
    const val FAT_SHIFT_1 = 4

    /**
     * Second shift value for fat extraction from FS003 data.
     */
    const val FAT_SHIFT_2 = 8

    /**
     * Third shift value for fat extraction from FS003 data.
     */
    const val FAT_SHIFT_3 = 12

    /**
     * Right shift value for fat extraction from FS003 data.
     */
    const val FAT_RIGHT_SHIFT = 3

    /**
     * Bit mask for fat data extraction.
     */
    const val FAT_MASK = 0x3ff

    /**
     * Divisor used to convert raw fat data to percentage.
     */
    const val FAT_DIVISOR = 10f

    /**
     * Invalid value indicator for fat data.
     *
     * When fat data equals this value, it indicates invalid or missing data.
     */
    const val FAT_INVALID_VALUE = 0x3ff

    // Muscle extraction constants

    /**
     * First shift value for muscle extraction from FS003 data.
     */
    const val MUSCLE_SHIFT_1 = 4

    /**
     * Second shift value for muscle extraction from FS003 data.
     */
    const val MUSCLE_SHIFT_2 = 8

    /**
     * Right shift value for muscle extraction from FS003 data.
     */
    const val MUSCLE_RIGHT_SHIFT = 2

    /**
     * Bit mask for muscle data extraction.
     */
    const val MUSCLE_MASK = 0x1ff

    /**
     * Divisor used to convert raw muscle data to percentage.
     */
    const val MUSCLE_DIVISOR = 10f

    /**
     * Base value added to muscle percentage calculations.
     *
     * This is added to the extracted muscle value to get the final percentage.
     */
    const val MUSCLE_BASE_VALUE = 14.9f

    /**
     * Invalid value indicator for muscle data.
     *
     * When muscle data equals this value, it indicates invalid or missing data.
     */
    const val MUSCLE_INVALID_VALUE = 0x1ff

    // Water extraction constants

    /**
     * First right shift value for water extraction from FS003 data.
     */
    const val WATER_RIGHT_SHIFT_1 = 1

    /**
     * First shift value for water extraction from FS003 data.
     */
    const val WATER_SHIFT_1 = 1

    /**
     * Second shift value for water extraction from FS003 data.
     */
    const val WATER_SHIFT_2 = 5

    /**
     * Bit mask for water data extraction.
     */
    const val WATER_MASK = 0x7f

    /**
     * Base value added to water percentage calculations.
     *
     * This is added to the extracted water value to get the final percentage.
     */
    const val WATER_BASE_VALUE = 18f

    /**
     * Divisor used to convert raw water data to percentage.
     */
    const val WATER_DIVISOR = 2f

    /**
     * Invalid value indicator for water data.
     *
     * When water data equals this value, it indicates invalid or missing data.
     */
    const val WATER_INVALID_VALUE = 0x7f

    // Mode extraction constants

    /**
     * Bit mask for mode data extraction.
     *
     * This extracts the measurement mode (kg/lb) from the FS003 data.
     */
    const val MODE_MASK = 0x1

    // ============================================================================
    // Error thresholds
    // ============================================================================

    /**
     * Maximum number of errors allowed for a scan to be considered valid.
     *
     * If the total number of errors exceeds this threshold, the scan is
     * considered invalid and the results are discarded.
     */
    const val MAX_ERRORS_FOR_INVALID_SCAN = 77

    // ============================================================================
    // Hamming code constants
    // ============================================================================

    /**
     * Constants related to Hamming code error correction.
     *
     * This object contains the matrices and constants used for Hamming code
     * encoding and decoding in the FS003 protocol.
     */
    object Hamming {
        /**
         * H-matrix used for Hamming code encoding.
         *
         * This matrix is used to encode data with error correction capabilities.
         */
        val H_MATRIX = intArrayOf(0x55, 0x66, 0x78)

        /**
         * Matrix used for extracting data from Hamming-encoded blocks.
         *
         * This matrix is used to decode data and extract the original values.
         */
        val EXTRACTION_MATRIX = intArrayOf(0x04, 0x10, 0x20, 0x40)
    }

    // ============================================================================
    // Measurement modes
    // ============================================================================

    /**
     * Constants related to measurement modes and units.
     *
     * This object contains the valid measurement modes supported by the
     * FS003 protocol and the library.
     */
    object Modes {
        /**
         * Kilograms measurement mode.
         *
         * Indicates that the scale is configured to display weight in kilograms.
         */
        const val KILOGRAMS = "kg"

        /**
         * Pounds measurement mode.
         *
         * Indicates that the scale is configured to display weight in pounds.
         */
        const val POUNDS = "lb"

        /**
         * Array of all valid measurement modes.
         *
         * This array contains all the measurement modes that are considered
         * valid by the library.
         */
        val VALID_MODES = arrayOf(KILOGRAMS, POUNDS)
    }
}
