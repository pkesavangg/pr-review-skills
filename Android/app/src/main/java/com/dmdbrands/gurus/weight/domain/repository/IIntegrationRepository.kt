package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import kotlinx.coroutines.flow.Flow

interface IIntegrationRepository {
  val integrations: Flow<Integrations?>
  suspend fun getAccount(accountId: String): AccountInfo
  suspend fun removeIntegration(provider: String, suggestion: Map<String, String>)
  suspend fun updateLocalAccount()
}
