package com.greatergoods.lib.wificonnect.model

/**
 * Sealed class representing a WiFi smart connect request.
 */
sealed class WifiConnectRequest {
    data class Esptouch(
        val params: EsptouchParams,
    ) : WifiConnectRequest()

    data class SmartConfig(
        val params: SmartConfigParams,
    ) : WifiConnectRequest()

    data class ApMode(
        val params: ApConnectParams,
    ) : WifiConnectRequest()
}

/**
 * Sealed class representing the result of a WiFi smart connect operation.
 */
sealed class WifiConnectResult {
    data class Esptouch(
        val result: EsptouchResult,
    ) : WifiConnectResult()

    data class SmartConfig(
        val result: SmartConfigResult,
    ) : WifiConnectResult()

    data class ApMode(
        val result: ApConnectResult,
    ) : WifiConnectResult()
}
