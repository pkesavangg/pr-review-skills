package com.dmdbrands.gurus.weight.features.deviceDetails.strings

import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper

object DeviceDetailsStrings {
  const val DeleteConfirmation = "Are you sure you want to delete this device?"
  const val Delete = "Delete"
  const val Cancel = "Cancel"
  const val DeleteSuccessMessage = "device deleted successfully"
  const val DeleteErrorMessage = "Error deleting device"
  const val DeleteLoaderMessage = "Deleting device..."
  const val DeleteLabel = "Delete Device"
  const val Close = "Close"

  /** TalkBack label for the app-bar close button (a11y). Kept dedicated and uniform with the
   *  other screens in this feature set, rather than reusing the general-purpose [Close]. */
  const val accCloseLabel = "Close"
  const val Mode = "Mode"
  const val AllBodyMetrics = "All Body Metrics"
  const val WeightOnly = "Weight Only"
  const val DisplayMetrics = "Display Metrics"
  const val Users = "Users"

  /**
   * Label for the user-slot setting row. BPM devices identify users with letters (A/B) or digits
   * (1/2), so we show the generic "User" label; weight scales show "User Number" because the
   * value is always numeric.
   */
  fun userNumberLabel(sku: String?) = when {
    DeviceHelper.isBpmDevice(sku) -> "User"
    else -> "User Number"
  }
  const val Connection = "Connection"
  const val Bluetooth = "Bluetooth"
  const val Connected = "Connected"
  const val WiFi = "Wi-Fi"
  const val WiFiMacAddress = "Wi-Fi MAC Address"
  const val Support = "Support"
  const val ScaleType = "Scale Type"
  const val BluetoothWiFi = "Bluetooth/Wi-Fi"
  const val Sku = "SKU"
  const val DatePaired = "Date Paired"
  const val ProductGuide = "Product Guide"
  const val SetupIncomplete = "Setup Incomplete"
  const val SetupWifi = "Setup Wi-Fi"

  // Testing Features (similar to Angular implementation)
  const val Others = "Others"
  const val DeviceMac = "Scale MAC"
  const val SoftwareUpdate = "Software Update"
  const val OtherSettings = "Other Settings"
  const val SessionImpedance = "Session Impedance"

  // Firmware Update Strings
  const val Version = "Version"
  const val AlreadyUpToDate = "Your scale is already up to date!"
  const val UpdateMessage = "A new firmware version"
  const val UpdateMessage1 = "is available for your scale."
  const val UpdateMessage2 = "This will update your scale immediately."
  const val UpdateNow = "Now"
  const val UpdateSchedule = "Schedule"
  const val Upgrade = "Upgrade"
  const val Date = "Date"
  const val Save = "Save"

  // Additional Settings Screen Strings
  const val DeviceDetails = "Scale Details"
  const val Manufacturer = "Manufacturer"
  const val DeviceName = "Device Name"
  const val MacAddress = "MAC Address"
  const val BroadcastId = "Broadcast ID"
  const val FirmwareRevision = "Firmware Revision"
  const val BatteryLevel = "Battery Level"
  const val DeviceFeatures = "Scale Features"
  const val EnableStartAnimation = "Enable Start Animation"
  const val EnableEndAnimation = "Enable End Animation"
  const val TimeFormat = "Time Format"
  const val ResetFirmware = "Reset Firmware"
  const val FactoryReset = "Factory Reset"
  const val DownloadLogs = "Download Logs"
  const val ClearScaleData = "Clear Scale Data"
  const val Unknown = "Unknown"
  const val NotSet = "Not Set"
  const val All = "All"
  const val Settings = "Settings"
  const val History = "History"
  const val Account = "Account"

  // ViewModel Toast Messages
  const val SessionImpedanceEnabled = "Session impedance enabled"
  const val SessionImpedanceDisabled = "Session impedance disabled"
  const val DeviceNotConnectedImpedance = "Scale must be connected to change session impedance"
  const val ErrorUpdatingImpedance = "Error updating session impedance"
  const val WifiRequiredForUpdate = "Wi-Fi must be configured to download firmware updates"
  const val UpdatingFirmware = "Updating Firmware..."
  const val FirmwareUpdateStarted = "Firmware update started immediately"
  const val FirmwareUpdateScheduled = "Firmware update scheduled for"
  const val DeviceNotConnectedUpdate = "Scale must be connected to update firmware"
  const val ErrorStartingUpdate = "Error starting firmware update"
  const val DownloadingLogs = "Downloading logs..."
  const val LogsDownloaded = "Logs downloaded successfully"
  const val DeviceNotConnectedLogs = "Scale must be connected to download logs"
  const val ErrorDownloadingLogs = "Error downloading logs"
  const val ClearingData = "Clearing"
  const val DataCleared = "data cleared successfully"
  const val DeviceNotConnectedClear = "Scale must be connected to clear data"
  const val ErrorClearingData = "Error clearing scale data"
  const val UpdatingTimeFormat = "Updating time format..."
  const val TimeFormatUpdated = "Time format updated to"
  const val DeviceNotConnectedTimeFormat = "Scale must be connected to change time format"
  const val ErrorChangingTimeFormat = "Error changing time format"
  const val UpdatingAnimation = "Updating"
  const val AnimationEnabled = "animation enabled"
  const val AnimationDisabled = "animation disabled"
  const val DeviceNotConnectedAnimation = "Scale must be connected to change animation settings"
  const val ErrorUpdatingAnimation = "Error updating animation settings"
  const val ResettingFirmware = "Resetting firmware..."
  const val FirmwareResetSuccess = "Firmware reset successfully"
  const val DeviceNotConnectedReset = "Scale must be connected to reset firmware"
  const val ErrorResettingFirmware = "Error resetting firmware"
  const val RestoringFactory = "Restoring factory settings..."
  const val FactoryRestoreSuccess = "Factory settings restored successfully"
  const val DeviceNotConnectedFactory = "Scale must be connected to restore factory settings"
  const val ErrorRestoringFactory = "Error restoring factory settings"
  const val StartAnimation = "start"
  const val EndAnimation = "end"
  const val TimeFormat12H = "12H"
  const val TimeFormat24H = "24H"
  const val ClearData = "Clear Data"

  // Enable Body Metrics Alert Dialog
  const val EnableBodyMetricsAlertTitle = "Enable Body Metrics"
  const val EnableBodyMetricsAlertMessage = "This will disable Weight Only Mode for one session, and all body metrics will be collected."
  const val EnableBodyMetricsAlertConfirm = "ENABLE"
  const val EnableBodyMetricsAlertCancel = "CANCEL"
  const val EnableBodyMetricsAlertSuccess = "Body metrics enabled successfully"
  const val EnableBodyMetricsAlertError = "Error enabling body metrics"
  const val UpdateMode = "Updating Mode..."
}
