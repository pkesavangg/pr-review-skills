package com.dmdbrands.gurus.weight.features.integration.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * State for Integration screen, including UI state and data.
 * @property integrations List of all integrations with their connection status.
 * @property selectedIntegrationForDisconnect The integration selected for disconnection.
 */
@Stable
data class IntegrationState(
  val integrations: ImmutableList<IntegrationItem> = persistentListOf(),
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

  /** Toggle Health Connect integration with out-of-sync check. */
  data class ToggleHealthConnectIntegration(
    val integration: IntegrationItem,
  ) : IntegrationIntent()

  /** User tapped the "Request new integration" footer CTA. */
  object RequestNewIntegration : IntegrationIntent()

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
  ): IntegrationState = when (intent) {
    is IntegrationIntent.InitializeIntegrations -> {
      val allIntegrations = IntegrationProvider.getAllProviders().map { provider ->
        IntegrationItem.fromProvider(provider)
      }
      state.copy(integrations = allIntegrations.toImmutableList())
    }

    is IntegrationIntent.SetIntegrations ->
      state.copy(integrations = intent.integrations.toImmutableList())

    is IntegrationIntent.RemoveIntegration ->
      state.copy(selectedIntegrationForDisconnect = intent.integrations)

    is IntegrationIntent.UpdateIntegrationConnectionStatus -> {
      val updatedIntegrations = state.integrations.map { integration ->
        if (integration.provider == intent.provider) {
          integration.copy(isConnected = intent.isConnected, isValid = intent.isValid)
        } else {
          integration
        }
      }
      state.copy(integrations = updatedIntegrations.toImmutableList())
    }

    // Side-effect-only intents (no state change here — handled by the ViewModel).
    is IntegrationIntent.LoadIntegrations,
    is IntegrationIntent.OpenIntegration,
    is IntegrationIntent.AddIntegration,
    is IntegrationIntent.OnBack,
    is IntegrationIntent.StartOAuthFlow,
    is IntegrationIntent.OAuthFlowCompleted,
    is IntegrationIntent.OAuthFlowFailed,
    is IntegrationIntent.CheckHealthConnectAvailability,
    is IntegrationIntent.NavigateToHealthConnect,
    is IntegrationIntent.RemoveHealthConnectIntegration,
    is IntegrationIntent.ToggleHealthConnectIntegration,
    is IntegrationIntent.RequestNewIntegration -> state
  }
}
