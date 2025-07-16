package com.greatergoods.meapp.domain.model.storage

import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGDevicePreference
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceDetails
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceEntity

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
      broadcastId = device.broadcastId,
      broadcastIdString = device.broadcastIdString,
      password = device.password,
      wifiMacAddress = device.wifiMac,
      isWifiConfigured = device.isWifiConfigured,
      // Add other fields as needed
    ),
    deviceType = device.deviceType,
    connectionStatus = if (device.isConnected) BLEStatus.CONNECTED else BLEStatus.DISCONNECTED,
    alreadyPaired = false,
    userNumber = device.userNumber?.toIntOrNull(),
    hasServerID = device.hasServerID,
    isWifiConfigured = device.isWifiConfigured,
    isWeighOnlyModeEnabledByOthers = scale?.isWeighOnlyModeEnabledByOthers ?: false,
    token = device.token,
    preferences = null, // Add mapping if needed,
  )

fun DeviceEntity.toDeviceDomainModel(): Device =
  Device(
    id = id,
    device = GGDeviceDetail(
      deviceName = deviceName ?: "",
      macAddress = mac ?: "",
      identifier = peripheralIdentifier ?: "",
      protocolType = protocolType,
      broadcastId = broadcastId,
      broadcastIdString = broadcastIdString,
      password = password,
      wifiMacAddress = wifiMac,
      isWifiConfigured = isWifiConfigured,
      // Add other fields as needed
    ),
    connectionStatus = if (isConnected) BLEStatus.CONNECTED else BLEStatus.DISCONNECTED,
    alreadyPaired = false,
    userNumber = userNumber?.toIntOrNull(),
    hasServerID = hasServerID,
    isWifiConfigured = isWifiConfigured,
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
        sku = null, // Not present in GGDevice
        mac = device?.macAddress,
        password = device?.password,
        isDeleted = false, // Not present in GGDevice
        deviceName = device?.deviceName,
        deviceType = device?.protocolType, // No deviceType in GGDevice, use protocolType
        broadcastId = device?.broadcastId,
        broadcastIdString = device?.broadcastIdString,
        userNumber = userNumber?.toString(),
        protocolType = device?.protocolType,
        createdAt = null, // Not present in GGDevice
        lastModified = null, // Not present in GGDevice
        isSynced = hasServerID,
        hasServerID = hasServerID,
        isConnected = connectionStatus == BLEStatus.CONNECTED,
        wifiMac = device?.wifiMacAddress,
        isWifiConfigured = isWifiConfigured,
        token = token,
      ),
    scale = null, // Add mapping if needed
    meta = null, // Add mapping if needed
    r4Preference = null, // Add mapping if needed
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


