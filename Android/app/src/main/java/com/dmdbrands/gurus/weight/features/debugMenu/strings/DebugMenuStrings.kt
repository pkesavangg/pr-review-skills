package com.dmdbrands.gurus.weight.features.debugMenu.strings

/**
 * String constants for the Debug Menu screen.
 * Based on Angular AppStrings.debug.
 */
object DebugMenuStrings {
    const val PageTitle = "Debug Menu"
    const val CautionTitle = "Caution!"
    const val CautionDescription = "Only use this menu if instructed by Greater Goods Customer Service"

    object SectionHeaders {
        const val AppInformation = "App Information"
        const val AppTroubleshooting = "App Troubleshooting"
        const val ScaleTroubleshooting = "Scale Troubleshooting"
    }

    object AppInfo {
        const val AppVersion = "App Version"
        const val NativeModules = "Native Modules"
        const val ComponentVersion = "Component Version"
        const val Api = "API"
        const val Time = "Time"
        const val Timezone = "Timezone"
        const val Minutes = "min"
    }

    object Actions {
        const val SendLog = "Send Weight Gurus Log"
        const val Resync = "Resync Entries"
        const val ClearData = "Clear All Local Data"
        const val SendScaleLog = "Send Scale Logs"
        const val ShowAppRate = "Rate the App"
    }

    object Loading {
        const val SendLogs = "Sending logs..."
        const val Resync = "Resyncing data..."
        const val PleaseWait = "Please wait..."
      const val SendScaleLogs = "Send Scale Log..."
    }

    object Success {
        const val LogSent = "Logs sent to Greater Goods"
        const val Synced = "Entries resynced."
    }

    object Alerts {
        const val DataHeader = "Your data has been cleared."
        const val DataMessage = "To complete this, you will need to close your app."
        const val ErrorHeader = "Error"
        const val ErrorMessage = "An error occurred. Please restart the app."
        const val NetworkError = "No internet connection available"
    }

    const val Ok = "OK"
}
