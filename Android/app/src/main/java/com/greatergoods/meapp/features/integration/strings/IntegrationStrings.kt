package com.greatergoods.meapp.features.integration.strings

/**
 * String constants for the Integration screen.
 */
object IntegrationStrings {
    const val Title = "Integrations"

    // Health Connect
    const val HealthConnectTitle = "Health Connect"
    const val HealthConnectDescription = "Connect with Health Connect to sync your health and fitness data across apps."

    // Provider Names
    const val FitbitProvider = "Fitbit"
    const val MyFitnessPalProvider = "MyFitnessPal"
    const val HealthConnectProvider = "Health Connect"

    // Actions
    const val Connect = "Connect"
    const val Remove = "Remove"
    const val Cancel = "Cancel"

    // Confirmation Messages
    const val DisconnectConfirmMessage = "Are you sure you want to disconnect from this integration? This will stop syncing data."
    const val DisconnectSuccess = "Successfully removed"

    const val removeIntegration = "Remove Integration?"
    const val removeAuthIntegration ="Are you sure you want to turn off this integration?"
    const val remove = "remove"

    const val authIntegrationCancelORFailed = "Sorry, something went wrong. Try again?"
    const val failed = "Failed!"
    const val cancel ="cancel"
    const val retry = "retry"
    const val pleaseWait ="Please wait..."
    const val done ="Done!"
    const val ok = "ok"
    const val openIntegrations = "Open Integrations"
    const val loading = "Loading..."

    // Reintegrate Alert Strings
    const val reintegrateAlertTitle = "Integration Issue"
    const val pluralityThis = "this"
    const val pluralityThese = "these"
    const val removeAllIntegrations = "Remove All"

    fun removeIntegration(name: String) = "Remove $name"
    fun reintegrateAlertMessage(plurality: String, names: String) =
        "Unable to connect to $names! You may need to\n" + "re-enable ${plurality} integration by re-authorizing your account."
}
