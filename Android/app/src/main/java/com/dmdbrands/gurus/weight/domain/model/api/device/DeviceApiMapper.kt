package com.dmdbrands.gurus.weight.domain.model.api.device

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import java.util.UUID

/**
 * Extension functions to map API models to domain models.
 */
fun DeviceApiModel.toDomainModel(
  connectionStatus: BLEStatus = BLEStatus.DISCONNECTED,
  wifiMacAddress: String? = null,
  isWifiConfigured: Boolean = false,
  broadcastIdHex: String? = null,
  // create/update paired-device responses drop the BLE identifiers and collapse the device's
  // category into the connection `type`. When mapping the response of a device we just sent, the
  // caller passes the locally-discovered values here to preserve them (see [broadcastIdHex]).
  macAddressOverride: String? = null,
  peripheralIdentifierOverride: String? = null,
  deviceTypeOverride: String? = null,
  // BACKEND WORKAROUND: the create/update AND get paired-device responses never return `scaleToken`,
  // so the caller passes the locally-generated token to preserve it. Without this the stored token
  // is null and R4 (0412) operations that need it (getUsers, updateAccount, readings) fail — e.g.
  // the scale user list shows blank names. REMOVE once the backend returns scaleToken in the
  // paired-device responses.
  scaleTokenOverride: String? = null,
): Device {
  val scaleId = if (id.isNullOrEmpty()) UUID.randomUUID().toString() else id
  // The create/update paired-device responses omit broadcastId, so when a caller maps the
  // response of a device it just sent (createPairedDevice/updatePairedDevice), it passes the
  // freshly-discovered broadcastId here to preserve it. Without this the stored row has a blank
  // broadcastId and live BLE readings can't match the device — they were silently dropped.
  val resolvedBroadcastId = broadcastIdHex ?: convertIntToHex(broadcastId, type)
  return Device(
    id = scaleId,
    device = GGDeviceDetail(
      systemID = scaleId,
      deviceName = name ?: "",
      // Server omits mac/peripheralIdentifier on create/update — keep the discovered ones so the
      // stored row can be matched on reconnect (baby scales have no broadcastId → rely on these).
      macAddress = macAddressOverride?.takeIf { it.isNotBlank() } ?: mac ?: "",
      identifier = peripheralIdentifierOverride?.takeIf { it.isNotBlank() } ?: peripheralIdentifier ?: "",
      broadcastId = resolvedBroadcastId,
      broadcastIdString = resolvedBroadcastId,
      password = convertIntToHex(password, type),
      wifiMacAddress = wifiMacAddress, // Not in API response
      isWifiConfigured = isWifiConfigured, // Not in API response
    ),
    connectionStatus = connectionStatus,
    preferences = preference?.toPreferences(scaleId, isSynced = true),
    nickname = nickname ?: name ?: "",
    // The API `type` is the CONNECTION type (e.g. a baby scale is sent as "bluetooth"), so mapping
    // it straight to deviceType would demote "babyScale"/"bpmA6Bluetooth" to "bluetooth". Preserve
    // the local setup category when the caller provides it. (baby-scale reconnect fix)
    deviceType = deviceTypeOverride ?: type,
    alreadyPaired = false,
    isSynced = true,
    sku = sku,
    createdAt = createdAt?.toString(),
    userNumber = userNumber,
    hasServerID = !id.isNullOrEmpty(),
    isWeighOnlyModeEnabledByOthers = false,
    // BACKEND WORKAROUND: server never echoes scaleToken, so fall back to the caller-supplied local
    // token. REMOVE the override once the backend returns scaleToken.
    token = scaleTokenOverride?.takeIf { it.isNotBlank() } ?: scaleToken,
    productType = productType,
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
    productType = productType,
  )

/**
 * Maps a domain [Device] to a [PairedDeviceRequest] for `POST /v3/paired-device/`
 * and `PATCH /v3/paired-device/{id}` (MOB-378).
 * [deviceType] (e.g. `weight_scale`, `baby_scale`, `bpm`) must be set on the Device;
 * defaults to `weight_scale` if absent to preserve backward compatibility.
 */
fun Device.toPairedDeviceRequest(): PairedDeviceRequest = PairedDeviceRequest(
    // API deviceType is the product CATEGORY ("weight_scale" / "baby_scale" / "bpm"). When the
    // domain Device didn't set productType (scan/discovery path), derive it from the SKU instead
    // of blindly defaulting to "weight_scale" — otherwise a baby scale / BPM monitor was sent as
    // deviceType="weight_scale" while its `type` was "babyScale"/"bpmBluetooth", which the server
    // rejects: "Invalid type … for deviceType weight_scale" (MOB-598/MOB-378).
    deviceType = productType ?: when {
        sku?.let { DeviceHelper.isBabyScale(it) } == true -> "baby_scale"
        DeviceHelper.isBpmDevice(sku) -> "bpm"
        else -> "weight_scale"
    },
    // API `type` is the CONNECTION type, not the setup/category. Allowed (§2.3):
    // wifi, bluetooth, appsync, lcbt, btWifiR4, bpmBluetooth, bpmLcbt. Map the setup types
    // that aren't valid connection types — e.g. "babyScale" is a BLE scale → "bluetooth"
    // (server 400s on type="babyScale"). The rest already match.
    type = when (deviceType) {
        DeviceSetupType.BabyScale.value -> DeviceSetupType.Bluetooth.value
        DeviceSetupType.BpmA6Bluetooth.value -> DeviceSetupType.BpmBluetooth.value
        DeviceSetupType.EspTouchWifi.value -> DeviceSetupType.Wifi.value
        else -> deviceType ?: ""
    },
    nickname = nickname,
    sku = sku ?: "",
    mac = device?.macAddress,
    broadcastId = convertHexToInt(device?.broadcastId),
    password = convertHexToInt(device?.password),
    userNumber = userNumber,
    name = device?.deviceName,
    peripheralIdentifier = device?.identifier,
    scaleToken = token,
)

fun convertHexToInt(value: String?): Long? {
  // Scales' broadcastIds and passwords are returned as hex strings, but need to be
  // stored as an integer

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

    // R4 scales and baby scales both carry a 6-byte MAC as their broadcastId, so they must be
    // padded to 12 hex chars. Otherwise a MAC with a trailing zero byte loses those bytes on the
    // read-back reverse (an int drops leading zeros), and the recovered broadcastId no longer
    // matches live BLE readings. The local deviceType for a baby scale is "babyScale"; the unified
    // API category is "baby_scale".
    if (protocolType == DeviceSetupType.BtWifiR4.value ||
      protocolType == DeviceSetupType.BabyScale.value ||
      protocolType == "baby_scale"
    ) {
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
