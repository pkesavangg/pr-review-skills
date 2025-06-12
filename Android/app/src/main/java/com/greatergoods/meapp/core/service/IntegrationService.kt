package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.data.storage.db.entity.account.Account
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.model.api.integration.UserAccount
import com.greatergoods.meapp.domain.model.api.user.AccountResponse
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.services.IIntegrationService
import com.greatergoods.meapp.features.common.model.DialogModel
import com.greatergoods.meapp.features.common.service.DialogQueueService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/**
 * Data class for invalid integration alert information.
 */
data class InvalidIntegrationAlert(
    val providers: List<String>,
    val message: String,
    val disableButtonText: String,
    val openIntegrationsButtonText: String = "Open Integrations"
)

/**
 * Service for managing integrations with third-party providers.
 */
@Singleton
class IntegrationService @Inject constructor(
    private val repository: IIntegrationRepository,
    private val dialogQueueService: DialogQueueService
) : IIntegrationService {
    lateinit var currentAccount: UserAccount

    init {
        // Eagerly fetch on service creation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                currentAccount = repository.fetchAccount()
            } catch (e: Exception) {
            }
        }
    }

    private val _invalidIntegrationAlert = MutableStateFlow<InvalidIntegrationAlert?>(null)
    override val invalidIntegrationAlert: StateFlow<InvalidIntegrationAlert?> = _invalidIntegrationAlert.asStateFlow()

    private val _shouldCheckIntegrations = MutableStateFlow(false)
    override val shouldCheckIntegrations: StateFlow<Boolean> = _shouldCheckIntegrations.asStateFlow()

    // Events for integration actions
    private val _navigateToIntegrationsEvent = MutableStateFlow<Unit?>(null)
    override val navigateToIntegrationsEvent: StateFlow<Unit?> = _navigateToIntegrationsEvent.asStateFlow()

    /**
     * Gets the active account ID.
     */
    override suspend fun getActiveAccountId(): String {
        return repository.getActiveAccountId()
    }

    /**
     * Gets the provider OAuth URL for the given integration provider.
     */
    override suspend fun getProviderUrl(provider: IntegrationProvider): String {
        return try {
            val accountId = repository.getActiveAccountId() // fetch real value
            when (provider) {
                IntegrationProvider.FITBIT -> AppConfig.integrations.fitbit(accountId)
                IntegrationProvider.MF_PAL -> AppConfig.integrations.mfPal(accountId)
            }
        } catch (e: Exception) {
            throw e // ✅ Don't return empty, let it fail explicitly
        }
    }


    /**
     * Removes an integration for the given provider.
     */
    override suspend fun removeIntegration(provider: String) {
        repository.removeIntegration(provider)
        refreshIntegrations()
    }

    /**
     * Removes multiple integrations for the given providers.
     */
    override suspend fun removeMultipleIntegrations(providers: List<String>) {
        try {
            providers.forEach { provider ->
                repository.removeIntegration(provider)
            }
            refreshIntegrations()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Gets a list of invalid integration providers for the given account.
     */
    override fun getInvalidIntegrationProviders(account: UserAccount): List<String> {
        val invalids = mutableListOf<String>()
        if (account.isFitbitOn && !account.isFitbitValid) invalids.add("fitbit")
        if (account.isGoogleFitOn && !account.isGoogleFitValid) invalids.add("googleFit")
        if (account.isMFPOn && !account.isMFPValid) invalids.add("mfPal")
        if (account.isUAOn && !account.isUAValid) invalids.add("uArmor")
        return invalids
    }

    /**
     * Refreshes integrations from the server and updates local account.
     */
    override suspend fun refreshIntegrations() {
        repository.updateLocalAccount()
    }

    /**
     * Checks if the account has any invalid integrations.
     */
    override fun hasInvalidIntegrations(account: UserAccount): Boolean {
        return (account.isFitbitOn && !account.isFitbitValid) ||
                (account.isGoogleFitOn && !account.isGoogleFitValid) ||
                (account.isMFPOn && !account.isMFPValid) ||
                (account.isUAOn && !account.isUAValid)
    }

    /**
     * Shows a reintegrate alert dialog for invalid integrations.
     * This is the main function that displays the alert with disable/reintegrate options.
     *
     * @param providers List of invalid integration provider names
     * @param onOpenIntegrations Callback when user chooses to open integrations page
     * @param skipInvalidIntegrationsCheck Whether this is being called from integrations page
     */
    override fun showReIntegrateAlert(
        providers: List<String>,
        onOpenIntegrations: (() -> Unit)?,
        skipInvalidIntegrationsCheck: Boolean
    ) {
        if (providers.isEmpty() || skipInvalidIntegrationsCheck) {
            return
        }

        val message = createInvalidIntegrationMessage(providers)
        val disableButtonText = if (providers.size > 1) {
            "Disable All"
        } else {
            "Disable ${getProviderDisplayName(providers.first())}"
        }

        // Create confirmation dialog with disable and reintegrate options
        val confirmDialog = DialogModel.Confirm(
            title = "Integration Issue",
            message = message,
            confirmText = "Open Integrations",
            cancelText = disableButtonText,
            onConfirm = {
                // User chose to reintegrate - navigate to integrations
                onOpenIntegrations?.invoke()
                _navigateToIntegrationsEvent.value = Unit
                dialogQueueService.dismissCurrent()
            },
            onCancel = {
                // User chose to disable - remove integrations
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        removeMultipleIntegrations(providers)
                    } catch (e: Exception) {
                        Log.e("IntegrationService", "Error disabling integrations", e)
                    }
                }
                dialogQueueService.dismissCurrent()
            },
            onDismiss = {
                dialogQueueService.dismissCurrent()
            },
            confirmPriority = 50 // High priority for integration issues
        )

        // Enqueue the dialog
        dialogQueueService.enqueue(confirmDialog)
    }

    /**
     * Checks for invalid integrations and shows alert if needed.
     * Call this after login or when integrations need to be checked.
     *
     * @param account The current account
     * @param skipInvalidIntegrationsCheck Whether to skip the check (e.g., when on integrations page)
     * @param onOpenIntegrations Callback when user chooses to open integrations page
     */
    override suspend fun checkForInvalidIntegrations(
        skipInvalidIntegrationsCheck: Boolean ,
        onOpenIntegrations: (() -> Unit)?
    ) {
        if (skipInvalidIntegrationsCheck) {
            return
        }

        val invalidProviders = getInvalidIntegrationProviders(currentAccount)
        if (invalidProviders.isNotEmpty()) {
            showReIntegrateAlert(
                providers = invalidProviders,
                onOpenIntegrations = onOpenIntegrations,
                skipInvalidIntegrationsCheck = skipInvalidIntegrationsCheck
            )
        }
    }

    /**
     * Triggers integration check. Call this after login or when needed.
     */
    override fun triggerIntegrationCheck() {
        _shouldCheckIntegrations.value = true
    }

    /**
     * Resets the integration check trigger.
     */
    override fun resetIntegrationCheck() {
        _shouldCheckIntegrations.value = false
    }

    /**
     * Consumes the navigation event (call this after handling navigation).
     */
    override fun consumeNavigationEvent() {
        _navigateToIntegrationsEvent.value = null
    }

    /**
     * Creates a user-friendly message for invalid integrations.
     */
    private fun createInvalidIntegrationMessage(providers: List<String>): String {
        val providerNames = providers.map { getProviderDisplayName(it) }

        return when (providerNames.size) {
            1 -> "Your ${providerNames.first()} integration is no longer valid. Would you like to disable it or re-integrate?"
            2 -> "${providerNames.joinToString(" and ")} integrations are no longer valid. Would you like to disable them or re-integrate?"
            else -> {
                val lastProvider = providerNames.last()
                val otherProviders = providerNames.dropLast(1).joinToString(", ")
                "$otherProviders, and $lastProvider integrations are no longer valid. Would you like to disable them or re-integrate?"
            }
        }
    }

    /**
     * Gets display name for a provider.
     */
    override fun getProviderDisplayName(provider: String): String {
        return when (provider) {
            "fitbit" -> "Fitbit"
            "googleFit" -> "Google Fit"
            "mfPal" -> "MyFitnessPal"
            "uArmor" -> "Under Armour"
            else -> provider.replaceFirstChar { it.uppercase() }
        }
    }
}
