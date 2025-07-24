package com.greatergoods.meapp.domain.repository

import com.greatergoods.meapp.domain.model.api.user.AccountInfo
import com.greatergoods.meapp.features.integration.model.Integrations
import kotlinx.coroutines.flow.Flow

interface IIntegrationRepository {
  val integrations: Flow<Integrations?>
  suspend fun getAccount(accountId: String): AccountInfo
  suspend fun removeIntegration(provider: String, suggestion: Map<String, String>)
  suspend fun updateLocalAccount()
}
