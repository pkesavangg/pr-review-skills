package com.dmdbrands.gurus.weight.features.goal.model

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GoalReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * [GoalFormControls.create] is used to build a realistic form for state construction.
 */
class GoalReducerTest {

    private lateinit var reducer: GoalReducer

    /** Builds a [GoalState] with a real form and the provided UI state flags. */
    private fun makeState(
        isLoading: Boolean = false,
        error: String? = null,
        account: com.dmdbrands.gurus.weight.domain.model.storage.Account.Account? = null,
        latestWeight: Double? = 0.0,
        goalType: GoalType = GoalType.LOSE_GAIN,
    ): GoalState {
        val controls = GoalFormControls.create(goalType = goalType)
        val form = FormGroup(controls)
        return GoalState(
            form = form,
            isLoading = isLoading,
            error = error,
            account = account,
            latestWeight = latestWeight,
        )
    }

    @BeforeEach
    fun setUp() {
        reducer = GoalReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default GoalState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.account).isNull()
        assertThat(state.latestWeight).isEqualTo(0.0)
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading to true and clears error`() {
        val state = makeState(isLoading = false, error = "previous error")

        val result = reducer.reduce(state, GoalIntent.Submit)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ChangeGoalType
    // -------------------------------------------------------------------------

    @Test
    fun `ChangeGoalType to MAINTAIN updates goalType control value`() {
        val state = makeState(goalType = GoalType.LOSE_GAIN)

        val result = reducer.reduce(state, GoalIntent.ChangeGoalType(GoalType.MAINTAIN))

        assertThat(result.form.controls.goalType.value).isEqualTo(GoalType.MAINTAIN.value)
    }

    @Test
    fun `ChangeGoalType to LOSE_GAIN updates goalType control value`() {
        val state = makeState(goalType = GoalType.MAINTAIN)

        val result = reducer.reduce(state, GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        assertThat(result.form.controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    @Test
    fun `ChangeGoalType preserves isLoading and error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.ChangeGoalType(GoalType.MAINTAIN))

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("err")
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores error message`() {
        val state = makeState(isLoading = true, error = null)

        val result = reducer.reduce(state, GoalIntent.Error("goal update failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("goal update failed")
    }

    @Test
    fun `Error replaces an existing error message`() {
        val state = makeState(isLoading = false, error = "old error")

        val result = reducer.reduce(state, GoalIntent.Error("new error"))

        assertThat(result.error).isEqualTo("new error")
    }

    // -------------------------------------------------------------------------
    // UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces the form and preserves other fields`() {
        val state = makeState(isLoading = true, error = "err")
        val newForm: FormGroup<GoalFormControls> = mockk(relaxed = true)

        val result = reducer.reduce(state, GoalIntent.UpdateForm(newForm))

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

        val result = reducer.reduce(state, GoalIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.OnBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // HandleGoalMet
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalMet setNewGoal true sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.HandleGoalMet(setNewGoal = true))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `HandleGoalMet setNewGoal false sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.HandleGoalMet(setNewGoal = false))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // HandleGoalLeave
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalLeave updateGoal true sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.HandleGoalLeave(updateGoal = true))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `HandleGoalLeave updateGoal false sets isLoading to false and clears error`() {
        val state = makeState(isLoading = true, error = "err")

        val result = reducer.reduce(state, GoalIntent.HandleGoalLeave(updateGoal = false))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateAccount with non-null account stores the account`() {
        val state = makeState(account = null)
        val fakeAccount: com.dmdbrands.gurus.weight.domain.model.storage.Account.Account =
            mockk(relaxed = true)

        val result = reducer.reduce(state, GoalIntent.UpdateAccount(fakeAccount))

        assertThat(result.account).isSameInstanceAs(fakeAccount)
    }

    @Test
    fun `UpdateAccount with null clears account`() {
        val fakeAccount: com.dmdbrands.gurus.weight.domain.model.storage.Account.Account =
            mockk(relaxed = true)
        val state = makeState(account = null).copy(account = fakeAccount)

        val result = reducer.reduce(state, GoalIntent.UpdateAccount(null))

        assertThat(result.account).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateLatestWeight stores the provided weight`() {
        val state = makeState(latestWeight = 0.0)

        val result = reducer.reduce(state, GoalIntent.UpdateLatestWeight(185.5))

        assertThat(result.latestWeight).isEqualTo(185.5)
    }

    @Test
    fun `UpdateLatestWeight with null clears latestWeight`() {
        val state = makeState(latestWeight = 180.0)

        val result = reducer.reduce(state, GoalIntent.UpdateLatestWeight(null))

        assertThat(result.latestWeight).isNull()
    }

    // -------------------------------------------------------------------------
    // ToggleMetPreviousGoal
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleMetPreviousGoal returns a copy without changing tracked fields`() {
        val state = makeState(isLoading = false, error = null, latestWeight = 175.0)

        val result = reducer.reduce(state, GoalIntent.ToggleMetPreviousGoal)

        // ToggleMetPreviousGoal returns state.copy() — all fields preserved
        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
        assertThat(result.latestWeight).isEqualTo(175.0)
    }
}
