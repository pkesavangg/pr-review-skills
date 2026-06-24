package com.dmdbrands.gurus.weight.features.login.model

import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LoginReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * A relaxed mockk is used for [FormGroup] because constructing a real form requires
 * Android string resources which are unavailable in unit tests.
 */
class LoginReducerTest {

    private lateinit var reducer: LoginReducer

    /** Builds a [LoginState] with a mocked form and the provided UI state flags. */
    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
    ): LoginState = LoginState(
        form = mockk(relaxed = true),
        isLoading = isLoading,
        error = error,
    )

    @BeforeEach
    fun setUp() {
        reducer = LoginReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default LoginState has expected initial values`() {
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

        val result = reducer.reduce(state, LoginIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenForgotPasswordModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenForgotPasswordModal sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, LoginIntent.OpenForgotPasswordModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, LoginIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores the error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, LoginIntent.Error("invalid credentials"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("invalid credentials")
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(isLoading = false, error = "old error")

        val result = reducer.reduce(state, LoginIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces the form and preserves other fields`() {
        val state = makeState(isLoading = true, error = "err")
        val newForm: FormGroup<LoginFormControls> = mockk(relaxed = true)

        val result = reducer.reduce(state, LoginIntent.UpdateForm(newForm))

        assertThat(result.form).isSameInstanceAs(newForm)
        // Other fields remain unchanged
        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("err")
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "login error")

        val result = reducer.reduce(state, LoginIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "some error")

        val result = reducer.reduce(state, LoginIntent.OnBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ShowMaxAccountAlert
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxAccountAlert sets isLoading to false and sets MaxAccountsReachedException message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, LoginIntent.ShowMaxAccountAlert)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo(MaxAccountsReachedException().message)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack returns state unchanged`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, LoginIntent.OnRequestBack)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("err")
    }

    @Test
    fun `OpenInAppBrowser returns state unchanged`() {
        val state = makeState(isLoading = false, error = null)

        val result = reducer.reduce(state, LoginIntent.OpenInAppBrowser("https://example.com"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }
}
