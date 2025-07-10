package com.greatergoods.meapp.domain.model.permission

/**
 * Enum representing different types of permissions that can be requested.
 * Maps to the GGPermissionType constants from the bluetooth connect plugin.
 */
enum class PermissionType(val value: String) {
    /** Bluetooth permission for iOS devices */
    BLUETOOTH("BLUETOOTH"),

    /** Bluetooth switch permission to enable/disable Bluetooth */
    BLUETOOTH_SWITCH("BLUETOOTH_SWITCH"),

    /** Nearby device permission for Bluetooth scanning (Android 12+) */
    NEARBY_DEVICE("NEARBY_DEVICE"),

    /** Location permission for Bluetooth scanning (Android < 12) */
    LOCATION("LOCATION"),

    /** Location switch permission to enable/disable location services */
    LOCATION_SWITCH("LOCATION_SWITCH"),

    /** Notification permission for push notifications */
    NOTIFICATION("NOTIFICATION"),

    /** Camera permission for QR code scanning */
    CAMERA("CAMERA"),

    /** All permissions */
    ALL("ALL")
}
