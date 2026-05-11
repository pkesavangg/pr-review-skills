package com.dmdbrands.gurus.weight.domain.model.storage.entry

import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.IUnitProcessable
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded

/**
 * Sealed class representing a database entry, which can be either a scale entry or a BPM entry.
 * All subclasses must override the [entry] property.
 */
sealed class Entry : IUnitProcessable<Entry> {
    /**
     * The core entry entity for this record.
     */
    abstract val entry: EntryEntity
    fun updateEntry(entry: EntryEntity): Entry {
        return when (this) {
            is ScaleEntry -> this.copy(entry = entry)
            is BpmEntry -> this.copy(entry = entry)
            is BabyEntry -> this.copy(entry = entry)
        }
    }

    override fun process(unit: WeightUnit?, weightLess: Weightless?): Entry {
        return when (this) {
            is ScaleEntry -> {
                val fromUnit = WeightUnit.LB
                val toUnit = unit ?: fromUnit
                val convertedWeight = convertWeight(this.scale.scaleEntry.weight, fromUnit, toUnit)
                val finalWeight =
                    if (weightLess?.isWeightlessOn == true) convertedWeight - weightLess.weightlessWeight else convertedWeight
                val normalizedWeight = finalWeight.rounded()?.takeIf { kotlin.math.abs(it) >= 0.0001 } ?: 0.0
                val newScaleEntry = this.scale.scaleEntry.copy(
                    weight = normalizedWeight,
                )
              newScaleEntry.prefix = if (weightLess?.isWeightlessOn == true && finalWeight > 0) "+" else ""
                this.copy(
                    entry = this.entry.copy(
                        unit = toUnit,
                    ),
                    scale = this.scale.copy(
                        scaleEntry = newScaleEntry,
                    ),
                )
            }

            is BpmEntry -> this
            is BabyEntry -> this
        }
    }
}

/**
 * Adapts a single [BpmEntry] to the [PeriodBpmSummary] shape expected by Health Connect sync.
 * [PeriodBpmSummary.period] is intentionally empty because HC sync operates on individual
 * entries, not period aggregations.
 */
fun BpmEntry.toBpmSummary(): PeriodBpmSummary = PeriodBpmSummary(
    period = "",
    entryTimestamp = this.entry.entryTimestamp,
    avgSystolic = this.systolic,
    avgDiastolic = this.diastolic,
    avgPulse = this.pulse,
)

fun Entry.toPeriodBodyScaleSummary(): PeriodBodyScaleSummary? {
    return when (this) {
        is ScaleEntry -> {
            val scaleEntity = this.scale.scaleEntry
            val metrics = this.scale.scaleEntryMetric
            PeriodBodyScaleSummary(
                period = "", // Set as needed
                entryTimestamp = this.entry.entryTimestamp, // ISO string
                weight = scaleEntity.weight,
                bodyFat = scaleEntity.bodyFat,
                muscleMass = scaleEntity.muscleMass,
                water = scaleEntity.water,
                bmi = scaleEntity.bmi,
                bmr = metrics?.bmr,
                metabolicAge = metrics?.metabolicAge?.toDouble(),
                proteinPercent = metrics?.proteinPercent,
                pulse = metrics?.pulse?.toDouble(),
                skeletalMusclePercent = metrics?.skeletalMusclePercent,
                subcutaneousFatPercent = metrics?.subcutaneousFatPercent,
                visceralFatLevel = metrics?.visceralFatLevel,
                boneMass = metrics?.boneMass,
                impedance = metrics?.impedance?.toDouble(),
                unit = this.entry.unit
            )
        }
        is BpmEntry -> null
        is BabyEntry -> null
    }
}



