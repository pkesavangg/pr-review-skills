package com.dmdbrands.gurus.weight.domain.model.common

import androidx.room.Ignore
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.convertWeight
import com.dmdbrands.gurus.weight.features.manualEntry.helper.EntryHelper.rounded
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryMonth(
    val entryTimestamp: String? = null,
    val avgWeight: Double? = null,
    val entryCount: Int? = null,
    val change: Double? = null
) : IUnitProcessable<HistoryMonth> {
    @Ignore
    var unit: String? = null
    @Ignore
    var avgWeightPrefix: String? = null

    override fun process(unit: WeightUnit?, weightLess: Weightless?): HistoryMonth {
        val monthYear = entryTimestamp?.parseToMonthYear()
        val toUnit = unit ?: WeightUnit.LB

        val convertedAvg = avgWeight?.convertToUnit(toUnit)
        val convertedChange = change?.convertToUnit(toUnit)

        val finalAvg = convertedAvg?.applyWeightless(weightLess)

        val result = this.copy(
            entryTimestamp = monthYear,
            avgWeight = finalAvg?.rounded(),
            entryCount = entryCount,
            change = convertedChange?.rounded()
        )
        result.unit = toUnit.label
        result.avgWeightPrefix = if (weightLess?.isWeightlessOn == true && finalAvg?.isPositive() == true) "+" else ""
        return result
    }

    // Extension functions for better readability
    private fun String.parseToMonthYear(): String = try {
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
            .format(ZonedDateTime.parse(this))
    } catch (e: Exception) {
        this
    }

    private fun Double.convertToUnit(toUnit: WeightUnit): Double =
        convertWeight(this, WeightUnit.LB, toUnit)

    private fun Double.applyWeightless(weightLess: Weightless?): Double =
        if (weightLess?.isWeightlessOn == true) this - weightLess.weightlessWeight else this

    private fun Double.isPositive(): Boolean = this > 0.0
}
