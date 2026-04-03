package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.BabyProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BabyScaleSetupReducerTest {

    private lateinit var reducer: BabyScaleSetupReducer
    private val initialState = BabyScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = BabyScaleSetupReducer()
    }

    private fun aBabyProfile(name: String = "Alice") = BabyProfile(name = name)

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default nickname`() {
        assertThat(initialState.nickname).isEqualTo("Smart Baby Scale")
    }

    @Test
    fun `initial state has empty babyProfiles`() {
        assertThat(initialState.babyProfiles).isEmpty()
    }

    @Test
    fun `initial state has empty editingProfile`() {
        assertThat(initialState.editingProfile.name).isEmpty()
        assertThat(initialState.editingProfile.birthday).isNull()
        assertThat(initialState.editingProfile.biologicalSex).isNull()
    }

    @Test
    fun `initial state has editingProfileIndex of -1`() {
        assertThat(initialState.editingProfileIndex).isEqualTo(-1)
    }

    @Test
    fun `initial step is SCALE_INFO`() {
        assertThat(initialState.step).isEqualTo(BabyScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // SetNickname
    // -------------------------------------------------------------------------

    @Test
    fun `SetNickname updates nickname`() {
        val result = reducer.reduce(initialState, BabyScaleSetupIntent.SetNickname("My Baby Scale"))
        assertThat(result?.nickname).isEqualTo("My Baby Scale")
    }

    @Test
    fun `SetNickname does not affect babyProfiles`() {
        val stateWithBaby = initialState.copy(babyProfiles = listOf(aBabyProfile()).toImmutableList())
        val result = reducer.reduce(stateWithBaby, BabyScaleSetupIntent.SetNickname("New Name"))
        assertThat(result?.babyProfiles).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // UpdateEditingProfile
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateEditingProfile sets editingProfile`() {
        val profile = aBabyProfile("Bob")
        val result = reducer.reduce(initialState, BabyScaleSetupIntent.UpdateEditingProfile(profile))
        assertThat(result?.editingProfile).isEqualTo(profile)
    }

    @Test
    fun `UpdateEditingProfile does not change babyProfiles list`() {
        val existingBabies = listOf(aBabyProfile("Alice")).toImmutableList()
        val state = initialState.copy(babyProfiles = existingBabies)
        val result = reducer.reduce(state, BabyScaleSetupIntent.UpdateEditingProfile(aBabyProfile("Bob")))
        assertThat(result?.babyProfiles).isEqualTo(existingBabies)
    }

    // -------------------------------------------------------------------------
    // SaveBabyProfile — add new
    // -------------------------------------------------------------------------

    @Test
    fun `SaveBabyProfile adds new profile when editingProfileIndex is -1`() {
        val state = initialState.copy(editingProfile = aBabyProfile("Alice"))
        val result = reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile)
        assertThat(result?.babyProfiles).hasSize(1)
        assertThat(result?.babyProfiles?.first()?.name).isEqualTo("Alice")
    }

    @Test
    fun `SaveBabyProfile resets editingProfile after save`() {
        val state = initialState.copy(editingProfile = aBabyProfile("Alice"))
        val result = reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile)
        assertThat(result?.editingProfile?.name).isEmpty()
        assertThat(result?.editingProfile?.birthday).isNull()
    }

    @Test
    fun `SaveBabyProfile resets editingProfileIndex to -1 after save`() {
        val state = initialState.copy(editingProfile = aBabyProfile("Alice"))
        val result = reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile)
        assertThat(result?.editingProfileIndex).isEqualTo(-1)
    }

    @Test
    fun `SaveBabyProfile accumulates multiple profiles`() {
        var state = initialState.copy(editingProfile = aBabyProfile("Alice"))
        state = requireNotNull(reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile))

        state = state.copy(editingProfile = aBabyProfile("Bob"))
        val result = reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile)

        assertThat(result?.babyProfiles).hasSize(2)
        assertThat(result?.babyProfiles?.map { it.name }).containsExactly("Alice", "Bob").inOrder()
    }

    // -------------------------------------------------------------------------
    // SaveBabyProfile — edit existing
    // -------------------------------------------------------------------------

    @Test
    fun `SaveBabyProfile replaces profile at editingProfileIndex`() {
        val profiles = listOf(aBabyProfile("Alice"), aBabyProfile("Bob")).toImmutableList()
        val state = initialState.copy(
            babyProfiles = profiles,
            editingProfile = aBabyProfile("Charlie"),
            editingProfileIndex = 0,
        )
        val result = reducer.reduce(state, BabyScaleSetupIntent.SaveBabyProfile)
        assertThat(result?.babyProfiles?.get(0)?.name).isEqualTo("Charlie")
        assertThat(result?.babyProfiles?.get(1)?.name).isEqualTo("Bob")
    }

    // -------------------------------------------------------------------------
    // EditBabyProfile
    // -------------------------------------------------------------------------

    @Test
    fun `EditBabyProfile sets editingProfile and editingProfileIndex`() {
        val profiles = listOf(aBabyProfile("Alice"), aBabyProfile("Bob")).toImmutableList()
        val state = initialState.copy(babyProfiles = profiles)
        val result = reducer.reduce(state, BabyScaleSetupIntent.EditBabyProfile(1))
        assertThat(result?.editingProfile?.name).isEqualTo("Bob")
        assertThat(result?.editingProfileIndex).isEqualTo(1)
    }

    @Test
    fun `EditBabyProfile with out-of-bounds index returns unchanged state`() {
        val state = initialState.copy(babyProfiles = listOf(aBabyProfile()).toImmutableList())
        val result = reducer.reduce(state, BabyScaleSetupIntent.EditBabyProfile(99))
        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // DeleteBabyProfile
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteBabyProfile removes profile at given index`() {
        val profiles = listOf(aBabyProfile("Alice"), aBabyProfile("Bob")).toImmutableList()
        val state = initialState.copy(babyProfiles = profiles)
        val result = reducer.reduce(state, BabyScaleSetupIntent.DeleteBabyProfile(0))
        assertThat(result?.babyProfiles).hasSize(1)
        assertThat(result?.babyProfiles?.first()?.name).isEqualTo("Bob")
    }

    @Test
    fun `DeleteBabyProfile on last profile results in empty list`() {
        val state = initialState.copy(babyProfiles = listOf(aBabyProfile()).toImmutableList())
        val result = reducer.reduce(state, BabyScaleSetupIntent.DeleteBabyProfile(0))
        assertThat(result?.babyProfiles).isEmpty()
    }

    // -------------------------------------------------------------------------
    // AddAnotherBaby
    // -------------------------------------------------------------------------

    @Test
    fun `AddAnotherBaby resets editingProfile to empty`() {
        val state = initialState.copy(editingProfile = aBabyProfile("Alice"), editingProfileIndex = 0)
        val result = reducer.reduce(state, BabyScaleSetupIntent.AddAnotherBaby)
        assertThat(result?.editingProfile?.name).isEmpty()
        assertThat(result?.editingProfile?.birthday).isNull()
    }

    @Test
    fun `AddAnotherBaby resets editingProfileIndex to -1`() {
        val state = initialState.copy(editingProfileIndex = 2)
        val result = reducer.reduce(state, BabyScaleSetupIntent.AddAnotherBaby)
        assertThat(result?.editingProfileIndex).isEqualTo(-1)
    }

    @Test
    fun `AddAnotherBaby does not change babyProfiles`() {
        val profiles = listOf(aBabyProfile("Alice")).toImmutableList()
        val state = initialState.copy(babyProfiles = profiles)
        val result = reducer.reduce(state, BabyScaleSetupIntent.AddAnotherBaby)
        assertThat(result?.babyProfiles).isEqualTo(profiles)
    }

    // -------------------------------------------------------------------------
    // ResetEditingProfile
    // -------------------------------------------------------------------------

    @Test
    fun `ResetEditingProfile clears editingProfile and index`() {
        val state = initialState.copy(
            editingProfile = aBabyProfile("Alice"),
            editingProfileIndex = 1,
        )
        val result = reducer.reduce(state, BabyScaleSetupIntent.ResetEditingProfile)
        assertThat(result?.editingProfile?.name).isEmpty()
        assertThat(result?.editingProfile?.birthday).isNull()
        assertThat(result?.editingProfileIndex).isEqualTo(-1)
    }

    @Test
    fun `ResetEditingProfile does not change nickname or babyProfiles`() {
        val profiles = listOf(aBabyProfile()).toImmutableList()
        val state = initialState.copy(nickname = "My Scale", babyProfiles = profiles)
        val result = reducer.reduce(state, BabyScaleSetupIntent.ResetEditingProfile)
        assertThat(result?.nickname).isEqualTo("My Scale")
        assertThat(result?.babyProfiles).isEqualTo(profiles)
    }

    // -------------------------------------------------------------------------
    // Step navigation (inherited from ScaleSetupReducer)
    // Note: Next/Back are handled by the ViewModel (onNext/onBack), not the reducer.
    // The reducer handles SetNewStep directly.
    // -------------------------------------------------------------------------

    @Test
    fun `SetNewStep changes to BABY_PROFILE_FORM`() {
        val result = reducer.reduce(initialState, ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.BABY_PROFILE_FORM))
        assertThat(result?.step).isEqualTo(BabyScaleSetupStep.BABY_PROFILE_FORM)
    }

    @Test
    fun `SetNewStep changes to BABY_LIST`() {
        val result = reducer.reduce(initialState, ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.BABY_LIST))
        assertThat(result?.step).isEqualTo(BabyScaleSetupStep.BABY_LIST)
    }

    @Test
    fun `SetNewStep preserves nickname and babyProfiles`() {
        val state = initialState.copy(
            nickname = "My Scale",
            babyProfiles = listOf(aBabyProfile()).toImmutableList(),
        )
        val result = reducer.reduce(state, ScaleSetupIntent.SetNewStep(BabyScaleSetupStep.SETUP_FINISHED))
        assertThat(result?.nickname).isEqualTo("My Scale")
        assertThat(result?.babyProfiles).hasSize(1)
    }
}
