package com.dmdbrands.gurus.weight.domain.model.api.device

import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import java.util.UUID
import android.util.Log

/**
 * Extension functions to map API models to domain models.
 */
fun DeviceApiModel.toDomainModel(): Device {
  val scaleId = if (id.isNullOrEmpty()) UUID.randomUUID().toString() else id
  return Device(
    id = scaleId,
    device = GGDeviceDetail(
      systemID = scaleId,
      deviceName = name ?: "",
      macAddress = mac ?: "",
      identifier = peripheralIdentifier ?: "",
      broadcastId = convertIntToHex(broadcastId, type),
      broadcastIdString = convertIntToHex(broadcastId, type),
      password = convertIntToHex(password, type),
      wifiMacAddress = null, // Not in API response
      isWifiConfigured = false, // Not in API response
    ),
    preferences = preference?.toPreferences(scaleId, isSynced = true),
    nickname = nickname ?: name ?: "",
    deviceType = type,
    alreadyPaired = false,
    isSynced = true,
    sku = sku,
    createdAt = createdAt?.toString(),
    userNumber = userNumber,
    hasServerID = !id.isNullOrEmpty(),
    isWeighOnlyModeEnabledByOthers = false,
    token = scaleToken,
  )
}

/**
 * Extension function to map a list of API models to domain models.
 */
fun List<DeviceApiModel>.toDomainModels(): List<Device> = map { it.toDomainModel() }

fun Device.toApiModel(): DeviceApiModel =
  DeviceApiModel(
    id = id,
    nickname = nickname,
    type = deviceType,
    userNumber = userNumber,
    mac = device?.macAddress,
    broadcastId = convertHexToInt(device?.broadcastId),
    password = convertHexToInt(device?.password),
    sku = sku, // Not present in GGDevice
    createdAt = createdAt.toString(),
    name = device?.deviceName,
    scaleToken = token,
    peripheralIdentifier = device?.identifier,
    preference = preferences?.toPreferencesApiModel(), // Not present in GGDevice, add if needed
    latestVersion = null, // Not present in GGDevice
  )

fun convertHexToInt(value: String?): Long? {
  // Scales' broadcastIds and passwords are returned as hex strings, but need to be
  // stored as an integer
  Log.d("TAG", "convertHexToInt - converting value: $value")

  if (value.isNullOrBlank()) return null
  else {
    val convertedValue = value
      .chunked(2)
      .reversed()
      .joinToString("")
      .uppercase()
    return convertedValue.toLong(16)
  }
}

fun convertIntToHex(value: Long?, protocolType: String?): String? {
  if (value == null) return null
  else {
    // Scales' broadcastIds and passwords are stored as integers and need to be
    // converted to a Hex string before being sent to the app
    var convertedValue = value.toString(16)

    if (protocolType == "btWifiR4") {
      convertedValue = "000000000000$convertedValue".takeLast(12)
    } else {
      if (convertedValue.length < 8) {
        convertedValue = "0000000$convertedValue".takeLast(8)
      } else if (convertedValue.length > 8 && convertedValue.length < 12) {
        convertedValue = "0000000$convertedValue".takeLast(12)
      }
    }

    return convertedValue.chunked(2).reversed().joinToString("").uppercase()
  }
}

/**
 * Convert Device domain model to R4ScalePreferenceApiModel for API calls.
 */
fun Preferences.toR4ScalePreferenceApiModel(): R4ScalePreferenceApiModel =
  R4ScalePreferenceApiModel(
    scaleId = id,
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset ?: false,
    shouldMeasureImpedance = shouldMeasureImpedance ?: false,
    shouldMeasurePulse = shouldMeasurePulse ?: false,
    timeFormat = timeFormat,
    tzOffset = tzOffset,
    wifiFotaScheduleTime = wifiFotaScheduleTime?.toInt() ?: 0,
  )

fun Preferences.toPreferencesApiModel(): PreferenceApiModel =
  PreferenceApiModel(
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset ?: false,
    shouldMeasureImpedance = shouldMeasureImpedance ?: false,
    shouldMeasurePulse = shouldMeasurePulse ?: false,
    timeFormat = timeFormat,
    tzOffset = tzOffset,
    wifiFotaScheduleTime = wifiFotaScheduleTime?.toInt() ?: 0,
  )

fun PreferenceApiModel.toPreferences(scaleId: String, isSynced: Boolean = false): Preferences =
  Preferences(
    id = scaleId,
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset,
    shouldMeasureImpedance = shouldMeasureImpedance,
    shouldMeasurePulse = shouldMeasurePulse,
    timeFormat = timeFormat,
    tzOffset = tzOffset,
    wifiFotaScheduleTime = wifiFotaScheduleTime?.toLong(),
    isSynced = isSynced,
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
