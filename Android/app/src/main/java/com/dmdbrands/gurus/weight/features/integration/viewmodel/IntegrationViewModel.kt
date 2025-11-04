package com.dmdbrands.gurus.weight.features.integration.viewmodel

import androidx.lifecycle.viewModelScope
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ChromeTabState
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.service.BaseIntentViewModel
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationReducer
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationState
import com.dmdbrands.gurus.weight.features.integration.strings.HealthConnectStrings
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing Integration screen state and handling user intents.
 */
@HiltViewModel
class IntegrationViewModel @Inject constructor(
  private val accountService: IAccountService,
  private val integrationService: IIntegrationService,
  private val healthConnectService: IHealthConnectService,
  private val healthConnectRepository: IHealthConnectRepository, // Inject repository
  private val integrationRepository: IIntegrationRepository
) : BaseIntentViewModel<IntegrationState, IntegrationIntent>(
  reducer = IntegrationReducer(),
) {

  /**
   * Current active account, updated automatically from accountService.
   */
  private var currentAccount: Account? = null
  private var isTabHidden = false
  private val TAG = "IntegrationViewModel"
  /**
   * Current OAuth provider being processed (tracked locally for OAuth flow).
   */
  private var currentOAuthProvider: IntegrationProvider? = null

  /**
   * Whether to skip the invalid integrations check on initialization.
   */
  private var skipInvalidIntegrationsCheck: Boolean = false

  override fun provideInitialState(): IntegrationState = IntegrationState()

  init {
    // Update currentAccount variable for local use
    viewModelScope.launch {
      accountService.activeAccountFlow.collectLatest { account ->
        currentAccount = account
        AppLog.d(TAG, "Active account updated: ${account?.id}")
      }

      loadIntegrations()
    }
  }

  /**
   * Handles incoming intents and updates the state accordingly.
   * @param intent The intent to handle.
   */
  override fun handleIntent(intent: IntegrationIntent) {
    super.handleIntent(intent)
    when (intent) {
      is IntegrationIntent.LoadIntegrations -> loadIntegrations()
      is IntegrationIntent.OpenIntegration -> openIntegration(intent.integrations)
      is IntegrationIntent.AddIntegration -> addIntegrations(intent.provider)
      is IntegrationIntent.RemoveIntegration -> disconnectAuthIntegration()
      is IntegrationIntent.OnBack -> onBack()
      is IntegrationIntent.NavigateToHealthConnect -> handleHealthConnectNavigation()
      is IntegrationIntent.RemoveHealthConnectIntegration -> removeHealthConnectIntegration()
      else -> {
        // Handle other intents that don't need special processing
      }
    }
  }

  private fun handleHealthConnectNavigation() {
    navigateToHealthConnect()
  }

  private fun removeHealthConnectIntegration() {
    viewModelScope.launch {
      try {
        confirmRemoveIntegration()
        AppLog.d(TAG, "Successfully removed Health Connect integration")
      } catch (e: Exception) {
        AppLog.e(
          TAG,
          "Failed to remove Health Connect integration",
          e,
        )
      }
    }
  }

  private fun confirmRemoveIntegration() {
    dialogQueueService.showDialog(
      DialogModel.Confirm(
        title = HealthConnectStrings.PopupStrings.removeHCIntegrationTitle,
        message = HealthConnectStrings.PopupStrings.removeHCIntegrationMessage,
        confirmText = HealthConnectStrings.ActionButtons.remove,
        cancelText = HealthConnectStrings.ActionButtons.cancel,
        onConfirm = {
          dialogQueueService.showLoader("Removing...")
          viewModelScope.launch {
            try {
              healthConnectService.removeHealthConnectIntegration()
              dialogQueueService.dismissLoader()
              dialogQueueService.dismissCurrent()
              refreshIntegrationStatus()
            } catch (e: Exception) {
            } finally {
              // Refresh integrations to get updated status from server
              dialogQueueService.showToast(
                Toast(HealthConnectStrings.ToastStrings.removeHC),
              )
            }
          }
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  private fun subscribeBrowserState() {
    viewModelScope.launch {
      try {
        customTabManager.subscribeChromeState().distinctUntilChanged().collect { state ->
          // Only process Chrome tab events if OAuth flow is active
          if (currentOAuthProvider == null) {
            AppLog.d(TAG, "Chrome tab state changed but no OAuth flow active - ignoring")
            return@collect
          }

          when (state) {
            ChromeTabState.TabHidden -> {
              AppLog.d(TAG, "Custom tab hidden - OAuth flow may be completed")
              if (!isTabHidden) {
                isTabHidden = true
                checkOAuthFlowCompletion()
              }
              AppLog.d(TAG, "Custom tab hidden - OAuth flow may be completed hello")
            }

            ChromeTabState.TabShown -> {
              isTabHidden = false
              AppLog.d(TAG, "Custom tab shown")
            }

            null -> {
              AppLog.d(TAG, "Custom tab state is null")
            }

            else -> {}
          }
        }
      } catch (e: Exception) {
        AppLog.e(
          TAG,
          "Failed to setup custom tab monitoring",
          e,
        )
      } finally {
        isTabHidden = false
        // Reset OAuth provider after flow completes
        currentOAuthProvider = null
      }
    }
  }

  /**
   * Loads all integrations with their current connection status.
   */
  private fun loadIntegrations() {
    viewModelScope.launch {
      try {
        handleIntent(IntegrationIntent.InitializeIntegrations)
        integrationService.getIntegrationsWithStatus().collectLatest { integrations ->
          val updatedIntegrations = integrations.map { integration ->
            if (integration.provider == IntegrationProvider.HealthConnect) {
              val currentAccount = currentAccount
              val healthConnectData = currentAccount?.let { account ->
                healthConnectRepository.getAccountByID(account.id)
              }
              val isHealthConnectOn = healthConnectData?.integrated ?: false
              val isOutOfSync = healthConnectData?.outOfSync ?: false
              integration.copy(
                isConnected = isHealthConnectOn,
                iconRes = if (isHealthConnectOn && isOutOfSync) AppIcons.Integrations.Health_Connect_Off else AppIcons.Integrations.Health_Connect_Logo,
              )
            } else {
              integration
            }
          }
          handleIntent(IntegrationIntent.SetIntegrations(updatedIntegrations))
          AppLog.d(TAG, "Loaded ${updatedIntegrations.size} integrations with status")
          if (!skipInvalidIntegrationsCheck) {
            val inactiveProviders = integrationService.checkForInactiveIntegrations()
            if (inactiveProviders.isNotEmpty()) {
              AppLog.d(TAG, "Found ${inactiveProviders.size} inactive integrations, showing alert")
              showReintegrateAlert(inactiveProviders)
            }
            skipInvalidIntegrationsCheck = true
          }
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to load integrations", e)
      }
      subscribeBrowserState()

    }
  }

  private fun openIntegration(integration: IntegrationItem) {
    if (integration.provider == IntegrationProvider.HealthConnect) {
      viewModelScope.launch {
        val isHealthConnectOn = currentAccount?.isHealthConnectOn == true
        // Get Health Connect data from local storage (like Angular service)
        val healthConnectData = currentAccount?.let { account ->
          healthConnectRepository.getAccountByID(account.id)
        }
        val permissionList = healthConnectData?.grantedPermissionList ?: emptyList()
        val isOutOfSync = isHealthConnectOn && permissionList.isEmpty()
        if (isOutOfSync) {
          // Show out of sync alert (call HealthConnectViewModel's alert or show directly)
          showOutOfSyncAlert()
        } else {
          if (integration.isConnected) {
            handleIntent(IntegrationIntent.RemoveIntegration(integration))
          } else {
            handleIntent(
              IntegrationIntent.AddIntegration(
                provider = integration.provider,
              ),
            )
          }
        }
      }
    } else {
      if (integration.isConnected) {
        handleIntent(IntegrationIntent.RemoveIntegration(integration))
      } else {
        handleIntent(
          IntegrationIntent.AddIntegration(
            provider = integration.provider,
          ),
        )
      }
    }
  }

  private fun showOutOfSyncAlert() {
    dialogQueueService.enqueue(
      DialogModel.Alert(
        title = HealthConnectStrings.OutOfSyncAlert.title,
        message = HealthConnectStrings.OutOfSyncAlert.description,
        dismissText = HealthConnectStrings.ActionButtons.close,
        onDismiss = {
          // Optionally navigate back or just dismiss
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Connects to the specified integration provider.
   */
  private fun addIntegrations(provider: IntegrationProvider) {
    viewModelScope.launch {
      try {
        val accountId = currentAccount?.id ?: run {
          AppLog.w(TAG, "No current account available for connecting to $provider")
          return@launch
        }
        if (provider.requiresOAuth()) {
          startOAuthFlow(provider = provider, accountId = accountId)
        } else if (provider.isPlatformSpecific()) {
          handleIntent(IntegrationIntent.CheckHealthConnectAvailability)
        } else {
          AppLog.w(TAG, "Unsupported provider type: $provider")
        }
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to connect to $provider", e)
      }
    }
  }

  /**
   * Refreshes integration status from the server.
   */
  private fun refreshIntegrationStatus() {
    viewModelScope.launch {
      AppLog.d(TAG, "Refreshing integration status")
      runCatching {
        integrationService.getIntegrationsWithStatus().first()
      }.onSuccess { integrations ->
        handleIntent(IntegrationIntent.SetIntegrations(integrations))
        AppLog.d(
          TAG,
          "Successfully refreshed integration status - ${integrations.size} integrations updated",
        )
      }.onFailure { e ->
        AppLog.e(
          TAG,
          "Failed to refresh integration status",
          e,
        )
      }
    }
  }

  /**
   * Handles back navigation.
   */
  private fun onBack() {
    viewModelScope.launch {
      try {
        navigationService.navigateBack()
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate back", e)
      }
    }
  }

  /**
   * Starts OAuth flow for a provider using custom tabs.
   */
  private fun startOAuthFlow(provider: IntegrationProvider, accountId: String) {
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Starting OAuth flow for $provider")
        // Set the current OAuth provider locally
        currentOAuthProvider = provider
        dialogQueueService.showLoader(
          "Loading...",
        )
        val oAuthUrl = integrationService.getOAuthUrl(provider, accountId)
        customTabManager.openChromeTab(oAuthUrl ?: "")
        dialogQueueService.dismissLoader()
      } catch (e: Exception) {
        AppLog.e(
          TAG,
          "Failed to start OAuth flow for $provider",
          e,
        )
        handleIntent(
          IntegrationIntent.OAuthFlowFailed(
            provider,
            e.message ?: "Unknown error",
          ),
        )
      }
    }
  }

  /**
   * Checks if OAuth flow was completed by examining the current account status.
   */
  private fun checkOAuthFlowCompletion() {
    val currentProvider = currentOAuthProvider
    if (currentProvider == null) {
      AppLog.w(
        TAG,
        "No current OAuth provider found - OAuth flow may have been cancelled or not properly initialized",
      )
      return
    }
    dialogQueueService.showLoader("Loading...")
    viewModelScope.launch {
      AppLog.d(TAG, "Checking OAuth flow completion for provider: $currentProvider")
      try {
        val (isConnected, _) = integrationService.getIntegrationStatus(currentProvider)
        if (isConnected) {
          // OAuth was successful
          AppLog.d(TAG, "OAuth flow completed successfully for $currentProvider")
          val integrations = integrationService.getIntegrationsWithStatus().first()
          handleIntent(IntegrationIntent.SetIntegrations(integrations))
          showSuccessAlert()
        } else {
          // OAuth may have failed or been cancelled
          AppLog.w(TAG, "OAuth flow completed but integration not connected for $currentProvider")
          showErrorAlert()
        }
      }catch (e: Exception) {
        AppLog.e(TAG, "Failed to check OAuth flow completion for $currentProvider", e)
      }
      finally {
          dialogQueueService.dismissLoader()
      }
    }
  }

  private fun disconnectAuthIntegration() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = IntegrationStrings.removeIntegration,
        message = IntegrationStrings.removeAuthIntegration,
        confirmText = IntegrationStrings.remove,
        cancelText = IntegrationStrings.cancel,
        onConfirm = {
          confirmDisconnect()
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Confirms the disconnect action and proceeds with disconnection.
   */
  private fun confirmDisconnect() {
    val selectedIntegration = state.value.selectedIntegrationForDisconnect
    if (selectedIntegration != null) {
      disconnectIntegration(selectedIntegration)
    }
  }

  /**
   * Disconnects from the specified integration.
   */
  private fun disconnectIntegration(integration: IntegrationItem) {
    viewModelScope.launch {
      try {
        AppLog.d(TAG, "Disconnecting from ${integration.provider}")
        // Show loading while disconnecting
        dialogQueueService.showLoader("Disconnecting...")

        // Attempt to disconnect
        integrationService.disconnectIntegration(integration.provider)

        // Only update UI after successful disconnection
        refreshIntegrationStatus() // This will get fresh state from server

        AppLog.d(TAG, "Successfully disconnected from ${integration.provider}")
        dialogQueueService.dismissLoader()

        // Show success toast
        dialogQueueService.showToast(
          Toast(
            message = "Successfully disconnected from ${integration.name}",
          ),
        )
      } catch (e: Exception) {
        AppLog.e(
          TAG,
          "Failed to disconnect from ${integration.provider}",
          e,
        )
        dialogQueueService.dismissLoader()

        // Show error toast
        dialogQueueService.showToast(
          Toast(
            message = "Failed to disconnect from ${integration.name}",
          ),
        )

        // Refresh to ensure UI shows correct state
        refreshIntegrationStatus()
      }
    }
  }



  /**
   * Shows reintegrate alert for inactive integrations.
   * @param inactiveProviders List of inactive integration providers
   */
  private fun showReintegrateAlert(inactiveProviders: List<IntegrationProvider>) {
    val providerNames = integrationService.getInvalidIntegrationNames(inactiveProviders)

    val isMultiple = inactiveProviders.size > 1
    val disableButtonText = if (isMultiple) {
      IntegrationStrings.removeAllIntegrations
    } else {
      IntegrationStrings.removeIntegration(providerNames)
    }
    val pluralityText = if (isMultiple) {
      IntegrationStrings.pluralityThese
    } else {
      IntegrationStrings.pluralityThis
    }
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = IntegrationStrings.reintegrateAlertTitle,
        message = IntegrationStrings.reintegrateAlertMessage(pluralityText, providerNames),
        confirmText = disableButtonText,
        cancelText = IntegrationStrings.ok,
        onConfirm = {
          handleDisableInactiveIntegrations(inactiveProviders)
          dialogQueueService.dismissCurrent()
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  /**
   * Handles disabling inactive integrations.
   * @param inactiveProviders List of inactive integration providers to disable
   */
  private fun handleDisableInactiveIntegrations(inactiveProviders: List<IntegrationProvider>) {
    viewModelScope.launch {
      val disableResults = mutableListOf<Boolean>()

      for (provider in inactiveProviders) {
        try {
          AppLog.d(TAG, "Disabling inactive integration: $provider")

          // Immediately update UI to show disconnected state
          handleIntent(
            IntegrationIntent.UpdateIntegrationConnectionStatus(
              provider,
              isConnected = false,
              isValid = true,
            ),
          )
          integrationService.disconnectIntegration(provider)
          disableResults.add(true)
          AppLog.d(TAG, "Successfully disabled inactive integration: $provider")
        } catch (e: Exception) {
          AppLog.e(TAG, "Failed to disable inactive integration: $provider", e)
          disableResults.add(false)
          // On error, mark as invalid (still connected but invalid)
          handleIntent(
            IntegrationIntent.UpdateIntegrationConnectionStatus(
              provider,
              isConnected = true,
              isValid = false,
            ),
          )
        }
      }
      refreshIntegrationStatus()
      if (disableResults.all { it } && inactiveProviders.size > 1) {
        dialogQueueService.enqueue(
          DialogModel.Alert(
            title = "",
            message = IntegrationStrings.done,
            dismissText = IntegrationStrings.ok,
            onDismiss = { dialogQueueService.dismissCurrent() },
          ),
        )
      }
    }
  }

  private fun showErrorAlert() {
    dialogQueueService.enqueue(
      DialogModel.Confirm(
        title = IntegrationStrings.failed,
        message = IntegrationStrings.authIntegrationCancelORFailed,
        confirmText = IntegrationStrings.retry,
        cancelText = IntegrationStrings.cancel,
        onConfirm = {
          // Retry the OAuth flow for the current provider
          currentOAuthProvider?.let { provider ->
            currentAccount?.let { account ->
              startOAuthFlow(provider = provider, accountId = account.id)
            }
          }
          dialogQueueService.dismissCurrent()
          isTabHidden = false
        },
        onCancel = {
          dialogQueueService.dismissCurrent()
          isTabHidden = false
          currentOAuthProvider = null // Clear OAuth provider
        },
        onDismiss = {
          dialogQueueService.dismissCurrent()
          isTabHidden = false
          currentOAuthProvider = null // Clear OAuth provider
        },
      ),
    )
  }

  private fun showSuccessAlert() {
    dialogQueueService.enqueue(
      DialogModel.Alert(
        title = null,
        message = IntegrationStrings.done,
        dismissText = IntegrationStrings.ok,
        onDismiss = {
          dialogQueueService.dismissCurrent()
          isTabHidden = false
          currentOAuthProvider = null // Clear OAuth provider
        },
        ),
    )
  }

  /**
   * Navigates to the Health Connect integration screen.
   */
  private fun navigateToHealthConnect() {
    viewModelScope.launch {
      try {
        navigationService.navigateTo(AppRoute.Integration.HealthConnect)
        AppLog.d(TAG, "Navigating to Health Connect integration")
      } catch (e: Exception) {
        AppLog.e(TAG, "Failed to navigate to Health Connect", e)
      }
    }
  }

  fun onHealthConnectIconClicked() {
    viewModelScope.launch {
      val isHealthConnectOn = currentAccount?.isHealthConnectOn == true
      // Get Health Connect data from local storage (like Angular service)
      val healthConnectData = currentAccount?.let { account ->
        healthConnectRepository.getAccountByID(account.id)
      }
      val permissionList = healthConnectData?.grantedPermissionList ?: emptyList()
      val isOutOfSync = isHealthConnectOn && permissionList.isEmpty()
      if (isOutOfSync) {
        showOutOfSyncAlert()
      }
    }
  }
}
