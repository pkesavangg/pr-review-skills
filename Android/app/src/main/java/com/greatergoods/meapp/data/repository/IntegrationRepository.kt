package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import com.greatergoods.meapp.features.integration.model.Integrations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationRepository @Inject constructor(
  private val accountRepository: IAccountRepository,
  private val authAPI: IAuthAPI,
  private val integrationAPI: IIntegrationAPI,
  private val accountDao: AccountDao
) : IIntegrationRepository {

  init {
    CoroutineScope(Dispatchers.IO).launch {
      updateLocalAccount();
    }
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
    isHealthConnectOn = false
  )
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
    val remoteAccount = accountRepository.getActiveAccount().first()
    if (remoteAccount == null) {
      _integrations.value = defaultIntegrations
      return
    }
    val account = accountRepository.getAccountFromAPI(remoteAccount.id)
    accountRepository.updateAccountInfo(account.id, account)
    // Convert to IntegrationsSettingsEntity
    val integrationsSettings = IntegrationsSettingsEntity(
      accountId = account.id,
      isFitbitOn = account.isFitbitOn,
      isFitbitValid = account.isFitbitValid,
      isHealthConnectOn = account.isHealthConnectOn,
      isHealthKitOn = account.isHealthKitOn,
      isMFPOn  = account.isMFPOn,
      isMFPValid = account.isMFPValid,
      isSynced = true,
    )
    accountDao.updateIntegrationsSettings(integrationsSettings)
    // Update the integrations flow
    _integrations.value = Integrations(
      isFitbitOn = account.isFitbitOn,
      isMFPOn = account.isMFPOn,
      isFitbitValid = account.isFitbitValid,
      isMFPValid = account.isMFPValid,
      isHealthConnectOn = account.isHealthConnectOn
    )
  }
}
