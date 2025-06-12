package com.greatergoods.meapp.features.integration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greatergoods.meapp.core.service.IntegrationService
import com.greatergoods.meapp.core.service.InvalidIntegrationAlert
import com.greatergoods.meapp.core.shared.utilities.browser.ChromeTabState
import com.greatergoods.meapp.core.shared.utilities.browser.ICustomTabManager
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.model.api.integration.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing integrations.
 */
@HiltViewModel
class IntegrationViewModel
    @Inject
    constructor(
        private val integrationService: IntegrationService,
        private val customTabManager: ICustomTabManager,
    ) : ViewModel() {
        private val _chromeTabState = MutableStateFlow<ChromeTabState>(ChromeTabState.Idle)
        val chromeTabState: StateFlow<ChromeTabState> = _chromeTabState

        private val _invalidIntegrations = MutableStateFlow<List<String>>(emptyList())
        val invalidIntegrations: StateFlow<List<String>> = _invalidIntegrations.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        // Navigation events for integration flow
        private val _navigateToIntegrationsEvent = MutableSharedFlow<Unit>()
        val navigateToIntegrationsEvent: SharedFlow<Unit> = _navigateToIntegrationsEvent.asSharedFlow()

        private val _showReintegrateAlert = MutableStateFlow<InvalidIntegrationAlert?>(null)
        val showReintegrateAlert: StateFlow<InvalidIntegrationAlert?> = _showReintegrateAlert

        init {
            viewModelScope.launch {
                customTabManager.subscribeChromeState().collect {
                    if (it != null) {
                        _chromeTabState.value = it
                    }
                }
            }

            // Observe integration check triggers
            viewModelScope.launch {
                integrationService.shouldCheckIntegrations.collect { shouldCheck ->
                    if (shouldCheck) {
                        integrationService.resetIntegrationCheck()
                        // You can trigger additional logic here if needed
                    }
                }
            }

            // Observe navigation events from service
            viewModelScope.launch {
                integrationService.navigateToIntegrationsEvent.collect { event ->
                    event?.let {
                        _navigateToIntegrationsEvent.emit(Unit)
                        integrationService.consumeNavigationEvent()
                    }
                }
            }
        }

        /**
         * Loads invalid integrations for the given account.
         */
        fun loadInvalidIntegrations(account: UserAccount) {
            _invalidIntegrations.value = integrationService.getInvalidIntegrationProviders(account)
        }

        /**
         * Checks for invalid integrations and shows alert if needed.
         *
         * @param account The current account
         * @param skipInvalidIntegrationsCheck Whether to skip the check (e.g., when on integrations page)
         */
        fun checkForInvalidIntegrations(skipInvalidIntegrationsCheck: Boolean = false) {
            viewModelScope.launch {
                try {
                    integrationService.checkForInvalidIntegrations(
                        skipInvalidIntegrationsCheck = skipInvalidIntegrationsCheck,
                        onOpenIntegrations = {
                            // This will trigger navigation via the service's StateFlow
                        },
                    )
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to check integrations: ${e.message}"
                }
            }
        }

        /**
         * Triggers integration check after login.
         */
        fun triggerIntegrationCheckAfterLogin() {
            integrationService.triggerIntegrationCheck()
        }

        /**
         * Shows reintegrate alert for specific providers.
         * Can be called manually if needed.
         *
         * @param providers List of invalid providers
         * @param skipInvalidIntegrationsCheck Whether to skip showing the alert
         */
        fun showReIntegrateAlert(
            providers: List<String>,
            skipInvalidIntegrationsCheck: Boolean = false,
        ) {
            integrationService.showReIntegrateAlert(
                providers = providers,
                skipInvalidIntegrationsCheck = skipInvalidIntegrationsCheck,
            )
        }

        /**
         * Adds an integration by opening the provider's OAuth URL in a browser.
         */
        fun addIntegration(provider: String) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    val enumProvider =
                        when (provider) {
                            "fitbit" -> IntegrationProvider.FITBIT
                            "mfPal" -> IntegrationProvider.MF_PAL
                            else -> throw IllegalArgumentException("Unknown provider: $provider")
                        }

                    val url = integrationService.getProviderUrl(enumProvider)
                    customTabManager.openChromeTab(url)
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to open $provider integration: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Removes an integration for the given provider.
         */
        fun removeIntegration(provider: String) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    integrationService.removeIntegration(provider)

                    // Remove from invalid integrations list
                    val currentInvalids = _invalidIntegrations.value.toMutableList()
                    currentInvalids.remove(provider)
                    _invalidIntegrations.value = currentInvalids
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to remove $provider integration: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Clears any error message.
         */
        fun clearError() {
            _errorMessage.value = null
        }

        override fun onCleared() {
            super.onCleared()
            customTabManager.unbind()
        }
    }
