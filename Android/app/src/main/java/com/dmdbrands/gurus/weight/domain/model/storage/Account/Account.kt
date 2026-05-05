package com.dmdbrands.gurus.weight.domain.model.storage.Account

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools.convertStoredToKg
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools.convertStoredToLbs
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter.calculateAge
import com.dmdbrands.gurus.weight.domain.enums.ActivityLevel
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.domain.enums.MetricKeyConstants
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.library.ggbluetooth.model.GGBTMetricConfig
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile

/**
 * Domain model representing a user account and its settings.
 */
data class Account(
  val id: String,
  val firstName: String,
  val lastName: String,
  val dob: String,
  val email: String,
  val expiresAt: String? = null,
  val fcmToken: String? = null,
  val gender: String,
  val isActiveAccount: Boolean = false,
  val isLoggedIn: Boolean = false,
  val isExpired: Boolean = false,
  val isSynced: Boolean = false,
  val lastActiveTime: String? = null,
  val zipcode: String,
  // Add other settings as needed, or use separate domain models
  val weightUnit: WeightUnit,
  val isWeightlessOn: Boolean? = false,
  val height: Int?,
  val activityLevel: String?,
  val weightlessTimestamp: String? = null, // nullable
  val weightlessWeight: Float? = null, // nullable
  val isStreakOn: Boolean? = false,
  val streakTimestamp: String? = null, // nullable
  val dashboardType: String? = DashboardType.DASHBOARD_4_METRICS.value,
  val dashboardMetrics: List<String>? = emptyList(),
  val progressMetrics: List<String>? = emptyList(),
  val defaultGraphSegment: GraphSegment = GraphSegment.DEFAULT,
  // Notification settings
  val shouldSendEntryNotifications: Boolean? = false,
  val shouldSendWeightInEntryNotifications: Boolean? = false,
  // Goal settings
  val goalType: String? = null, // 'lose', 'gain', 'maintain'
  val goalWeight: Double? = null, // target weight
  val initialWeight: Double = 0.0, // initial weight when goal was set
  val metPreviousGoal: Boolean? = null, // whether previous goal was met
  val goalPercent: Double = 0.0, // calculated goal completion percentage

  // Integration
  val isFitbitOn: Boolean = false,
  val isFitbitValid: Boolean = false,
  val isHealthConnectOn: Boolean = false,
  val isHealthKitOn: Boolean = false,
  val isMFPOn: Boolean = false,
  val isMFPValid: Boolean = false,
) {
  /**
   * Get the metric of account
   */
  val isMetric: Boolean = weightUnit == WeightUnit.KG

  /**
   * Get the display weightless weight
   */
  val displayWeightlessWeight: () -> String = {
    val weight = weightlessWeight?.toDouble() ?: 0.0
    val convertedWeight =
      if (isMetric) {
        convertStoredToKg(weight)
      } else {
        convertStoredToLbs(weight)
      }
    String.format("%.1f ${weightUnit.label}", convertedWeight)
  }

  fun toGGBTUserProfile(): GGBTUserProfile {
    val metricConfig = MetricKey.entries.map {
      GGBTMetricConfig(
        metric = MetricKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase(),
        enabled = dashboardMetrics?.contains(MetricKeyConstants.ENUM_TO_CAMEL_CASE[it] ?: it.name.lowercase()) ?: false,
      )
    }
    val heightCm: Double? =
      height?.let { ConversionTools.convertStoredHeightToCm(it).toDouble() }
    return GGBTUserProfile(
      name = firstName,
      age = calculateAge(dob),
      sex = gender,
      unit = weightUnit.value,
      weight = initialWeight,
      height = heightCm,
      goalWeight = goalWeight,
      isAthlete = activityLevel.equals(ActivityLevel.ATHLETE.name, ignoreCase = true),
      goalType = goalType,
      metrics = metricConfig,
    )
  }
}

fun Account?.toGoal(): Goal? {
  if (this?.goalType == null) return null
  val activeAccount = this ?: return null
  return Goal(
    goalWeight = activeAccount.goalWeight ?: 0.0,
    initialWeight = activeAccount.initialWeight,
    type = activeAccount.goalType ?: "",
    goalType = activeAccount.goalType ?: "",
    percent = activeAccount.goalPercent,
    metPreviousGoal = activeAccount.metPreviousGoal ?: false,
    account = activeAccount, // Include account for weightless info access
  )
}

fun Account?.toWeightless(): Weightless {
  val rawWeightless = this?.weightlessWeight ?: 0f
  val unit = this?.weightUnit
  val weightlessInLb = ConversionTools.convertStoredToDisplay(rawWeightless.toDouble(), unit == WeightUnit.KG)
  return Weightless(
    isWeightlessOn = this?.isWeightlessOn ?: false,
    weightlessWeight = weightlessInLb.toFloat(),
  )
}

/**
 * Converts Account domain model to AccountInfo API model.
 * Used for syncing local account data to database when offline.
 */
fun Account.toAccountInfo(): AccountInfo {
  return AccountInfo(
    id = this.id,
    email = this.email,
    firstName = this.firstName,
    lastName = this.lastName,
    gender = this.gender,
    zipcode = this.zipcode,
    weightUnit = this.weightUnit.value,
    isWeightlessOn = this.isWeightlessOn ?: false,
    height = this.height ?: 1700,
    activityLevel = this.activityLevel ?: ActivityLevel.NORMAL.name.lowercase(),
    dob = this.dob,
    weightlessTimestamp = this.weightlessTimestamp,
    weightlessWeight = this.weightlessWeight,
    isStreakOn = this.isStreakOn ?: false,
    dashboardType = this.dashboardType ?: DashboardType.DASHBOARD_4_METRICS.value,
    dashboardMetrics = this.dashboardMetrics ?: emptyList(),
    progressMetrics = this.progressMetrics ?: emptyList(),
    goalType = this.goalType,
    goalWeight = this.goalWeight?.toFloat(),
    initialWeight = this.initialWeight.toFloat(),
    metPreviousGoal = this.metPreviousGoal ?: false,
    goalPercent = this.goalPercent.toInt(),
    shouldSendEntryNotifications = this.shouldSendEntryNotifications ?: false,
    shouldSendWeightInEntryNotifications = this.shouldSendWeightInEntryNotifications ?: false,
    isFitbitOn = this.isFitbitOn,
    isFitbitValid = this.isFitbitValid,
    isHealthConnectOn = this.isHealthConnectOn,
    isHealthKitOn = this.isHealthKitOn,
    isMFPOn = this.isMFPOn,
    isMFPValid = this.isMFPValid,
  )
}
