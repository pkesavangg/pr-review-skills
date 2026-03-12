package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

interface IWiFiConfigManager {
    fun gatherNetworks()
    fun connectToWifi()
    fun handlePasswordNetworkStatus()
    fun clearWifiPasswordForm()
    fun cancelTimeout()
}
