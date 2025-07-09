package com.greatergoods.meapp.features.ScaleMetricsSetting.model

import com.greatergoods.meapp.resources.AppIcons

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
    ScaleMetric("Body Mass Index", "bmi", AppIcons.Metrics.Bmi),
    ScaleMetric("Body Fat", "bodyFatPercent", AppIcons.Metrics.BodyFat),
    ScaleMetric("Muscle Mass", "musclePercent", AppIcons.Metrics.MuscleMass),
    ScaleMetric("Body Water", "bodyWaterPercent", AppIcons.Metrics.Water),
    ScaleMetric("Heart Rate", "heartRate", AppIcons.Metrics.Pulse),
    ScaleMetric("Bone Mass", "bonePercent", AppIcons.Metrics.BoneMass),
    ScaleMetric("Visceral Fat", "visceralFatLevel", AppIcons.Metrics.VisceralFat),
    ScaleMetric("Subcutaneous Fat", "subcutaneousFatPercent", AppIcons.Metrics.SubcutaneousFat),
    ScaleMetric("Protein", "proteinPercent", AppIcons.Metrics.Protein),
    ScaleMetric("Skeletal Muscles", "skeletalMusclePercent", AppIcons.Metrics.SkeletalMusclePercent),
    ScaleMetric("Basal Metabolic Rate", "bmr", AppIcons.Metrics.Bmr),
    ScaleMetric("Metabolic Age", "metabolicAge", AppIcons.Metrics.MetabolicAge),
  )

/**
 * List of other scale metrics (goals and averages) that complement body composition data.
 * These metrics do not have associated icons.
 */
val otherScaleMetrics =
  listOf(
    ScaleMetric("Goal Progress", "goalProgress", metricIcon = null),
    ScaleMetric("Daily Average", "dailyAverage", metricIcon = null),
    ScaleMetric("Weekly Average", "weeklyAverage", metricIcon = null),
    ScaleMetric("Monthly Average", "monthlyAverage", metricIcon = null),
  )
