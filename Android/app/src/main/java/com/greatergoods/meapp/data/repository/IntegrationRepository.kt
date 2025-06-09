package com.greatergoods.meapp.data.repository

import com.greatergoods.meapp.data.api.IIntegrationAPI
import com.greatergoods.meapp.data.storage.db.dao.AccountDao
import com.greatergoods.meapp.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.greatergoods.meapp.domain.model.api.integration.UserAccount
import com.greatergoods.meapp.domain.repository.IIntegrationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationRepository @Inject constructor(
    private val integrationAPI: IIntegrationAPI,
    private val accountDao: AccountDao
): IIntegrationRepository {

    override suspend fun getActiveAccountId(): String {
        return integrationAPI.getAccount().id
    }

    override suspend fun fetchAccount(): UserAccount {
        return integrationAPI.getAccount()
    }


    override suspend fun removeIntegration(provider: String) {
        return integrationAPI.removeIntegration(provider)
    }

    override suspend fun updateLocalAccount() {
        val remoteAccount = integrationAPI.getAccount()
        // Convert to IntegrationsSettingsEntity
        val integrationsSettings = IntegrationsSettingsEntity(
            accountId = remoteAccount.id,
            isFitbitOn = remoteAccount.isFitbitOn,
            isFitbitValid = remoteAccount.isFitbitValid,
            isHealthConnectOn = remoteAccount.isHealthConnectOn == true,
            isHealthKitOn = remoteAccount.isHealthKitOn == true,
            isMfpOn = remoteAccount.isMFPOn,
            isMfpValid = remoteAccount.isMFPValid,
            isSynced = true
        )
         accountDao.updateIntegrationsSettings(integrationsSettings)
    }
}
