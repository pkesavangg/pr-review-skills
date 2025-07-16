package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.integrations.IntegrationPreferences
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for Health Connect server endpoints.
 */
interface IHealthConnectAPI {
  companion object {
    private const val HEALTH_CONNECT = "health"
    private const val LOG = "health/log"
    private const val INTEGRATION = "integrations/"
    private const val ENTRIES = "entries/"
    private const val SYNC = "sync/"
  }

  /**
   * Gets Health Connect integration status for an account.
   *
   * @param accountId The account ID
   * @return Integration status response
   */
  @GET("${HEALTH_CONNECT}${INTEGRATION}status")
  suspend fun getIntegrationStatus(
    @Query("accountId") accountId: String
  ): HealthConnectIntegrationStatusResponse

  /**
   * Saves Health Connect integration for an account.
   *
   * @param request Integration save request
   * @return Integration save response
   */
  @POST("${INTEGRATION}${HEALTH_CONNECT}")
  suspend fun saveIntegration(
    @Body request: HealthConnectIntegrationRequest
  ): Unit

  /**
   * Removes Health Connect integration for an account.
   *
   * @param accountId The account ID
   * @return Integration removal response
   */
  @DELETE("${INTEGRATION}${HEALTH_CONNECT}/{deviceId}")
  suspend fun removeIntegration(
    @Path("deviceId") deviceId: String
  ): Unit

  /**
   * Syncs entries to Health Connect server.
   *
   * @param request Sync entries request
   * @return Sync response
   */
  @POST("${HEALTH_CONNECT}${SYNC}entries")
  suspend fun syncEntries(
    @Body request: HealthConnectSyncEntriesRequest
  ): HealthConnectSyncResponse

  /**
   * Gets sync status for an account.
   *
   * @param accountId The account ID
   * @return Sync status response
   */
  @GET("${HEALTH_CONNECT}${SYNC}status")
  suspend fun getSyncStatus(
    @Query("accountId") accountId: String
  ): HealthConnectSyncStatusResponse

  /**
   * Updates sync timestamp for an account.
   *
   * @param request Update timestamp request
   * @return Update response
   */
  @PUT("${HEALTH_CONNECT}${SYNC}timestamp")
  suspend fun updateSyncTimestamp(
    @Body request: HealthConnectUpdateTimestampRequest
  ): HealthConnectSyncResponse

  /**
   * Marks entries as synced.
   *
   * @param request Mark synced request
   * @return Mark synced response
   */
  @PUT("${HEALTH_CONNECT}${ENTRIES}mark-synced")
  suspend fun markEntriesAsSynced(
    @Body request: HealthConnectMarkSyncedRequest
  ): HealthConnectSyncResponse

  /**
   * Gets Health Connect permissions for an account.
   *
   * @param accountId The account ID
   * @return Permissions response
   */
  @GET("${HEALTH_CONNECT}permissions")
  suspend fun getPermissions(
    @Query("accountId") accountId: String
  ): HealthConnectPermissionsResponse

  /**
   * Updates Health Connect permissions for an account.
   *
   * @param request Update permissions request
   * @return Update response
   */
  @PUT("${HEALTH_CONNECT}permissions")
  suspend fun updatePermissions(
    @Body request: HealthConnectUpdatePermissionsRequest
  ): HealthConnectSyncResponse
}

// Data classes for API requests and responses

/**
 * Health Connect integration status response.
 */
data class HealthConnectIntegrationStatusResponse(
  val isIntegrated: Boolean,
  val permissions: List<String>,
  val lastSyncTimestamp: String?,
  val deviceId: String?,
  val integrationTimestamp: String?
)

/**
 * Health Connect integration request.
 *
 * @property accountId The account ID for the integration.
 * @property deviceId The device ID associated with the integration.
 * @property type The integration type (e.g., "health_connect").
 * @property preferences Optional preferences for the integration (e.g., scopes).
 * @property integratedAt Optional timestamp when integration was established.
 * @property updatedAt Optional timestamp when integration was last updated.
 * @property permissions The list of granted Health Connect permissions.
 */
data class HealthConnectIntegrationRequest(
  val deviceId: String,
  val type: String = "healthconnect",
  val preferences: IntegrationPreferences? = null,
  val integratedAt: String? = null,
  val updatedAt: String? = null,
)

/**
 * Health Connect integration response.
 */
data class HealthConnectIntegrationResponse(
  val success: Boolean,
  val message: String,
  val integrationId: String?
)

/**
 * Health Connect sync entries request.
 */
data class HealthConnectSyncEntriesRequest(
  val accountId: String,
  val entries: List<HealthConnectSyncEntry>,
  val deviceId: String,
  val syncTimestamp: String
)

/**
 * Health Connect sync entry data.
 */
data class HealthConnectSyncEntry(
  val entryId: String,
  val timestamp: String,
  val weight: Double?,
  val bodyFat: Double?,
  val muscleMass: Double?,
  val boneMass: Double?,
  val bmr: Double?,
  val pulse: Int?,
  val water: Double?,
  val bmi: Double?,
  val permissions: List<String>
)

/**
 * Health Connect sync response.
 */
data class HealthConnectSyncResponse(
  val success: Boolean,
  val message: String,
  val syncedCount: Int?,
  val failedCount: Int?,
  val failedEntries: List<String>?
)

/**
 * Health Connect sync status response.
 */
data class HealthConnectSyncStatusResponse(
  val lastSyncTimestamp: String?,
  val pendingEntriesCount: Int,
  val isSyncInProgress: Boolean,
  val lastSyncStatus: String?
)

/**
 * Health Connect update timestamp request.
 */
data class HealthConnectUpdateTimestampRequest(
  val accountId: String,
  val timestamp: String
)

/**
 * Health Connect mark synced request.
 */
data class HealthConnectMarkSyncedRequest(
  val accountId: String,
  val entryIds: List<String>,
  val syncTimestamp: String
)

/**
 * Health Connect permissions response.
 */
data class HealthConnectPermissionsResponse(
  val permissions: List<String>,
  val lastUpdated: String?
)

/**
 * Health Connect update permissions request.
 */
data class HealthConnectUpdatePermissionsRequest(
  val accountId: String,
  val permissions: List<String>
)
