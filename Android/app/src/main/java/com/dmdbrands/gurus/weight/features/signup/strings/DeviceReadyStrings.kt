package com.dmdbrands.gurus.weight.features.signup.strings

/**
 * Strings for the per-device and aggregate success screens shown at the
 * tail of the multi-device signup flow (MA-3825).
 */
object DeviceReadyStrings {
    const val finish = "GET STARTED"
    const val connectAnother = "CONNECT ANOTHER DEVICE"
    const val allDevicesTitle = "All your health profiles have been set up successfully!"

    fun deviceTitle(deviceName: String): String = "Your $deviceName profile is ready!"
}
