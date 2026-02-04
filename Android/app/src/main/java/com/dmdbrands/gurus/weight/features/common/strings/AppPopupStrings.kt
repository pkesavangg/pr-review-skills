package com.dmdbrands.gurus.weight.features.common.strings

import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType

/**
 * Static strings for AppPopup composable.
 */
object AppPopupStrings {
  const val LogoContentDescription = "Logo"

  object UnsavedChanges {
    const val Title = "Confirm"
    const val ManualEntryTitle = "Your entry has not been saved!"
    const val Message = "Are you sure you want to leave?"
    const val ManualEntryMessage = "Are you sure you want to exit?"
    const val Return = "Return"
    const val Exit = "Exit"
  }

  object MaxAccountReachedAlert {
    const val Title = "Maximum Users Reached"

    const val Message1 = "Please swipe left to remove any unused accounts before attempting to add a new one."
    const val Message2 = "Log in to a saved account, then open Settings and tap Switch Accounts to remove users."
    const val ConfirmButton = "OK"
  }

  object RemoveAccountDialog {
    const val Title = "Remove %s?"
    const val Message = "Are you sure you want to remove %s from this device?"
    const val CancelButton = "Cancel"
    const val ConfirmButton = "Remove"
    const val Loader = "Removing Account..."
  }

  object BackgroundLoggedOutAlert {
    fun Title(username: String) = "$username was logged out"

    const val Message = "Please log back in to continue."
    const val ConfirmButton = "OK"
  }

  object AccountSwitchInfo {
    const val Header = "NEW: Add Multiple Accounts"
    const val Message =
      "Switch between Weight Gurus accounts by pressing and holding the profile icon or selecting \"Switch Accounts\" in settings."
    const val AddAccountButton = "ADD ACCOUNT"
    const val CloseContentDescription = "Close"
  }

  object ModelNumberHelpPopup {
    const val Title = "Where to find your model number"
    const val Message =
      "Check the back of your scale for a sticker with your four-digit model number.\n\nFor example, if you have a 0375 Bluetooth Scale, the sticker will show the URL: greatergoods.com/0375"
    const val CloseContentDescription = "Close"
    const val ImageContentDescription = "Example sticker showing model number location on scale"
  }

  object UnsavedExitPopup {
    const val Title = "Unsaved Changes"
    const val Message = "Are you sure you want to leave this page? Changes you made will not be saved. "
    const val Leave = "Leave"
    const val Cancel = "Cancel"
  }

  object ScaleDiscoveredPopup {
    const val Title = "New Scale Discovered"
    const val CloseContentDescription = "Close"
  }

  object PermissionsPopup {
    fun Title(permissionType: String, isScaleSetupRequest: Boolean = false) = when (permissionType) {
      GGPermissionType.BLUETOOTH_SWITCH -> if (isScaleSetupRequest) "Bluetooth is Turned Off"
      else "It looks like your Bluetooth is disabled!"
      GGPermissionType.NEARBY_DEVICE -> "Nearby Devices Permission."
      GGPermissionType.LOCATION_SWITCH -> "Your Location may be disabled!"
      GGPermissionType.LOCATION -> "Weight Gurus needs location access to connect your scale."
      GGPermissionType.NOTIFICATION -> "Notifications are disabled!"
      GGPermissionType.CAMERA -> "You have not given permission to access camera!"
      GGPermissionType.ALL -> "Unable to scan devices!"
      else -> ""
    }

    fun Message(permissionType: String, isScaleSetupRequest: Boolean = false) = when (permissionType) {
      GGPermissionType.BLUETOOTH_SWITCH -> if (isScaleSetupRequest) "Bluetooth is required to connect to your scale and collect measurements. Please turn on Bluetooth and try again." else
        "You will not be able to sync with your Bluetooth scale."

      GGPermissionType.NEARBY_DEVICE ->
        "Android requires apps that connect to a Bluetooth LE device ask for permission that determines " +
          "the relative location of nearby devices. Sorry for the inconvenience, but we don't store or use any of this information."

      GGPermissionType.LOCATION_SWITCH ->
        "Android requires any app that connects to a Bluetooth / Wi-Fi device to ask " +
          "for location permissions. So, we’re asking—but we don’t actually use or store " +
          "any information about your location."

      GGPermissionType.LOCATION ->
        "Android requires you share location permissions with any app that connects via Wi-Fi / Bluetooth. " +
          "Weight Gurus does not store this information."

      GGPermissionType.NOTIFICATION ->
        "Notification permissions have been turned off. Enable notifications to receive updates from your Wi-Fi scale."

      GGPermissionType.CAMERA ->
        "You will not be able to pair or sync with your App sync scale. Please enable it from your app permissions."

      GGPermissionType.ALL ->
        "One or more required permissions or device services may be disabled. Visit the App Permissions screen " +
          "in the Settings tab to check and enable the app’s permissions access."

      else -> ""
    }

    fun ConfirmButton(permissionType: String, isScaleSetupRequest: Boolean = false) = when (permissionType) {
      GGPermissionType.ALL -> "App Permission"
      GGPermissionType.BLUETOOTH_SWITCH, GGPermissionType.NOTIFICATION -> if (isScaleSetupRequest) "turn on" else "Enable"
      else -> "Allow"
    }

    fun CancelButton(permissionType: String) = when (permissionType) {
      GGPermissionType.ALL -> "Cancel"
      GGPermissionType.BLUETOOTH_SWITCH,
      GGPermissionType.NOTIFICATION -> "Ignore"
      else -> "Return"
    }
  }

  object AppsyncEntryPopup {
    const val Title = "Your AppSync Scan was successful!"
    fun Weight(weight: Float?, weightUnit: String) = "Weight: ${weight?.let { "$it $weightUnit" } ?: "--"}"
    fun Bodyfat(bodyfat: Float?) = "Body Fat: ${bodyfat?.let { "$it%" } ?: "--%"}"
    fun MuscleMass(muscleMass: Float?) = "Muscle Mass: ${muscleMass?.let { "$it%" } ?: "--%"}"
    fun WaterWeight(waterWeight: Float?) = "Water Weight:  ${waterWeight?.let { "$it%" } ?: "--%"}"
    fun Bmi(bmi: Float?) = "BMI: $bmi"
    const val SaveButton = "Save"
    const val EditButton = "Edit"
  }

  object WeightOnlyModeEnabledPopup {
    const val Title = "A User has Weight Only Mode on"
    const val Message = "You can enable All Body Metrics for one session. This will temporarily disable Weight Only mode, and all body metrics will be collected."
    const val ConfirmButton = "Enable"
    const val CancelButton = "Dismiss"
  }

  object SetGoalPopup {
    const val Title = "Set a Goal"
    const val Message = "A great tool for tracking your journey that can always be changed in the app settings."
    const val ConfirmButton = "LET'S DO IT"
    const val CloseContentDescription = "Close"
  }

  object R4ProfileUpdatePending {
    const val Title = "Updates Pending..."
    const val Message =
      "Scale settings can’t be updated at this time. Weight Gurus will save changes and update the scale next time it connects."
    const val ConfirmButton = "Ok"
  }
}
