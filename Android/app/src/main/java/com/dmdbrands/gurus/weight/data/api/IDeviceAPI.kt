package com.dmdbrands.gurus.weight.data.api

import com.dmdbrands.gurus.weight.domain.model.api.device.DeviceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.PairedDeviceRequest
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleMetaDataApiModel
import com.dmdbrands.gurus.weight.domain.model.api.device.ScaleTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API interface for scale-related endpoints.
 * Supports operations for paired scales and scale preferences.
 */
interface IDeviceAPI {
  companion object Companion {
    private const val SCALES = "paired-scale"
    private const val PAIRED_DEVICE = "paired-device"
    private const val PREFERENCE = "scale-r4/preference"
    private const val ACCOUNT_SCALE = "account/scale"
  }

  // ── Unified /v3/paired-device/ endpoints (MOB-378) ────────────────────────

  /** Creates a paired device via the unified endpoint. Returns the created device. */
  @POST(PAIRED_DEVICE)
  suspend fun createPairedDevice(@Body request: PairedDeviceRequest): Response<DeviceApiModel>

  /** Lists all paired devices, optionally filtered by [deviceType]. */
  @GET(PAIRED_DEVICE)
  suspend fun getPairedDevices(
      @Query("deviceType") deviceType: String? = null,
  ): Response<List<DeviceApiModel>>

  /** Updates a paired device's properties (e.g. nickname). */
  @PATCH("$PAIRED_DEVICE/{deviceId}")
  suspend fun updatePairedDevice(
      @Path("deviceId") deviceId: String,
      @Body request: PairedDeviceRequest,
  ): Response<DeviceApiModel>

  /** Deletes a paired device (returns 204). */
  @DELETE("$PAIRED_DEVICE/{deviceId}")
  suspend fun deletePairedDevice(@Path("deviceId") deviceId: String): Response<Unit>

  /**
   * Get scale token for the account.
   * @param r Optional parameter for scale type (e.g., "4" for R4 scale).
   */
  @GET(ACCOUNT_SCALE)
  suspend fun getScaleToken(
    @Query("r") r: String? = null
  ): Response<ScaleTokenResponse>

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
    @Body updatedDevice: DeviceApiModel
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
