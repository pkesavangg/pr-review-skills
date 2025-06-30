package com.greatergoods.libs.appsync.model

/**
 * Result of an AppSync scan operation.
 *
 * This data class encapsulates all the information returned from a scan operation,
 * including the extracted measurements, error counts, and operation status. The
 * measurements are nullable to handle cases where the scan was unsuccessful or
 * the data could not be extracted properly.
 *
 * The error properties provide detailed information about the quality of the scan
 * and can be used to determine if the results are reliable. A high error count
 * typically indicates poor image quality or invalid data.
 *
 * @property weight Weight in kilograms, null if not available or extraction failed
 * @property fat Percent body fat, null if not available or extraction failed
 * @property muscle Percent muscle, null if not available or extraction failed
 * @property water Percent water, null if not available or extraction failed
 * @property mode Measurement mode (e.g., "kg", "lb"), null if not available
 * @property weightErrors Number of errors encountered during weight extraction
 * @property fatErrors Number of errors encountered during fat extraction
 * @property muscleErrors Number of errors encountered during muscle extraction
 * @property waterErrors Number of errors encountered during water extraction
 * @property modeErrors Number of errors encountered during mode extraction
 * @property errors Total number of errors detected across all measurements
 * @property zoom Final zoom level used during the scan
 * @property canceled True if the user canceled the scan operation
 * @property manual True if the user chose manual entry instead of scanning
 */
data class AppSyncResult(
    /**
     * The weight measurement in kilograms.
     *
     * This value represents the body weight extracted from the scale display.
     * Null if the weight could not be extracted due to poor image quality,
     * invalid data, or scan cancellation.
     */
    val weight: Float?,
    /**
     * The body fat percentage.
     *
     * This value represents the percentage of body fat extracted from the scale display.
     * Null if the fat percentage could not be extracted due to poor image quality,
     * invalid data, or scan cancellation.
     */
    val fat: Float?,
    /**
     * The muscle percentage.
     *
     * This value represents the percentage of muscle mass extracted from the scale display.
     * Null if the muscle percentage could not be extracted due to poor image quality,
     * invalid data, or scan cancellation.
     */
    val muscle: Float?,
    /**
     * The water percentage.
     *
     * This value represents the percentage of body water extracted from the scale display.
     * Null if the water percentage could not be extracted due to poor image quality,
     * invalid data, or scan cancellation.
     */
    val water: Float?,
    /**
     * The measurement mode used by the scale.
     *
     * This indicates the unit system used by the scale, typically "kg" for kilograms
     * or "lb" for pounds. Null if the mode could not be extracted.
     */
    val mode: String?,
    /**
     * Number of errors encountered during weight extraction.
     *
     * Higher values indicate poorer quality weight data. A value of 0 indicates
     * successful extraction with no errors.
     */
    val weightErrors: Int = 0,
    /**
     * Number of errors encountered during fat extraction.
     *
     * Higher values indicate poorer quality fat data. A value of 0 indicates
     * successful extraction with no errors.
     */
    val fatErrors: Int = 0,
    /**
     * Number of errors encountered during muscle extraction.
     *
     * Higher values indicate poorer quality muscle data. A value of 0 indicates
     * successful extraction with no errors.
     */
    val muscleErrors: Int = 0,
    /**
     * Number of errors encountered during water extraction.
     *
     * Higher values indicate poorer quality water data. A value of 0 indicates
     * successful extraction with no errors.
     */
    val waterErrors: Int = 0,
    /**
     * Number of errors encountered during mode extraction.
     *
     * Higher values indicate poorer quality mode data. A value of 0 indicates
     * successful extraction with no errors.
     */
    val modeErrors: Int = 0,
    /**
     * Total number of errors detected across all measurements.
     *
     * This is the sum of all individual error counts. Higher values indicate
     * poorer overall scan quality. A value of 0 indicates a perfect scan.
     */
    val errors: Int = 0,
    /**
     * Final zoom level used during the scan.
     *
     * This indicates the zoom level that was active when the scan completed.
     * Useful for debugging and understanding scan conditions.
     */
    val zoom: Int = 1,
    /**
     * Indicates whether the scan operation was canceled by the user.
     *
     * When true, all measurement values will be null as no scan was completed.
     * This is different from a failed scan, which may have partial data.
     */
    val canceled: Boolean = false,
    /**
     * Indicates whether the user chose manual entry instead of scanning.
     *
     * When true, all measurement values will be null as the user opted to
     * enter data manually rather than scan the scale display.
     */
    val manual: Boolean = false,
)
