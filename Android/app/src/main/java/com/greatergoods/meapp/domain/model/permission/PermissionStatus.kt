package com.greatergoods.meapp.domain.model.permission

/**
 * Data class representing the status of all permissions.
 * Maps permission states from the bluetooth connect plugin.
 */
data class PermissionStatus(
    /** Bluetooth permission status (mainly for iOS) */
    val bluetooth: String = PermissionState.NOT_REQUESTED,

    /** Bluetooth switch status (enabled/disabled) */
    val bluetoothSwitch: String = PermissionState.NOT_REQUESTED,

    /** Nearby device permission status (Android 12+) */
    val nearbyDevice: String = PermissionState.NOT_REQUESTED,

    /** Location permission status */
    val location: String = PermissionState.NOT_REQUESTED,

    /** Location switch status (enabled/disabled) */
    val locationSwitch: String = PermissionState.NOT_REQUESTED,

    /** Notification permission status */
    val notification: String = PermissionState.NOT_REQUESTED,

    /** Camera permission status */
    val camera: String = PermissionState.NOT_REQUESTED
) {
    /**
     * Checks if all essential permissions for Bluetooth scanning are granted.
     * @param isAndroid12Plus Whether the device is Android 12 or higher
     * @return true if all essential permissions are granted
     */
    fun isBluetoothScanPermissionGranted(isAndroid12Plus: Boolean): Boolean {
        return if (isAndroid12Plus) {
            bluetoothSwitch == PermissionState.ENABLED &&
            nearbyDevice == PermissionState.ENABLED
        } else {
            bluetoothSwitch == PermissionState.ENABLED &&
            location == PermissionState.ENABLED
        }
    }

    /**
     * Checks if any permission is permanently denied.
     * @return true if any permission is permanently denied
     */
    fun hasPermissionsPermanentlyDenied(): Boolean {
        return listOf(
            bluetooth, bluetoothSwitch, nearbyDevice,
            location, locationSwitch, notification, camera
        ).any { it == PermissionState.PERMANENTLY_DENIED }
    }
}

/**
 * Object containing permission state constants that map to GGPermissionState.
 */
object PermissionState {
    /** Permission is granted/enabled */
    const val ENABLED = "ENABLED"

    /** Permission status not determined */
    const val NOT_DETERMINED = "NOT_DETERMINED"

    /** Permission is denied/disabled */
    const val DISABLED = "DISABLED"

    /** Location permission granted but only approximate */
    const val APPROX_LOCATION = "APPROX_LOCATION"

    /** Permission has not been requested yet */
    const val NOT_REQUESTED = "NOT_REQUESTED"

    /** Permission is permanently denied by user */
    const val PERMANENTLY_DENIED = "PERMANENTLY_DENIED"
}
