package com.greatergoods.meapp.domain.model.storage

import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGDevicePreference
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceDetails
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceEntity
import com.greatergoods.meapp.data.storage.db.entity.device.R4ScalePreferenceEntity
import com.greatergoods.meapp.domain.model.api.device.convertHexToInt
import com.greatergoods.meapp.domain.model.api.device.convertIntToHex

/**
 * Extension functions for mapping between Device domain model and database entities.
 */
fun DeviceDetails.toDeviceDomainModel(): Device =
  Device(
    id = device.id,
    device = GGDeviceDetail(
      deviceName = device.deviceName ?: "",
      macAddress = device.mac ?: "",
      identifier = device.peripheralIdentifier ?: "",
      protocolType = device.protocolType,
      broadcastId = convertIntToHex(device.broadcastId, device.deviceType),
      password = convertIntToHex(device.password, device.deviceType),
      wifiMacAddress = device.wifiMac,
      isWifiConfigured = device.wifiMac != null,
      // Add other fields as needed
    ),
    sku = device.sku,
    isSynced = device.isSynced,
    isDeleted = device.isDeleted,
    createdAt = device.createdAt,
    deviceType = device.deviceType,
    alreadyPaired = false,
    userNumber = device.userNumber?.toIntOrNull(),
    hasServerID = device.hasServerID,
    isWeighOnlyModeEnabledByOthers = scale?.isWeighOnlyModeEnabledByOthers ?: false,
    token = device.token,
    preferences = r4Preference?.toPreferences(), // Add mapping if needed,
  )

fun R4ScalePreferenceEntity.toPreferences(): Preferences {
  return Preferences(
    id = this.id,  // assuming `id` is a String in entity
    tzOffset = this.tzOffset,
    timeFormat = this.timeFormat,
    displayName = this.displayName,
    displayMetrics = this.displayMetrics,
    shouldMeasurePulse = this.shouldMeasurePulse,
    shouldMeasureImpedance = this.shouldMeasureImpedance,
    shouldFactoryReset = this.shouldFactoryReset,
    wifiFotaScheduleTime = this.wifiFotaScheduleTime?.toLong(),
    isSynced = this.isSynced,
  )
}

fun Preferences.toR4ScalePreferenceEntity(): R4ScalePreferenceEntity {
  return R4ScalePreferenceEntity(
    id = this.id.toString(),  // converting Long to String since entity expects String
    displayName = this.displayName,
    displayMetrics = this.displayMetrics,
    shouldFactoryReset = this.shouldFactoryReset ?: false,
    shouldMeasureImpedance = this.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = this.shouldMeasurePulse ?: false,
    timeFormat = this.timeFormat,
    tzOffset = this.tzOffset,
    wifiFotaScheduleTime = this.wifiFotaScheduleTime?.toInt(), // safely cast Long? to Int?
    isSynced = this.isSynced,
  )
}

fun DeviceEntity.toDeviceDomainModel(): Device =
  Device(
    id = id,
    device = GGDeviceDetail(
      deviceName = deviceName ?: "",
      macAddress = mac ?: "",
      identifier = peripheralIdentifier ?: "",
      protocolType = protocolType,
      broadcastId = convertIntToHex(broadcastId, deviceType),
      password = convertIntToHex(password, deviceType),
      wifiMacAddress = wifiMac,
      isWifiConfigured = wifiMac != null,
    ),
    sku = sku,
    isSynced = isSynced,
    isDeleted = isDeleted,
    createdAt = createdAt,
    alreadyPaired = false,
    deviceType = deviceType,
    userNumber = userNumber?.toIntOrNull(),
    hasServerID = hasServerID,
    isWeighOnlyModeEnabledByOthers = false,
    token = token,
    preferences = null, // Add mapping if needed
  )

fun Device.toDeviceDetails(accountId: String): DeviceDetails =
  DeviceDetails(
    device =
      DeviceEntity(
        id = id,
        accountId = accountId, // Not present in GGDevice, set as needed
        peripheralIdentifier = device?.identifier,
        nickname = device?.deviceName, // No nickname in GGDevice, use deviceName
        sku = sku, // Not present in GGDevice
        mac = device?.macAddress,
        password = convertHexToInt(device?.password),
        deviceName = device?.deviceName,
        deviceType = deviceType, // No deviceType in GGDevice, use protocolType
        broadcastId = convertHexToInt(device?.broadcastId),
        userNumber = userNumber?.toString(),
        protocolType = device?.protocolType,
        createdAt = createdAt, // Not present in GGDevice
        isSynced = isSynced,
        hasServerID = hasServerID,
        wifiMac = device?.wifiMacAddress,
        isDeleted = isDeleted,
        token = token,
      ),

    scale = null, // Add mapping if needed
    meta = null, // Add mapping if needed
    r4Preference = preferences?.toR4ScalePreferenceEntity(), // Add mapping if needed
    bpm = null, // Add mapping if needed
  )

fun Device.toGGBTDevice(): GGBTDevice =
  GGBTDevice(
    name = device?.deviceName ?: nickname,
    password = device?.password,
    broadcastId = device?.broadcastId ?: "",
    preference = this.preferences?.toGGDevicePreference(),
    userNumber = userNumber,
    token = token,
    syncAllData = true,
  )

fun Preferences.toGGDevicePreference(): GGDevicePreference =
  GGDevicePreference(
    tzOffset = tzOffset,
    timeFormat = timeFormat,
    displayName = displayName,
    displayMetrics = displayMetrics,
    shouldFactoryReset = shouldFactoryReset,
    shouldMeasurePulse = shouldMeasurePulse,
    shouldMeasureImpedance = shouldMeasureImpedance,
    wifiFotaScheduleTime = wifiFotaScheduleTime,
  )


