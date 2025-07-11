package com.greatergoods.meapp.core.service

import com.greatergoods.meapp.core.network.interfaces.IConnectivityObserver
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.domain.interfaces.IDialogQueueService
import com.greatergoods.meapp.domain.model.api.integration.IntegrationProvider
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.domain.services.IAccountService
import com.greatergoods.meapp.domain.services.IIntegrationService
import com.greatergoods.meapp.features.common.model.Toast
import com.greatergoods.meapp.features.integration.model.IntegrationItem
import com.greatergoods.meapp.features.integration.strings.IntegrationStrings
import com.greatergoods.meapp.resources.AppIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service implementation for managing third-party integrations.
 * Handles OAuth flows, connection status, and API communication.
 */
@Singleton
class IntegrationService @Inject constructor(
  connectivityObserver: IConnectivityObserver,
  dialogQueueService: IDialogQueueService,
  private val accountService: IAccountService,
  private val integrationRepository: IIntegrationRepository
) :  BaseService(connectivityObserver, dialogQueueService), IIntegrationService {

    companion object {
        private const val TAG = "IntegrationService"
    }

    /**
     * Gets all available integrations with their connection status.
     * @return Flow of integration items with current status
     */
    override fun getIntegrationsWithStatus(): Flow<List<IntegrationItem>> = flow {
        try {
            AppLog.d(TAG, "Getting integrations with status")
            val currentAccount = accountService.getCurrentAccount()
            if (currentAccount == null) {
                AppLog.w(TAG, "No current account found")
                emit(emptyList())
                return@flow
            }

            val integrations = mutableListOf<IntegrationItem>()
            // Create Fitbit integration safely
            val fitbitIntegration = IntegrationItem(
                    provider = IntegrationProvider.Fitbit,
                    name = IntegrationStrings.FitbitProvider,
                    isConnected = currentAccount.isFitbitOn,
                    isValid = currentAccount.isFitbitValid,
                    iconRes = AppIcons.Integrations.Fitbit,
                    platformRequirement = IntegrationProvider.Fitbit.getPlatformRequirement(),
                    requiresOAuth = IntegrationProvider.Fitbit.requiresOAuth(),
                )
                integrations.add(fitbitIntegration)
                val mfpIntegration = IntegrationItem(
                    provider = IntegrationProvider.MyFitnessPal,
                    name = IntegrationStrings.MyFitnessPalProvider,
                    isConnected = currentAccount.isMFPOn,
                    isValid = currentAccount.isMFPValid,
                    iconRes = AppIcons.Integrations.My_Fitness_Pal,
                    platformRequirement = IntegrationProvider.MyFitnessPal.getPlatformRequirement(),
                    requiresOAuth = IntegrationProvider.MyFitnessPal.requiresOAuth(),
                )
                integrations.add(mfpIntegration)
                val healthConnectIntegration = IntegrationItem(
                    provider = IntegrationProvider.HealthConnect,
                    name = IntegrationStrings.HealthConnectProvider,
                    isConnected = currentAccount.isHealthConnectOn,
                    isValid = true, // Health Connect validity is handled differently
                    iconRes = AppIcons.Integrations.Health_Connect_Logo,
                    platformRequirement = IntegrationProvider.HealthConnect.getPlatformRequirement(),
                    requiresOAuth = IntegrationProvider.HealthConnect.requiresOAuth(),
                )
                integrations.add(healthConnectIntegration)
                AppLog.d(TAG, "Successfully created HealthConnect integration item")

            emit(integrations)
            AppLog.d(TAG, "Successfully loaded ${integrations.size} integrations")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get integrations with status", e.toString())
        }
    }

    /**
     * Connects to a third-party integration provider.
     * @param provider The integration provider to connect to
     * @param accountId The account ID for the integration
     * @return The OAuth URL for providers that require OAuth, null for others
     */
    override suspend fun connectIntegration(provider: IntegrationProvider, accountId: String): String? {
        return try {
            AppLog.d(TAG, "Connecting to integration: $provider for account: $accountId")
            if (provider.requiresOAuth()) {
                val oAuthUrl = provider.getOAuthUrl(accountId)
                AppLog.d(TAG, "Generated OAuth URL for $provider: $oAuthUrl")
                oAuthUrl
            } else {
                // For non-OAuth providers like Health Connect
                AppLog.d(TAG, "Provider $provider does not require OAuth")
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to connect to integration: $provider", e.toString())
            throw e
        }
    }

    /**
     * Disconnects from a third-party integration provider.
     * @param provider The integration provider to disconnect from
     */
    override suspend fun disconnectIntegration(provider: IntegrationProvider) {
        try {
            AppLog.d(TAG, "Disconnecting from integration: $provider")
            dialogQueueService.showLoader(IntegrationStrings.loading)
            // Map provider to API endpoint
            val apiProvider = when (provider) {
                IntegrationProvider.Fitbit -> "fitbit"
                IntegrationProvider.MyFitnessPal -> "mfp"
                IntegrationProvider.HealthConnect -> "healthconnect"
            }
            integrationRepository.removeIntegration(apiProvider)
            dialogQueueService.dismissLoader()
            dialogQueueService.showToast(Toast(
                message = IntegrationStrings.DisconnectSuccess,
                action = null
            ))
            AppLog.d(TAG, "Successfully disconnected from integration: $provider")
        } catch (e: Exception) {
            dialogQueueService.dismissLoader()
            AppLog.e(TAG, "Failed to disconnect from integration: $provider", e.toString())
            throw e
        }
    }

    /**
     * Checks if an integration is connected and valid.
     * @param provider The integration provider to check
     * @return Pair of (isConnected, isValid)
     */
    override suspend fun getIntegrationStatus(provider: IntegrationProvider): Pair<Boolean, Boolean> {
        return try {
            AppLog.d(TAG, "Getting integration status for: $provider")
            accountService.refreshAccount();
            val currentAccount = accountService.getCurrentAccount()
            if (currentAccount == null) {
                AppLog.w(TAG, "No current account found")
                return false to false
            }
            val status = when (provider) {
                IntegrationProvider.Fitbit -> {
                    currentAccount.isFitbitOn to currentAccount.isFitbitValid
                }
                IntegrationProvider.MyFitnessPal -> {
                    currentAccount.isMFPOn to currentAccount.isMFPValid
                }
                IntegrationProvider.HealthConnect -> {
                    currentAccount.isHealthConnectOn to true
                }
            }
            AppLog.d(TAG, "Integration status for $provider: connected=${status.first}, valid=${status.second}")
            status
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get integration status for: $provider", e.toString())
            false to false
        }
    }

    /**
     * Gets the OAuth URL for a provider.
     * @param provider The integration provider
     * @param accountId The account ID
     * @return The OAuth URL
     */
    override fun getOAuthUrl(provider: IntegrationProvider, accountId: String): String {
        return provider.getOAuthUrl(accountId)
    }

}
