package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.model.api.integration.UserAccount
import com.greatergoods.meapp.core.service.InvalidIntegrationAlert
import kotlinx.coroutines.flow.StateFlow

/**
 * Service interface for managing integrations with third-party providers.
 *
 * This service handles OAuth integration flows, validation checks, and user alerts
 * for third-party health and fitness platform integrations.
 */
interface IIntegrationService {

    /**
     * StateFlow for invalid integration alert information.
     */
    val invalidIntegrationAlert: StateFlow<InvalidIntegrationAlert?>

    /**
     * StateFlow indicating whether integration checks should be performed.
     */
    val shouldCheckIntegrations: StateFlow<Boolean>

    /**
     * StateFlow for navigation events to the integrations screen.
     */
    val navigateToIntegrationsEvent: StateFlow<Unit?>

    /**
     * Gets the active account ID.
     *
     * @return The active account ID
     */
    suspend fun getActiveAccountId(): String

    /**
     * Gets the provider OAuth URL for the given integration provider.
     *
     * @param provider The integration provider to get the URL for
     * @return The OAuth URL for the specified provider
     * @throws Exception if unable to retrieve the URL
     */
    suspend fun getProviderUrl(provider: IntegrationProvider): String

    /**
     * Removes an integration for the given provider.
     *
     * @param provider The provider name to remove integration for
     */
    suspend fun removeIntegration(provider: String)

    /**
     * Removes multiple integrations for the given providers.
     *
     * @param providers List of provider names to remove integrations for
     * @throws Exception if unable to remove any of the integrations
     */
    suspend fun removeMultipleIntegrations(providers: List<String>)

    /**
     * Gets a list of invalid integration providers for the given account.
     *
     * @param account The user account to check for invalid integrations
     * @return List of invalid provider names
     */
    fun getInvalidIntegrationProviders(account: UserAccount): List<String>

    /**
     * Refreshes integrations from the server and updates local account.
     */
    suspend fun refreshIntegrations()

    /**
     * Checks if the account has any invalid integrations.
     *
     * @param account The user account to check
     * @return True if the account has invalid integrations, false otherwise
     */
    fun hasInvalidIntegrations(account: UserAccount): Boolean

    /**
     * Shows a reintegrate alert dialog for invalid integrations.
     * This displays the alert with disable/reintegrate options.
     *
     * @param providers List of invalid integration provider names
     * @param onOpenIntegrations Callback when user chooses to open integrations page
     * @param skipInvalidIntegrationsCheck Whether this is being called from integrations page
     */
    fun showReIntegrateAlert(
        providers: List<String>,
        onOpenIntegrations: (() -> Unit)? = null,
        skipInvalidIntegrationsCheck: Boolean = false
    )

    /**
     * Checks for invalid integrations and shows alert if needed.
     * Call this after login or when integrations need to be checked.
     *
     * @param skipInvalidIntegrationsCheck Whether to skip the check (e.g., when on integrations page)
     * @param onOpenIntegrations Callback when user chooses to open integrations page
     */
    suspend fun checkForInvalidIntegrations(
        skipInvalidIntegrationsCheck: Boolean = false,
        onOpenIntegrations: (() -> Unit)? = null
    )

    /**
     * Triggers integration check. Call this after login or when needed.
     */
    fun triggerIntegrationCheck()

    /**
     * Resets the integration check trigger.
     */
    fun resetIntegrationCheck()

    /**
     * Consumes the navigation event (call this after handling navigation).
     */
    fun consumeNavigationEvent()

    /**
     * Gets display name for a provider.
     *
     * @param provider The provider identifier
     * @return The user-friendly display name for the provider
     */
    fun getProviderDisplayName(provider: String): String
}
