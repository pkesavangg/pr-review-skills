package com.dmdbrands.gurus.weight.features.ScaleSetup.manager

import com.dmdbrands.library.ggbluetooth.model.GGScanResponse

interface IBLEDiscoveryManager {
    fun startPairing()
    fun cancelPairing()
    fun handleScanResponse(response: GGScanResponse.DeviceDetail)
}
