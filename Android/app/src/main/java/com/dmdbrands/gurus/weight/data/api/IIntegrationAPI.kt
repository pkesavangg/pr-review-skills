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
        private const val REQUEST = "request"
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
     * Submits a user-suggested integration. Matches the existing wgApp4 endpoint
     * (POST /integrations/request, body { "request": "<text>" }, plain-text response).
     */
    @POST(INTEGRATIONS + REQUEST)
    suspend fun requestIntegration(
        @Body body: Map<String, String>,
    ): Response<ResponseBody>
}
