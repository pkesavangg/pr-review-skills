package com.dmdbrands.gurus.weight.domain.model.api.device

import com.google.gson.annotations.SerializedName

/**
 * API model representing a device response from the server.
 * This matches the exact structure of the API response.
 */
data class DeviceApiModel(
  @SerializedName("id")
  val id: String?,

  @SerializedName("nickname")
  val nickname: String?,

  @SerializedName("type")
  val type: String?,

  @SerializedName("createdAt")
  val createdAt: String?,

  @SerializedName("userNumber")
  val userNumber: Int?,

  @SerializedName("mac")
  val mac: String?,

  @SerializedName("broadcastId")
  val broadcastId: Long?,

  @SerializedName("password")
  val password: Long?,

  @SerializedName("sku")
  val sku: String?,

  @SerializedName("name")
  val name: String?,

  @SerializedName("scaleToken")
  val scaleToken: String?,

  @SerializedName("peripheralIdentifier")
  val peripheralIdentifier: String?,

  @SerializedName("preference")
  val preference: PreferenceApiModel?,

  @SerializedName("latestVersion")
  val latestVersion: String?,

  @SerializedName("productType")
  val productType: String? = null,

  @SerializedName("broadcastName")
  val broadcastName: String? = null,
)

/**
 * API model representing device preferences from the server.
 */
data class PreferenceApiModel(
  @SerializedName("tzOffset")
  val tzOffset: Int?,

  @SerializedName("timeFormat")
  val timeFormat: String?,

  @SerializedName("displayName")
  val displayName: String?,

  @SerializedName("displayMetrics")
  val displayMetrics: List<String>?,

  @SerializedName("shouldMeasurePulse")
  val shouldMeasurePulse: Boolean?,

  @SerializedName("shouldMeasureImpedance")
  val shouldMeasureImpedance: Boolean?,

  @SerializedName("shouldFactoryReset")
  val shouldFactoryReset: Boolean?,

  @SerializedName("wifiFotaScheduleTime")
  val wifiFotaScheduleTime: Int?
)

/**
 * API model representing scale token response from the server.
 */
data class ScaleTokenResponse(
  @SerializedName("token")
  val token: String
)
