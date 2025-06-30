package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult

/**
 * Factory for creating AppSyncResult objects for different scan scenarios.
 *
 * This object provides a centralized way to create [AppSyncResult] instances
 * for various outcomes of the scanning process. It ensures consistent result
 * creation and reduces code duplication across the scanning components.
 *
 * The factory supports creating results for:
 * - Successful scans with measurement data
 * - User cancellation of the scan
 * - Manual entry selection
 * - Error conditions
 * - Empty/invalid scans
 *
 * Each factory method sets appropriate default values and flags to ensure
 * the result accurately represents the specific scenario.
 */
object AppSyncResultFactory {
    /**
     * Creates a successful scan result with measurement values.
     *
     * This method creates a result object representing a successful scan
     * where valid measurement data was extracted from the FS003 protocol.
     * All measurement values are optional (nullable) to handle cases where
     * some measurements may not be available or valid.
     *
     * @param weight Weight in kilograms, or null if not available
     * @param fat Percent body fat, or null if not available
     * @param muscle Percent muscle, or null if not available
     * @param water Percent water, or null if not available
     * @param mode Measurement mode (e.g., "kg", "lb"), or null if not available
     * @param weightErrors Number of transmission errors detected during weight extraction
     * @param fatErrors Number of transmission errors detected during fat extraction
     * @param muscleErrors Number of transmission errors detected during muscle extraction
     * @param waterErrors Number of transmission errors detected during water extraction
     * @param modeErrors Number of transmission errors detected during mode extraction
     * @param errors Total number of transmission errors across all measurements
     * @param zoom Zoom level used during the scan (1-5)
     * @return [AppSyncResult] with measurement data and error counts
     */
    fun createSuccessResult(
        weight: Float?,
        fat: Float?,
        muscle: Float?,
        water: Float?,
        mode: String?,
        weightErrors: Int = 0,
        fatErrors: Int = 0,
        muscleErrors: Int = 0,
        waterErrors: Int = 0,
        modeErrors: Int = 0,
        errors: Int = 0,
        zoom: Int = AppSyncConstants.DEFAULT_ZOOM,
    ): AppSyncResult =
        AppSyncResult(
            weight = weight,
            fat = fat,
            muscle = muscle,
            water = water,
            mode = mode,
            weightErrors = weightErrors,
            fatErrors = fatErrors,
            muscleErrors = muscleErrors,
            waterErrors = waterErrors,
            modeErrors = modeErrors,
            errors = errors,
            zoom = zoom,
            canceled = false,
            manual = false,
        )

    /**
     * Creates a cancel result when the user cancels the scan.
     *
     * This method creates a result object indicating that the user actively
     * cancelled the scanning process. All measurement values are set to null
     * since no scan was completed, and the [canceled] flag is set to true.
     *
     * @param zoom Zoom level at the time of cancellation (1-5)
     * @return [AppSyncResult] indicating user cancellation with no measurements
     */
    fun createCancelResult(zoom: Int = AppSyncConstants.DEFAULT_ZOOM): AppSyncResult =
        AppSyncResult(
            weight = null,
            fat = null,
            muscle = null,
            water = null,
            mode = null,
            zoom = zoom,
            canceled = true,
            manual = false,
        )

    /**
     * Creates a manual entry result when the user chooses manual entry.
     *
     * This method creates a result object indicating that the user chose
     * to manually enter measurement data instead of completing a scan.
     * All measurement values are set to null since no scan was performed,
     * and the [manual] flag is set to true.
     *
     * @param zoom Zoom level at the time of manual entry selection (1-5)
     * @return [AppSyncResult] indicating manual entry selection with no measurements
     */
    fun createManualEntryResult(zoom: Int = AppSyncConstants.DEFAULT_ZOOM): AppSyncResult =
        AppSyncResult(
            weight = null,
            fat = null,
            muscle = null,
            water = null,
            mode = null,
            zoom = zoom,
            canceled = false,
            manual = true,
        )

    /**
     * Creates an error result when the scan fails.
     *
     * This method creates a result object indicating that the scan failed
     * due to an error condition. All measurement values are set to null
     * since no valid data was extracted, and the total error count is set
     * to -1 to indicate an error state.
     *
     * @param errorMessage Optional error message describing the failure
     * @param zoom Zoom level at the time of the error (1-5)
     * @return [AppSyncResult] indicating scan failure with no measurements
     */
    fun createErrorResult(
        errorMessage: String? = null,
        zoom: Int = AppSyncConstants.DEFAULT_ZOOM,
    ): AppSyncResult =
        AppSyncResult(
            weight = null,
            fat = null,
            muscle = null,
            water = null,
            mode = null,
            errors = -1, // Indicates error state
            zoom = zoom,
            canceled = false,
            manual = false,
        )

    /**
     * Creates an empty result with no measurements.
     *
     * This method creates a result object with no measurement data.
     * This is typically used for initialization or when a scan completes
     * but no valid data was extracted. All measurement values are set
     * to null and error counts are set to 0.
     *
     * @param zoom Zoom level used during the scan (1-5)
     * @return [AppSyncResult] with no measurements and zero error counts
     */
    fun createEmptyResult(zoom: Int = AppSyncConstants.DEFAULT_ZOOM): AppSyncResult =
        AppSyncResult(
            weight = null,
            fat = null,
            muscle = null,
            water = null,
            mode = null,
            zoom = zoom,
            canceled = false,
            manual = false,
        )
}
