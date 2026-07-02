package com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.Helper

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.enum.NotifyScaleMode
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.DeviceMetric
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.DeviceMetricKeys
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.otherDeviceMetrics
import com.dmdbrands.gurus.weight.features.DeviceMetricsSetting.model.deviceMetrics
import com.dmdbrands.library.ggbluetooth.enums.TimeFormat
import com.greatergoods.ggbluetoothsdk.external.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * Helper object providing utility functions for display metrics management.
 */
object DeviceMetricsHelper {

  fun getAllMetrics(): ImmutableList<String> {
    return persistentListOf(
      DeviceMetricKeys.BMI,
      DeviceMetricKeys.BODY_FAT_PERCENT,
      DeviceMetricKeys.MUSCLE_PERCENT,
      DeviceMetricKeys.BODY_WATER_PERCENT,
      DeviceMetricKeys.HEART_RATE,
      DeviceMetricKeys.BONE_PERCENT,
      DeviceMetricKeys.VISCERAL_FAT_LEVEL,
      DeviceMetricKeys.SUBCUTANEOUS_FAT_PERCENT,
      DeviceMetricKeys.PROTEIN_PERCENT,
      DeviceMetricKeys.SKELETAL_MUSCLE_PERCENT,
      DeviceMetricKeys.BMR,
      DeviceMetricKeys.METABOLIC_AGE,
      DeviceMetricKeys.GOAL_PROGRESS,
      DeviceMetricKeys.DAILY_AVERAGE,
      DeviceMetricKeys.WEEKLY_AVERAGE,
      DeviceMetricKeys.MONTHLY_AVERAGE,
    )
  }

  fun getDefaultPreference(displayName: String, id: String = UUID.randomUUID().toString()): Preferences {
    return Preferences(
      id = id,
      timeFormat = TimeFormat.TWELVE,
      tzOffset = Utils.getTimeZoneInMinutes(),
      displayName = displayName,
      displayMetrics = getAllMetrics(),
      shouldMeasureImpedance = true,
      shouldMeasurePulse = false,
      shouldFactoryReset = false,
      wifiFotaScheduleTime = 0L,
    )
  }

  /**
   * Creates ordered metric states based on current metrics from the scale.
   *
   * @param currentMetrics Current ordered list of metric keys from the scale.
   * @return Pair of ordered body metrics and other metrics with proper enabled states.
   */
  fun createOrderedMetrics(currentMetrics: List<String>): Pair<List<DeviceMetric>, List<DeviceMetric>> {
    val allBodyMetrics = deviceMetrics.associateBy { it.key }
    val allOtherMetrics = otherDeviceMetrics.associateBy { it.key }

    val orderedBodyMetrics = mutableListOf<DeviceMetric>()
    val orderedOtherMetrics = mutableListOf<DeviceMetric>()

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
