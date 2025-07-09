package com.greatergoods.meapp.domain.model.storage.Account

import com.dmdbrands.library.ggbluetooth.model.GGBTMetricConfig
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.meapp.core.shared.utilities.ConversionTools.convertStoredToKg
import com.greatergoods.meapp.core.shared.utilities.ConversionTools.convertStoredToLbs
import com.greatergoods.meapp.core.shared.utilities.DateTimeConverter.calculateAge
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.proto.MetricKey

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
  val dashboardType: String? = "Dashboard_4_metrics",
  val dashboardMetrics: List<String>? = emptyList(),
  // Notification settings
  val shouldSendEntryNotifications: Boolean? = false,
  val shouldSendWeightInEntryNotifications: Boolean? = false,
  // Goal settings
  val goalType: String? = null, // 'lose', 'gain', 'maintain'
  val goalWeight: Double? = null, // target weight
  val initialWeight: Double = 0.0, // initial weight when goal was set
  val metPreviousGoal: Boolean? = null, // whether previous goal was met
  val goalPercent: Double = 0.0, // calculated goal completion percentage
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
    val metricConfig = MetricKey.entries.filter { it != MetricKey.UNRECOGNIZED }.map {
      GGBTMetricConfig(
        metric = it.name,
        enabled = dashboardMetrics?.contains(it.name) ?: false,
      )
    }
    return GGBTUserProfile(
      name = "$firstName $lastName",
      age = calculateAge(dob),
      sex = gender,
      unit = weightUnit.name,
      weight = initialWeight,
      height = height?.toDouble(),
      goalWeight = goalWeight,
      goalType = goalType,
      metrics = metricConfig,
    )
  }
}
