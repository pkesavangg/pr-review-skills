package com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model

import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ForgotPasswordDialogReducer].
 *
 * The reducer is a pure function — no coroutines or mocking needed.
 * [ForgotPasswordDialogFormControls.create] builds a realistic form for state construction.
 */
class ForgotPasswordDialogReducerTest {

    private lateinit var reducer: ForgotPasswordDialogReducer

    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
        isSuccess: Boolean = false,
    ): ForgotPasswordDialogState {
        val controls = ForgotPasswordDialogFormControls.create()
        val form = FormGroup(controls)
        return ForgotPasswordDialogState(
            form = form,
            isLoading = isLoading,
            error = error,
            isSuccess = isSuccess,
        )
    }

    @BeforeEach
    fun setUp() {
        reducer = ForgotPasswordDialogReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default ForgotPasswordDialogState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.isSuccess).isFalse()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading to true, clears error and isSuccess`() {
        val state = makeState(isLoading = false, error = "previous error", isSuccess = true)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `Submit when already loading keeps isLoading true`() {
        val state = makeState(isLoading = true, error = null, isSuccess = false)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
        assertThat(result.isSuccess).isFalse()
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    @Test
    fun `Close sets isLoading to false, clears error and isSuccess`() {
        val state = makeState(isLoading = true, error = "some error", isSuccess = true)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Close)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `Close when state is already clean preserves clean state`() {
        val state = makeState(isLoading = false, error = null, isSuccess = false)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Close)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.isSuccess).isFalse()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false, stores error message and clears isSuccess`() {
        val state = makeState(isLoading = true, error = null, isSuccess = true)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Error("reset failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("reset failed")
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(error = "old error")

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    @Test
    fun `Error with empty string stores empty error`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Error(""))

        assertThat(result.error).isEqualTo("")
        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success sets isLoading to false, isSuccess to true and clears error`() {
        val state = makeState(isLoading = true, error = "err", isSuccess = false)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `Success when already succeeded keeps isSuccess true`() {
        val state = makeState(isLoading = false, error = null, isSuccess = true)

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.Success)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.error).isNull()
        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetEmail
    // -------------------------------------------------------------------------

    @Test
    fun `SetEmail updates email control value in form`() {
        val state = makeState()

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.SetEmail("user@example.com"))

        assertThat(result.form.controls.email.value).isEqualTo("user@example.com")
    }

    @Test
    fun `SetEmail with empty string clears email control value`() {
        val state = makeState()
        // First set a value to ensure we start from a non-empty state
        val stateWithEmail = reducer.reduce(state, ForgotPasswordDialogIntent.SetEmail("user@example.com"))

        val result = reducer.reduce(stateWithEmail, ForgotPasswordDialogIntent.SetEmail(""))

        assertThat(result.form.controls.email.value).isEqualTo("")
    }

    @Test
    fun `SetEmail preserves isLoading and error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, ForgotPasswordDialogIntent.SetEmail("a@b.com"))

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("err")
    }

    @Test
    fun `SetEmail replaces previously set email`() {
        val state = makeState()
        val afterFirst = reducer.reduce(state, ForgotPasswordDialogIntent.SetEmail("first@example.com"))

        val result = reducer.reduce(afterFirst, ForgotPasswordDialogIntent.SetEmail("second@example.com"))

        assertThat(result.form.controls.email.value).isEqualTo("second@example.com")
    }
}
