package com.greatergoods.meapp.features.integration.model

import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider

/**
 * State for Integration screen, including UI state and data.
 * @property isLoading Whether the integration operations are ongoing.
 * @property error Error message to display, if any.
 * @property integrations List of all integrations with their connection status.
 * @property showDisconnectDialog Whether to show the disconnect confirmation dialog.
 * @property selectedIntegrationForDisconnect The integration selected for disconnection.
 * @property oAuthState Current OAuth flow state.
 * @property healthConnectState Health Connect specific state.
 * @property showReintegrateAlert Whether to show reintegrate alert for invalid integrations.
 * @property invalidIntegrations List of invalid integration providers.
 * @property isHealthConnectAvailable Whether Health Connect is available on the device.
 * @property isHealthConnectOutOfSync Whether Health Connect is out of sync.
 */
data class IntegrationState(
  val isLoading: Boolean = false,
  val error: String? = null,
  val integrations: List<IntegrationItem> = emptyList(),
  val showDisconnectDialog: Boolean = false,
  val selectedIntegrationForDisconnect: IntegrationItem? = null,
  val oAuthState: OAuthState = OAuthState.Idle,
  val healthConnectState: HealthConnectState = HealthConnectState.Idle,
  val showReintegrateAlert: Boolean = false,
  val invalidIntegrations: List<String> = emptyList(),
  val isHealthConnectAvailable: Boolean = false,
  val isHealthConnectOutOfSync: Boolean = false,
) : IReducer.State

/**
 * Intents for Integration screen actions.
 */
sealed class IntegrationIntent : IReducer.Intent {
  /** Load integrations data. */
  object LoadIntegrations : IntegrationIntent()

  /** Set integrations with their connection status. */
  data class SetIntegrations(
    val integrations: List<IntegrationItem>,
  ) : IntegrationIntent()

  /** Set connected integrations. */
  data class SetConnectedIntegrations(
    val integrations: List<IntegrationItem>,
  ) : IntegrationIntent()

  /** Connect to an integration. */
  data class ConnectIntegration(
    val provider: IntegrationProvider,
  ) : IntegrationIntent()

  /** Disconnect from an integration. */
  data class DisconnectIntegration(
    val provider: IntegrationProvider,
  ) : IntegrationIntent()

  /** Show disconnect confirmation dialog. */
  data class ShowDisconnectDialog(
    val integration: IntegrationItem,
  ) : IntegrationIntent()

  /** Hide disconnect confirmation dialog. */
  object HideDisconnectDialog : IntegrationIntent()

  /** Confirm disconnect action. */
  object ConfirmDisconnect : IntegrationIntent()

  /** Show an error message. */
  data class Error(
    val message: String,
  ) : IntegrationIntent()

  /** Clear error message. */
  object ClearError : IntegrationIntent()

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

  /** Cancel OAuth flow. */
  object CancelOAuthFlow : IntegrationIntent()

  /** Check Health Connect availability. */
  object CheckHealthConnectAvailability : IntegrationIntent()

  /** Set Health Connect availability status. */
  data class SetHealthConnectAvailability(
    val isAvailable: Boolean,
  ) : IntegrationIntent()

  /** Request Health Connect permissions. */
  object RequestHealthConnectPermissions : IntegrationIntent()

  /** Set Health Connect permission status. */
  data class SetHealthConnectPermissionStatus(
    val status: HealthConnectPermissionStatus,
  ) : IntegrationIntent()

  /** Start Health Connect data sync. */
  object StartHealthConnectSync : IntegrationIntent()

  /** Update Health Connect sync progress. */
  data class UpdateHealthConnectSyncProgress(
    val progress: Int,
  ) : IntegrationIntent()

  /** Health Connect sync completed. */
  object HealthConnectSyncCompleted : IntegrationIntent()

  /** Health Connect sync failed. */
  data class HealthConnectSyncFailed(
    val error: String,
  ) : IntegrationIntent()

  /** Show reintegrate alert for invalid integrations. */
  data class ShowReintegrateAlert(
    val invalidProviders: List<IntegrationProvider>,
  ) : IntegrationIntent()

  /** Hide reintegrate alert. */
  object HideReintegrateAlert : IntegrationIntent()

  /** Set invalid integrations list. */
  data class SetInvalidIntegrations(
    val providers: List<IntegrationProvider>,
  ) : IntegrationIntent()

  /** Set Health Connect out of sync status. */
  data class SetHealthConnectOutOfSync(
    val isOutOfSync: Boolean,
  ) : IntegrationIntent()

  /** Refresh integration status. */
  object RefreshIntegrationStatus : IntegrationIntent()

  /** Update integration sync status. */
  data class UpdateIntegrationSyncStatus(
    val provider: IntegrationProvider,
    val syncStatus: SyncStatus,
  ) : IntegrationIntent()

  /** Initialize integrations with all available providers. */
  object InitializeIntegrations : IntegrationIntent()

  /** Update integration connection status. */
  data class UpdateIntegrationConnectionStatus(
    val provider: IntegrationProvider,
    val isConnected: Boolean,
    val isValid: Boolean = true,
  ) : IntegrationIntent()
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
        state.copy(isLoading = true, error = null)
      }

      is IntegrationIntent.InitializeIntegrations -> {
        val allIntegrations =
          IntegrationProvider.getAllProviders().map { provider ->
            IntegrationItem.fromProvider(provider)
          }
        state.copy(integrations = allIntegrations)
      }

      is IntegrationIntent.SetIntegrations -> {
        state.copy(
          integrations = intent.integrations,
          isLoading = false,
          error = null,
        )
      }

      is IntegrationIntent.ConnectIntegration -> {
        val provider = intent.provider
        if (provider.requiresOAuth()) {
          // For OAuth providers, start OAuth flow
          state.copy(
            isLoading = true,
            error = null,
          )
        } else if (provider.isPlatformSpecific()) {
          // For platform-specific providers like Health Connect
          state.copy(
            healthConnectState =
              state.healthConnectState.copy(
                isRequestingPermissions = true,
              ),
            isLoading = true,
            error = null,
          )
        } else {
          // For other providers
          state.copy(isLoading = true, error = null)
        }
      }

      is IntegrationIntent.DisconnectIntegration -> {
        state.copy(isLoading = true, error = null)
      }

      is IntegrationIntent.ShowDisconnectDialog -> {
        state.copy(
          showDisconnectDialog = true,
          selectedIntegrationForDisconnect = intent.integration,
        )
      }

      is IntegrationIntent.HideDisconnectDialog -> {
        state.copy(
          showDisconnectDialog = false,
          selectedIntegrationForDisconnect = null,
        )
      }

      is IntegrationIntent.ConfirmDisconnect -> {
        state.copy(
          showDisconnectDialog = false,
          selectedIntegrationForDisconnect = null,
        )
      }

      is IntegrationIntent.Error -> {
        state.copy(isLoading = false, error = intent.message)
      }

      is IntegrationIntent.ClearError -> {
        state.copy(error = null)
      }

      is IntegrationIntent.OnBack -> {
        state.copy(isLoading = false, error = null)
      }

      is IntegrationIntent.StartOAuthFlow -> {
        val provider = intent.provider
        state.copy(
          oAuthState = OAuthState.startOAuth(provider, intent.accountId),
          isLoading = true,
          error = null,
        )
      }

      is IntegrationIntent.OAuthFlowCompleted -> {
        state.copy(
          oAuthState = OAuthState.Idle,
          isLoading = false,
          error = null,
        )
      }

      is IntegrationIntent.OAuthFlowFailed -> {
        state.copy(
          oAuthState =
            OAuthState(
              isOAuthInProgress = false,
              currentProvider = intent.provider,
              oAuthUrl = null,
              oAuthError = intent.error,
              accountId = state.oAuthState.accountId,
            ),
          isLoading = false,
          error = intent.error,
        )
      }

      is IntegrationIntent.CancelOAuthFlow -> {
        state.copy(
          oAuthState = OAuthState.Idle,
          isLoading = false,
        )
      }

      is IntegrationIntent.CheckHealthConnectAvailability -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isCheckingAvailability = true,
            ),
        )
      }

      is IntegrationIntent.SetHealthConnectAvailability -> {
        state.copy(
          isHealthConnectAvailable = intent.isAvailable,
          healthConnectState =
            state.healthConnectState.copy(
              isCheckingAvailability = false,
            ),
        )
      }

      is IntegrationIntent.RequestHealthConnectPermissions -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isRequestingPermissions = true,
            ),
        )
      }

      is IntegrationIntent.SetHealthConnectPermissionStatus -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isRequestingPermissions = false,
              permissionStatus = intent.status,
            ),
        )
      }

      is IntegrationIntent.StartHealthConnectSync -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isSyncingData = true,
              syncProgress = 0,
              syncError = null,
            ),
        )
      }

      is IntegrationIntent.UpdateHealthConnectSyncProgress -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              syncProgress = intent.progress,
            ),
        )
      }

      is IntegrationIntent.HealthConnectSyncCompleted -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isSyncingData = false,
              syncProgress = 100,
              syncError = null,
            ),
        )
      }

      is IntegrationIntent.HealthConnectSyncFailed -> {
        state.copy(
          healthConnectState =
            state.healthConnectState.copy(
              isSyncingData = false,
              syncError = intent.error,
            ),
          error = intent.error,
        )
      }

      is IntegrationIntent.ShowReintegrateAlert -> {
        val invalidProviderNames = intent.invalidProviders.map { it.displayName }
        state.copy(
          showReintegrateAlert = true,
          invalidIntegrations = invalidProviderNames,
        )
      }

      is IntegrationIntent.HideReintegrateAlert -> {
        state.copy(showReintegrateAlert = false)
      }

      is IntegrationIntent.SetInvalidIntegrations -> {
        val invalidProviderNames = intent.providers.map { it.displayName }
        state.copy(invalidIntegrations = invalidProviderNames)
      }

      is IntegrationIntent.SetHealthConnectOutOfSync -> {
        state.copy(isHealthConnectOutOfSync = intent.isOutOfSync)
      }

      is IntegrationIntent.RefreshIntegrationStatus -> {
        state.copy(isLoading = true, error = null)
      }

      is IntegrationIntent.UpdateIntegrationSyncStatus -> {
        val updatedIntegrations =
          state.integrations.map { integration ->
            if (integration.provider == intent.provider) {
              integration.copy(syncStatus = intent.syncStatus)
            } else {
              integration
            }
          }
        state.copy(integrations = updatedIntegrations)
      }

      is IntegrationIntent.UpdateIntegrationConnectionStatus -> {
        val updatedIntegrations =
          state.integrations.map { integration ->
            if (integration.provider == intent.provider) {
              integration.copy(
                isConnected = intent.isConnected,
                isValid = intent.isValid,
              )
            } else {
              integration
            }
          }
        state.copy(integrations = updatedIntegrations)
      }

      is IntegrationIntent.SetConnectedIntegrations -> {
        state
      }
    }
}
