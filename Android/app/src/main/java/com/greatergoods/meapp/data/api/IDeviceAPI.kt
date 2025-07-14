package com.greatergoods.meapp.data.api

import com.greatergoods.meapp.domain.model.api.device.DeviceApiModel
import com.greatergoods.meapp.domain.model.api.device.R4ScalePreferenceApiModel
import com.greatergoods.meapp.domain.model.api.device.ScaleMetaDataApiModel
import com.greatergoods.meapp.domain.model.api.device.ScaleTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path

/**
 * API interface for scale-related endpoints.
 * Supports operations for paired scales and scale preferences.
 */
interface IDeviceAPI {
    companion object Companion {
        private const val SCALES = "paired-scale"
        private const val PREFERENCE = "scale-r4/preference"
        private const val ACCOUNT_SCALE = "account/scale"
    }

    /**
     * Get scale token for the account.
     */
    @GET("account/scale?r=4")
    suspend fun getScaleToken(): Response<ScaleTokenResponse>

    /**
     * Get all paired scales for the account.
     */
    @GET(SCALES)
    suspend fun getPairedScales(): Response<List<DeviceApiModel>>

    /**
     * Save a new scale to the account.
     * @param scale The scale data to save.
     */
    @POST(SCALES)
    suspend fun saveScale(
        @Body scale: DeviceApiModel
    ): Response<DeviceApiModel>

    /**
     * Delete a scale from the account.
     * @param scaleId The ID of the scale to delete.
     */
    @DELETE("$SCALES/{scaleId}")
    suspend fun deleteScale(
        @Path("scaleId") scaleId: String
    ): Response<Unit>

    /**
     * Edit scale properties (e.g., nickname).
     * @param scaleId The ID of the scale to edit.
     * @param properties The properties to update.
     */
    @PATCH("$SCALES/{scaleId}")
    suspend fun editScale(
        @Path("scaleId") scaleId: String,
        @Body properties: Map<String, Any>
    ): Response<DeviceApiModel>

    /**
     * Update scale metadata.
     * @param scaleId The ID of the scale.
     * @param metadata The metadata to update.
     */
    @PATCH("$SCALES/{scaleId}/info")
    suspend fun updateScaleMetadata(
        @Path("scaleId") scaleId: String,
        @Body metadata: ScaleMetaDataApiModel
    ): Response<Unit>

    /**
     * Save/Update scale preferences for the account.
     * @param preferences The preferences to save.
     */
    @POST(PREFERENCE)
    suspend fun saveScalePreferences(
        @Body preferences: R4ScalePreferenceApiModel
    ): Response<R4ScalePreferenceApiModel>
}
