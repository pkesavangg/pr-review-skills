package com.greatergoods.libs.healthconnect.model

/**
 * Sealed class representing the result of a Health Connect operation.
 */
sealed class HealthConnectResult<out T> {
    data class Success<T>(
        val data: T,
    ) : HealthConnectResult<T>()

    data class Error(
        val error: Throwable,
    ) : HealthConnectResult<Nothing>()
}
