package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IAuthAPI
import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.domain.repository.IAccountRepository
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationRepository @Inject constructor(
  private val accountRepository: IAccountRepository,
  private val authAPI: IAuthAPI,
  private val integrationAPI: IIntegrationAPI,
  private val accountDao: AccountDao
) : IIntegrationRepository {

  override suspend fun getAccount(accountId: String): AccountInfo {
    return authAPI.getAccountWithToken(accountId)
  }

  override suspend fun removeIntegration(provider: String) {
    return integrationAPI.removeIntegration(provider)
  }

  override suspend fun updateLocalAccount() {
    val remoteAccount = accountRepository.getActiveAccount().first()
    if (remoteAccount == null) {
      return
    }
    // Convert to IntegrationsSettingsEntity
    val integrationsSettings = IntegrationsSettingsEntity(
      accountId = remoteAccount.id,
      isFitbitOn = remoteAccount.isFitbitOn,
      isFitbitValid = remoteAccount.isFitbitValid,
      isHealthConnectOn = remoteAccount.isHealthConnectOn,
      isHealthKitOn = remoteAccount.isHealthKitOn,
      isMFPOn  = remoteAccount.isMFPOn,
      isMFPValid = remoteAccount.isMFPValid,
      isSynced = true,
    )
    accountDao.updateIntegrationsSettings(integrationsSettings)
  }
}
