package com.dmdbrands.gurus.weight.features.changePassword.model

import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ChangePasswordReducer].
 *
 * The reducer is a pure function — no coroutines or mocking needed for the reducer itself.
 * [ChangePasswordFormControls.create] builds a realistic form for state construction.
 */
class ChangePasswordReducerTest {

    private lateinit var reducer: ChangePasswordReducer

    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
    ): ChangePasswordState {
        val controls = ChangePasswordFormControls.create()
        val form = FormGroup(controls)
        return ChangePasswordState(
            form = form,
            isLoading = isLoading,
            error = error,
        )
    }

    @BeforeEach
    fun setUp() {
        reducer = ChangePasswordReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default ChangePasswordState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading to true and clears error`() {
        val state = makeState(isLoading = false, error = "previous error")

        val result = reducer.reduce(state, ChangePasswordIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Submit when already loading keeps isLoading true`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenForgotPasswordModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenForgotPasswordModal sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, ChangePasswordIntent.OpenForgotPasswordModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `OpenForgotPasswordModal when state is clean preserves clean state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.OpenForgotPasswordModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "help error")

        val result = reducer.reduce(state, ChangePasswordIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `OpenHelpModal when already idle preserves state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnRequestBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "back error")

        val result = reducer.reduce(state, ChangePasswordIntent.OnRequestBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `OnRequestBack when already idle preserves state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.OnRequestBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores the error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.Error("password change failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("password change failed")
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(isLoading = false, error = "old error")

        val result = reducer.reduce(state, ChangePasswordIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    @Test
    fun `Error with empty message stores empty string`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.Error(""))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "previous error")

        val result = reducer.reduce(state, ChangePasswordIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Success when already idle preserves state`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, ChangePasswordIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Form is preserved across all intents
    // -------------------------------------------------------------------------

    @Test
    fun `form instance is preserved after Submit`() {
        val controls = ChangePasswordFormControls.create()
        val form = FormGroup(controls)
        val state = ChangePasswordState(form = form)

        val result = reducer.reduce(state, ChangePasswordIntent.Submit)

        assertThat(result.form).isSameInstanceAs(form)
    }

    @Test
    fun `form instance is preserved after Error`() {
        val controls = ChangePasswordFormControls.create()
        val form = FormGroup(controls)
        val state = ChangePasswordState(form = form)

        val result = reducer.reduce(state, ChangePasswordIntent.Error("err"))

        assertThat(result.form).isSameInstanceAs(form)
    }
}
