package com.dmdbrands.gurus.weight.features.dashboard.viewmodel.baby

import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeConverter
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBabySummary
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [prependBirthPoint] — the babyApp-parity birth point that makes the
 * growth curve start at the birthday with the birth weight/length as its first point.
 */
class PrependBirthPointTest {

    private val birthIso = "2025-06-15"

    private fun profile(
        birthdate: String? = birthIso,
        weight: Int? = 33000,
        length: Int? = 500,
    ) = BabyProfile(
        id = "baby-1",
        accountId = "acct-1",
        name = "Test",
        birthdate = birthdate,
        sex = "male",
        birthWeightDecigrams = weight,
        birthLengthMillimeters = length,
    )

    private fun reading(dateIso: String, weight: Int? = 40000) =
        PeriodBabySummary(
            period = dateIso,
            entryTimestamp = dateIso,
            babyId = "baby-1",
            avgWeightDecigrams = weight,
            avgLengthMillimeters = null,
        )

    @Test
    fun `prepends birth point at the birthdate with birth measurements`() {
        val result = prependBirthPoint(profile(), listOf(reading("2025-07-20")))
        assertThat(result).hasSize(2)
        val first = result.first()
        assertThat(first.entryTimestamp).isEqualTo(birthIso)
        assertThat(first.avgWeightDecigrams).isEqualTo(33000)
        assertThat(first.avgLengthMillimeters).isEqualTo(500)
        assertThat(first.babyId).isEqualTo("baby-1")
    }

    @Test
    fun `prepends to an empty series when birth measurement exists`() {
        val result = prependBirthPoint(profile(), emptyList())
        assertThat(result).hasSize(1)
        assertThat(result.first().avgWeightDecigrams).isEqualTo(33000)
    }

    @Test
    fun `no birth point when profile has neither birth weight nor length`() {
        val entries = listOf(reading("2025-07-20"))
        val result = prependBirthPoint(profile(weight = null, length = null), entries)
        assertThat(result).isEqualTo(entries)
    }

    @Test
    fun `no birth point when birthdate is null`() {
        val entries = listOf(reading("2025-07-20"))
        val result = prependBirthPoint(profile(birthdate = null), entries)
        assertThat(result).isEqualTo(entries)
    }

    @Test
    fun `no duplicate birth point when a reading already lands on the birth day`() {
        // A reading on the birthdate itself — should not add a second birth-day point.
        val entries = listOf(reading(birthIso, weight = 33500), reading("2025-07-20"))
        val result = prependBirthPoint(profile(), entries)
        assertThat(result).isEqualTo(entries)
    }

    @Test
    fun `birth point carries only the available measurement`() {
        val result = prependBirthPoint(profile(weight = 33000, length = null), emptyList())
        assertThat(result).hasSize(1)
        assertThat(result.first().avgWeightDecigrams).isEqualTo(33000)
        assertThat(result.first().avgLengthMillimeters).isNull()
    }

    @Test
    fun `birth point timestamp resolves to the birthdate`() {
        val result = prependBirthPoint(profile(), emptyList())
        assertThat(result.first().getTimeStamp())
            .isEqualTo(DateTimeConverter.isoToTimestamp(birthIso))
    }
}
