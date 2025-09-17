package com.dmdbrands.gurus.weight.domain.model.storage.entry

import androidx.room.Ignore
import com.dmdbrands.gurus.weight.domain.model.common.IUnitProcessable
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded

data class PeriodBodyScaleSummary(
  val period: String, // "YYYY-MM" for month, "YYYY-MM-DD" for day
  val entryTimestamp: String, // For average: latest timestamp in period; for latest: timestamp of the latest entry
  val weight: Double,
  val bodyFat: Double? = null,
  val muscleMass: Double? = null,
  val water: Double? = null,
  val bmi: Double? = null,
  val bmr: Double? = null,
  val metabolicAge: Double? = null,
  val proteinPercent: Double? = null,
  val pulse: Double? = null,
  val skeletalMusclePercent: Double? = null,
  val subcutaneousFatPercent: Double? = null,
  val visceralFatLevel: Double? = null,
  val boneMass: Double? = null,
  val impedance: Double? = null,
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
