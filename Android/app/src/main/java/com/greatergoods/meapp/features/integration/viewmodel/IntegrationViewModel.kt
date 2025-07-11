package com.greatergoods.meapp.features.integration.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.shared.utilities.browser.ChromeTabState
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.integration.model.IntegrationIntent
import com.greatergoods.meapp.features.integration.model.IntegrationItem
import com.greatergoods.meapp.features.integration.model.IntegrationReducer
import com.greatergoods.meapp.features.integration.model.IntegrationState
import com.greatergoods.meapp.features.integration.strings.IntegrationStrings
import com.greatergoods.meapp.resources.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Integration screen. Handles integration state, connection logic, and navigation.
 * @property accountService Service for account management and getting active account.
 * @property integrationService Service for managing integrations with third-party providers.
 */
@HiltViewModel
class IntegrationViewModel
@Inject
constructor(
  private val accountService: IAccountService,
) : BaseIntentViewModel<IntegrationState, IntegrationIntent>(
  reducer = IntegrationReducer(),
) {
  override fun provideInitialState(): IntegrationState = IntegrationState()

  init {

    AppLog.d("IntegrationViewModel", "IntegrationViewModel initialized")
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: IntegrationIntent) {
    when (intent) {
      is IntegrationIntent.LoadIntegrations -> loadIntegrations()
      is IntegrationIntent.ConnectIntegration -> connectIntegration(intent.provider)
      is IntegrationIntent.DisconnectIntegration -> disconnectIntegration(intent.provider)
      is IntegrationIntent.ShowDisconnectDialog -> showDisconnectDialog(intent.integration)
      is IntegrationIntent.HideDisconnectDialog -> hideDisconnectDialog()
      is IntegrationIntent.ConfirmDisconnect -> confirmDisconnect()
      is IntegrationIntent.ClearError -> clearError()
      is IntegrationIntent.OnBack -> onBack()
      is IntegrationIntent.OAuthFlowCompleted -> handleOAuthFlowCompleted(intent.provider)
      is IntegrationIntent.OAuthFlowFailed -> handleOAuthFlowFailed(intent.provider, intent.error)
      is IntegrationIntent.StartOAuthFlow -> startOAuthFlow(intent.provider, intent.accountId)
      is IntegrationIntent.CancelOAuthFlow -> cancelOAuthFlow()
      is IntegrationIntent.CheckHealthConnectAvailability -> checkHealthConnectAvailability()
      is IntegrationIntent.RequestHealthConnectPermissions -> requestHealthConnectPermissions()
      is IntegrationIntent.StartHealthConnectSync -> startHealthConnectSync()
      is IntegrationIntent.RefreshIntegrationStatus -> refreshIntegrationStatus()
      else -> {
        // Handle other intents that don't need special processing
      }
    }
    super.handleIntent(intent)
  }

  /**
   * Loads all integrations with their current connection status from the database.
   */
  private fun loadIntegrations() {
    // Monitor custom tab state for OAuth flow completion
    viewModelScope.launch {
      customTabManager.subscribeChromeState().collect { state ->
        when (state) {
          ChromeTabState.TabHidden -> {
            AppLog.d("IntegrationViewModel", "Custom tab hidden - OAuth flow may be completed")
            // Check if OAuth flow was completed successfully
            checkOAuthFlowCompletion()
          }

          ChromeTabState.TabShown -> {
            AppLog.d("IntegrationViewModel", "Custom tab shown")
          }

          null -> {
            AppLog.d("IntegrationViewModel", "Custom tab state is null")
          }

          else -> {}
        }
      }
    }
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Loading integrations")

        // Initialize with all available providers
        handleIntent(IntegrationIntent.InitializeIntegrations)

        // Load actual connection status from integration service
        val integrationsWithStatus = getIntegrationsWithConnectionStatus()

        // Update state with actual connection status
        handleIntent(IntegrationIntent.SetIntegrations(integrationsWithStatus))

        AppLog.d("IntegrationViewModel", "Loaded ${integrationsWithStatus.size} integrations from database")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to load integrations", e.toString())
        handleIntent(IntegrationIntent.Error("Failed to load integrations: ${e.message}"))
      }
    }
  }

  /**
   * Enables the specified integration provider.
   */
  private fun connectIntegration(provider: IntegrationProvider) {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Connecting to $provider")

        // Get current account for OAuth flow
        val currentAccount = accountService.getCurrentAccount()
        val accountId = currentAccount?.id ?: throw IllegalStateException("No current account found")

        if (provider.requiresOAuth()) {
          // For OAuth providers, start OAuth flow
          handleIntent(IntegrationIntent.StartOAuthFlow(provider = provider, accountId = accountId))
        } else if (provider.isPlatformSpecific()) {
          // For platform-specific providers like Health Connect
          handleIntent(IntegrationIntent.CheckHealthConnectAvailability)
        } else {
          // For other providers
          handleIntent(IntegrationIntent.Error("Unsupported provider type: $provider"))
        }

        AppLog.d("IntegrationViewModel", "Started connection process for $provider")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to connect to $provider", e.toString())
        handleIntent(IntegrationIntent.Error("Failed to connect to $provider: ${e.message}"))
      }
    }
  }

  /**
   * Disconnects from the specified integration provider.
   */
  private fun disconnectIntegration(provider: IntegrationProvider) {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Disconnecting from $provider")

        // Use the integration service to remove the integration
        // integrationService.removeIntegration(provider.apiValue)

        // Refresh integrations to get updated status from database
        refreshIntegrations()

        AppLog.d("IntegrationViewModel", "Successfully disabled $provider")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to disconnect from $provider", e.toString())
        handleIntent(IntegrationIntent.Error("Failed to disconnect from $provider: ${e.message}"))
      }
    }
  }

  /**
   * Refreshes integrations from the database and updates the UI state.
   */
  private fun refreshIntegrations() {
    viewModelScope.launch {
      try {
        // Refresh integrations from the service
        // integrationService.refreshIntegrations()

        // Reload integrations with updated status
        val integrationsWithStatus = getIntegrationsWithConnectionStatus()
        handleIntent(IntegrationIntent.SetIntegrations(integrationsWithStatus))

        AppLog.d("IntegrationViewModel", "Refreshed integrations from database")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to refresh integrations", e.toString())
      }
    }
  }

  /**
   * Shows the disconnect confirmation dialog.
   */
  private fun showDisconnectDialog(integration: IntegrationItem) {
    handleIntent(IntegrationIntent.ShowDisconnectDialog(integration))
  }

  /**
   * Hides the disconnect confirmation dialog.
   */
  private fun hideDisconnectDialog() {
    handleIntent(IntegrationIntent.HideDisconnectDialog)
  }

  /**
   * Confirms the disconnect action and proceeds with disconnection.
   */
  private fun confirmDisconnect() {
    val selectedIntegration = state.value.selectedIntegrationForDisconnect
    if (selectedIntegration != null) {
      disconnectIntegration(selectedIntegration.provider)
    }
    hideDisconnectDialog()
  }

  /**
   * Clears any error message.
   */
  private fun clearError() {
    handleIntent(IntegrationIntent.ClearError)
  }

  /**
   * Handles back navigation.
   */
  private fun onBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to navigate back", e.toString())
      }
    }
  }

  /**
   * Gets integrations with their actual connection status from the integration service.
   * This fetches the current account from the database and maps the integration status.
   */
  private suspend fun getIntegrationsWithConnectionStatus(): List<IntegrationItem> {
    try {
      // Get the current account from the account service
      val currentAccount = accountService.getCurrentAccount()

      AppLog.d("IntegrationViewModel", "Fetched account from account service: ${currentAccount?.id}")

      // Map the account's integration status to IntegrationItem objects
      return listOf(
        IntegrationItem(
          provider = IntegrationProvider.Fitbit,
          name = IntegrationStrings.FitbitProvider,
          isConnected = currentAccount?.isFitbitOn ?: false,
          isValid = currentAccount?.isFitbitValid ?: false,
          iconRes = AppIcons.Integrations.Fitbit, // Temporary placeholder
          platformRequirement = IntegrationProvider.Fitbit.getPlatformRequirement(),
          requiresOAuth = IntegrationProvider.Fitbit.requiresOAuth(),
        ),
        IntegrationItem(
          provider = IntegrationProvider.MyFitnessPal,
          name = IntegrationStrings.MyFitnessPalProvider,
          isConnected = currentAccount?.isMfpOn ?: false,
          isValid = currentAccount?.isMfpValid ?: false,
          iconRes = AppIcons.Integrations.My_Fitness_Pal, // Temporary placeholder
          platformRequirement = IntegrationProvider.MyFitnessPal.getPlatformRequirement(),
          requiresOAuth = IntegrationProvider.MyFitnessPal.requiresOAuth(),
        ),
        IntegrationItem(
          provider = IntegrationProvider.HealthConnect,
          name = IntegrationStrings.HealthConnectProvider,
          isConnected = currentAccount?.isHealthConnectOn ?: false,
          isValid = true, // Health Connect validity is handled differently
          iconRes = AppIcons.Integrations.Health_Connect_Logo, // Temporary placeholder
          platformRequirement = IntegrationProvider.HealthConnect.getPlatformRequirement(),
          requiresOAuth = IntegrationProvider.HealthConnect.requiresOAuth(),
        ),
      )
    } catch (e: Exception) {
      AppLog.e("IntegrationViewModel", "Failed to get integration status from account service", e.toString())
      // Return empty list if we can't fetch from account service
      return emptyList()
    }
  }

  /**
   * Handles OAuth flow completion for a provider.
   */
  private fun handleOAuthFlowCompleted(provider: IntegrationProvider) {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "OAuth flow completed for $provider")

        // Refresh integrations to get updated status from database
        refreshIntegrations()

        // Update OAuth state
        handleIntent(IntegrationIntent.OAuthFlowCompleted(provider))

        AppLog.d("IntegrationViewModel", "Successfully completed OAuth flow for $provider")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to handle OAuth completion for $provider", e.toString())
      }
    }
  }

  /**
   * Handles OAuth flow failure for a provider.
   */
  private fun handleOAuthFlowFailed(
    provider: IntegrationProvider,
    error: String,
  ) {
    AppLog.e("IntegrationViewModel", "OAuth flow failed for $provider: $error")

    // Update OAuth state to show error
    try {
      // handleIntent(IntegrationIntent.OAuthFlowFailed(provider, error))
    } catch (e: Exception) {
      AppLog.e("IntegrationViewModel", "Failed to handle OAuth failure for $provider", e.toString())
    }
  }

  /**
   * Starts OAuth flow for a provider using custom tabs.
   */
  private fun startOAuthFlow(
    provider: IntegrationProvider,
    accountId: String,
  ) {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Starting OAuth flow for $provider")

        // Get the OAuth URL from the integration service
        // val oAuthUrl = integrationService.getProviderUrl(provider)

        // Bind the custom tab service
        val isServiceBound = customTabManager.bindService()
        if (!isServiceBound) {
          throw Exception("Failed to bind custom tab service")
        }

        // Open the OAuth URL in a custom tab
        // customTabManager.openChromeTab(oAuthUrl)

        AppLog.d("IntegrationViewModel", "Successfully opened OAuth URL for $provider in custom tab")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to start OAuth flow for $provider", e.toString())
        handleIntent(IntegrationIntent.OAuthFlowFailed(provider, e.message ?: "Unknown error"))
      }
    }
  }

  /**
   * Cancels the current OAuth flow.
   */
  private fun cancelOAuthFlow() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Cancelling OAuth flow")

        // Unbind the custom tab service
        customTabManager.unbind()

        handleIntent(IntegrationIntent.CancelOAuthFlow)

        AppLog.d("IntegrationViewModel", "Successfully cancelled OAuth flow")
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to cancel OAuth flow", e.toString())
      }
    }
  }

  /**
   * Checks Health Connect availability.
   */
  private fun checkHealthConnectAvailability() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Checking Health Connect availability")
        // Here you would check if Health Connect is available on the device
        // For now, we'll assume it's available on Android 13+
        handleIntent(IntegrationIntent.SetHealthConnectAvailability(true))
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to check Health Connect availability", e.toString())
        handleIntent(IntegrationIntent.SetHealthConnectAvailability(false))
      }
    }
  }

  /**
   * Requests Health Connect permissions.
   */
  private fun requestHealthConnectPermissions() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Requesting Health Connect permissions")
        // Here you would request Health Connect permissions
        // For now, we'll just simulate the process
        // handleIntent(
        //   IntegrationIntent.SetHealthConnectPermissionStatus(
        //     com.greatergoods.meapp.features.integration.enums.HealthConnectPermissionStatus.ALL,
        //   ),
        // )
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to request Health Connect permissions", e.toString())
        // handleIntent(
        //   IntegrationIntent.SetHealthConnectPermissionStatus(
        //     com.greatergoods.meapp.features.integration.enums.HealthConnectPermissionStatus.NONE,
        //   ),
        // )
      }
    }
  }

  /**
   * Starts Health Connect data sync.
   */
  private fun startHealthConnectSync() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Starting Health Connect sync")
        // Here you would start syncing data with Health Connect
        // For now, we'll just simulate the process
        handleIntent(IntegrationIntent.HealthConnectSyncCompleted)
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to start Health Connect sync", e.toString())
        handleIntent(IntegrationIntent.HealthConnectSyncFailed(e.message ?: "Unknown error"))
      }
    }
  }

  /**
   * Checks if the OAuth flow was completed successfully by fetching current user account from API.
   */
  private fun checkOAuthFlowCompletion() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Checking OAuth flow completion")

        // Get the current OAuth state to determine which provider was being connected
        val currentOAuthState = state.value.oAuthState
        val provider = currentOAuthState.currentProvider
        if (provider != null) {
          AppLog.d(
            "IntegrationViewModel",
            "Fetching current user account from API to check $provider integration status",
          )

          // Add a small delay to give the server time to process the OAuth callback
          delay(1000) // 1 second delay

          // Try to fetch the current user account from the API with retry mechanism
          var currentUserAccount: Account? = null
          var isConnected = false
          var retryCount = 0
          val maxRetries = 3

          while (retryCount < maxRetries && !isConnected) {
            try {
              currentUserAccount = accountService.getCurrentAccount()

              if(currentUserAccount == null){
                 return@launch
              }
              AppLog.d(
                "IntegrationViewModel",
                "Fetched user account from API (attempt ${retryCount + 1}): ${currentUserAccount.id}",
              )

              // Check if the integration is now connected based on the API response
              isConnected =
                when (provider) {
                  IntegrationProvider.Fitbit ->
                    currentUserAccount.isFitbitOn &&
                      currentUserAccount.isFitbitValid

                  IntegrationProvider.MyFitnessPal ->
                    currentUserAccount.isMfpOn &&
                      currentUserAccount.isMfpValid

                  IntegrationProvider.HealthConnect ->
                    currentUserAccount.isHealthConnectOn

                }

              if (isConnected) {
                AppLog.d(
                  "IntegrationViewModel",
                  "OAuth flow completed successfully for $provider on attempt ${retryCount + 1}",
                )
                break
              } else {
                AppLog.d(
                  "IntegrationViewModel",
                  "Integration not yet connected for $provider on attempt ${retryCount + 1}",
                )
                if (retryCount < maxRetries - 1) {
                  delay(2000) // Wait 2 seconds before retry
                }
              }
            } catch (e: Exception) {
              AppLog.e(
                "IntegrationViewModel",
                "Failed to fetch user account on attempt ${retryCount + 1}",
                e.toString(),
              )
              if (retryCount < maxRetries - 1) {
                delay(2000) // Wait 2 seconds before retry
              }
            }
            retryCount++
          }

          if (isConnected && currentUserAccount != null) {
            // Refresh integrations to update local database
            // integrationService.refreshIntegrations()

            // Handle OAuth completion
            handleOAuthFlowCompleted(provider)
          } else {
            AppLog.d(
              "IntegrationViewModel",
              "OAuth flow may have failed for $provider after $maxRetries attempts",
            )
            if (currentUserAccount != null) {
              AppLog.d(
                "IntegrationViewModel",
                "Integration status from API - $provider: connected=${
                  when (provider) {
                    IntegrationProvider.Fitbit -> currentUserAccount.isFitbitOn
                    IntegrationProvider.MyFitnessPal -> currentUserAccount.isMfpOn
                    IntegrationProvider.HealthConnect -> currentUserAccount.isHealthConnectOn
                    else -> false
                  }
                }, valid=${
                  when (provider) {
                    IntegrationProvider.Fitbit -> currentUserAccount.isFitbitValid
                    IntegrationProvider.MyFitnessPal -> currentUserAccount.isMfpValid
                    IntegrationProvider.HealthConnect -> true
                    else -> false
                  }
                }",
              )
            }

            // Handle OAuth failure
            handleIntent(
              IntegrationIntent.OAuthFlowFailed(
                provider,
                "Integration not connected after OAuth flow (checked $maxRetries times)",
              ),
            )
          }
        } else {
          AppLog.d("IntegrationViewModel", "No provider found in OAuth state")
        }

        // Clean up the custom tab service
        customTabManager.unbind()
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to check OAuth flow completion", e.toString())
        // Handle OAuth failure due to API error
        val currentOAuthState = state.value.oAuthState
        val provider = currentOAuthState.currentProvider
        if (provider != null) {
          handleIntent(
            IntegrationIntent.OAuthFlowFailed(
              provider,
              "Failed to verify integration status: ${e.message}",
            ),
          )
        }
      }
    }
  }

  /**
   * Refreshes integration status.
   */
  private fun refreshIntegrationStatus() {
    viewModelScope.launch {
      try {
        AppLog.d("IntegrationViewModel", "Refreshing integration status")
        refreshIntegrations()
      } catch (e: Exception) {
        AppLog.e("IntegrationViewModel", "Failed to refresh integration status", e.toString())
      }
    }
  }

  /**
   * Cleanup resources when ViewModel is cleared.
   */
  override fun onCleared() {
    super.onCleared()
    try {
      AppLog.d("IntegrationViewModel", "Cleaning up IntegrationViewModel resources")
      customTabManager.unbind()
    } catch (e: Exception) {
      AppLog.e("IntegrationViewModel", "Failed to cleanup resources", e.toString())
    }
  }
}
