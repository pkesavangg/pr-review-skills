package com.greatergoods.libs.appsync.utility

import com.greatergoods.libs.appsync.config.AppSyncConstants
import com.greatergoods.libs.appsync.model.AppSyncResult

/**
 * Factory for creating AppSyncResult objects.
 * Provides methods to create different types of results (success, cancel, manual, error).
 */
object AppSyncResultFactory {
    /**
     * Creates a successful scan result with measurement values.
     *
     * @param weight Weight in kilograms
     * @param fat Percent body fat
     * @param muscle Percent muscle
     * @param water Percent water
     * @param mode Measurement mode (kg/lb)
     * @param weightErrors Number of errors in weight extraction
     * @param fatErrors Number of errors in fat extraction
     * @param muscleErrors Number of errors in muscle extraction
     * @param waterErrors Number of errors in water extraction
     * @param modeErrors Number of errors in mode extraction
     * @param errors Total number of errors
     * @param zoom Zoom level used
     * @return AppSyncResult with measurement data
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
     * @param zoom Zoom level at time of cancellation
     * @return AppSyncResult indicating cancellation
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
     * @param zoom Zoom level at time of manual entry selection
     * @return AppSyncResult indicating manual entry
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
     * @param errorMessage Optional error message
     * @param zoom Zoom level at time of error
     * @return AppSyncResult indicating error
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
     * @param zoom Zoom level
     * @return AppSyncResult with null measurements
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
