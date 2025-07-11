package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.domain.model.common.WeightUnit
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import android.os.Parcelable

/**
 * Data class representing dashboard metrics for display in the dashboard screen.
 * This model contains the key health metrics that are displayed in the metrics section.
 */
@Serializable
@Parcelize
data class DashboardMetric(
  val entryTimeStamp: String? = null,
  val weight: Double?,
  val bmi: Double?,
  val bodyFat: Double?,
  val muscleMass: Double?,
  val bodyWater: Double?,
  val heartRate: Int?,
  val boneMass: Double?,
  val visceralFatLevel: Double?,
  val subcutaneousFatPercent: Double?,
  val proteinPercent: Double?,
  val skeletalMusclePercent: Double?,
  val bmr: Double?,
  val metabolicAge: Int?,
  val unit: WeightUnit,
) : Parcelable {
  companion object {
    /**
     * Creates DashboardMetrics from a PeriodBodyScaleSummary.
     * @param summary The period summary containing the metrics data.
     * @return DashboardMetrics object with the extracted metrics.
     */
    fun fromPeriodSummary(summary: PeriodBodyScaleSummary): DashboardMetric =
      DashboardMetric(
        entryTimeStamp = summary.entryTimestamp,
        weight = summary.weight,
        bmi = summary.bmi,
        bodyFat = summary.bodyFat,
        muscleMass = summary.muscleMass,
        bodyWater = summary.water,
        heartRate = summary.pulse?.toInt(),
        boneMass = summary.boneMass,
        visceralFatLevel = summary.visceralFatLevel,
        subcutaneousFatPercent = summary.subcutaneousFatPercent,
        proteinPercent = summary.proteinPercent,
        skeletalMusclePercent = summary.skeletalMusclePercent,
        bmr = summary.bmr,
        metabolicAge = summary.metabolicAge?.toInt(),
        unit = summary.unit,
      )

    fun fromScaleEntry(scaleEntry: ScaleEntry): DashboardMetric =
      DashboardMetric(
        entryTimeStamp = scaleEntry.entry.entryTimestamp,
        weight = scaleEntry.scale.scaleEntry.weight,
        bmi = scaleEntry.scale.scaleEntry.bmi,
        bodyFat = scaleEntry.scale.scaleEntry.bodyFat,
        muscleMass = scaleEntry.scale.scaleEntry.muscleMass,
        bodyWater = scaleEntry.scale.scaleEntry.water,
        heartRate = scaleEntry.scale.scaleEntryMetric?.pulse,
        boneMass = scaleEntry.scale.scaleEntryMetric?.boneMass,
        visceralFatLevel = scaleEntry.scale.scaleEntryMetric?.visceralFatLevel,
        subcutaneousFatPercent = scaleEntry.scale.scaleEntryMetric?.subcutaneousFatPercent,
        proteinPercent = scaleEntry.scale.scaleEntryMetric?.proteinPercent,
        skeletalMusclePercent = scaleEntry.scale.scaleEntryMetric?.skeletalMusclePercent,
        bmr = scaleEntry.scale.scaleEntryMetric?.bmr,
        metabolicAge = scaleEntry.scale.scaleEntryMetric?.metabolicAge,
        unit = scaleEntry.entry.unit,
      )

    /**
     * Creates an empty DashboardMetrics object.
     * @return Empty DashboardMetrics with all values set to null.
     */
    fun empty(): DashboardMetric =
      DashboardMetric(
        bmi = null,
        weight = null,
        bodyFat = null,
        muscleMass = null,
        bodyWater = null,
        heartRate = null,
        boneMass = null,
        visceralFatLevel = null,
        subcutaneousFatPercent = null,
        proteinPercent = null,
        skeletalMusclePercent = null,
        bmr = null,
        metabolicAge = null,
        unit = WeightUnit.LB,
      )
  }
}
