package com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model

import com.dmdbrands.gurus.weight.resources.AppIcons

/**
 * Constants for scale metric keys.
 */
object DeviceMetricKeys {
  const val BMI = "bmi"
  const val BODY_FAT_PERCENT = "bodyFatPercent"
  const val MUSCLE_PERCENT = "musclePercent"
  const val BODY_WATER_PERCENT = "bodyWaterPercent"
  const val HEART_RATE = "heartRate"
  const val BONE_PERCENT = "bonePercent"
  const val VISCERAL_FAT_LEVEL = "visceralFatLevel"
  const val SUBCUTANEOUS_FAT_PERCENT = "subcutaneousFatPercent"
  const val PROTEIN_PERCENT = "proteinPercent"
  const val SKELETAL_MUSCLE_PERCENT = "skeletalMusclePercent"
  const val BMR = "bmr"
  const val METABOLIC_AGE = "metabolicAge"
  const val GOAL_PROGRESS = "goalProgress"
  const val DAILY_AVERAGE = "dailyAverage"
  const val WEEKLY_AVERAGE = "weeklyAverage"
  const val MONTHLY_AVERAGE = "monthlyAverage"
}

/**
 * Constants for scale metric display labels.
 */
object DeviceMetricDisplayLabels {
  const val BODY_MASS_INDEX = "Body Mass Index"
  const val BODY_FAT = "Body Fat"
  const val MUSCLE_MASS = "Muscle Mass"
  const val BODY_WATER = "Body Water"
  const val HEART_RATE = "Heart Rate"
  const val BONE_MASS = "Bone Mass"
  const val VISCERAL_FAT = "Visceral Fat"
  const val SUBCUTANEOUS_FAT = "Subcutaneous Fat"
  const val PROTEIN = "Protein"
  const val SKELETAL_MUSCLES = "Skeletal Muscles"
  const val BASAL_METABOLIC_RATE = "Basal Metabolic Rate"
  const val METABOLIC_AGE = "Metabolic Age"
  const val GOAL_PROGRESS = "Goal Progress"
  const val DAILY_AVERAGE = "Daily Average"
  const val WEEKLY_AVERAGE = "Weekly Average"
  const val MONTHLY_AVERAGE = "Monthly Average"
}

/**
 * Data class representing a scale metric with display and state information.
 *
 * @property label The display name of the metric.
 * @property key The unique identifier key for the metric.
 * @property metricIcon The drawable resource ID for the metric icon (null for metrics without icons).
 * @property isEnabled Whether the metric is enabled by default.
 */
data class DeviceMetric(
  val label: String,
  val key: String,
  val metricIcon: Int?,
  val isEnabled: Boolean = true,
  val isIncluded: Boolean = true,
)

/**
 * List of body composition metrics available on smart scales.
 */
val deviceMetrics =
  listOf(
    DeviceMetric(DeviceMetricDisplayLabels.BODY_MASS_INDEX, DeviceMetricKeys.BMI, AppIcons.Metrics.Bmi),
    DeviceMetric(DeviceMetricDisplayLabels.BODY_FAT, DeviceMetricKeys.BODY_FAT_PERCENT, AppIcons.Metrics.BodyFat),
    DeviceMetric(DeviceMetricDisplayLabels.MUSCLE_MASS, DeviceMetricKeys.MUSCLE_PERCENT, AppIcons.Metrics.MuscleMass),
    DeviceMetric(DeviceMetricDisplayLabels.BODY_WATER, DeviceMetricKeys.BODY_WATER_PERCENT, AppIcons.Metrics.Water),
    DeviceMetric(DeviceMetricDisplayLabels.HEART_RATE, DeviceMetricKeys.HEART_RATE, AppIcons.Metrics.Pulse),
    DeviceMetric(DeviceMetricDisplayLabels.BONE_MASS, DeviceMetricKeys.BONE_PERCENT, AppIcons.Metrics.BoneMass),
    DeviceMetric(DeviceMetricDisplayLabels.VISCERAL_FAT, DeviceMetricKeys.VISCERAL_FAT_LEVEL, AppIcons.Metrics.VisceralFat),
    DeviceMetric(DeviceMetricDisplayLabels.SUBCUTANEOUS_FAT, DeviceMetricKeys.SUBCUTANEOUS_FAT_PERCENT, AppIcons.Metrics.SubcutaneousFat),
    DeviceMetric(DeviceMetricDisplayLabels.PROTEIN, DeviceMetricKeys.PROTEIN_PERCENT, AppIcons.Metrics.Protein),
    DeviceMetric(DeviceMetricDisplayLabels.SKELETAL_MUSCLES, DeviceMetricKeys.SKELETAL_MUSCLE_PERCENT, AppIcons.Metrics.SkeletalMusclePercent),
    DeviceMetric(DeviceMetricDisplayLabels.BASAL_METABOLIC_RATE, DeviceMetricKeys.BMR, AppIcons.Metrics.Bmr),
    DeviceMetric(DeviceMetricDisplayLabels.METABOLIC_AGE, DeviceMetricKeys.METABOLIC_AGE, AppIcons.Metrics.MetabolicAge),
  )

/**
 * List of other scale metrics (goals and averages) that complement body composition data.
 * These metrics do not have associated icons.
 */
val otherDeviceMetrics =
  listOf(
    DeviceMetric(DeviceMetricDisplayLabels.GOAL_PROGRESS, DeviceMetricKeys.GOAL_PROGRESS, metricIcon = null),
    DeviceMetric(DeviceMetricDisplayLabels.DAILY_AVERAGE, DeviceMetricKeys.DAILY_AVERAGE, metricIcon = null),
    DeviceMetric(DeviceMetricDisplayLabels.WEEKLY_AVERAGE, DeviceMetricKeys.WEEKLY_AVERAGE, metricIcon = null),
    DeviceMetric(DeviceMetricDisplayLabels.MONTHLY_AVERAGE, DeviceMetricKeys.MONTHLY_AVERAGE, metricIcon = null),
  )
