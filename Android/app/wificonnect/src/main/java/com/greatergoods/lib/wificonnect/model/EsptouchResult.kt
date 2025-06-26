package com.greatergoods.lib.wificonnect.model

/**
 * Result of Esptouch smart connect.
 */
sealed class EsptouchResult {
    /** Success result. */
    data class Success(
        val deviceType: String,
        val deviceMac: String,
    ) : EsptouchResult()

    /** Failure result. */
    data class Failure(
        val errorMessage: String,
    ) : EsptouchResult()
}
