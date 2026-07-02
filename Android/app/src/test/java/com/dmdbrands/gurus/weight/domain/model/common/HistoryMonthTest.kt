package com.dmdbrands.gurus.weight.domain.model.common

import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HistoryMonth.process], which converts an LB-stored monthly
 * average to the active display unit and applies the optional weightless offset.
 */
class HistoryMonthTest {

    @Test
    fun `process with null unit defaults to LB and leaves values unchanged`() {
        val month = HistoryMonth(entryTimestamp = "2024-01", avgWeight = 150.0, entryCount = 5, change = 2.0)

        val result = month.process(unit = null, weightLess = null)

        assertThat(result.avgWeight).isEqualTo(150.0)
        assertThat(result.change).isEqualTo(2.0)
        assertThat(result.entryCount).isEqualTo(5)
        assertThat(result.entryTimestamp).isEqualTo("2024-01")
        assertThat(result.unit).isEqualTo(WeightUnit.LB.label)
        assertThat(result.avgWeightPrefix).isEmpty()
    }

    @Test
    fun `process converts LB to KG and rounds to one decimal`() {
        val month = HistoryMonth(avgWeight = 100.0, change = 10.0)

        val result = month.process(unit = WeightUnit.KG, weightLess = null)

        // 100 / 2.20462 = 45.359..., 10 / 2.20462 = 4.535...
        assertThat(result.avgWeight).isEqualTo(45.4)
        assertThat(result.change).isEqualTo(4.5)
        assertThat(result.unit).isEqualTo(WeightUnit.KG.label)
    }

    @Test
    fun `process applies weightless offset to average weight`() {
        val month = HistoryMonth(avgWeight = 150.0, change = 1.0)
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = 100f)

        val result = month.process(unit = WeightUnit.LB, weightLess = weightless)

        // 150 - 100 = 50 (positive) -> prefix "+"
        assertThat(result.avgWeight).isEqualTo(50.0)
        assertThat(result.avgWeightPrefix).isEqualTo("+")
    }

    @Test
    fun `process with weightless yielding non-positive average uses empty prefix`() {
        val month = HistoryMonth(avgWeight = 80.0, change = 1.0)
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = 100f)

        val result = month.process(unit = WeightUnit.LB, weightLess = weightless)

        // 80 - 100 = -20 (non-positive) -> empty prefix
        assertThat(result.avgWeight).isEqualTo(-20.0)
        assertThat(result.avgWeightPrefix).isEmpty()
    }

    @Test
    fun `process with weightless off does not subtract offset and keeps empty prefix`() {
        val month = HistoryMonth(avgWeight = 150.0, change = 1.0)
        val weightless = Weightless(isWeightlessOn = false, weightlessWeight = 100f)

        val result = month.process(unit = WeightUnit.LB, weightLess = weightless)

        assertThat(result.avgWeight).isEqualTo(150.0)
        assertThat(result.avgWeightPrefix).isEmpty()
    }

    @Test
    fun `process with null avgWeight and null change keeps them null`() {
        val month = HistoryMonth(entryTimestamp = "2024-02", avgWeight = null, entryCount = 0, change = null)

        val result = month.process(unit = WeightUnit.KG, weightLess = Weightless(isWeightlessOn = true, weightlessWeight = 10f))

        assertThat(result.avgWeight).isNull()
        assertThat(result.change).isNull()
        assertThat(result.avgWeightPrefix).isEmpty()
    }
}
