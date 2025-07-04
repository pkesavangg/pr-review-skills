package com.greatergoods.meapp.domain.model.storage.entry

import com.greatergoods.meapp.data.storage.db.entity.entry.EntryEntity
import com.greatergoods.meapp.domain.model.common.IUnitProcessable
import com.greatergoods.meapp.domain.model.common.WeightUnit
import com.greatergoods.meapp.features.goal.helper.Weightless
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.convertWeight
import com.greatergoods.meapp.features.manualEntry.helper.EntryHelper.rounded

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
                val newScaleEntry = this.scale.scaleEntry.copy(
                    weight = finalWeight.rounded() ?: this.scale.scaleEntry.weight
                )
                newScaleEntry.prefix = if (weightLess?.isWeightlessOn == true && finalWeight > 0) "+" else ""
                this.copy(
                    entry = this.entry.copy(
                        unit = toUnit,
                    ),
                    scale = this.scale.copy(
                        scaleEntry = newScaleEntry
                    ),
                )
            }

            is BpmEntry -> this
        }
    }
}



