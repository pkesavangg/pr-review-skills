package com.greatergoods.meapp.domain.model.storage.entry

import androidx.room.Ignore
import com.greatergoods.meapp.domain.model.common.IUnitProcessable
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.goal.helper.Weightless
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.convertWeight
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded

data class PeriodBodyScaleSummary(
  val period: String, // "YYYY-MM" for month, "YYYY-MM-DD" for day
  val entryTimestamp: String, // For average: latest timestamp in period; for latest: timestamp of the latest entry
  val weight: Double,
  val bodyFat: Double?,
  val muscleMass: Double?,
  val water: Double?,
  val bmi: Double?,
  val bmr: Double?,
  val metabolicAge: Double?,
  val proteinPercent: Double?,
  val pulse: Double?,
  val skeletalMusclePercent: Double?,
  val subcutaneousFatPercent: Double?,
  val visceralFatLevel: Double?,
  val boneMass: Double?,
  val impedance: Double?,
  val unit: WeightUnit
) : IUnitProcessable<PeriodBodyScaleSummary> {
  @Ignore
  var prefix: String? = null

  override fun process(unit: WeightUnit?, weightLess: Weightless?): PeriodBodyScaleSummary {
    val fromUnit = WeightUnit.LB
    val toUnit = unit ?: fromUnit
    val convertedWeight = convertWeight(this.weight, fromUnit, toUnit)
    val finalWeight =
      if (weightLess?.isWeightlessOn == true) convertedWeight - weightLess.weightlessWeight else convertedWeight
    val result = this.copy(
      weight = finalWeight.rounded() ?: this.weight,
      unit = toUnit,
    )
    result.prefix = if (weightLess?.isWeightlessOn == true && finalWeight > 0) "+" else ""
    return result
  }
}
