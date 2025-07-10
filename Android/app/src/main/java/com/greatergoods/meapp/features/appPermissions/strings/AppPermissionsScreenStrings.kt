package com.greatergoods.meapp.features.appPermissions.strings

/**
 * String constants for the App Permissions screen.
 */
object AppPermissionsScreenStrings {
  const val Title = "App Permissions"
  const val Description = "Control what Me.Health can access on your device."

  // Group Headers (from Figma)
  const val BluetoothHeader = "Bluetooth"
  const val LocationHeader = "Location"
  const val CameraHeader = "Camera (AppSync)"
  const val NotificationsHeader = "Notifications"

  // Bluetooth
  const val BluetoothEnabledDescription = "Bluetooth authorized"
  const val BluetoothDisabledDescription = "Allow Bluetooth access"

  // Nearby Devices
  const val NearbyDevicesEnabledDescription = "Nearby Devices authorized"
  const val NearbyDevicesDisabledDescription = "Allow nearby devices access"

  // Location
  const val LocationEnabledDescription = "Location authorized"
  const val LocationDisabledDescription = "Allow location access"

  const val LocationSwitchEnabledDescription = "Location services authorized"
  const val LocationSwitchDisabledDescription = "Allow location services access"

  const val NotificationsEnabledDescription = "Notifications authorized"
  const val NotificationsDisabledDescription = "Allow notifications access"

  const val CameraEnabledDescription = "Camera authorized"
  const val CameraDisabledDescription = "Allow camera access"

  // Status Labels
  const val Granted = "Granted"
  const val Denied = "Denied"
  const val NotRequested = "Not Requested"

  // Actions
  const val Grant = "Grant"
  const val Settings = "Settings"
  const val Refresh = "Refresh"

  // Messages
  const val PermissionRequired = "This permission is required for full functionality."
  const val PermissionOptional = "This permission is optional but recommended."
  const val OpenSettings = "Open device settings to manage permissions."

  // Error Messages
  object Error {
    const val PermissionCheck = "Unable to check permission status."
    const val SettingsAccess = "Unable to open device settings."
  }
}
