package com.dmdbrands.gurus.weight.features.weightless.model

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WeightlessReducer].
 *
 * The reducer is a pure function — no coroutines or mocking needed.
 * [WeightlessFormControls.create] builds a realistic form for state construction.
 */
class WeightlessReducerTest {

    private lateinit var reducer: WeightlessReducer

    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
        isWeightlessOn: Boolean = false,
        hasToggleChanged: Boolean = false,
        weightUnit: WeightUnit = WeightUnit.LB,
        isMetric: Boolean = false,
    ): WeightlessState {
        val controls = WeightlessFormControls.create(weightUnit)
        val form = FormGroup(controls)
        return WeightlessState(
            form = form,
            isLoading = isLoading,
            error = error,
            isWeightlessOn = isWeightlessOn,
            hasToggleChanged = hasToggleChanged,
            weightUnit = weightUnit,
            isMetric = isMetric,
        )
    }

    @BeforeEach
    fun setUp() {
        reducer = WeightlessReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default WeightlessState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.isWeightlessOn).isFalse()
        assertThat(state.hasToggleChanged).isFalse()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading to true and clears error`() {
        val state = makeState(isLoading = false, error = "previous error")

        val result = reducer.reduce(state, WeightlessIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Submit when already loading keeps isLoading true`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, WeightlessIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ToggleWeightless
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleWeightless when off toggles isWeightlessOn to true`() {
        val state = makeState(isWeightlessOn = false)

        val result = reducer.reduce(state, WeightlessIntent.ToggleWeightless)

        assertThat(result.isWeightlessOn).isTrue()
    }

    @Test
    fun `ToggleWeightless when on toggles isWeightlessOn to false`() {
        val state = makeState(isWeightlessOn = true)

        val result = reducer.reduce(state, WeightlessIntent.ToggleWeightless)

        assertThat(result.isWeightlessOn).isFalse()
    }

    @Test
    fun `ToggleWeightless sets hasToggleChanged to true`() {
        val state = makeState(isWeightlessOn = false, hasToggleChanged = false)

        val result = reducer.reduce(state, WeightlessIntent.ToggleWeightless)

        assertThat(result.hasToggleChanged).isTrue()
    }

    @Test
    fun `ToggleWeightless toggled twice keeps hasToggleChanged true`() {
        val state = makeState(isWeightlessOn = false, hasToggleChanged = false)

        val afterFirst = reducer.reduce(state, WeightlessIntent.ToggleWeightless)
        val afterSecond = reducer.reduce(afterFirst, WeightlessIntent.ToggleWeightless)

        assertThat(afterSecond.isWeightlessOn).isFalse()
        assertThat(afterSecond.hasToggleChanged).isTrue()
    }

    @Test
    fun `ToggleWeightless preserves weightUnit`() {
        val state = makeState(isWeightlessOn = false, weightUnit = WeightUnit.KG)

        val result = reducer.reduce(state, WeightlessIntent.ToggleWeightless)

        assertThat(result.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `ToggleWeightless rebuilds form with same weight value`() {
        val state = makeState(isWeightlessOn = false)

        val result = reducer.reduce(state, WeightlessIntent.ToggleWeightless)

        // The form is rebuilt but retains the same initial weight value "0.0"
        assertThat(result.form.controls.weightlessWeight.value)
            .isEqualTo(state.form.controls.weightlessWeight.value)
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, WeightlessIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `OpenHelpModal when already idle preserves state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, WeightlessIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores the error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, WeightlessIntent.Error("update failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("update failed")
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(error = "old error")

        val result = reducer.reduce(state, WeightlessIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces the form and preserves other fields`() {
        val state = makeState(isLoading = true, error = "err", isWeightlessOn = true)
        val newForm: FormGroup<WeightlessFormControls> = mockk(relaxed = true)

        val result = reducer.reduce(state, WeightlessIntent.UpdateForm(newForm))

        assertThat(result.form).isSameInstanceAs(newForm)
        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("err")
        assertThat(result.isWeightlessOn).isTrue()
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "update failed")

        val result = reducer.reduce(state, WeightlessIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Success when already idle preserves state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, WeightlessIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, WeightlessIntent.OnBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `OnBack preserves isWeightlessOn and hasToggleChanged`() {
        val state = makeState(isLoading = true, isWeightlessOn = true, hasToggleChanged = true)

        val result = reducer.reduce(state, WeightlessIntent.OnBack)

        assertThat(result.isWeightlessOn).isTrue()
        assertThat(result.hasToggleChanged).isTrue()
    }
}
