package com.greatergoods.meapp.domain.services

import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.features.integration.model.IntegrationItem
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing third-party integrations.
 */
interface IIntegrationService {
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
     * @return The OAuth URL
     */
    fun getOAuthUrl(provider: IntegrationProvider, accountId: String): String
}
