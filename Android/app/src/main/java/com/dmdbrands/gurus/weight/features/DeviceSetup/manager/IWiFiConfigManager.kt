package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

interface IWiFiConfigManager {
    fun gatherNetworks()
    fun connectToWifi()
    fun handlePasswordNetworkStatus()
    fun clearWifiPasswordForm()
    fun cancelTimeout()
}
