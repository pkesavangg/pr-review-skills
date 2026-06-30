package com.dmdbrands.gurus.weight.features.integration.strings

/**
 * String constants for the Integration screen.
 */
object IntegrationStrings {
    const val Title = "Integrations"

    // Section headers (device-driven)
    const val SectionWeightScale = "Integrations for weight scales"
    const val SectionWeightScaleAndBpm = "Integrations for Weight Scales & BPM"

    // Footer CTA
    const val RequestNewIntegration = "Request new integration"

    // Request-an-Integration modal
    const val RequestModalTitle = "Request an Integration"
    const val RequestModalSubtitle = "What would you like to see added?"
    const val RequestModalPlaceholder = "Integration"
    const val RequestModalSend = "SEND"
    const val RequestModalCancel = "CANCEL"
    const val RequestSubmittedToast = "Thanks! We'll consider your request."
    const val RequestFailedToast = "Couldn't send your request. Try again later."

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

    // region Accessibility (TalkBack)
    // State words spoken in addition to the integration name so the connected/disconnected
    // status is announced (not conveyed by colour/checkmark alone). Dynamic values (the
    // provider name) are composed at the call site; only the state word is a const here.
    const val accConnectedState = "Connected"
    const val accNotConnectedState = "Not connected"

    fun accConnectAction(name: String) = "Connect to $name"
    fun accDisconnectAction(name: String) = "Disconnect from $name"
    fun accProviderLogo(name: String) = "$name logo"
    // endregion
}
