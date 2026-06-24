package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing third-party integrations.
 */
interface IIntegrationService {

    /**
     * Gets the current list of integration items.
     * @return List of integration items
     */
    val integrationState: Flow<IntegrationItem>
    /**
     * Gets all available integrations with their connection status.
     * @return Flow of integration items with current status
     */
    fun getIntegrationsWithStatus(): Flow<List<IntegrationItem>>

    /**
     * Connects to a third-party integration provider.
     * @param provider The integration provider to connect to
     * @param accountId The account ID for the integration
     * @return The OAuth URL for providers that require OAuth, null for others
     */
    suspend fun connectIntegration(provider: IntegrationProvider, accountId: String): String?

    /**
     * Disconnects from a third-party integration provider.
     * @param provider The integration provider to disconnect from
     */
    suspend fun disconnectIntegration(provider: IntegrationProvider)

    /**
     * Checks if an integration is connected and valid.
     * @param provider The integration provider to check
     * @return Pair of (isConnected, isValid)
     */
    suspend fun getIntegrationStatus(provider: IntegrationProvider): Pair<Boolean, Boolean>

    /**
     * Gets the OAuth URL for a provider.
     * @param provider The integration provider
     * @param accountId The account ID
     * @return The OAuth URL or null if provider doesn't use OAuth
     */
    fun getOAuthUrl(provider: IntegrationProvider, accountId: String): String?

    /**
     * Checks for inactive integrations by verifying connection and validity status.
     * @return List of inactive integration providers
     */
    suspend fun checkForInactiveIntegrations(): List<IntegrationProvider>

    /**
     * Gets the display names for invalid integration providers.
     * @param providers List of integration providers
     * @return Formatted string of provider names
     */
    fun getInvalidIntegrationNames(providers: List<IntegrationProvider>): String

    /**
     * Submits a user-suggested integration as free-text feedback via
     * `POST integrations/request` with body `{ "request": "<text>" }`
     * (see [com.dmdbrands.gurus.weight.data.api.IIntegrationAPI.requestIntegration]).
     *
     * @param suggestion The user-entered integration name / description.
     * @throws IllegalArgumentException if the suggestion is blank.
     * @throws Exception on network or non-2xx server response; callers should report it.
     */
    suspend fun submitIntegrationRequest(suggestion: String)
}
