package com.dmdbrands.gurus.weight.features.signup.model

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SignupReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * [SignupFormControls.create] constructs a fully initialised form, which is used by
 * the state factory helpers below.
 */
class SignupReducerTest {

    private lateinit var reducer: SignupReducer

    /** Returns a [SignupState] pinned to [step] with sensible defaults. */
    private fun stateAt(
        step: SignupStep = SignupStep.NAME,
        isLoading: Boolean = false,
        error: String? = null,
        goalSkipped: Boolean = false,
    ): SignupState {
        val controls = SignupFormControls.create()
        val form = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(controls)
        return SignupState(
            form = form,
            currentStep = step,
            isLoading = isLoading,
            error = error,
            goalSkipped = goalSkipped,
        )
    }

    @BeforeEach
    fun setUp() {
        reducer = SignupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default SignupState starts at NAME step with expected values`() {
        val state = stateAt()

        assertThat(state.currentStep).isEqualTo(SignupStep.NAME)
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.goalSkipped).isFalse()
        assertThat(state.isFirstStep).isTrue()
        assertThat(state.isLastStep).isFalse()
        assertThat(state.showSkipButton).isFalse()
    }

    // -------------------------------------------------------------------------
    // Next — advance step
    // -------------------------------------------------------------------------

    @Test
    fun `Next from NAME moves to BIRTHDAY and clears error`() {
        val state = stateAt(step = SignupStep.NAME, error = "some error")

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.currentStep).isEqualTo(SignupStep.BIRTHDAY)
        assertThat(result.error).isNull()
    }

    @Test
    fun `Next from GOAL resets goalSkipped to false before advancing`() {
        val state = stateAt(step = SignupStep.GOAL, goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.goalSkipped).isFalse()
        assertThat(result.currentStep).isEqualTo(SignupStep.EMAIL)
    }

    @Test
    fun `Next on the last step does not advance and sets isLoading to false`() {
        val state = stateAt(step = SignupStep.PASSWORD)

        val result = reducer.reduce(state, SignupIntent.Next)

        // Still on last step
        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Back — go to previous step
    // -------------------------------------------------------------------------

    @Test
    fun `Back from BIRTHDAY returns to NAME and clears error`() {
        val state = stateAt(step = SignupStep.BIRTHDAY, error = "err")

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.NAME)
        assertThat(result.error).isNull()
    }

    @Test
    fun `Back from GOAL resets goalSkipped to false`() {
        val state = stateAt(step = SignupStep.GOAL, goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.goalSkipped).isFalse()
    }

    @Test
    fun `Back from EMAIL resets goalSkipped to false`() {
        val state = stateAt(step = SignupStep.EMAIL, goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.goalSkipped).isFalse()
    }

    @Test
    fun `Back from first step stays on first step`() {
        val state = stateAt(step = SignupStep.NAME)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.NAME)
    }

    // -------------------------------------------------------------------------
    // Skip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip from GOAL advances step and sets goalSkipped to true`() {
        val state = stateAt(step = SignupStep.GOAL, goalSkipped = false)

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.EMAIL)
        assertThat(result.goalSkipped).isTrue()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Skip from non-GOAL step returns state unchanged`() {
        val state = stateAt(step = SignupStep.NAME)

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.NAME)
        assertThat(result.goalSkipped).isFalse()
    }

    // -------------------------------------------------------------------------
    // OnRequestBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack sets isLoading to false and clears error`() {
        val state = stateAt(isLoading = true, error = "err")

        val result = reducer.reduce(state, SignupIntent.OnRequestBack)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal sets isLoading to false and clears error`() {
        val state = stateAt(isLoading = true, error = "err")

        val result = reducer.reduce(state, SignupIntent.OpenHelpModal)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error sets isLoading to false and stores the error message`() {
        val state = stateAt(isLoading = true)

        val result = reducer.reduce(state, SignupIntent.Error("signup failed"))

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isEqualTo("signup failed")
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success sets isLoading to false and clears error`() {
        val state = stateAt(isLoading = true, error = "signup failed")

        val result = reducer.reduce(state, SignupIntent.Success)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // UpdateGoalSkipped
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateGoalSkipped true sets goalSkipped to true`() {
        val state = stateAt(goalSkipped = false)

        val result = reducer.reduce(state, SignupIntent.UpdateGoalSkipped(true))

        assertThat(result.goalSkipped).isTrue()
    }

    @Test
    fun `UpdateGoalSkipped false sets goalSkipped to false`() {
        val state = stateAt(goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.UpdateGoalSkipped(false))

        assertThat(result.goalSkipped).isFalse()
    }

    // -------------------------------------------------------------------------
    // ToggleMetric
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleMetric clears error and returns updated state`() {
        val state = stateAt(error = "some error")

        val result = reducer.reduce(state, SignupIntent.ToggleMetric(useMetric = true))

        assertThat(result.error).isNull()
        // Step and loading flag are not changed by ToggleMetric
        assertThat(result.currentStep).isEqualTo(state.currentStep)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `OpenURL returns state unchanged`() {
        val state = stateAt(step = SignupStep.PASSWORD, error = "err")

        val result = reducer.reduce(state, SignupIntent.OpenURL("https://example.com"))

        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
        assertThat(result.error).isEqualTo("err")
    }
}
