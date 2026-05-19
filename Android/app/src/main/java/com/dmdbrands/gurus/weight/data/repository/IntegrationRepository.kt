package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IIntegrationAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationRepository @Inject constructor(
  private val accountRepository: IAccountRepository,
  private val authAPI: IAuthAPI,
  private val integrationAPI: IIntegrationAPI,
  private val accountDao: AccountDao,
  private val healthConnectRepository: IHealthConnectRepository
) : IIntegrationRepository {
  private var integration: Integrations? = null

  private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  init {
    accountRepository.getActiveAccount()
      .distinctUntilChanged { old, new ->
        old?.id == new?.id &&
          old?.isFitbitOn == new?.isFitbitOn &&
          old?.isFitbitValid == new?.isFitbitValid &&
          old?.isHealthKitOn == new?.isHealthKitOn &&
          old?.isMFPOn == new?.isMFPOn &&
          old?.isMFPValid == new?.isMFPValid
      }
      .onEach { account ->
        if (account == null) return@onEach
        val healthConnectData = healthConnectRepository.getAccountByID(account.id)
        val isHealthConnectOn = healthConnectData?.integrated ?: false
        _integrations.value = (_integrations.value ?: defaultIntegrations).copy(
          isFitbitOn = account.isFitbitOn,
          isFitbitValid = account.isFitbitValid,
          isHealthConnectOn = isHealthConnectOn,
          healthkit = account.isHealthKitOn,
          isMFPOn = account.isMFPOn,
          isMFPValid = account.isMFPValid,
        )
      }
      .launchIn(repositoryScope)
  }

  // Default integrations (match your Angular default)
  private val defaultIntegrations = Integrations(
    isFitbitOn = false,
    isGoogleFitOn = false,
    isMFPOn = false,
    isUAOn = false,
    isFitbitValid = false,
    isGoogleFitValid = false,
    isMFPValid = false,
    isUAValid = false,
    healthkit = false,
    isHealthConnectOn = false,
  )

  private val _integrationsFromServer = MutableStateFlow(defaultIntegrations)
  override val integrationsFromServer: StateFlow<Integrations> = _integrationsFromServer.asStateFlow()

  // StateFlow for integrations (like BehaviorSubject)
  private val _integrations = MutableStateFlow<Integrations?>(defaultIntegrations)
  override val integrations: StateFlow<Integrations?> = _integrations.asStateFlow()

  override suspend fun getAccount(accountId: String): AccountInfo {
    return authAPI.getAccountWithToken(accountId)
  }

  override suspend fun removeIntegration(provider: String, suggestion: Map<String, String>) {
    return integrationAPI.removeIntegration(provider, suggestion)
  }

  override suspend fun updateLocalAccount() {
    try {
      val localAccount = accountRepository.getActiveAccount().first() ?: return
      val remoteAccount = accountRepository.getAccountFromAPI(localAccount.id)
      AppLog.d("IntegrationRepository", "Updated local account: $remoteAccount")

      // Get Health Connect integration status from DataStore (similar to Angular's getHealthConnectIntegrationStatus)
      val healthConnectData = healthConnectRepository.getAccountByID(localAccount.id)
      val isHealthConnectOn = healthConnectData?.integrated ?: false

      // Convert to IntegrationsSettingsEntity
      val integrationsSettings = IntegrationsSettingsEntity(
        accountId = remoteAccount.id,
        isFitbitOn = remoteAccount.isFitbitOn,
        isFitbitValid = remoteAccount.isFitbitValid,
        isHealthConnectOn = remoteAccount.isHealthConnectOn,
        isHealthKitOn = remoteAccount.isHealthKitOn,
        isMFPOn = remoteAccount.isMFPOn,
        isMFPValid = remoteAccount.isMFPValid,
        isSynced = true,
      )
      accountDao.updateIntegrationsSettings(integrationsSettings)

      // Update the integrations flow with server data
      _integrationsFromServer.value = Integrations(
        isFitbitOn = remoteAccount.isFitbitOn,
        isMFPOn = remoteAccount.isMFPOn,
        isFitbitValid = remoteAccount.isFitbitValid,
        isMFPValid = remoteAccount.isMFPValid,
        isHealthConnectOn = remoteAccount.isHealthConnectOn,
      )

      // Update the integrations flow with local Health Connect status from DataStore
      val currentIntegrations = _integrations.value ?: defaultIntegrations
      _integrations.value = currentIntegrations.copy(
        isFitbitOn = remoteAccount.isFitbitOn,
        isFitbitValid = remoteAccount.isFitbitValid,
        isMFPOn = remoteAccount.isMFPOn,
        isMFPValid = remoteAccount.isMFPValid,
        isHealthConnectOn = isHealthConnectOn,
      )

    } catch (e: Exception) {
      AppLog.d("IntegrationRepository", "Failed to update local account")
    }
  }

  /**
   * Updates Health Connect integration status offline.
   * Sets isHealthConnectOn and marks as unsynced (isSynced = false).
   * Similar to Angular's setHealthConnectIntegrationStatus method.
   * @param isHealthConnectOn Whether Health Connect integration is enabled
   */
  override suspend fun updateHealthConnectIntegrationOffline(isHealthConnectOn: Boolean) {
    try {
      val activeAccount = accountRepository.getActiveAccount().first()
      if (activeAccount == null) {
        AppLog.w("IntegrationRepository", "No active account found for updating Health Connect integration")
        return
      }

      val accountId = activeAccount.id

      // Get current values or use defaults
      val integrationsSettings = IntegrationsSettingsEntity(
        accountId = accountId,
        isFitbitOn = activeAccount.isFitbitOn,
        isFitbitValid = activeAccount.isFitbitValid,
        isHealthConnectOn = isHealthConnectOn,
        isHealthKitOn = activeAccount.isHealthKitOn,
        isMFPOn = activeAccount.isMFPOn,
        isMFPValid = activeAccount.isMFPValid,
        isSynced = false, // Mark as unsynced for offline update
      )

      // Update in database
      accountDao.updateIntegrationsSettings(integrationsSettings)
      AppLog.d("IntegrationRepository", "Updated Health Connect integration status offline: $isHealthConnectOn for account: $accountId")

      // Update the integrations flow
      val currentIntegrations = _integrations.value ?: defaultIntegrations
      _integrations.value = currentIntegrations.copy(isHealthConnectOn = isHealthConnectOn)
    } catch (e: Exception) {
      AppLog.e("IntegrationRepository", "Failed to update Health Connect integration offline", e)
    }
  }
}
