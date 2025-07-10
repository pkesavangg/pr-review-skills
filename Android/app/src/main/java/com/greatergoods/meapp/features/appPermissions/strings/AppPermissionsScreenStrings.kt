package com.greatergoods.meapp.features.appPermissions.strings

/**
 * String constants for the App Permissions screen.
 */
object AppPermissionsScreenStrings {
    const val Title = "App Permissions"
    const val Description = "Manage permissions for your health and fitness data"

    //Bluetooth
    const val BluetoothPermissions = "Bluetooth"

    const val Notification = "Notification"
    const val NotificationsDescription = "Send reminders and updates about your health journey"

    const val Camera = "Camera (AppSync)"
    const val CameraDescription = "Take photos for profile picture and barcode scanning"

    const val Location = "Location"
    const val LocationDescription = "Detect nearby compatible scales and devices"

    const val Bluetooth = "Bluetooth"
    const val BluetoothDescription = "Connect to smart scales and health devices"

    // Status Labels
    const val Granted = "Granted"
    const val Denied = "Denied"
    const val NotRequested = "Not Requested"

    // Actions
    const val Grant = "Grant"
    const val Settings = "Settings"
    const val Refresh = "Refresh"

    // Messages
    const val PermissionRequired = "This permission is required for full functionality"
    const val PermissionOptional = "This permission is optional but recommended"
    const val OpenSettings = "Open device settings to manage permissions"

    // Error Messages
    object Error {
        const val PermissionCheck = "Unable to check permission status"
        const val SettingsAccess = "Unable to open device settings"
    }
}
