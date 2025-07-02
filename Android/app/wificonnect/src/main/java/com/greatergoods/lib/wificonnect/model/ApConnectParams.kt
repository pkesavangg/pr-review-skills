package com.greatergoods.lib.wificonnect.model

/**
 * Parameters for AP mode smart connect.
 * @property ssid The SSID of the Wi-Fi network.
 * @property password The password for the Wi-Fi network.
 * @property userNumber The user number for the connection.
 * @property tokenHexString The token as a hex string.
 */
data class ApConnectParams(
    val ssid: String,
    val password: String,
    val userNumber: Int,
    val tokenHexString: String,
)
