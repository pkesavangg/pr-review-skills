package com.greatergoods.libs.appsync

/**
 * Result of an AppSync scan operation.
 *
 * @property weight Weight in kilograms (nullable if not available)
 * @property fat Percent body fat (nullable if not available)
 * @property muscle Percent muscle (nullable if not available)
 * @property water Percent water (nullable if not available)
 * @property mode Measurement mode (e.g., "kg", "lb")
 * @property weightErrors Number of errors in weight extraction
 * @property fatErrors Number of errors in fat extraction
 * @property muscleErrors Number of errors in muscle extraction
 * @property waterErrors Number of errors in water extraction
 * @property modeErrors Number of errors in mode extraction
 * @property errors Total number of errors detected
 * @property zoom Final zoom level used
 * @property canceled True if the operation was canceled
 * @property manual True if manual entry was triggered
 */
data class AppSyncResult(
    val weight: Float?,
    val fat: Float?,
    val muscle: Float?,
    val water: Float?,
    val mode: String?,
    val weightErrors: Int = 0,
    val fatErrors: Int = 0,
    val muscleErrors: Int = 0,
    val waterErrors: Int = 0,
    val modeErrors: Int = 0,
    val errors: Int = 0,
    val zoom: Int = 1,
    val canceled: Boolean = false,
    val manual: Boolean = false,
)
