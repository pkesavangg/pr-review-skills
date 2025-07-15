package com.greatergoods.meapp.features.ScaleMetricsSetting.Helper

import com.greatergoods.meapp.domain.model.storage.BLEStatus
import com.greatergoods.meapp.domain.model.storage.Device
import com.greatergoods.meapp.features.ScaleMetricsSetting.enum.NotifyScaleMode
import com.greatergoods.meapp.features.ScaleMetricsSetting.model.ScaleMetric
import com.greatergoods.meapp.features.ScaleMetricsSetting.model.otherScaleMetrics
import com.greatergoods.meapp.features.ScaleMetricsSetting.model.scaleMetrics

/**
 * Helper object providing utility functions for display metrics management.
 */
object ScaleMetricsHelper {

  fun getAllMetrics(): List<String> {
    return listOf(
      "bmi",
      "bodyFatPercent",
      "musclePercent",
      "bodyWaterPercent",
      "heartRate",
      "bonePercent",
      "visceralFatLevel",
      "subcutaneousFatPercent",
      "proteinPercent",
      "skeletalMusclePercent",
      "bmr",
      "metabolicAge",
      "goalProgress",
      "dailyAverage",
      "weeklyAverage",
      "monthlyAverage",
    )
  }

  /**
   * Creates ordered metric states based on current metrics from the scale.
   *
   * @param currentMetrics Current ordered list of metric keys from the scale.
   * @return Pair of ordered body metrics and other metrics with proper enabled states.
   */
  fun createOrderedMetrics(currentMetrics: List<String>): Pair<List<ScaleMetric>, List<ScaleMetric>> {
    val allBodyMetrics = scaleMetrics.associateBy { it.key }
    val allOtherMetrics = otherScaleMetrics.associateBy { it.key }

    val orderedBodyMetrics = mutableListOf<ScaleMetric>()
    val orderedOtherMetrics = mutableListOf<ScaleMetric>()

    // Add metrics in the order they appear in currentMetrics
    currentMetrics.forEach { key ->
      allBodyMetrics[key]?.let { metric ->
        orderedBodyMetrics.add(metric.copy(isEnabled = true))
      }
      allOtherMetrics[key]?.let { metric ->
        orderedOtherMetrics.add(metric.copy(isEnabled = true))
      }
    }

    // Add remaining disabled metrics
    allBodyMetrics.values.forEach { metric ->
      if (!orderedBodyMetrics.any { it.key == metric.key }) {
        orderedBodyMetrics.add(metric.copy(isEnabled = false))
      }
    }

    allOtherMetrics.values.forEach { metric ->
      if (!orderedOtherMetrics.any { it.key == metric.key }) {
        orderedOtherMetrics.add(metric.copy(isEnabled = false))
      }
    }

    return Pair(orderedBodyMetrics, orderedOtherMetrics)
  }

  /**
   * Determines the notification mode based on scale configuration.
   *
   * @param scale The device scale to check configuration for.
   * @param isSettingsSaving Whether settings are currently being saved.
   * @return The appropriate NotifyScaleMode.
   */
  fun getNotifyScaleMode(scale: Device, isSettingsSaving: Boolean = false): NotifyScaleMode {
    if (isSettingsSaving) return NotifyScaleMode.None

    val isUsersWeightOnlyModeEnabled =
      scale.isWeighOnlyModeEnabledByOthers && scale.connectionStatus == BLEStatus.CONNECTED

    val shouldMeasureImpedance = scale.preferences?.shouldMeasureImpedance ?: false
    val shouldMeasurePulse = scale.preferences?.shouldMeasurePulse ?: false

    return when {
      isUsersWeightOnlyModeEnabled && shouldMeasureImpedance && !shouldMeasurePulse -> {
        NotifyScaleMode.UserWeightOnlyModeOnWithHeartRateOff
      }

      isUsersWeightOnlyModeEnabled -> {
        NotifyScaleMode.UserWeightOnlyModeOn
      }

      !shouldMeasureImpedance && !shouldMeasurePulse -> {
        NotifyScaleMode.WeightOnlyModeOn
      }

      !isUsersWeightOnlyModeEnabled && shouldMeasureImpedance && !shouldMeasurePulse -> {
        NotifyScaleMode.HeartRateOff
      }

      shouldMeasureImpedance && shouldMeasurePulse && !isUsersWeightOnlyModeEnabled -> {
        NotifyScaleMode.None
      }

      else -> NotifyScaleMode.None
    }
  }
}
