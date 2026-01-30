package com.dmdbrands.gurus.weight.features.ScaleMetricsSetting.model

import com.dmdbrands.gurus.weight.resources.AppIcons

/**
 * Constants for scale metric keys.
 */
object ScaleMetricKeys {
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
object ScaleMetricDisplayKeys {
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
data class ScaleMetric(
  val label: String,
  val key: String,
  val metricIcon: Int?,
  val isEnabled: Boolean = true,
  val isIncluded: Boolean = true,
)

/**
 * List of body composition metrics available on smart scales.
 */
val scaleMetrics =
  listOf(
    ScaleMetric(ScaleMetricDisplayKeys.BODY_MASS_INDEX, ScaleMetricKeys.BMI, AppIcons.Metrics.Bmi),
    ScaleMetric(ScaleMetricDisplayKeys.BODY_FAT, ScaleMetricKeys.BODY_FAT_PERCENT, AppIcons.Metrics.BodyFat),
    ScaleMetric(ScaleMetricDisplayKeys.MUSCLE_MASS, ScaleMetricKeys.MUSCLE_PERCENT, AppIcons.Metrics.MuscleMass),
    ScaleMetric(ScaleMetricDisplayKeys.BODY_WATER, ScaleMetricKeys.BODY_WATER_PERCENT, AppIcons.Metrics.Water),
    ScaleMetric(ScaleMetricDisplayKeys.HEART_RATE, ScaleMetricKeys.HEART_RATE, AppIcons.Metrics.Pulse),
    ScaleMetric(ScaleMetricDisplayKeys.BONE_MASS, ScaleMetricKeys.BONE_PERCENT, AppIcons.Metrics.BoneMass),
    ScaleMetric(ScaleMetricDisplayKeys.VISCERAL_FAT, ScaleMetricKeys.VISCERAL_FAT_LEVEL, AppIcons.Metrics.VisceralFat),
    ScaleMetric(ScaleMetricDisplayKeys.SUBCUTANEOUS_FAT, ScaleMetricKeys.SUBCUTANEOUS_FAT_PERCENT, AppIcons.Metrics.SubcutaneousFat),
    ScaleMetric(ScaleMetricDisplayKeys.PROTEIN, ScaleMetricKeys.PROTEIN_PERCENT, AppIcons.Metrics.Protein),
    ScaleMetric(ScaleMetricDisplayKeys.SKELETAL_MUSCLES, ScaleMetricKeys.SKELETAL_MUSCLE_PERCENT, AppIcons.Metrics.SkeletalMusclePercent),
    ScaleMetric(ScaleMetricDisplayKeys.BASAL_METABOLIC_RATE, ScaleMetricKeys.BMR, AppIcons.Metrics.Bmr),
    ScaleMetric(ScaleMetricDisplayKeys.METABOLIC_AGE, ScaleMetricKeys.METABOLIC_AGE, AppIcons.Metrics.MetabolicAge),
  )

/**
 * List of other scale metrics (goals and averages) that complement body composition data.
 * These metrics do not have associated icons.
 */
val otherScaleMetrics =
  listOf(
    ScaleMetric(ScaleMetricDisplayKeys.GOAL_PROGRESS, ScaleMetricKeys.GOAL_PROGRESS, metricIcon = null),
    ScaleMetric(ScaleMetricDisplayKeys.DAILY_AVERAGE, ScaleMetricKeys.DAILY_AVERAGE, metricIcon = null),
    ScaleMetric(ScaleMetricDisplayKeys.WEEKLY_AVERAGE, ScaleMetricKeys.WEEKLY_AVERAGE, metricIcon = null),
    ScaleMetric(ScaleMetricDisplayKeys.MONTHLY_AVERAGE, ScaleMetricKeys.MONTHLY_AVERAGE, metricIcon = null),
  )
