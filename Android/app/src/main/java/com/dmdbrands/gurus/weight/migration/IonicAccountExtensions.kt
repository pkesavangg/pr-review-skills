package com.dmdbrands.gurus.weight.migration

import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightlessSettingsEntity

/**
 * Extension functions for IonicAccount to convert to various settings entities.
 * These extensions use the composition pattern with proper domain model separation.
 */

/**
 * Converts IonicAccount to GoalSettingsEntity using proper domain composition.
 */
fun IonicAccount.toGoalSettings(): GoalSettingsEntity {
  val goalData = this.goal
  return GoalSettingsEntity(
    accountId = this.id ?: "",
    goalType = goalData?.goalType?.name ?: "",
    weight = goalData?.initialWeight?.toFloat() ?: 0.0f,
    goalWeight = goalData?.goalWeight?.toString() ?: "0.0",
    goalPercent = goalData?.percent?.toFloat() ?: 0.0f,
    isSynced = true,
  )
}

/**
 * Converts IonicAccount to WeightlessSettingsEntity using proper domain composition.
 */
fun IonicAccount.toWeightlessSettings(): WeightlessSettingsEntity {
  val weightlessData = this.weightless
  return WeightlessSettingsEntity(
    accountId = this.id ?: "",
    isWeightlessOn = weightlessData?.isWeightlessOn ?: false,
    weightlessTimestamp = weightlessData?.weightlessTimestamp?.toString() ?: "",
    weightlessWeight = weightlessData?.weightlessWeight?.toFloat() ?: 0.0f,
    isSynced = true,
  )
}

/**
 * Converts IonicAccount to IntegrationsSettingsEntity using proper domain composition.
 */
fun IonicAccount.toIntegrationsSettings(): IntegrationsSettingsEntity {
  val integrationsData = this.integrations
  return IntegrationsSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    isMFPOn = integrationsData?.isMFPOn ?: false,
    isMFPValid = integrationsData?.isMFPValid ?: false,
    isFitbitOn = integrationsData?.isFitbitOn ?: false,
    isFitbitValid = integrationsData?.isFitbitValid ?: false,
    isHealthConnectOn = integrationsData?.isHealthConnectOn ?: false,
    isHealthKitOn = integrationsData?.healthkit ?: false,
  )
}

/**
 * Converts IonicAccount to WeightCompSettingsEntity using proper domain composition.
 */
fun IonicAccount.toWeightCompSettings(): WeightCompSettingsEntity {
  val profileData = this.profile
  return WeightCompSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    height = profileData?.height?.toInt() ?: 1700, // Default height if not set
    activityLevel = profileData?.activityLevel?.name?.lowercase() ?: "normal", // Default activity level
    weightUnit = profileData?.weightUnit?.name?.lowercase() ?: "lb", // Default weight unit
  )
}

/**
 * Converts IonicAccount to NotificationSettingsEntity using proper domain composition.
 */
fun IonicAccount.toNotificationSettings(): NotificationSettingsEntity {
  val notificationsData = this.notifications
  return NotificationSettingsEntity(
    accountId = this.id ?: "",
    isSynced = true,
    shouldSendEntryNotifications = notificationsData?.shouldSendEntryNotifications ?: false,
    shouldSendWeightInEntryNotifications = notificationsData?.shouldSendWeightInEntryNotifications ?: false,
  )
}
