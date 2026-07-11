package com.dmdbrands.gurus.weight.features.signup.strings

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
}
