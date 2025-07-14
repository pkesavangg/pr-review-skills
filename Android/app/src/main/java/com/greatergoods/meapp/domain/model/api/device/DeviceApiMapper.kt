package com.greatergoods.meapp.domain.model.api.device

import com.greatergoods.meapp.domain.model.storage.Device
import java.util.UUID

/**
 * Extension functions to map API models to domain models.
 */
fun DeviceApiModel.toDomainModel(accountId: String): Device =
  Device(
    // Device properties
    id = if (id.isNullOrEmpty()) UUID.randomUUID().toString() else id,
    accountId = accountId,
    peripheralIdentifier = peripheralIdentifier,
    nickname = nickname,
    sku = sku,
    mac = mac,
    password = password,
    isDeleted = false,
    deviceName = name,
    deviceType = type,
    broadcastId = broadcastId?.toString(),
    broadcastIdString = broadcastId?.toString(),
    userNumber = userNumber?.toString(),
    protocolType = null, // Not in API response
    createdAt = createdAt,
    lastModified = System.currentTimeMillis(),
    isSynced = true,
    hasServerID = !id.isNullOrEmpty(), // True if API provided ID, false if generated
    isConnected = false,
    wifiMac = null, // Not in API response
    isWifiConfigured = false, // Not in API response
    token = scaleToken,
    // Body Scale properties
    scaleType = type,
    bodyComp = type?.contains("scale", ignoreCase = true) == true,
    isWeighOnlyModeEnabledByOthers = false,
    // R4 Prefs
    displayName = preference?.displayName,
    displayMetrics = preference?.displayMetrics,
    shouldFactoryReset = preference?.shouldFactoryReset ?: false,
    shouldMeasureImpedance = preference?.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = preference?.shouldMeasurePulse ?: false,
    timeFormat = preference?.timeFormat,
    tzOffset = preference?.tzOffset,
    wifiFotaScheduleTime = preference?.wifiFotaScheduleTime,
    prefsUpdatedAt = null, // Not in API response
    // Meta
    modelNumber = null, // Not in API response
    serialNumber = null, // Not in API response
    firmwareRevision = null, // Not in API response
    hardwareRevision = null, // Not in API response
    softwareRevision = null, // Not in API response
    manufacturerName = null, // Not in API response
    systemId = null, // Not in API response
    latestVersion = latestVersion,
    // BPM
    hasNumericUsers = null, // Not in API response
  )

/**
 * Extension function to map a list of API models to domain models.
 */
fun List<DeviceApiModel>.toDomainModels(accountId: String): List<Device> = map { it.toDomainModel(accountId) }

fun Device.toApiModel(): DeviceApiModel =
  DeviceApiModel(
    id = id,
    nickname = nickname,
    type = deviceType,
    createdAt = createdAt,
    userNumber = userNumber?.toIntOrNull(),
    mac = mac,
    broadcastId = broadcastId?.toLongOrNull(),
    password = password,
    sku = sku,
    name = deviceName,
    scaleToken = token,
    peripheralIdentifier = peripheralIdentifier,
    preference =
      PreferenceApiModel(
        tzOffset = tzOffset,
        timeFormat = timeFormat,
        displayName = displayName,
        displayMetrics = displayMetrics,
        shouldMeasurePulse = shouldMeasurePulse,
        shouldMeasureImpedance = shouldMeasureImpedance,
        shouldFactoryReset = shouldFactoryReset,
        wifiFotaScheduleTime = wifiFotaScheduleTime,
      ),
    latestVersion = latestVersion,
  )

/**
 * Convert Device domain model to R4ScalePreferenceApiModel for API calls.
 */
fun Device.toR4ScalePreferenceApiModel(): R4ScalePreferenceApiModel =
  R4ScalePreferenceApiModel(
    scaleId = id,
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset,
    shouldMeasureImpedance = shouldMeasureImpedance,
    shouldMeasurePulse = shouldMeasurePulse,
    timeFormat = timeFormat,
    tzOffset = tzOffset,
    wifiFotaScheduleTime = wifiFotaScheduleTime,
  )

/**
 * Convert Device domain model to ScaleMetaDataApiModel for API calls.
 */
fun Device.toScaleMetaDataApiModel(): ScaleMetaDataApiModel =
  ScaleMetaDataApiModel(
    modelNumber = modelNumber,
    serialNumber = serialNumber,
    firmwareRevision = firmwareRevision,
    hardwareRevision = hardwareRevision,
    softwareRevision = softwareRevision,
    manufacturerName = manufacturerName,
    systemId = systemId,
    latestVersion = latestVersion,
  )

/**
 * Create R4ScalePreferenceApiModel from individual parameters.
 */
fun createR4ScalePreferenceApiModel(
  scaleId: String,
  displayName: String? = null,
  displayMetrics: List<String>? = null,
  shouldFactoryReset: Boolean = false,
  shouldMeasureImpedance: Boolean = true,
  shouldMeasurePulse: Boolean = false,
  timeFormat: String? = null,
  tzOffset: Int? = 0,
  wifiFotaScheduleTime: Int? = 0,
): R4ScalePreferenceApiModel =
  R4ScalePreferenceApiModel(
    scaleId = scaleId,
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset,
    shouldMeasureImpedance = shouldMeasureImpedance,
    shouldMeasurePulse = shouldMeasurePulse,
    timeFormat = timeFormat,
    tzOffset = tzOffset,
    wifiFotaScheduleTime = wifiFotaScheduleTime,
  )
