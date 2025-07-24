package com.greatergoods.meapp.data.api

import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Path

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
     * @param suggestion The integration suggestion data
     */
   @HTTP(method = "DELETE", path = "${INTEGRATIONS}{provider}", hasBody = true)
    suspend fun removeIntegration(
        @Path("provider") provider: String,
        @Body suggestion: Map<String, String>
    )

}
