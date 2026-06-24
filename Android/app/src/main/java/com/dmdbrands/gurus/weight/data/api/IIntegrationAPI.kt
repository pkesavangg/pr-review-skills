package com.dmdbrands.gurus.weight.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API interface for integration endpoints.
 */
interface IIntegrationAPI {
    companion object {
        private const val INTEGRATIONS = "integrations/"
        private const val SUGGESTION = "suggestion"
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

    /**
     * Submits a user-suggested integration to the v3 server
     * (POST /v3/integrations/suggestion, body { "suggestion": "<text>" }, 201 on success).
     */
    @POST(INTEGRATIONS + SUGGESTION)
    suspend fun requestIntegration(
        @Body body: Map<String, String>,
    ): Response<ResponseBody>
}
