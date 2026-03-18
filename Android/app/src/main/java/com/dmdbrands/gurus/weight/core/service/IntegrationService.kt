package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
  appNavigationService: IAppNavigationService,
  private val accountService: IAccountService,
  private val integrationRepository: IIntegrationRepository,
  private val healthConnectRepository: IHealthConnectRepository,
) : BaseService(connectivityObserver, dialogQueueService, appNavigationService), IIntegrationService {
  companion object {
    private const val TAG = "IntegrationService"
  }

  // Coroutine scope for background operations
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _integrationState = MutableStateFlow(
    IntegrationItem(
      provider = IntegrationProvider.Fitbit,
      name = IntegrationStrings.FitbitProvider,
      isConnected = false,
      isValid = false,
      iconRes = AppIcons.Integrations.Fitbit,
      requiresOAuth = true,
    ),
  )
  override val integrationState get() = _integrationState.asStateFlow()

  init {
    // Subscribe to checkIntegrations flow from AccountService
    serviceScope.launch {
      accountService.checkIntegrations.collectLatest { shouldCheck ->
        if (shouldCheck) {
          AppLog.d(TAG, "Received checkIntegrations signal, checking for inactive integrations")
          try {
            integrationRepository.updateLocalAccount()
            val inactiveProviders = checkForInactiveIntegrations()
            if (inactiveProviders.isNotEmpty()) {
              showReintegrateAlert(inactiveProviders)
            }
          } catch (e: Exception) {
            AppLog.e(TAG, "Failed to process checkIntegrations signal", e)
          }
        }
      }
    }
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
      val isHealthConnectIntegrated = healthConnectRepository.getAccountByID(currentAccount.id)
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
        isConnected = isHealthConnectIntegrated?.integrated == true,
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
      AppLog.e(TAG, "Failed to get integrations with status", e)
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
      AppLog.d(TAG, "Connecting to integration: $provider")
      if (provider.requiresOAuth()) {
        val oAuthUrl = provider.getOAuthUrl(accountId)
        if (oAuthUrl != null) {
          AppLog.d(TAG, "Generated OAuth URL for $provider: $oAuthUrl")
          oAuthUrl
        } else {
          AppLog.w(TAG, "OAuth URL is null for provider: $provider")
          null
        }
      } else {
        // For non-OAuth providers like Health Connect
        AppLog.d(TAG, "Provider $provider does not require OAuth")
        null
      }
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to connect to integration: $provider", e)
      throw e
    }
  }

  /**
   * Disconnects from a third-party integration provider.
   * @param provider The integration provider to disconnect from
   */
  override suspend fun disconnectIntegration(provider: IntegrationProvider) {
    try {
      requireNetworkAvailable(onError = { showNetworkErrorAndThrow() })
      AppLog.d(TAG, "Disconnecting from integration: $provider")
      dialogQueueService.showLoader(IntegrationStrings.loading)
      // Remove integration from API
      removeIntegrationOnApi(provider)
      // Refresh account data
      accountService.refreshAccount()
      dialogQueueService.dismissLoader()
      dialogQueueService.showToast(
        Toast(
          message = IntegrationStrings.DisconnectSuccess,
          action = null,
        ),
      )
      AppLog.d(TAG, "Successfully disconnected from integration: $provider")
      // Update integrations flow
      integrationRepository.updateLocalAccount()
    } catch (e: Exception) {
      dialogQueueService.dismissLoader()
      AppLog.e(TAG, "Failed to disconnect from integration: $provider", e)
      throw e
    }
  }

  /**
   * Removes an integration from the API and refreshes account data.
   * Similar to Angular removeIntegrationOnApi method.
   * @param provider The integration provider to remove
   */
  private suspend fun removeIntegrationOnApi(provider: IntegrationProvider) {
    try {
      AppLog.d(TAG, "Removing integration from API: $provider")
      val apiProvider = getProviderStringForDelete(provider)
      val suggestion = mapOf("suggestion" to apiProvider)
      integrationRepository.removeIntegration(apiProvider, suggestion)
      AppLog.d(TAG, "Successfully removed integration from API: $provider")
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to remove integration from API: $provider", e)
      throw e
    }
  }

  /**
   * Gets the API provider string for deletion, similar to Angular getProviderStringForDelete.
   * @param provider The integration provider
   * @return API provider string for deletion
   */
  private fun getProviderStringForDelete(provider: IntegrationProvider): String {
    return when (provider) {
      IntegrationProvider.Fitbit -> "fitbit"
      IntegrationProvider.MyFitnessPal -> "mfp"
      IntegrationProvider.HealthConnect -> "healthconnect"
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
      integrationRepository.updateLocalAccount()
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
          currentAccount.isHealthConnectOn to currentAccount.isHealthConnectOn
        }
      }
      AppLog.d(TAG, "Integration status for $provider: connected=${status.first}, valid=${status.second}")
      status
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to get integration status for: $provider", e)
      false to false
    }
  }

  /**
   * Gets the OAuth URL for a provider.
   * @param provider The integration provider
   * @param accountId The account ID
   * @return The OAuth URL or null if provider doesn't use OAuth
   */
  override fun getOAuthUrl(provider: IntegrationProvider, accountId: String): String? {
    return provider.getOAuthUrl(accountId)
  }

  /**
   * Checks for inactive integrations by verifying connection and validity status.
   * @return List of inactive integration providers
   */
  override suspend fun checkForInactiveIntegrations(): List<IntegrationProvider> {
    return try {
      AppLog.d(TAG, "Checking for inactive integrations")
      val inactiveIntegrations = mutableListOf<IntegrationProvider>()
      val currentAccount = accountService.getCurrentAccount()
      if (currentAccount == null) {
        AppLog.w(TAG, "No current account found for checking inactive integrations")
        return emptyList()
      }
      // Check Fitbit integration
      if (currentAccount.isFitbitOn && !currentAccount.isFitbitValid) {
        inactiveIntegrations.add(IntegrationProvider.Fitbit)
        AppLog.d(TAG, "Found inactive Fitbit integration")
      }
      // Check MyFitnessPal integration
      if (currentAccount.isMFPOn && !currentAccount.isMFPValid) {
        inactiveIntegrations.add(IntegrationProvider.MyFitnessPal)
        AppLog.d(TAG, "Found inactive MyFitnessPal integration")
      }
      AppLog.d(TAG, "Found ${inactiveIntegrations.size} inactive integrations: $inactiveIntegrations")
      inactiveIntegrations
    } catch (e: Exception) {
      AppLog.e(TAG, "Failed to check for inactive integrations", e)
      emptyList()
    }
  }

  /**
   * Gets the display names for invalid integration providers.
   * @param providers List of integration providers
   * @return Formatted string of provider names
   */
  override fun getInvalidIntegrationNames(providers: List<IntegrationProvider>): String {
    val providerNames = providers.map { getProviderString(it) }
    return when {
      providerNames.isEmpty() -> ""
      providerNames.size == 1 -> providerNames[0]
      else -> {
        val lastString = providerNames.last()
        val others = providerNames.dropLast(1)
        "${others.joinToString(", ")} and $lastString"
      }
    }
  }

  /**
   * Gets the display string for an integration provider.
   * @param provider The integration provider
   * @return Display name for the provider
   */
  private fun getProviderString(provider: IntegrationProvider): String {
    return when (provider) {
      IntegrationProvider.Fitbit -> "Fitbit"
      IntegrationProvider.MyFitnessPal -> "My Fitness Pal"
      IntegrationProvider.HealthConnect -> "Health Connect"
    }
  }

  /**
   * Shows reintegrate alert for inactive integrations.
   * @param inactiveProviders List of inactive integration providers
   */
  private fun showReintegrateAlert(inactiveProviders: List<IntegrationProvider>) {
    val providerNames = getInvalidIntegrationNames(inactiveProviders)

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
        cancelText = IntegrationStrings.openIntegrations,
        onConfirm = {
          CoroutineScope(Dispatchers.IO).launch {
            try {
              AppLog.d(TAG, "Disabling ${inactiveProviders.size} inactive integrations")
              for (provider in inactiveProviders) {
                try {
                  removeIntegrationOnApi(provider)
                  AppLog.d(TAG, "Successfully disabled integration: $provider")
                } catch (e: Exception) {
                  AppLog.e(TAG, "Failed to disable integration: $provider", e)
                }
              }
              // Refresh account data after all integrations are removed
              accountService.refreshAccount()
              AppLog.d(TAG, "Successfully disabled all inactive integrations")
            } catch (e: Exception) {
              AppLog.e(TAG, "Failed to disable inactive integrations", e)
            }
            dialogQueueService.dismissCurrent()
          }
        },
        onCancel = {
          openIntegrationList()
          dialogQueueService.dismissCurrent()
        },
      ),
    )
  }

  private fun openIntegrationList() {
    CoroutineScope(Dispatchers.IO).launch {
      appNavigationService.navigateTo(AppRoute.Integration.IntegrationList)
    }
  }
}
