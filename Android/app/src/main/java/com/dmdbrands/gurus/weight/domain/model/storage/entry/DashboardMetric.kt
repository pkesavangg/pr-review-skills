package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.graph.GraphUtil.averageOrNull
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import android.os.Parcelable

/**
 * Data class representing dashboard metrics for display in the dashboard screen.
 * This model contains the key health metrics that are displayed in the metrics section.
 */
@Serializable
@Parcelize
data class DashboardMetric(
  val isSingleEntry: Boolean = true,
  val rangeText: String? = null,
  val entryTimeStamp: List<String>? = null,
  /**
   * Per MA-3965: when [isSingleEntry] is true (a graph point is selected on Week/Month),
   * true iff the selected day is the most recent day in the data set. Drives the
   * metric-info label between "latest entry" and "day average". Ignored for Year/Total
   * (always "month average") and for history-list openings.
   */
  val isLatestDaySelected: Boolean = false,
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
  val isEmpty: Boolean = false,
) : Parcelable {
  companion object {
    /**
     * Creates DashboardMetrics from a PeriodBodyScaleSummary.
     * @param periodBodyScaleSummaries The period summary containing the metrics data.
     * @return DashboardMetrics object with the extracted metrics.
     */
    fun fromPeriodSummaries(
      periodBodyScaleSummaries: List<PeriodBodyScaleSummary>,
      isSingleEntry: Boolean = true,
      rangeText: String? = null,
      isLatestDaySelected: Boolean = false,
    ): DashboardMetric =
      if (periodBodyScaleSummaries.isEmpty())
        this.empty(rangeText = rangeText?.lowercase())
      else
        DashboardMetric(
          rangeText = rangeText?.lowercase(),
          isSingleEntry = isSingleEntry,
          isLatestDaySelected = isLatestDaySelected,
          entryTimeStamp = periodBodyScaleSummaries.map { it.entryTimestamp },
          weight = periodBodyScaleSummaries.map { it.weight }.averageOrNull(),
          bmi = periodBodyScaleSummaries.mapNotNull { it.bmi }.averageOrNull(),
          bodyFat = periodBodyScaleSummaries.mapNotNull { it.bodyFat }.averageOrNull(),
          muscleMass = periodBodyScaleSummaries.mapNotNull { it.muscleMass }.averageOrNull(),
          bodyWater = periodBodyScaleSummaries.mapNotNull { it.water }.averageOrNull(),
          heartRate = periodBodyScaleSummaries.mapNotNull { it.pulse }.averageOrNull()?.toInt(),
          boneMass = periodBodyScaleSummaries.mapNotNull { it.boneMass }.averageOrNull(),
          visceralFatLevel = periodBodyScaleSummaries.mapNotNull { it.visceralFatLevel }.averageOrNull()?.roundToInt()
            ?.toDouble(),
          subcutaneousFatPercent = periodBodyScaleSummaries.mapNotNull { it.subcutaneousFatPercent }.averageOrNull(),
          proteinPercent = periodBodyScaleSummaries.mapNotNull { it.proteinPercent }.averageOrNull(),
          skeletalMusclePercent = periodBodyScaleSummaries.mapNotNull { it.skeletalMusclePercent }.averageOrNull(),
          bmr = periodBodyScaleSummaries.mapNotNull { it.bmr }.averageOrNull(),
          metabolicAge = periodBodyScaleSummaries.mapNotNull { it.metabolicAge }.averageOrNull()?.roundToInt(),
          unit = periodBodyScaleSummaries.random().unit,
        )

    fun fromScaleEntry(scaleEntry: ScaleEntry): DashboardMetric =
      DashboardMetric(
        isSingleEntry = true,
        entryTimeStamp = listOf(scaleEntry.entry.entryTimestamp),
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
    fun empty(rangeText: String? = null): DashboardMetric =
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
        isSingleEntry = false,
        unit = WeightUnit.LB,
        rangeText = rangeText,
        isEmpty = true,
      )
  }
}
