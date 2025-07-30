package com.dmdbrands.gurus.weight.domain.model.api.device

import com.google.gson.annotations.SerializedName

/**
 * API model representing R4 scale preferences.
 * This matches the structure expected by the scale-r4/preference endpoint.
 */
data class R4ScalePreferenceApiModel(
    @SerializedName("scaleId")
    val scaleId: String,
    
    @SerializedName("displayName")
    val displayName: String?,
    
    @SerializedName("displayMetrics")
    val displayMetrics: List<String>?,
    
    @SerializedName("shouldFactoryReset")
    val shouldFactoryReset: Boolean,
    
    @SerializedName("shouldMeasureImpedance")
    val shouldMeasureImpedance: Boolean,
    
    @SerializedName("shouldMeasurePulse")
    val shouldMeasurePulse: Boolean,
    
    @SerializedName("timeFormat")
    val timeFormat: String?,
    
    @SerializedName("tzOffset")
    val tzOffset: Int?,
    
    @SerializedName("wifiFotaScheduleTime")
    val wifiFotaScheduleTime: Int?
)

/**
 * API model representing scale metadata.
 * Used for updating scale metadata information.
 */
data class ScaleMetaDataApiModel(
    @SerializedName("modelNumber")
    val modelNumber: String?,
    
    @SerializedName("serialNumber")
    val serialNumber: String?,
    
    @SerializedName("firmwareRevision")
    val firmwareRevision: String?,
    
    @SerializedName("hardwareRevision")
    val hardwareRevision: String?,
    
    @SerializedName("softwareRevision")
    val softwareRevision: String?,
    
    @SerializedName("manufacturerName")
    val manufacturerName: String?,
    
    @SerializedName("systemId")
    val systemId: String?,
    
    @SerializedName("latestVersion")
    val latestVersion: String?
) 