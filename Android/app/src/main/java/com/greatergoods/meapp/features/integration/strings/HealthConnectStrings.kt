package com.greatergoods.meapp.features.integration.strings

/**
 * String constants for the StartConnect screen.
 */
object HealthConnectStrings  {
  object StartConnectStrings {
    const val Title = "Integrate Health Connect"
    const val Description =
      "Personalize your experience and control which information is shared between Weight Gurus and Health Connect. Your privacy and data security are top priorities."
  }

  object ActionButtons {
    const val connect = "connect"
    const val sync = "sync"
    const val cancel = "cancel"
    const val finish = "finish"
    const val updatePermissions = "update permissions"
    const val skip = "skip"
    const val remove = "remove"
    const val exit = "exit"
    const val exitSetup = "exit setup"
    const val download = "download"
    const val openHealthConnect = "OPEN HEALTH CONNECT"
    const val removeIntegration = "REMOVE INTEGRATION"
    const val close = "Close"
  }

  object PopupStrings {
    const val addHealthConnectTitle = "Add Health Connect Integration"
    const val addHealthConnectDescription =
      "It looks like you’re using Weight Gurus on a new device. To continue syncing with Health Connect, please reconnect."
    const val finishAddingHcTitle = "Finish Adding Health Connect"
    const val finsihAddingHcDescription =
      "Weight Gurus permissions have been turned on in Health Connect. Connect to complete set up."
    const val hcNotInstalledTitle = "Health Connect Not Installed"
    const val hcNotInstalledDescription = "Download Health Connect before integrating with Weight Gurus."
    const val outOfSyncTitle = "Health Connect is Out of Sync"
    const val outOfSyncDescription = "Enable app permissions in Health Connect or remove the integration in Weight Gurus."
    const val removeHCIntegrationTitle = "Are You Sure?"
    const val removeHCIntegrationMessage = "The integration will be removed. To fully disconnect, ensure all  Weight Gurus permissions are turned off in Health Connect."

  }

  object ExitAlert {
    const val title = "Are You Sure?"
    const val description = " Health Connect will not sync with Weight Gurus."
  }

  object dataNotSynced {
    const val title = "We’re out of sync."
    const val message =
      "Sorry, we couldn’t sync data to Health Connect."
  }

  object ToastStrings {
    const val syncToast = "Weight history successfully synced!"
    const val removeHC = "Health Connect integration removed."
    const val syncHc = "Health Connect is synced!"
  }

  object AddHealthConnectStrings {
    const val Title = "Add Health Connect Integration"
    const val Description =
      "It looks like you’re using Weight Gurus on a new device. To continue syncing with Health Connect, please reconnect."
  }

  object FinishConnectStrings {
    const val Title = "Integration Connected"
    const val Description =
      "Manage settings anytime by opening Health Connect and going to App Permissions → Weight Gurus."
  }

  object StartFullReconnectStrings {
    const val Title = "Integration Reconnected"
    const val Description =
      "All metrics are enabled. Manage settings in Health Connect by navigating to App Permissions → Weight Gurus."
  }

  object PartialReconnectStrings {
    const val Title = "Reconnecting Integration"
    const val Description =
      "Some metrics may already be enabled and won't show on the next screen. Manage settings in Health Connect by navigating to App Permissions → Weight Gurus."
  }

  object FinishPartialReconnectStrings {
    const val Title = "Integration Reconnected"
  }

  object IntegrationFailedStrings {
    const val Title = "Integration Failed"
    const val Description =
      "To troubleshoot, open Heath Connect and turn on Weight Gurus permissions. Then, come back to Weight Gurus and finish connecting."
  }

  object MultiDeviceConnectionStrings {
    const val Title = "Add Health Connect Integration"
    const val Description =
      "It looks like you’re using Weight Gurus on a new device. To continue syncing with Health Connect, please reconnect."
  }

  object UserConflictStrings {
    const val Title = "User Conflict"
    const val Description =
      "Another user has already connected to Health Connect on this device. Please ask them to log in to their account and disconnect the integration. "
  }

  object SyncAlert {
    const val title = "Sync Weight History"
    const val description =
      "Do you want to sync all entries to Health Connect? You cannot do this later without reconnecting."
  }

  object OutOfSyncAlert {
    const val title = "Health Connect is Out of Sync"
    const val description = "Enable app permissions in Health Connect or remove the integration in Weight Gurus."
  }
}

