package com.greatergoods.meapp.features.integration.model

import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.resources.AppIcons

/**
 * Represents an integration item with provider information and connection status.
 * @property provider The integration provider type.
 * @property name Display name for the integration.
 * @property isConnected Whether the integration is currently connected.
 * @property isValid Whether the integration is valid (not expired).
 * @property iconRes Resource ID for the integration icon.
 * @property lastSyncTime Timestamp of last successful sync.
 * @property syncStatus Current sync status of the integration.
 * @property platformRequirement Platform requirement for this integration.
 * @property requiresOAuth Whether this integration requires OAuth flow.
 */
data class IntegrationItem(
  val provider: IntegrationProvider,
  val name: String,
  val isConnected: Boolean = false,
  val isValid: Boolean = true,
  val iconRes: Int,
  val lastSyncTime: String? = null,
  val syncStatus: SyncStatus = SyncStatus.Idle,
  val platformRequirement: String? = null,
  val requiresOAuth: Boolean = false,
) {
  /**
   * Creates an IntegrationItem from an IntegrationProvider with default values.
   */
  companion object {
    fun fromProvider(provider: IntegrationProvider): IntegrationItem =
      IntegrationItem(
        provider = provider,
        name = provider.displayName,
        iconRes = getIconForProvider(provider),
        platformRequirement = provider.getPlatformRequirement(),
        requiresOAuth = provider.requiresOAuth(),
      )

    private fun getIconForProvider(provider: IntegrationProvider): Int =
      when (provider) {
        is IntegrationProvider.Fitbit -> AppIcons.Integrations.Fitbit
        is IntegrationProvider.MyFitnessPal -> AppIcons.Integrations.My_Fitness_Pal
        is IntegrationProvider.HealthConnect -> AppIcons.Integrations.Health_Connect_Logo
      }
  }
}
