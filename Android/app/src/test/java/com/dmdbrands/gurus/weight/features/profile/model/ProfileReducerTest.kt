package com.dmdbrands.gurus.weight.features.profile.model

import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.AppValidatorConfig
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ProfileReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * [ProfileFormControls.create] is used to build a realistic form for state construction.
 */
class ProfileReducerTest {

    private lateinit var reducer: ProfileReducer

    private val defaultBirthday = DateTimeValue.Date(
        DateTimeValue.getEpochMillisFromDateString(AppValidatorConfig.DateOfBirth.DEFAULT_VALUE)
    )

    /** Builds a [ProfileState] with the real form and provided UI state flags. */
    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
    ): ProfileState {
        val form = FormGroup(ProfileFormControls.create())
        return ProfileState(form = form, isLoading = isLoading, error = error)
    }

    @BeforeEach
    fun setUp() {
        reducer = ProfileReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default ProfileState has expected initial values`() {
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

        val result = reducer.reduce(state, ProfileIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // LoadProfile
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile sets isLoading to true and clears error`() {
        val state = makeState(isLoading = false, error = "stale error")

        val result = reducer.reduce(state, ProfileIntent.LoadProfile)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ProfileLoaded
    // -------------------------------------------------------------------------

    @Test
    fun `ProfileLoaded sets isLoading to false, clears error and populates form`() {
        val state = makeState(isLoading = true, error = "err")
        val intent = ProfileIntent.ProfileLoaded(
            firstName = "Jane",
            lastName = "Doe",
            email = "jane@example.com",
            zipcode = "90210",
            birthday = defaultBirthday,
        )

        val result = reducer.reduce(state, intent)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        // Verify the form was updated with the provided values
        assertThat(result.form.controls.firstName.value).isEqualTo("Jane")
        assertThat(result.form.controls.lastName.value).isEqualTo("Doe")
        assertThat(result.form.controls.email.value).isEqualTo("jane@example.com")
        assertThat(result.form.controls.zipcode.value).isEqualTo("90210")
    }

    @Test
    fun `ProfileLoaded form is a new instance not the original form`() {
        val state = makeState()
        val intent = ProfileIntent.ProfileLoaded(
            firstName = "John",
            lastName = "Smith",
            email = "john@example.com",
            zipcode = "10001",
            birthday = defaultBirthday,
        )

        val result = reducer.reduce(state, intent)

        assertThat(result.form).isNotSameInstanceAs(state.form)
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, ProfileIntent.Error("profile update failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("profile update failed")
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(isLoading = false, error = "old error")

        val result = reducer.reduce(state, ProfileIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces the form and preserves other fields`() {
        val state = makeState(isLoading = true, error = "err")
        val newForm: FormGroup<ProfileFormControls> = mockk(relaxed = true)

        val result = reducer.reduce(state, ProfileIntent.UpdateForm(newForm))

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
        val state = makeState(isLoading = true, error = "update failed")

        val result = reducer.reduce(state, ProfileIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnRequestBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack returns state unchanged`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, ProfileIntent.OnRequestBack)

        // OnRequestBack performs no state change
        assertThat(result).isSameInstanceAs(state)
    }
}
