package com.dmdbrands.gurus.weight.features.signup.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression guard for the height unit toggle labels (MOB-1217).
 *
 * Unit symbols must be lowercase and non-pluralised per the standard convention
 * (NIST/ISO; matches Apple Health / Fitbit), and the ft/in label carries no spaces
 * around the slash. These are rendered with `uppercaseLabels = false` on the signup
 * height slide, so the literals below are what the user sees.
 */
class SignupStringsTest {

    @Test
    fun `height ft-in label is lowercase with no spaces around the slash`() {
        assertThat(SignupStrings.heightUnitFtIn).isEqualTo("ft/in")
    }

    @Test
    fun `height cm label is lowercase`() {
        assertThat(SignupStrings.heightUnitCm).isEqualTo("cm")
    }

    /**
     * Regression guard for MOB-1453: the profile-ready title must name ALL completed devices
     * (not just the latest), lowercased and pluralised. This is the exact bug the PR fixes.
     */
    @Test
    fun `readyTitle names both completed devices, plural and lowercased`() {
        assertThat(DeviceReadyStrings.readyTitle(setOf(ProductType.MY_WEIGHT, ProductType.BLOOD_PRESSURE)))
            .isEqualTo("Your blood pressure monitor & weight scale profiles are ready!")
    }

    /**
     * The two-device title is listed in the fixed READY_ORDER (Blood Pressure -> Weight -> Baby),
     * independent of the order the user actually completed the devices in.
     */
    @Test
    fun `readyTitle uses fixed device order regardless of input order`() {
        val a = DeviceReadyStrings.readyTitle(setOf(ProductType.MY_WEIGHT, ProductType.BLOOD_PRESSURE))
        val b = DeviceReadyStrings.readyTitle(setOf(ProductType.BLOOD_PRESSURE, ProductType.MY_WEIGHT))
        assertThat(a).isEqualTo(b)
        assertThat(a).isEqualTo("Your blood pressure monitor & weight scale profiles are ready!")
    }

    @Test
    fun `readyTitle for a single device uses the singular profile-ready copy`() {
        assertThat(DeviceReadyStrings.readyTitle(setOf(ProductType.MY_WEIGHT)))
            .isEqualTo("Your Weight Scale profile is ready!")
    }
}
