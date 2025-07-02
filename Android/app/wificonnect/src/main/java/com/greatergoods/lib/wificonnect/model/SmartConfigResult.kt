package com.greatergoods.lib.wificonnect.model

/**
 * Result of SmartConfig.
 */
sealed class SmartConfigResult {
    object Success : SmartConfigResult()

    data class Failure(
        val errorMessage: String,
    ) : SmartConfigResult()
}
