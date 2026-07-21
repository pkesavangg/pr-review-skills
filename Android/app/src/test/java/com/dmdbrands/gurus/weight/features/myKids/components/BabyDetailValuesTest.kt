package com.dmdbrands.gurus.weight.features.myKids.components

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.features.myKids.strings.MyKidsStrings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [babyDetailValues] — the pure formatter behind the My Kids expanded detail rows.
 * Covers the unset fallbacks, sex capitalization, birthday formatting, and metric/imperial routing.
 */
class BabyDetailValuesTest {

    private fun baby(
        name: String = "Baby",
        birthdate: String? = null,
        sex: String? = null,
        birthLengthMillimeters: Int? = null,
        birthWeightDecigrams: Int? = null,
    ) = BabyProfile(
        id = "1",
        accountId = "a",
        name = name,
        birthdate = birthdate,
        sex = sex,
        birthLengthMillimeters = birthLengthMillimeters,
        birthWeightDecigrams = birthWeightDecigrams,
    )

    @Test
    fun `all fields unset falls back to placeholder`() {
        val details = babyDetailValues(baby(), isMetric = false)

        assertThat(details.birthday).isEqualTo(MyKidsStrings.ValueUnset)
        assertThat(details.biologicalSex).isEqualTo(MyKidsStrings.ValueUnset)
        assertThat(details.birthLength).isEqualTo(MyKidsStrings.ValueUnset)
        assertThat(details.birthWeight).isEqualTo(MyKidsStrings.ValueUnset)
    }

    @Test
    fun `blank sex and non-positive measurements fall back to placeholder`() {
        val details = babyDetailValues(
            baby(sex = "  ", birthLengthMillimeters = 0, birthWeightDecigrams = 0),
            isMetric = false,
        )

        assertThat(details.biologicalSex).isEqualTo(MyKidsStrings.ValueUnset)
        assertThat(details.birthLength).isEqualTo(MyKidsStrings.ValueUnset)
        assertThat(details.birthWeight).isEqualTo(MyKidsStrings.ValueUnset)
    }

    @Test
    fun `stored lowercase sex is capitalized for display`() {
        assertThat(babyDetailValues(baby(sex = "male"), isMetric = false).biologicalSex).isEqualTo("Male")
        assertThat(babyDetailValues(baby(sex = "female"), isMetric = false).biologicalSex).isEqualTo("Female")
        assertThat(babyDetailValues(baby(sex = "private"), isMetric = false).biologicalSex).isEqualTo("Private")
    }

    @Test
    fun `iso birthdate is formatted as a display date`() {
        val details = babyDetailValues(baby(birthdate = "2024-06-10"), isMetric = false)

        assertThat(details.birthday).isEqualTo("June 10, 2024")
    }

    @Test
    fun `length and weight use imperial units when not metric`() {
        val details = babyDetailValues(
            baby(birthLengthMillimeters = 655, birthWeightDecigrams = 7370),
            isMetric = false,
        )

        assertThat(details.birthLength).contains("in")
        assertThat(details.birthWeight).contains("oz")
    }

    @Test
    fun `length and weight use metric units when metric`() {
        val details = babyDetailValues(
            baby(birthLengthMillimeters = 655, birthWeightDecigrams = 7370),
            isMetric = true,
        )

        assertThat(details.birthLength).contains("cm")
        assertThat(details.birthWeight).contains("kg")
    }
}
