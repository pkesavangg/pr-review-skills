package com.greatergoods.meapp.domain.model.storage

import com.greatergoods.meapp.data.storage.db.entity.device.BodyScaleEntity
import com.greatergoods.meapp.data.storage.db.entity.device.BpmEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceDetails
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceEntity
import com.greatergoods.meapp.data.storage.db.entity.device.DeviceMetaDataEntity
import com.greatergoods.meapp.data.storage.db.entity.device.R4ScalePreferenceEntity

/**
 * Extension functions for mapping between Device domain model and database entities.
 */
fun DeviceDetails.toDeviceDomainModel(): Device =
  Device(
    id = device.id,
    accountId = device.accountId,
    peripheralIdentifier = device.peripheralIdentifier,
    nickname = device.nickname,
    sku = device.sku,
    mac = device.mac,
    password = device.password,
    isDeleted = device.isDeleted,
    deviceName = device.deviceName,
    deviceType = device.deviceType,
    broadcastId = device.broadcastId,
    broadcastIdString = device.broadcastIdString,
    userNumber = device.userNumber,
    protocolType = device.protocolType,
    createdAt = device.createdAt,
    lastModified = device.lastModified,
    isSynced = device.isSynced,
    hasServerID = device.hasServerID,
    isConnected = device.isConnected,
    wifiMac = device.wifiMac,
    isWifiConfigured = device.isWifiConfigured,
    token = device.token,
    scaleType = scale?.scaleType,
    bodyComp = scale?.bodyComp ?: false,
    displayName = r4Preference?.displayName,
    displayMetrics = r4Preference?.displayMetrics,
    shouldFactoryReset = r4Preference?.shouldFactoryReset ?: false,
    shouldMeasureImpedance = r4Preference?.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = r4Preference?.shouldMeasurePulse ?: false,
    timeFormat = r4Preference?.timeFormat,
    tzOffset = r4Preference?.tzOffset,
    wifiFotaScheduleTime = r4Preference?.wifiFotaScheduleTime,
    prefsUpdatedAt = r4Preference?.updatedAt,
    modelNumber = meta?.modelNumber,
    serialNumber = meta?.serialNumber,
    firmwareRevision = meta?.firmwareRevision,
    hardwareRevision = meta?.hardwareRevision,
    softwareRevision = meta?.softwareRevision,
    manufacturerName = meta?.manufacturerName,
    systemId = meta?.systemId,
    latestVersion = meta?.latestVersion,
    hasNumericUsers = bpm?.hasNumericUsers,
    isWeighOnlyModeEnabledByOthers = scale?.isWeighOnlyModeEnabledByOthers ?: false,
  )

fun DeviceEntity.toDeviceDomainModel(): Device =
  Device(
    id = id,
    accountId = accountId,
    peripheralIdentifier = peripheralIdentifier,
    nickname = nickname,
    sku = sku,
    mac = mac,
    password = password,
    isDeleted = isDeleted,
    deviceName = deviceName,
    deviceType = deviceType,
    broadcastId = broadcastId,
    broadcastIdString = broadcastIdString,
    userNumber = userNumber,
    protocolType = protocolType,
    createdAt = createdAt,
    lastModified = lastModified,
    isSynced = isSynced,
    hasServerID = hasServerID,
    isConnected = isConnected,
    wifiMac = wifiMac,
    isWifiConfigured = isWifiConfigured,
    token = token,
    scaleType = null, // Will be set by DeviceDetails
    bodyComp = false, // Will be set by DeviceDetails
    displayName = null, // Will be set by DeviceDetails
    displayMetrics = null, // Will be set by DeviceDetails
    shouldFactoryReset = false, // Will be set by DeviceDetails
    shouldMeasureImpedance = false, // Will be set by DeviceDetails
    shouldMeasurePulse = false, // Will be set by DeviceDetails
    timeFormat = null, // Will be set by DeviceDetails
    tzOffset = null, // Will be set by DeviceDetails
    wifiFotaScheduleTime = 0, // Will be set by DeviceDetails
    prefsUpdatedAt = null, // Will be set by DeviceDetails
    modelNumber = null, // Will be set by DeviceDetails
    serialNumber = null, // Will be set by DeviceDetails
    firmwareRevision = null, // Will be set by DeviceDetails
    hardwareRevision = null, // Will be set by DeviceDetails
    softwareRevision = null, // Will be set by DeviceDetails
    manufacturerName = null, // Will be set by DeviceDetails
    systemId = null, // Will be set by DeviceDetails
    latestVersion = null, // Will be set by DeviceDetails
    hasNumericUsers = null, // Will be set by DeviceDetails,
    isWeighOnlyModeEnabledByOthers = false, // Will be set by DeviceDetails
  )

fun Device.toDeviceDetails(): DeviceDetails =
  DeviceDetails(
    device =
      DeviceEntity(
        id = id,
        accountId = accountId,
        peripheralIdentifier = peripheralIdentifier,
        nickname = nickname,
        sku = sku,
        mac = mac,
        password = password,
        isDeleted = isDeleted,
        deviceName = deviceName,
        deviceType = deviceType,
        broadcastId = broadcastId,
        broadcastIdString = broadcastIdString,
        userNumber = userNumber,
        protocolType = protocolType,
        createdAt = createdAt,
        lastModified = lastModified,
        isSynced = isSynced,
        hasServerID = hasServerID,
        isConnected = isConnected,
        wifiMac = wifiMac,
        isWifiConfigured = isWifiConfigured,
        token = token,
      ),
    scale =
      if (scaleType != null || bodyComp) {
        BodyScaleEntity(
          id = id,
          scaleType = scaleType,
          bodyComp = bodyComp,
          isWeighOnlyModeEnabledByOthers = isWeighOnlyModeEnabledByOthers,
        )
      } else {
        null
      },
    meta =
      if (modelNumber != null || serialNumber != null || firmwareRevision != null) {
        DeviceMetaDataEntity(
          id = id,
          modelNumber = modelNumber,
          serialNumber = serialNumber,
          firmwareRevision = firmwareRevision,
          hardwareRevision = hardwareRevision,
          softwareRevision = softwareRevision,
          manufacturerName = manufacturerName,
          systemId = systemId,
          latestVersion = latestVersion,
        )
      } else {
        null
      },
    r4Preference =
      if (displayName != null || displayMetrics != null) {
        R4ScalePreferenceEntity(
          id = id,
          displayName = displayName,
          displayMetrics = displayMetrics,
          shouldFactoryReset = shouldFactoryReset,
          shouldMeasureImpedance = shouldMeasureImpedance,
          shouldMeasurePulse = shouldMeasurePulse,
          timeFormat = timeFormat,
          tzOffset = tzOffset,
          wifiFotaScheduleTime = wifiFotaScheduleTime,
          updatedAt = prefsUpdatedAt,
        )
      } else {
        null
      },
    bpm =
      if (hasNumericUsers != null) {
        BpmEntity(
          id = id,
          hasNumericUsers = hasNumericUsers,
        )
      } else {
        null
      },
  )
