package com.dmdbrands.gurus.weight.features.manualEntry.viewmodel

import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationSeverity
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the blood-pressure systolic≤diastolic cross-field advisory warning
 * wired in [BloodPressureEntryForm.create] (MOB-1432).
 *
 * Behaviour mirrors Balance Health (bpmMobileApp4): when systolic is not higher than diastolic
 * an orange advisory appears on the affected field(s), the values are retained, and the reading
 * is still saveable (WARNING severity — never blocking). The cross-field rule fires only while a
 * field is inside its typical range, so it never collides with the range warning.
 */
class BloodPressureCrossFieldWarningTest {

    private fun controls() = BloodPressureEntryForm.create().bloodPressure.controls

    // -------------------------------------------------------------------------
    // Inverted pair (both in range) — the reported case: 110 / 120
    // -------------------------------------------------------------------------

    @Test
    fun `inverted pair warns on both systolic and diastolic`() {
        val c = controls()

        c.systolic.onValueChange("110")
        c.diastolic.onValueChange("120")

        assertThat(c.systolic.warningMessage).isEqualTo(EntryScreenStrings.SYSTOLIC_CROSS_WARNING)
        assertThat(c.diastolic.warningMessage).isEqualTo(EntryScreenStrings.DIASTOLIC_CROSS_WARNING)
    }

    @Test
    fun `inverted pair cross-field warning is advisory (WARNING severity)`() {
        val c = controls()

        c.systolic.onValueChange("110")
        c.diastolic.onValueChange("120")

        assertThat(c.systolic.warning?.severity).isEqualTo(ValidationSeverity.WARNING)
        assertThat(c.diastolic.warning?.severity).isEqualTo(ValidationSeverity.WARNING)
    }

    @Test
    fun `inverted pair is still saveable (warnings never block validity)`() {
        val c = controls()

        c.systolic.onValueChange("110")
        c.diastolic.onValueChange("120")
        c.pulse.onValueChange("72")

        // Warnings are advisory: every control still reports its value as valid.
        assertThat(c.systolic.isValueValid()).isTrue()
        assertThat(c.diastolic.isValueValid()).isTrue()
        assertThat(c.systolic.isError).isFalse()
        assertThat(c.diastolic.isError).isFalse()
    }

    // -------------------------------------------------------------------------
    // Correctly ordered pair — no warning
    // -------------------------------------------------------------------------

    @Test
    fun `correctly ordered pair shows no cross-field warning`() {
        val c = controls()

        c.systolic.onValueChange("130")
        c.diastolic.onValueChange("80")

        assertThat(c.systolic.warning).isNull()
        assertThat(c.diastolic.warning).isNull()
    }

    @Test
    fun `equal values show no cross-field warning (strict inequality parity)`() {
        val c = controls()

        c.systolic.onValueChange("120")
        c.diastolic.onValueChange("120")

        assertThat(c.systolic.warning).isNull()
        assertThat(c.diastolic.warning).isNull()
    }

    // -------------------------------------------------------------------------
    // Correcting the reading clears the warning on both fields
    // -------------------------------------------------------------------------

    @Test
    fun `correcting systolic clears the warning on both fields`() {
        val c = controls()
        c.systolic.onValueChange("110")
        c.diastolic.onValueChange("120")
        assertThat(c.systolic.warning).isNotNull()

        c.systolic.onValueChange("130")

        assertThat(c.systolic.warning).isNull()
        assertThat(c.diastolic.warning).isNull()
    }

    // -------------------------------------------------------------------------
    // Out-of-range field keeps its range warning; sibling still shows cross-field
    // (systolic 55 is below the typical range → range warning wins on systolic)
    // -------------------------------------------------------------------------

    @Test
    fun `out-of-range systolic keeps range warning while diastolic shows cross-field`() {
        val c = controls()

        c.systolic.onValueChange("55")
        c.diastolic.onValueChange("120")

        // Systolic is below its typical range, so its range warning is shown, NOT the cross-field one.
        assertThat(c.systolic.warningMessage).isNotEqualTo(EntryScreenStrings.SYSTOLIC_CROSS_WARNING)
        assertThat(c.systolic.warning).isNotNull()
        // Diastolic is in range and higher than systolic → cross-field advisory.
        assertThat(c.diastolic.warningMessage).isEqualTo(EntryScreenStrings.DIASTOLIC_CROSS_WARNING)
    }

    // -------------------------------------------------------------------------
    // Only one field entered
    // -------------------------------------------------------------------------

    @Test
    fun `systolic alone shows no cross-field warning`() {
        val c = controls()

        c.systolic.onValueChange("110")

        assertThat(c.systolic.warning).isNull()
    }

    @Test
    fun `diastolic alone (systolic empty) shows cross-field warning`() {
        val c = controls()

        c.diastolic.onValueChange("120")

        // bpmMobileApp4 parity: diastolic in range with an empty systolic still advises.
        assertThat(c.diastolic.warningMessage).isEqualTo(EntryScreenStrings.DIASTOLIC_CROSS_WARNING)
    }
}
