package com.greatergoods.meapp.features.integration.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider

/**
 * State for Integration screen, including UI state and data.
 * @property integrations List of all integrations with their connection status.
 * @property selectedIntegrationForDisconnect The integration selected for disconnection.
 */
data class IntegrationState(
  val integrations: List<IntegrationItem> = emptyList(),
  val selectedIntegrationForDisconnect: IntegrationItem? = null,
) : IReducer.State

/**
 * Intents for Integration screen actions.
 */
sealed class IntegrationIntent : IReducer.Intent {
  /** Load integrations data. */
  data class OpenIntegration(
    val integrations: IntegrationItem,
  ) : IntegrationIntent()
  /** Load integrations data. */
  object LoadIntegrations : IntegrationIntent()
  /** Connect to an integration. */
  data class AddIntegration(
    val provider: IntegrationProvider,
  ) : IntegrationIntent()

  /** Show disconnect confirmation dialog. */
  data class RemoveIntegration(
    val integrations: IntegrationItem,
  ) : IntegrationIntent()


  /** Confirm disconnect action. */
  object ConfirmDisconnect : IntegrationIntent()

  /** Navigate back. */
  object OnBack : IntegrationIntent()

  /** Start OAuth flow for a provider. */
  data class StartOAuthFlow(
    val provider: IntegrationProvider,
    val accountId: String,
  ) : IntegrationIntent()

  /** OAuth flow completed successfully. */
  data class OAuthFlowCompleted(
    val provider: IntegrationProvider,
  ) : IntegrationIntent()

  /** OAuth flow failed. */
  data class OAuthFlowFailed(
    val provider: IntegrationProvider,
    val error: String,
  ) : IntegrationIntent()

  /** Check Health Connect availability. */
  object CheckHealthConnectAvailability : IntegrationIntent()

  /** Initialize integrations with all available providers. */
  object InitializeIntegrations : IntegrationIntent()

  /** Set all integrations with their current status. */
  data class SetIntegrations(
    val integrations: List<IntegrationItem>,
  ) : IntegrationIntent()

  /** Update integration connection status. */
  data class UpdateIntegrationConnectionStatus(
    val provider: IntegrationProvider,
    val isConnected: Boolean,
    val isValid: Boolean = true,
  ) : IntegrationIntent()

  data object NavigateToHealthConnect : IntegrationIntent()

  /** Remove Health Connect integration (special case). */
  object RemoveHealthConnectIntegration : IntegrationIntent()

}

/**
 * Reducer for Integration screen state transitions.
 */
class IntegrationReducer : IReducer<IntegrationState, IntegrationIntent> {
  /**
   * Reduces the current state and intent to a new state.
   * @param state The current state.
   * @param intent The intent/action to handle.
   * @return The new state after applying the intent.
   */
  override fun reduce(
    state: IntegrationState,
    intent: IntegrationIntent,
  ): IntegrationState =
    when (intent) {
      is IntegrationIntent.LoadIntegrations -> {
        state
      }

      is IntegrationIntent.OpenIntegration -> {
        state
      }

      is IntegrationIntent.InitializeIntegrations -> {
        val allIntegrations =
          IntegrationProvider.getAllProviders().map { provider ->
            IntegrationItem.fromProvider(provider)
          }
        state.copy(integrations = allIntegrations)
      }

      is IntegrationIntent.SetIntegrations -> {
        state.copy(integrations = intent.integrations)
      }

      is IntegrationIntent.AddIntegration -> {
        state
      }

      is IntegrationIntent.RemoveIntegration -> {
        state.copy(selectedIntegrationForDisconnect = intent.integrations)
      }

      is IntegrationIntent.ConfirmDisconnect -> {
        state.copy(selectedIntegrationForDisconnect = null)
      }

      is IntegrationIntent.OnBack -> {
        state
      }

      is IntegrationIntent.StartOAuthFlow -> {
        state
      }

      is IntegrationIntent.OAuthFlowCompleted -> {
        state
      }

      is IntegrationIntent.OAuthFlowFailed -> {
        state
      }

      is IntegrationIntent.CheckHealthConnectAvailability -> {
        state
      }

      is IntegrationIntent.UpdateIntegrationConnectionStatus -> {
        val updatedIntegrations =
          state.integrations.map { integration ->
            if (integration.provider == intent.provider) {
              integration.copy(isConnected = intent.isConnected, isValid = intent.isValid)
            } else {
              integration
            }
          }
        state.copy(integrations = updatedIntegrations)
      }

      is IntegrationIntent.NavigateToHealthConnect ->{
        state
      }

      is IntegrationIntent.RemoveHealthConnectIntegration ->{
        state
      }

    }
}
