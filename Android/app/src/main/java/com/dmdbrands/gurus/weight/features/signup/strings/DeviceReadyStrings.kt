package com.dmdbrands.gurus.weight.features.signup.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Strings for the per-device and aggregate success screens shown at the
 * tail of the multi-device signup flow (MA-3825).
 */
object DeviceReadyStrings {

    // Fixed display order for the multi-device success copy — matches the approved Figma
    // annotation, which always lists Blood Pressure Monitor, then Weight Scale, then Baby
    // Scale regardless of the order the user actually completed them. (MOB-1453)
    private val READY_ORDER = listOf(ProductType.BLOOD_PRESSURE, ProductType.MY_WEIGHT, ProductType.BABY)
    const val finish = "GET STARTED"
    const val connectAnother = "CONNECT ANOTHER DEVICE"
    const val allDevicesTitle = "All your health profiles have been set up successfully!"

    fun deviceTitle(deviceName: String): String = "Your $deviceName profile is ready!"

    /**
     * Success title reflecting ALL devices registered so far (MOB-1453), not just the latest.
     * One device -> "Your <device> profile is ready!"; two -> "Your <a> & <b> profiles are
     * ready!" (plural), listed in the fixed [READY_ORDER] and lowercased in the multi-device
     * sentence to match the approved Figma copy. Three devices use [allDevicesTitle] (the
     * ALL_DEVICES_READY step), so this only needs the 1- and 2-device cases.
     */
    fun readyTitle(devices: Set<ProductType>): String {
        val ordered = READY_ORDER.filter { it in devices }
        return when {
            ordered.size <= 1 -> deviceTitle(ordered.firstOrNull()?.displayName.orEmpty())
            else -> "Your ${ordered.joinToString(" & ") { it.displayName.lowercase() }} profiles are ready!"
        }
    }
}
