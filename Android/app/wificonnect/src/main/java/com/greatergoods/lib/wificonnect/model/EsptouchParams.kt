package com.greatergoods.lib.wificonnect.model

/**
 * Parameters for Esptouch smart connect.
 * @property ssid The SSID of the Wi-Fi network.
 * @property bssid The BSSID of the Wi-Fi network.
 * @property token The token for the connection.
 * @property password The password for the Wi-Fi network.
 * @property userNumber The user number for the connection.
 */
data class EsptouchParams(
    val ssid: String,
    val bssid: String,
    val token: String,
    val password: String,
    val userNumber: Int,
)
