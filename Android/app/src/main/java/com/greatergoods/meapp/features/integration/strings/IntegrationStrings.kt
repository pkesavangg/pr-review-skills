package com.greatergoods.meapp.features.integration.strings

/**
 * Strings for the Integration screen.
 */
object IntegrationStrings {
  const val Title = "Integrations"
  const val Subtitle = "Connect your favorite apps and devices"
  const val AddIntegrationTitle = "Add Integration"
  const val ConnectedIntegrationsTitle = "Connected Integrations"
  const val NoIntegrationsMessage = "No integrations connected yet"
  const val ConnectButton = "CONNECT"
  const val DisconnectButton = "DISCONNECT"
  const val ManageButton = "MANAGE"

  // Provider names
  const val FitbitProvider = "Fitbit"
  const val MyFitnessPalProvider = "MyFitnessPal"
  const val HealthConnectProvider = "Health Connect"

  // Alert dialogs
  object DisconnectDialog {
    const val Title = "Disconnect Integration"
    const val Message = "Are you sure you want to disconnect this integration? Your data will no longer sync."
    const val ConfirmButton = "DISCONNECT"
    const val CancelButton = "CANCEL"
  }

  object ErrorDialog {
    const val Title = "Connection Error"
    const val Message = "Unable to connect to this service. Please try again."
    const val RetryButton = "RETRY"
    const val CancelButton = "CANCEL"
  }

  // Integration status messages
  object Status {
    const val Connected = "Connected"
    const val Disconnected = "Disconnected"
    const val Connecting = "Connecting..."
    const val Disconnecting = "Disconnecting..."
    const val Error = "Connection Error"
  }

  // Platform requirements
  object Requirements {
    const val HealthConnectRequirement = "Requires Android 13+"
    const val NoRequirement = "No special requirements"
  }

  // Success messages
  object Success {
    const val ConnectedMessage = "Successfully connected to %s"
    const val DisconnectedMessage = "Successfully disconnected from %s"
  }

  // Error messages
  object Errors {
    const val ConnectionFailed = "Failed to connect to %s"
    const val DisconnectionFailed = "Failed to disconnect from %s"
    const val NetworkError = "Network error. Please check your connection and try again."
    const val OAuthError = "Authentication failed. Please try again."
  }
}
