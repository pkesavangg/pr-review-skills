package com.greatergoods.meapp.domain.model.api.device

import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import java.util.UUID

/**
 * Extension functions to map API models to domain models.
 */
fun DeviceApiModel.toDomainModel(accountId: String): Device =
  Device(
    id = if (id.isNullOrEmpty()) UUID.randomUUID().toString() else id,
    device = GGDeviceDetail(
      deviceName = name ?: "",
      macAddress = mac ?: "",
      identifier = peripheralIdentifier ?: "",
      protocolType = type,
      broadcastId = broadcastId?.toString(),
      broadcastIdString = broadcastId?.toString(),
      password = password,
      wifiMacAddress = null, // Not in API response
      isWifiConfigured = false, // Not in API response
      // Add other fields as needed
    ),
    connectionStatus = BLEStatus.DISCONNECTED,
    nickname = nickname ?: name ?: "",
    deviceType = type ?: "",
    alreadyPaired = false,
    sku = sku,
    createdAt = createdAt?.toString(),
    userNumber = userNumber,
    hasServerID = !id.isNullOrEmpty(),
    wifiMac = null, // Not in API response
    isWifiConfigured = false, // Not in API response
    isWeighOnlyModeEnabledByOthers = false,
    token = scaleToken,
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
    userNumber = userNumber,
    mac = device?.macAddress,
    broadcastId = device?.broadcastId?.toLongOrNull(),
    password = device?.password,
    sku = sku, // Not present in GGDevice
    createdAt = createdAt.toString(),
    name = device?.deviceName,
    scaleToken = token,
    peripheralIdentifier = device?.identifier,
    preference = null, // Not present in GGDevice, add if needed
    latestVersion = null, // Not present in GGDevice
  )

/**
 * Convert Device domain model to R4ScalePreferenceApiModel for API calls.
 */
fun Device.toR4ScalePreferenceApiModel(): R4ScalePreferenceApiModel =
  R4ScalePreferenceApiModel(
    scaleId = id,
    displayName = preferences?.displayName,
    displayMetrics = preferences?.displayMetrics,
    shouldFactoryReset = preferences?.shouldFactoryReset ?: false,
    shouldMeasureImpedance = preferences?.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = preferences?.shouldMeasurePulse ?: false,
    timeFormat = preferences?.timeFormat,
    tzOffset = preferences?.tzOffset,
    wifiFotaScheduleTime = preferences?.wifiFotaScheduleTime?.toInt() ?: 0,
  )

/**
 * Convert Device domain model to ScaleMetaDataApiModel for API calls.
 */
fun Device.toScaleMetaDataApiModel(): ScaleMetaDataApiModel =
  ScaleMetaDataApiModel(
    modelNumber = device?.modelNumber,
    serialNumber = device?.serialNumber,
    firmwareRevision = device?.firmwareRevision,
    hardwareRevision = device?.hardwareRevision,
    softwareRevision = device?.softwareRevision,
    manufacturerName = device?.manufacturerName,
    systemId = device?.systemID,
    latestVersion = "",
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
