package com.greatergoods.meapp.features.integration.viewmodel

import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.shared.utilities.browser.ChromeTabState
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.model.storage.Account.Account
import com.greatergoods.meapp.domain.repository.IHealthConnectRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IHealthConnectService
import com.greatergoods.meapp.domain.services.IIntegrationService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.common.service.BaseIntentViewModel
import com.greatergoods.meapp.features.integration.model.IntegrationIntent
import com.greatergoods.meapp.features.integration.model.IntegrationItem
import com.greatergoods.meapp.features.integration.model.IntegrationReducer
import com.greatergoods.meapp.features.integration.model.IntegrationState
import com.greatergoods.meapp.features.integration.strings.HealthConnectStrings
import com.greatergoods.meapp.features.integration.strings.IntegrationStrings
import com.greatergoods.meapp.resources.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
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
    private val integrationRepository: IIntegrationRepository // Inject IntegrationRepository for integrations flow
) : BaseIntentViewModel<IntegrationState, IntegrationIntent>(
    reducer = IntegrationReducer(),
) {

    /**
     * Current active account, updated automatically from accountService.
     */
    private var currentAccount: Account? = null

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
                healthConnectRepository.updateIntegrationStateFromLocalStorage()
                AppLog.d("IntegrationViewModel", "Active account updated: ${account?.id}")
            }
        }

        // Observe integrations from IntegrationRepository (like BehaviorSubject)
        viewModelScope.launch {
            integrationRepository.integrations.collectLatest { integrations ->
                if (integrations != null) {
                    loadIntegrations()
                }
            }
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
            is IntegrationIntent.ConfirmDisconnect -> confirmDisconnect()
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
                AppLog.d("IntegrationViewModel", "Successfully removed Health Connect integration")
            } catch (e: Exception) {
                AppLog.e(
                    "IntegrationViewModel",
                    "Failed to remove Health Connect integration",
                    e.toString()
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
          CoroutineScope(Dispatchers.IO).launch {
            healthConnectService.removeHealthConnectIntegration()

            // Update local storage and sync state
            currentAccount?.let { account ->
              healthConnectRepository.updateHealthConnectIntegrationStatus(account.id, false)
            }

            // Update UI state
            handleIntent(
              IntegrationIntent.UpdateIntegrationConnectionStatus(
                IntegrationProvider.HealthConnect,
                isConnected = false,
                isValid = false,
              )
            )
            dialogQueueService.showToast(
              Toast(HealthConnectStrings.ToastStrings.removeHC)
            )
            dialogQueueService.dismissCurrent()
            dialogQueueService.dismissLoader()
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

  private fun subscribeBrowserState(){
    viewModelScope.launch { try {
      customTabManager.subscribeChromeState().collect { state ->
        when (state) {
          ChromeTabState.TabHidden -> {
            AppLog.d("IntegrationViewModel", "Custom tab hidden - OAuth flow may be completed")
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
    catch (e: Exception){
      AppLog.e(
        "IntegrationViewModel",
        "Failed to setup custom tab monitoring",
        e.toString()
      )
    }
    }
  }
    /**
     * Loads all integrations with their current connection status.
     */
    private fun loadIntegrations() {
      subscribeBrowserState()
        viewModelScope.launch {
            try {
                handleIntent(IntegrationIntent.InitializeIntegrations)
                integrationService.getIntegrationsWithStatus().collect { integrations ->
                    // Update integrations state to show proper checkmarks
                    val updatedIntegrations = integrations.map { integration ->
                        if (integration.provider == IntegrationProvider.HealthConnect) {
                            val currentAccount = currentAccount
                            // Get Health Connect status from local storage (like Angular service)
                            val isHealthConnectOn = currentAccount?.isHealthConnectOn == true
                            val healthConnectData = currentAccount?.let { account ->
                                healthConnectRepository.getAccountByID(account.id)
                            }
                            val isOutOfSync = healthConnectData?.outOfSync == true

                            integration.copy(
                                isConnected = isHealthConnectOn,
                                iconRes = if (isHealthConnectOn && isOutOfSync) AppIcons.Integrations.Health_Connect_Off else AppIcons.Integrations.Health_Connect_Logo,
                            )
                        } else {
                            integration
                        }
                    }
                    handleIntent(IntegrationIntent.SetIntegrations(updatedIntegrations))

                    // Update the HealthConnectRepository state to sync with local storage
                    healthConnectRepository.updateIntegrationStateFromLocalStorage()

                    AppLog.d("IntegrationViewModel", "Loaded ${updatedIntegrations.size} integrations with status")
                    if (!skipInvalidIntegrationsCheck) {
                        val inactiveProviders = checkForInactiveIntegrations()
                        if (inactiveProviders.isNotEmpty()) {
                            AppLog.d("IntegrationViewModel", "Found ${inactiveProviders.size} inactive integrations, showing alert")
                            showReintegrateAlert(inactiveProviders)
                        }
                    } else {
                        checkForInactiveIntegrations()
                    }
                }
            } catch (e: Exception) {
                AppLog.e("IntegrationViewModel", "Failed to load integrations", e.toString())
            }
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
                    AppLog.w("IntegrationViewModel", "No current account available for connecting to $provider")
                    return@launch
                }
                if (provider.requiresOAuth()) {
                    startOAuthFlow(provider = provider, accountId = accountId)
                } else if (provider.isPlatformSpecific()) {
                    handleIntent(IntegrationIntent.CheckHealthConnectAvailability)
                } else {
                    AppLog.w("IntegrationViewModel", "Unsupported provider type: $provider")
                }
            } catch (e: Exception) {
                AppLog.e("IntegrationViewModel", "Failed to connect to $provider", e.toString())
            }
        }
    }



    /**
     * Refreshes integration status from the server.
     */
    private fun refreshIntegrationStatus() {
        viewModelScope.launch {
            AppLog.d("IntegrationViewModel", "Refreshing integration status")

            runCatching {
                integrationService.getIntegrationsWithStatus().first()
            }.onSuccess { integrations ->
                handleIntent(IntegrationIntent.SetIntegrations(integrations))
                AppLog.d("IntegrationViewModel", "Successfully refreshed integration status - ${integrations.size} integrations updated")
            }.onFailure { e ->
                AppLog.e(
                    "IntegrationViewModel",
                    "Failed to refresh integration status",
                    e.toString()
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
                AppLog.e("IntegrationViewModel", "Failed to navigate back", e.toString())
            }
        }
    }

    /**
     * Starts OAuth flow for a provider using custom tabs.
     */
    private fun startOAuthFlow(provider: IntegrationProvider, accountId: String) {
        viewModelScope.launch {
            try {
                AppLog.d("IntegrationViewModel", "Starting OAuth flow for $provider")
                // Set the current OAuth provider locally
                currentOAuthProvider = provider
                dialogQueueService.showLoader(
                    "Loading..."
                )
                val oAuthUrl = integrationService.getOAuthUrl(provider, accountId)
                customTabManager.openChromeTab(oAuthUrl)
                dialogQueueService.dismissLoader()
            } catch (e: Exception) {
                AppLog.e(
                    "IntegrationViewModel",
                    "Failed to start OAuth flow for $provider",
                    e.toString()
                )
                handleIntent(
                    IntegrationIntent.OAuthFlowFailed(
                        provider,
                        e.message ?: "Unknown error"
                    )
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
            AppLog.w("IntegrationViewModel", "No current OAuth provider found - OAuth flow may have been cancelled or not properly initialized")
            return
        }
        viewModelScope.launch {
            AppLog.d("IntegrationViewModel", "Checking OAuth flow completion for provider: $currentProvider")
            runCatching {
                val (isConnected, _) = integrationService.getIntegrationStatus(currentProvider)
                if (isConnected) {
                    // OAuth was successful
                    AppLog.d("IntegrationViewModel", "OAuth flow completed successfully for $currentProvider")
                    val integrations = integrationService.getIntegrationsWithStatus().first()
                    handleIntent(IntegrationIntent.SetIntegrations(integrations))
                    showSuccessAlert()
                } else {
                    // OAuth may have failed or been cancelled
                    AppLog.w("IntegrationViewModel", "OAuth flow completed but integration not connected for $currentProvider")
                    showErrorAlert()
                }
            }.onFailure { e ->
                AppLog.e("IntegrationViewModel", "Failed to check OAuth flow completion for $currentProvider", e.toString())
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
                AppLog.d("IntegrationViewModel", "Disconnecting from ${integration.provider}")
                integrationService.disconnectIntegration(integration.provider)
                // Immediately update UI to show disconnected state
                handleIntent(
                    IntegrationIntent.UpdateIntegrationConnectionStatus(
                        integration.provider,
                        isConnected = false,
                        isValid = true
                    )
                )
                AppLog.d("IntegrationViewModel", "Successfully disconnected from ${integration.provider}")
            } catch (e: Exception) {
                AppLog.e(
                    "IntegrationViewModel",
                    "Failed to disconnect from ${integration.provider}",
                    e.toString()
                )
                handleIntent(
                    IntegrationIntent.UpdateIntegrationConnectionStatus(
                        integration.provider,
                        isConnected = integration.isConnected,
                        isValid = false // Mark as invalid if disconnect failed
                    )
                )
            }
        }
    }

    /**
     * Checks for inactive integrations and returns them.
     * This method does NOT update state variables to avoid interfering with UI checkmarks.
     * It directly queries the integration service to determine validity.
     * @return List of IntegrationProvider that are inactive
     */
    private suspend fun checkForInactiveIntegrations(): List<IntegrationProvider> {
        return try {
            AppLog.d("IntegrationViewModel", "Checking for inactive integrations via API only")
            val inactiveIntegrations = mutableListOf<IntegrationProvider>()
            val allProviders = IntegrationProvider.getAllProviders()
            for (provider in allProviders) {
                try {
                    val (isConnected, isValid) = integrationService.getIntegrationStatus(provider)
                    if (isConnected && !isValid) {
                        inactiveIntegrations.add(provider)
                        AppLog.d("IntegrationViewModel", "Found inactive integration: $provider (connected but invalid)")
                    }
                } catch (e: Exception) {
                    AppLog.e("IntegrationViewModel", "Failed to check status for $provider", e.toString())
                }
            }
            AppLog.d("IntegrationViewModel", "Found ${inactiveIntegrations.size} inactive integrations: $inactiveIntegrations")
            inactiveIntegrations
        } catch (e: Exception) {
            AppLog.e("IntegrationViewModel", "Failed to check for inactive integrations", e.toString())
            emptyList()
        }
    }

    /**
     * Gets the names of invalid integrations from the provided list.
     * @param providers List of IntegrationProvider to get names for
     * @return List of display names for the provided integration providers
     */
    private fun getInvalidIntegrationNames(providers: List<IntegrationProvider>): List<String> {
        return providers.map { it.displayName }
            .also { names ->
                AppLog.d("IntegrationViewModel", "Invalid integration names: $names")
            }
    }

    /**
     * Gets the names of all invalid integrations from current state.
     * @return List of display names for integrations that are connected but invalid
     */
    private fun getAllInvalidIntegrationNames(): List<String> {
        return state.value.integrations
            .filter { it.isConnected && !it.isValid }
            .map { it.provider.displayName }
            .also { names ->
                AppLog.d("IntegrationViewModel", "Found ${names.size} invalid integrations: $names")
            }
    }

    /**
     * Public method to get invalid integration names for UI display.
     * @return List of display names for integrations that are connected but invalid
     */
    fun getInvalidIntegrationNamesForUI(): List<String> {
        return getAllInvalidIntegrationNames()
    }

    /**
     * Shows reintegrate alert for inactive integrations.
     * @param inactiveProviders List of inactive integration providers
     */
    private fun showReintegrateAlert(inactiveProviders: List<IntegrationProvider>) {
        val integrationNames = getInvalidIntegrationNames(inactiveProviders)
        val namesText = integrationNames.joinToString(", ")

        val isMultiple = inactiveProviders.size > 1
        val disableButtonText = if (isMultiple) {
            IntegrationStrings.removeAllIntegrations
        } else {
            IntegrationStrings.removeIntegration(namesText)
        }

        val pluralityText = if (isMultiple) {
            IntegrationStrings.pluralityThese
        } else {
            IntegrationStrings.pluralityThis
        }

        dialogQueueService.enqueue(
          DialogModel.Confirm(
                title = IntegrationStrings.reintegrateAlertTitle,
                message = IntegrationStrings.reintegrateAlertMessage(pluralityText, namesText),
                confirmText = disableButtonText,
                cancelText = IntegrationStrings.ok,
                onConfirm = {
                    handleDisableInactiveIntegrations(inactiveProviders)
                    dialogQueueService.dismissCurrent()
                },
                onCancel = {
                    dialogQueueService.dismissCurrent()
                }
            )
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
                    AppLog.d("IntegrationViewModel", "Disabling inactive integration: $provider")

                    // Immediately update UI to show disconnected state
                    handleIntent(
                        IntegrationIntent.UpdateIntegrationConnectionStatus(
                            provider,
                            isConnected = false,
                            isValid = true
                        )
                    )

                    integrationService.disconnectIntegration(provider)
                    disableResults.add(true)
                    AppLog.d("IntegrationViewModel", "Successfully disabled inactive integration: $provider")
                } catch (e: Exception) {
                    AppLog.e("IntegrationViewModel", "Failed to disable inactive integration: $provider", e.toString())
                    disableResults.add(false)

                    // On error, mark as invalid (still connected but invalid)
                    handleIntent(
                        IntegrationIntent.UpdateIntegrationConnectionStatus(
                            provider,
                            isConnected = true,
                            isValid = false
                        )
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
                        onDismiss = { dialogQueueService.dismissCurrent() }
                    )
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
                    dialogQueueService.dismissCurrent()
                },
                onCancel = {
                    dialogQueueService.dismissCurrent()
                },
                onDismiss = {
                  dialogQueueService.dismissCurrent()
                }
            ),
        )
    }

    private fun showSuccessAlert() {
        dialogQueueService.enqueue(
          DialogModel.Alert(
                title = "",
                message = IntegrationStrings.done,
                dismissText = IntegrationStrings.ok,
                onDismiss = {
                    dialogQueueService.dismissCurrent()
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
                AppLog.d("IntegrationViewModel", "Navigating to Health Connect integration")
            } catch (e: Exception) {
                AppLog.e("IntegrationViewModel", "Failed to navigate to Health Connect", e.toString())
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
