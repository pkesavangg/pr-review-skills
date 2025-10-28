package com.dmdbrands.gurus.weight.migration.helper

import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegratedDeviceInfo
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegrationData
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegrationOperationType
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.DashboardSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.BodyScaleEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceMetaDataEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.R4ScalePreferenceEntity
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MilestoneKey
import com.dmdbrands.gurus.weight.migration.model.IonicAccount
import com.dmdbrands.gurus.weight.migration.model.IonicHealthConnectData
import com.dmdbrands.gurus.weight.migration.model.IonicScale
import com.dmdbrands.gurus.weight.migration.model.OperationType
import java.util.UUID

/**
 * Extension functions for IonicAccount to convert to various settings entities.
 * These extensions use direct field access from the flattened IonicAccount structure.
 */

/**
 * Converts IonicAccount to GoalSettingsEntity using direct field access.
 */
fun IonicAccount.toGoalSettings(): GoalSettingsEntity {
  return GoalSettingsEntity(
    accountId = this.id ?: "",
    goalType = this.goalType?.name ?: "",
    weight = this.initialWeight?.toFloat() ?: 0.0f,
    goalWeight = this.goalWeight?.toString() ?: "0.0",
    goalPercent = this.percent?.toFloat() ?: 0.0f,
    isSynced = true,
  )
}

/**
 * Converts IonicAccount to WeightlessSettingsEntity using direct field access.
 */
fun IonicAccount.toWeightlessSettings(): WeightlessSettingsEntity {
  return WeightlessSettingsEntity(
    accountId = this.id ?: "",
    isWeightlessOn = this.isWeightlessOn ?: false,
    weightlessTimestamp = this.weightlessTimestamp ?: "",
    weightlessWeight = this.weightlessWeight?.toFloat() ?: 0.0f,
    isSynced = true,
  )
}

/**
 * Converts IonicAccount to IntegrationsSettingsEntity using direct field access.
 */
fun IonicAccount.toIntegrationsSettings(): IntegrationsSettingsEntity {
  return IntegrationsSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    isMFPOn = this.isMFPOn ?: false,
    isMFPValid = this.isMFPValid ?: false,
    isFitbitOn = this.isFitbitOn ?: false,
    isFitbitValid = this.isFitbitValid ?: false,
    isHealthConnectOn = this.isHealthConnectOn ?: false,
    isHealthKitOn = this.healthkit ?: false,
  )
}

/**
 * Converts IonicAccount to WeightCompSettingsEntity using direct field access.
 */
fun IonicAccount.toWeightCompSettings(): WeightCompSettingsEntity {
  return WeightCompSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    height = this.height?.toInt() ?: 1700, // Default height if not set
    activityLevel = this.activityLevel ?: "normal", // Default activity level
    weightUnit = this.weightUnit ?: "lb", // Default weight unit
  )
}

/**
 * Converts IonicAccount to NotificationSettingsEntity using direct field access.
 */
fun IonicAccount.toNotificationSettings(): NotificationSettingsEntity {
  return NotificationSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    shouldSendEntryNotifications = this.shouldSendEntryNotifications ?: false,
    shouldSendWeightInEntryNotifications = this.shouldSendWeightInEntryNotifications ?: false,
  )
}

/**
 * Converts IonicAccount to DashboardSettingsEntity using direct field access.
 */
fun IonicAccount.toDashboardSettings(): DashboardSettingsEntity {
  return DashboardSettingsEntity(
    accountId = this.id ?: "",
    dashboardMetrics = this.dashboardMetrics ?: emptyList(),
    dashboardMilestones = MilestoneKey.getDefaultMilestones().map { it.name.lowercase() },
    dashboardType = this.dashboardType?.name ?: DashboardType.DASHBOARD_4_METRICS.value,
    isSynced = true,
  )
}

// MARK: - IonicScale Extension Functions

/**
 * Converts IonicScale to DeviceDetails.
 * Creates a complete DeviceDetails object with all related entities from the scale data.
 */
fun IonicScale.toDeviceDetails(accountID: String): DeviceDetails {
  return DeviceDetails(
    device = this.toDeviceEntity(accountID),
    scale = this.toBodyScaleEntity(),
    bpm = null, // BPM entity not required for scales
    meta = this.toDeviceMetaDataEntity(),
    r4Preference = this.toR4ScalePreferenceEntity(),
  )
}

fun IonicHealthConnectData.toHealthConnectData(accountID: String): HealthConnectData {
  val builder = HealthConnectData.newBuilder()

  // --- booleans ---
  builder.setIntegrated(integrated.toBooleanStrictOrNull() ?: false)
  builder.setAlertSeen(alertSeen.toBooleanStrictOrNull() ?: false)
  builder.setOpen(open.toBooleanStrictOrNull() ?: false)
  builder.setOutOfSync(outOfSync.toBooleanStrictOrNull() ?: false)
  builder.setModalState(modalState.toBooleanStrictOrNull() ?: false)

  // --- integration info ---
  integrationStatus?.let { info ->
    val protoOperation = when (info.operationType) {
      OperationType.SAVE -> ProtoIntegrationOperationType.PROTO_SAVE
      OperationType.REMOVE -> ProtoIntegrationOperationType.PROTO_REMOVE
    }

    val protoScopes = ProtoIntegrationData.newBuilder()
      .setAccountId(accountID) // map if you have accountId available
      .setDeviceId(info.scopes.deviceId)
      .addAllScopes(info.scopes.preferences?.scopes ?: emptyList())
      .build()

    val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
      .setOperationType(protoOperation)
      .setScopes(protoScopes)
      .build()

    builder.setIntegrationInfo(protoIntegrationInfo)
  }

  // --- assignedTo ---
  if (assignedTo.isNotBlank()) {
    builder.assignedTo = assignedTo
  }

  // --- granted permissions ---
  if (grantedPermission.isNotBlank()) {
    val permissions = grantedPermission
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    builder.addAllGrantedPermission(permissions)
  }

  return builder.build()
}

/**
 * Converts IonicScale to DeviceEntity.
 * Creates the main device entity from the scale data.
 */
private fun IonicScale.toDeviceEntity(accountID: String): DeviceEntity {
  return DeviceEntity(
    id = this.id ?: UUID.randomUUID().toString(),
    accountId = this.userId ?: accountID,
    peripheralIdentifier = this.peripheralIdentifier,
    nickname = this.nickname ?: this.name,
    sku = this.sku,
    mac = this.mac,
    password = this.password?.toLong(),
    isDeleted = this.isDeleted ?: false,
    deviceName = this.name,
    deviceType = this.type,
    broadcastId = this.broadcastId?.toLong(),
    broadcastIdString = this.broadcastIdString,
    userNumber = this.userNumber?.toString(),
    protocolType = this.type, // Using type as protocol type
    createdAt = this.createdAt,
    isSynced = true,
    hasServerID = !this.id.isNullOrBlank(),
    token = this.scaleToken,
  )
}

/**
 * Converts IonicScale to BodyScaleEntity.
 * Creates the body scale specific entity from the scale data.
 */
private fun IonicScale.toBodyScaleEntity(): BodyScaleEntity {
  return BodyScaleEntity(
    id = this.id ?: UUID.randomUUID().toString(),
    scaleType = this.type,
    bodyComp = this.bodyComp ?: false,
    isWeighOnlyModeEnabledByOthers = this.isWeighOnlyModeEnabledByOthers ?: false,
  )
}

/**
 * Converts IonicScale to DeviceMetaDataEntity if metadata is available.
 * Creates the device metadata entity from the scale's metadata.
 */
private fun IonicScale.toDeviceMetaDataEntity(): DeviceMetaDataEntity? {
  val metadata = this.metaData ?: return null

  return DeviceMetaDataEntity(
    id = this.id ?: UUID.randomUUID().toString(),
    modelNumber = metadata.modelNumber,
    serialNumber = metadata.serialNumber,
    firmwareRevision = metadata.firmwareRevision,
    hardwareRevision = metadata.hardwareRevision,
    softwareRevision = metadata.softwareRevision,
    manufacturerName = metadata.manufacturerName,
    systemId = metadata.systemId,
    latestVersion = this.latestVersion,
  )
}

/**
 * Converts IonicScale to R4ScalePreferenceEntity if R4 preferences are available.
 * Creates the R4 scale preference entity from the scale's preference data.
 */
private fun IonicScale.toR4ScalePreferenceEntity(): R4ScalePreferenceEntity? {
  val preference = this.preference ?: return null

  return R4ScalePreferenceEntity(
    id = this.id ?: UUID.randomUUID().toString(),
    displayName = preference.displayName,
    displayMetrics = preference.displayMetrics,
    shouldFactoryReset = preference.shouldFactoryReset ?: false,
    shouldMeasureImpedance = preference.shouldMeasureImpedance ?: false,
    shouldMeasurePulse = preference.shouldMeasurePulse ?: false,
    timeFormat = preference.timeFormat,
    tzOffset = preference.tzOffset?.toInt(),
    wifiFotaScheduleTime = preference.wifiFotaScheduleTime?.toInt(),
    isSynced = true,
  )
}
