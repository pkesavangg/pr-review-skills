package com.greatergoods.meapp.data.api

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for integration endpoints.
 */
interface IIntegrationAPI {
    companion object {
        private const val INTEGRATIONS = "integrations/"
    }

    /**
     * Removes an integration for the current user.
     * @param provider The integration provider to remove (fitbit, google, mfp, ua)
     */
    @DELETE("${INTEGRATIONS}{provider}")
    suspend fun removeIntegration(
        @Path("provider") provider: String
    )

    /**
     * Saves health integration data.
     * @param integrationData The integration data to save
     */
    @POST("${INTEGRATIONS}health")
    suspend fun saveHealthIntegration(
        integrationData: Any // Replace with actual integration data model
    )

    /**
     * Saves health integration entries/logs.
     * @param integrationEntry The integration entry to save
     */
    @POST("${INTEGRATIONS}health/log")
    suspend fun saveHealthIntegrationEntry(
        integrationEntry: Any // Replace with actual integration entry model
    )

    /**
     * Deletes a health integration by device ID.
     * @param deviceId The device ID to delete
     */
    @DELETE("${INTEGRATIONS}health/{deviceId}")
    suspend fun deleteHealthIntegration(
        @Path("deviceId") deviceId: String
    )
}
