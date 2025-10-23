package com.dmdbrands.gurus.weight.features.appPermissions.strings

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
  const val NetworkHeader = "Network"

  // Bluetooth
  const val BluetoothEnabledDescription = "Bluetooth is turned on"
  const val BluetoothDisabledDescription = "Turn on Bluetooth"

  // Nearby Devices
  const val NearbyDevicesEnabledDescription = "Nearby Devices authorized"
  const val NearbyDevicesDisabledDescription = "Nearby devices is not authorized"

  // Location
  const val LocationEnabledDescription = "Location access is authorized"
  const val LocationDisabledDescription = "Authorize location access"

  // Network
  const val NetworkEnabledDescription = "Network Connected"
  const val NetworkDisabledDescription = "Network Disconnected"

  const val EnabledWifiDescription = "Connected to"
  const val DisabledWifiDescription = "Enable Wi-Fi to continue"

  const val LocationSwitchEnabledDescription = "Location access is enabled"
  const val LocationSwitchDisabledDescription = "Enable location on your device"

  const val NotificationsEnabledDescription = "Notifications are enabled"
  const val NotificationsDisabledDescription = "Enable notifications"

  const val CameraEnabledDescription = "Camera access authorized"
  const val CameraDisabledDescription = "Authorize camera access"

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
