package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API interface for Health Connect server endpoints.
 */
interface IHealthConnectAPI {
  companion object {
    private const val HEALTH_CONNECT = "health"
    private const val LOG = "integrations/health/log"
    private const val INTEGRATION = "integrations/"
  }

  /**
   * Saves Health Connect integration for an account.
   *
   * @param request Integration save request
   * @return Integration save response
   */
  @POST("${INTEGRATION}${HEALTH_CONNECT}")
  suspend fun saveIntegration(
    @Body request: HealthConnectIntegrationRequest
  )

  /**
   * Removes Health Connect integration for an account.
   *
   * @return Integration removal response
   */
  @DELETE("${INTEGRATION}${HEALTH_CONNECT}/{deviceId}")
  suspend fun removeIntegration(
    @Path("deviceId") deviceId: String
  )

  /**
   * Marks entries as synced.
   *
   * @param request Mark synced request
   * @return Mark synced response
   */
  @POST(LOG)
  suspend fun sync(
    @Body request: HealthConnectSyncEntry
  )
}

/**
 * Health Connect integration request.
 *
 * @property deviceId The device ID associated with the integration.
 * @property type The integration type (e.g., "health_connect").
 * @property preferences Optional preferences for the integration (e.g., scopes).
 * @property integratedAt Optional timestamp when integration was established.
 * @property updatedAt Optional timestamp when integration was last updated.
 */
data class HealthConnectIntegrationRequest(
  val deviceId: String,
  val type: String = "healthconnect",
  val preferences: IntegrationPreferences? = null,
  val integratedAt: String? = null,
  val updatedAt: String? = null,
)

/**
 * Health Connect sync entry data.
 */
data class HealthConnectSyncEntry(
  val type: String,
  val sentAt: String,
  val timestamp: String,
  val weight: Double?,
  val bodyFat: Double?,
  val muscleMass: Double?,
  val water: Double?,
  val bmi: Double?,
  val data: Map<String, String>?
)
