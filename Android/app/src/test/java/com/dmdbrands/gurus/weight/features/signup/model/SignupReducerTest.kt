package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.features.common.helper.form.ValidationType
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
        steps: kotlinx.collections.immutable.ImmutableList<SignupStep> = SignupState.COMMON_STEPS,
    ): SignupState {
        val controls = SignupFormControls.create()
        val form = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(controls)
        return SignupState(
            form = form,
            steps = steps,
            currentStep = step,
            isLoading = isLoading,
            error = error,
            goalSkipped = goalSkipped,
        )
    }

    /**
     * The ordered step list for a first-pass Weight Scale signup. This is the
     * list that actually contains GENDER / HEIGHT / GOAL, so GOAL-step reducer
     * tests must pin their state to it.
     */
    private val weightScaleSteps =
        SignupState.stepsForDevice(com.dmdbrands.gurus.weight.domain.enums.ProductType.MY_WEIGHT)

    /** The ordered step list for a first-pass Baby Scale signup (contains ADD_BABY / BABY_ADDED). */
    private val babySteps =
        SignupState.stepsForDevice(com.dmdbrands.gurus.weight.domain.enums.ProductType.BABY)

    /** A [SignupState] pinned to ADD_BABY with a [BabyState] for baby-flow reducer tests. */
    private fun babyStateAt(
        babies: List<BabyProfile> = emptyList(),
        babyForm: BabyFormControls = BabyFormControls.create(),
        editingBabyId: String? = null,
        step: SignupStep = SignupStep.ADD_BABY,
    ): SignupState {
        val controls = SignupFormControls.create()
        val form = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(controls)
        return SignupState(
            form = form,
            steps = babySteps,
            currentStep = step,
            babyState = BabyState(babies = babies, babyForm = babyForm, editingBabyId = editingBabyId),
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
    fun `Next from NAME moves to EMAIL and clears error`() {
        // COMMON_STEPS order: NAME → EMAIL → BIRTHDAY → PICK_DEVICE → PASSWORD
        val state = stateAt(step = SignupStep.NAME, error = "some error")

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.currentStep).isEqualTo(SignupStep.EMAIL)
        assertThat(result.error).isNull()
    }

    @Test
    fun `Next from GOAL resets goalSkipped to false before advancing`() {
        // GOAL lives in the Weight Scale step list, where GOAL → PASSWORD.
        val state = stateAt(
            step = SignupStep.GOAL,
            goalSkipped = true,
            steps = weightScaleSteps,
        )

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.goalSkipped).isFalse()
        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
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
    fun `Back from BIRTHDAY returns to EMAIL and clears error`() {
        // COMMON_STEPS order: NAME → EMAIL → BIRTHDAY → ... so Back from
        // BIRTHDAY lands on EMAIL.
        val state = stateAt(step = SignupStep.BIRTHDAY, error = "err")

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.EMAIL)
        assertThat(result.error).isNull()
    }

    @Test
    fun `Back from GOAL resets goalSkipped to false`() {
        val state = stateAt(step = SignupStep.GOAL, goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.goalSkipped).isFalse()
    }

    @Test
    fun `Back from EMAIL returns to NAME and leaves goalSkipped untouched`() {
        // goalSkipped is only reset when stepping back off GOAL; stepping back
        // off any other step preserves it. EMAIL is index 1 in COMMON_STEPS.
        val state = stateAt(step = SignupStep.EMAIL, goalSkipped = true)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.NAME)
        assertThat(result.goalSkipped).isTrue()
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
        // First pass (no registered devices): Skip on GOAL advances to the next
        // step in the Weight Scale list, which is PASSWORD.
        val state = stateAt(
            step = SignupStep.GOAL,
            goalSkipped = false,
            steps = weightScaleSteps,
        )

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
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

    // -------------------------------------------------------------------------
    // Baby flow — Skip / Next / Edit / AddAnother
    // -------------------------------------------------------------------------

    @Test
    fun `Skip on ADD_BABY with existing babies returns to BABY_ADDED preserving babies`() {
        val baby = BabyProfile(name = "Sally")
        val state = babyStateAt(babies = listOf(baby))

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.BABY_ADDED)
        assertThat(result.babyState?.babies).containsExactly(baby)
        assertThat(result.babyState?.editingBabyId).isNull()
    }

    @Test
    fun `Skip on ADD_BABY with no babies advances to PASSWORD and resets baby state`() {
        val state = babyStateAt(babies = emptyList())

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
        assertThat(result.babyState?.babies).isEmpty()
    }

    @Test
    fun `Next on ADD_BABY captures birthWeightOz and weightUnit into the baby`() {
        val form = BabyFormControls.create()
        form.name.onValueChange("Tammy")
        form.birthWeight.onValueChange("7")
        form.birthWeightOz.onValueChange("4")
        form.weightUnit.onValueChange(BabyWeightUnit.LBS_OZ)
        val state = babyStateAt(babyForm = form)

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.currentStep).isEqualTo(SignupStep.BABY_ADDED)
        val saved = result.babyState?.babies?.single()
        assertThat(saved?.birthWeightOz).isEqualTo("4")
        assertThat(saved?.weightUnit).isEqualTo(BabyWeightUnit.LBS_OZ)
    }

    @Test
    fun `EditBaby seeds form excluding the edited baby's own name`() {
        val sally = BabyProfile(id = "s1", name = "Sally")
        val tammy = BabyProfile(id = "s2", name = "Tammy")
        val state = babyStateAt(babies = listOf(sally, tammy))

        val result = reducer.reduce(state, SignupIntent.EditBaby(sally))

        assertThat(result.currentStep).isEqualTo(SignupStep.ADD_BABY)
        assertThat(result.babyState?.editingBabyId).isEqualTo("s1")
        val nameControl = requireNotNull(result.babyState?.babyForm?.name)
        // Its own name is allowed (excluded from the duplicate set)…
        nameControl.onValueChange("Sally")
        assertThat(nameControl.error?.type).isNotEqualTo(ValidationType.DUPLICATE)
        // …but a sibling's name is flagged.
        nameControl.onValueChange("Tammy")
        assertThat(nameControl.error?.type).isEqualTo(ValidationType.DUPLICATE)
    }

    @Test
    fun `AddAnotherBaby seeds form with existing names for duplicate detection`() {
        val sally = BabyProfile(id = "s1", name = "Sally")
        val state = babyStateAt(babies = listOf(sally), step = SignupStep.BABY_ADDED)

        val result = reducer.reduce(state, SignupIntent.AddAnotherBaby)

        assertThat(result.currentStep).isEqualTo(SignupStep.ADD_BABY)
        assertThat(result.babyState?.editingBabyId).isNull()
        val nameControl = requireNotNull(result.babyState?.babyForm?.name)
        nameControl.onValueChange("Sally")
        assertThat(nameControl.error?.type).isEqualTo(ValidationType.DUPLICATE)
    }
}
