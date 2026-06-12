package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Response

interface IIntegrationRepository {
  val integrationsFromServer: Flow<Integrations?>
  val integrations: Flow<Integrations?>
  suspend fun getAccount(accountId: String): AccountInfo
  suspend fun removeIntegration(provider: String, suggestion: Map<String, String>)

  /**
   * Submits a user-suggested integration via `POST integrations/request`.
   * @param body Request payload, e.g. `mapOf("request" to "<text>")`.
   */
  suspend fun requestIntegration(body: Map<String, String>): Response<ResponseBody>
  suspend fun updateLocalAccount()
  /**
   * Updates Health Connect integration status offline.
   * Sets isHealthConnectOn and marks as unsynced (isSynced = false).
   * Similar to Angular's setHealthConnectIntegrationStatus method.
   * @param isHealthConnectOn Whether Health Connect integration is enabled
   */
  suspend fun updateHealthConnectIntegrationOffline(isHealthConnectOn: Boolean)
}
