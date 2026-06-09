package com.dmdbrands.gurus.weight.features.profile.model

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileReducerTest {

    private lateinit var reducer: ProfileReducer
    private lateinit var initialState: ProfileState

    @BeforeEach
    fun setUp() {
        reducer = ProfileReducer()
        initialState = ProfileState(
            form = FormGroup(ProfileFormControls.create()),
        )
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading true and clears error`() {
        val state = initialState.copy(error = "previous error")

        val result = reducer.reduce(state, ProfileIntent.Submit)!!

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // LoadProfile
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile sets isLoading true and clears error`() {
        val result = reducer.reduce(initialState, ProfileIntent.LoadProfile)!!

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ProfileLoaded
    // -------------------------------------------------------------------------

    @Test
    fun `ProfileLoaded populates form with all fields including gender and height`() {
        val intent = ProfileIntent.ProfileLoaded(
            firstName = "John",
            lastName = "Doe",
            email = "john@test.com",
            zipcode = "12345",
            birthday = DateTimeValue.Date(0L),
            gender = "male",
            height = 1700,
            weightUnit = WeightUnit.LB,
        )

        val result = reducer.reduce(initialState, intent)!!

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.form.controls.firstName.value).isEqualTo("John")
        assertThat(result.form.controls.lastName.value).isEqualTo("Doe")
        assertThat(result.form.controls.email.value).isEqualTo("john@test.com")
        assertThat(result.form.controls.zipcode.value).isEqualTo("12345")
        assertThat(result.form.controls.gender.value).isEqualTo("male")
        assertThat(result.form.controls.height.value).isEqualTo(1700)
        assertThat(result.weightUnit).isEqualTo(WeightUnit.LB)
    }

    @Test
    fun `ProfileLoaded with default gender and height uses empty and zero`() {
        val intent = ProfileIntent.ProfileLoaded(
            firstName = "Jane",
            lastName = "Doe",
            email = "jane@test.com",
            zipcode = "54321",
            birthday = DateTimeValue.Date(0L),
        )

        val result = reducer.reduce(initialState, intent)!!

        assertThat(result.form.controls.gender.value).isEmpty()
        assertThat(result.form.controls.height.value).isEqualTo(0)
        assertThat(result.weightUnit).isNull()
    }

    @Test
    fun `ProfileLoaded sets weightUnit from intent`() {
        val intent = ProfileIntent.ProfileLoaded(
            firstName = "A",
            lastName = "B",
            email = "a@b.com",
            zipcode = "00000",
            birthday = DateTimeValue.Date(0L),
            weightUnit = WeightUnit.KG,
        )

        val result = reducer.reduce(initialState, intent)!!

        assertThat(result.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets error message and clears isLoading`() {
        val state = initialState.copy(isLoading = true)

        val result = reducer.reduce(state, ProfileIntent.Error("Network failure"))!!

        assertThat(result.error).isEqualTo("Network failure")
        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces form in state`() {
        val newForm = FormGroup(
            ProfileFormControls.create(firstName = "Updated"),
        )

        val result = reducer.reduce(initialState, ProfileIntent.UpdateForm(newForm))!!

        assertThat(result.form).isEqualTo(newForm)
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success clears isLoading and error`() {
        val state = initialState.copy(isLoading = true, error = "old error")

        val result = reducer.reduce(state, ProfileIntent.Success)!!

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — no state change
    // -------------------------------------------------------------------------

    @Test
    fun `ShowBiologicalSexModal returns null — handled as side effect`() {
        val result = reducer.reduce(initialState, ProfileIntent.ShowBiologicalSexModal)
        assertThat(result).isNull()
    }

    @Test
    fun `ShowHeightModal returns null — handled as side effect`() {
        val result = reducer.reduce(initialState, ProfileIntent.ShowHeightModal)
        assertThat(result).isNull()
    }

    @Test
    fun `OnRequestBack returns null — handled as side effect`() {
        val result = reducer.reduce(initialState, ProfileIntent.OnRequestBack)
        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // ProfileFormControls.create
    // -------------------------------------------------------------------------

    @Test
    fun `ProfileFormControls create includes gender and height controls`() {
        val controls = ProfileFormControls.create(
            firstName = "A",
            lastName = "B",
            email = "a@b.com",
            zipcode = "12345",
            gender = "female",
            height = 1650,
        )

        assertThat(controls.gender.value).isEqualTo("female")
        assertThat(controls.height.value).isEqualTo(1650)
    }

    @Test
    fun `ProfileFormControls create defaults gender to empty and height to zero`() {
        val controls = ProfileFormControls.create()

        assertThat(controls.gender.value).isEmpty()
        assertThat(controls.height.value).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default ProfileState has expected initial values`() {
        assertThat(initialState.isLoading).isFalse()
        assertThat(initialState.error).isNull()
        assertThat(initialState.weightUnit).isNull()
        assertThat(initialState.form).isNotNull()
    }
}
