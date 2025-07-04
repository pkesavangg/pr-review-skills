package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.support.SendLogRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API interface for support endpoints.
 */
interface ISupportAPI {
    companion object {
        private const val SUPPORT = "support/"
        private const val LOG = "log"
    }

        /**
     * Sends application logs to support.
     * Based on Angular http.service.ts sendLog() method.
     * Server returns plain text response, not JSON.
     *
     * @param request SendLogRequest containing log data.
     * @return Response containing plain text indicating success or failure.
     */
    @POST(SUPPORT + LOG)
    suspend fun sendLog(
        @Body request: SendLogRequest,
    ): Response<ResponseBody>
}
