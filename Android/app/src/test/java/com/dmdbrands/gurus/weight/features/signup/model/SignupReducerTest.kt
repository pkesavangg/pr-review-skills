package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.enums.ProductType
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

    // -------------------------------------------------------------------------
    // Next — isFinalDataStep short-circuit & ADD_BABY with null babyState
    // -------------------------------------------------------------------------

    @Test
    fun `Next on the final data step only clears error and does not advance`() {
        // On PASSWORD (next is DEVICE_READY) the reducer short-circuits — advancement
        // is gated on SignupViewModel dispatching RegisterDevice after signup succeeds.
        val state = stateAt(step = SignupStep.PASSWORD, error = "boom", steps = weightScaleSteps)

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.currentStep).isEqualTo(SignupStep.PASSWORD)
        assertThat(result.registeredDevices).isEmpty()
        assertThat(result.error).isNull()
    }

    @Test
    fun `Next on ADD_BABY with null babyState returns state unchanged`() {
        // Defensive guard: ADD_BABY without a BabyState can't build a baby.
        val state = stateAt(step = SignupStep.ADD_BABY, steps = babySteps)

        val result = reducer.reduce(state, SignupIntent.Next)

        assertThat(result.currentStep).isEqualTo(SignupStep.ADD_BABY)
        assertThat(result.babyState).isNull()
    }

    // -------------------------------------------------------------------------
    // Back — baby edit discard & empty BABY_ADDED skip
    // -------------------------------------------------------------------------

    @Test
    fun `Back while editing a baby discards the edit and returns to BABY_ADDED`() {
        val sally = BabyProfile(id = "s1", name = "Sally")
        val state = babyStateAt(babies = listOf(sally), editingBabyId = "s1")

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.BABY_ADDED)
        assertThat(result.babyState?.editingBabyId).isNull()
        // The already-added baby is preserved.
        assertThat(result.babyState?.babies).containsExactly(sally)
    }

    @Test
    fun `Back from PASSWORD skips an empty BABY_ADDED slide and lands on ADD_BABY`() {
        // Baby first-pass step list: ... ADD_BABY, BABY_ADDED, PASSWORD, terminal.
        // With no babies added, Back from PASSWORD must skip the meaningless
        // "baby added" slide and land on ADD_BABY.
        val state = babyStateAt(babies = emptyList(), step = SignupStep.PASSWORD)

        val result = reducer.reduce(state, SignupIntent.Back)

        assertThat(result.currentStep).isEqualTo(SignupStep.ADD_BABY)
    }

    // -------------------------------------------------------------------------
    // Skip — editing baby, and loop-pass (registered devices present)
    // -------------------------------------------------------------------------

    @Test
    fun `Skip while editing a baby discards the edit and returns to BABY_ADDED`() {
        val sally = BabyProfile(id = "s1", name = "Sally")
        val state = babyStateAt(babies = listOf(sally), editingBabyId = "s1")

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.currentStep).isEqualTo(SignupStep.BABY_ADDED)
        assertThat(result.babyState?.editingBabyId).isNull()
        assertThat(result.babyState?.babies).containsExactly(sally)
    }

    @Test
    fun `Skip on GOAL loop pass registers the device and jumps to the terminal`() {
        val state = stateAt(step = SignupStep.GOAL, steps = weightScaleSteps)
            .copy(registeredDevices = setOf(ProductType.BLOOD_PRESSURE))
        state.form.controls.device.onValueChange(ProductType.MY_WEIGHT.id)

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.goalSkipped).isTrue()
        assertThat(result.registeredDevices)
            .containsExactly(ProductType.BLOOD_PRESSURE, ProductType.MY_WEIGHT)
        assertThat(result.currentStep).isEqualTo(weightScaleSteps.last())
    }

    @Test
    fun `Skip on GOAL loop pass with no selected device only sets goalSkipped`() {
        val state = stateAt(step = SignupStep.GOAL, steps = weightScaleSteps)
            .copy(registeredDevices = setOf(ProductType.BLOOD_PRESSURE))
        // device control left blank → fromId returns null.

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.goalSkipped).isTrue()
        assertThat(result.currentStep).isEqualTo(SignupStep.GOAL)
        assertThat(result.registeredDevices).containsExactly(ProductType.BLOOD_PRESSURE)
    }

    @Test
    fun `Skip on ADD_BABY loop pass with no babies registers the device and jumps to terminal`() {
        val state = babyStateAt(babies = emptyList())
            .copy(registeredDevices = setOf(ProductType.MY_WEIGHT))
        state.form.controls.device.onValueChange(ProductType.BABY.id)

        val result = reducer.reduce(state, SignupIntent.Skip)

        assertThat(result.registeredDevices)
            .containsExactly(ProductType.MY_WEIGHT, ProductType.BABY)
        assertThat(result.currentStep).isEqualTo(babySteps.last())
        assertThat(result.babyState?.babies).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SelectDevice
    // -------------------------------------------------------------------------

    @Test
    fun `SelectDevice weight scale expands the step list with GENDER HEIGHT GOAL`() {
        val state = stateAt(step = SignupStep.PICK_DEVICE)

        val result = reducer.reduce(state, SignupIntent.SelectDevice(ProductType.MY_WEIGHT.id))

        assertThat(result.steps).containsAtLeast(
            SignupStep.GENDER, SignupStep.HEIGHT, SignupStep.GOAL,
        )
        assertThat(result.babyState).isNull()
        assertThat(result.form.controls.device.value).isEqualTo(ProductType.MY_WEIGHT.id)
    }

    @Test
    fun `SelectDevice baby scale creates a BabyState and baby step path`() {
        val state = stateAt(step = SignupStep.PICK_DEVICE)

        val result = reducer.reduce(state, SignupIntent.SelectDevice(ProductType.BABY.id))

        assertThat(result.babyState).isNotNull()
        assertThat(result.steps).containsAtLeast(SignupStep.ADD_BABY, SignupStep.BABY_ADDED)
    }

    @Test
    fun `SelectDevice on first pass resets abandoned path data when device changes`() {
        val state = stateAt(step = SignupStep.PICK_DEVICE, steps = weightScaleSteps)
        state.form.controls.device.onValueChange(ProductType.MY_WEIGHT.id)
        state.form.controls.sex.onValueChange("male")

        // Switching to a different device on the first pass clears the abandoned
        // weight-path fields.
        val result = reducer.reduce(state, SignupIntent.SelectDevice(ProductType.BLOOD_PRESSURE.id))

        assertThat(result.form.controls.sex.value).isEmpty()
        assertThat(result.form.controls.device.value).isEqualTo(ProductType.BLOOD_PRESSURE.id)
    }

    // -------------------------------------------------------------------------
    // DeleteBaby (reducer-level filter)
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteBaby removes the matching baby from the list`() {
        val sally = BabyProfile(id = "s1", name = "Sally")
        val tammy = BabyProfile(id = "s2", name = "Tammy")
        val state = babyStateAt(babies = listOf(sally, tammy), step = SignupStep.BABY_ADDED)

        val result = reducer.reduce(state, SignupIntent.DeleteBaby("s1"))

        assertThat(result.babyState?.babies).containsExactly(tammy)
    }

    @Test
    fun `DeleteBaby with null babyState returns state unchanged`() {
        val state = stateAt(step = SignupStep.BABY_ADDED)

        val result = reducer.reduce(state, SignupIntent.DeleteBaby("s1"))

        assertThat(result.babyState).isNull()
    }

    @Test
    fun `EditBaby with null babyState returns state unchanged`() {
        val state = stateAt(step = SignupStep.BABY_ADDED)

        val result = reducer.reduce(state, SignupIntent.EditBaby(BabyProfile(id = "x", name = "X")))

        assertThat(result.currentStep).isEqualTo(SignupStep.BABY_ADDED)
        assertThat(result.babyState).isNull()
    }

    @Test
    fun `AddAnotherBaby with null babyState returns state unchanged`() {
        val state = stateAt(step = SignupStep.BABY_ADDED)

        val result = reducer.reduce(state, SignupIntent.AddAnotherBaby)

        assertThat(result.babyState).isNull()
    }

    // -------------------------------------------------------------------------
    // Multi-device loop — ConnectAnotherDevice / RegisterDevice
    // -------------------------------------------------------------------------

    @Test
    fun `ConnectAnotherDevice resets the device control and returns to PICK_DEVICE`() {
        val state = stateAt(step = SignupStep.DEVICE_READY, steps = weightScaleSteps)
            .copy(registeredDevices = setOf(ProductType.MY_WEIGHT))
        state.form.controls.device.onValueChange(ProductType.MY_WEIGHT.id)

        val result = reducer.reduce(state, SignupIntent.ConnectAnotherDevice)

        assertThat(result.currentStep).isEqualTo(SignupStep.PICK_DEVICE)
        assertThat(result.form.controls.device.value).isEmpty()
        // Shared data is preserved across the loop — only the device resets.
        assertThat(result.registeredDevices).containsExactly(ProductType.MY_WEIGHT)
    }

    @Test
    fun `RegisterDevice records the selected device and jumps to the terminal`() {
        val state = stateAt(step = SignupStep.PASSWORD, steps = weightScaleSteps)
        state.form.controls.device.onValueChange(ProductType.MY_WEIGHT.id)

        val result = reducer.reduce(state, SignupIntent.RegisterDevice)

        assertThat(result.registeredDevices).containsExactly(ProductType.MY_WEIGHT)
        assertThat(result.currentStep).isEqualTo(weightScaleSteps.last())
        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `RegisterDevice with no selected device sets a Missing device error`() {
        val state = stateAt(step = SignupStep.PASSWORD)
        // device control left blank.

        val result = reducer.reduce(state, SignupIntent.RegisterDevice)

        assertThat(result.error).isEqualTo("Missing device")
        assertThat(result.registeredDevices).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Error-screen intents — AccountCreated / ShowDeviceError / RetryDevice
    // -------------------------------------------------------------------------

    @Test
    fun `AccountCreated flips accountCreated to true`() {
        val state = stateAt()

        val result = reducer.reduce(state, SignupIntent.AccountCreated)

        assertThat(result.accountCreated).isTrue()
    }

    @Test
    fun `ShowDeviceError moves to the ERROR terminal and clears loading`() {
        val state = stateAt(step = SignupStep.PASSWORD, isLoading = true)

        val result = reducer.reduce(state, SignupIntent.ShowDeviceError)

        assertThat(result.currentStep).isEqualTo(SignupStep.ERROR)
        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `RetryDevice sets isLoading true and clears error`() {
        val state = stateAt(step = SignupStep.ERROR, error = "previous failure")

        val result = reducer.reduce(state, SignupIntent.RetryDevice)

        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isNull()
    }

    @Test
    fun `FinishSignup clears loading and error`() {
        val state = stateAt(isLoading = true, error = "err")

        val result = reducer.reduce(state, SignupIntent.FinishSignup)

        assertThat(result.isLoading).isFalse()
        assertThat(result.error).isNull()
    }

    // -------------------------------------------------------------------------
    // stepsForDevice — branch coverage
    // -------------------------------------------------------------------------

    @Test
    fun `stepsForDevice with null device returns just PICK_DEVICE`() {
        val steps = SignupState.stepsForDevice(device = null)

        assertThat(steps).containsExactly(SignupStep.PICK_DEVICE)
    }

    @Test
    fun `stepsForDevice loop pass collapses head and omits already-captured slides`() {
        // MY_WEIGHT already registered captured sex/height/goal. Adding it again on a
        // loop pass collapses the head to PICK_DEVICE and omits PASSWORD + captured steps.
        val steps = SignupState.stepsForDevice(
            device = ProductType.MY_WEIGHT,
            registeredDevices = setOf(ProductType.MY_WEIGHT),
        )

        assertThat(steps.first()).isEqualTo(SignupStep.PICK_DEVICE)
        assertThat(steps).doesNotContain(SignupStep.NAME)
        assertThat(steps).doesNotContain(SignupStep.PASSWORD)
        assertThat(steps).doesNotContain(SignupStep.GENDER)
    }

    @Test
    fun `stepsForDevice uses ALL_DEVICES_READY terminal for the last device`() {
        // Two of three products registered; adding the third is the last device.
        val steps = SignupState.stepsForDevice(
            device = ProductType.BLOOD_PRESSURE,
            registeredDevices = setOf(ProductType.MY_WEIGHT, ProductType.BABY),
        )

        assertThat(steps.last()).isEqualTo(SignupStep.ALL_DEVICES_READY)
    }

    @Test
    fun `stepsForDevice blood pressure first pass includes GENDER only`() {
        val steps = SignupState.stepsForDevice(device = ProductType.BLOOD_PRESSURE)

        assertThat(steps).contains(SignupStep.GENDER)
        assertThat(steps).doesNotContain(SignupStep.HEIGHT)
        assertThat(steps).doesNotContain(SignupStep.GOAL)
    }

    // -------------------------------------------------------------------------
    // convertWeightValue — unit conversion helper
    // -------------------------------------------------------------------------

    @Test
    fun `convertWeightValue returns the input unchanged when blank`() {
        assertThat(convertWeightValue("", fromMetric = true, toMetric = false)).isEmpty()
    }

    @Test
    fun `convertWeightValue converts kg to lbs preserving one decimal`() {
        // "605" == 60.5 kg → 60.5 * 2.20462 = 133.38 → 133.4 → "1334".
        val result = convertWeightValue("605", fromMetric = true, toMetric = false)

        assertThat(result).isEqualTo("1334")
    }

    @Test
    fun `convertWeightValue converts lbs to kg preserving one decimal`() {
        // "1334" == 133.4 lbs → 133.4 / 2.20462 = 60.51 → 60.5 → "605".
        val result = convertWeightValue("1334", fromMetric = false, toMetric = true)

        assertThat(result).isEqualTo("605")
    }

    @Test
    fun `convertWeightValue leaves the value unchanged when units do not change`() {
        assertThat(convertWeightValue("605", fromMetric = false, toMetric = false)).isEqualTo("605")
        assertThat(convertWeightValue("605", fromMetric = true, toMetric = true)).isEqualTo("605")
    }

    @Test
    fun `convertWeightValue handles single-digit input via the leading-zero path`() {
        // "5" is treated as 0.5; converting same-unit yields "05".
        val result = convertWeightValue("5", fromMetric = true, toMetric = true)

        assertThat(result).isEqualTo("05")
    }

    // -------------------------------------------------------------------------
    // Reducer guard — unhandled intent falls through to else
    // -------------------------------------------------------------------------

    @Test
    fun `OpenBabySexPicker is a side-effect intent and leaves state unchanged`() {
        val state = stateAt(step = SignupStep.ADD_BABY, error = "err", steps = babySteps)

        val result = reducer.reduce(state, SignupIntent.OpenBabySexPicker)

        assertThat(result.currentStep).isEqualTo(SignupStep.ADD_BABY)
        assertThat(result.error).isEqualTo("err")
    }

    // -------------------------------------------------------------------------
    // SignupState — computed properties (index / progress / final-data-step)
    // -------------------------------------------------------------------------

    @Test
    fun `currentStepIndex and progress track position in the step list`() {
        val state = stateAt(step = SignupStep.BIRTHDAY)

        assertThat(state.currentStepIndex).isEqualTo(2) // NAME, EMAIL, BIRTHDAY
        assertThat(state.isFirstStep).isFalse()
        assertThat(state.isLastStep).isFalse()
        assertThat(state.progress).isEqualTo(3f / SignupState.COMMON_STEPS.size)
    }

    @Test
    fun `ERROR step pins index and progress to the end of the list`() {
        val state = stateAt(step = SignupStep.ERROR, steps = weightScaleSteps)

        // ERROR is terminal and not part of [steps]; it pins to the last index.
        assertThat(state.currentStepIndex).isEqualTo(weightScaleSteps.lastIndex)
        assertThat(state.isLastStep).isTrue()
        assertThat(state.progress).isEqualTo(1f)
    }

    @Test
    fun `isFinalDataStep is true on PASSWORD and false on GENDER`() {
        val onPassword = stateAt(step = SignupStep.PASSWORD, steps = weightScaleSteps)
        val onGender = stateAt(step = SignupStep.GENDER, steps = weightScaleSteps)

        assertThat(onPassword.isFinalDataStep).isTrue() // next is DEVICE_READY
        assertThat(onGender.isFinalDataStep).isFalse() // next is HEIGHT
    }

    @Test
    fun `showSkipButton is true on GOAL and on empty BABY_ADDED only`() {
        assertThat(stateAt(step = SignupStep.GOAL, steps = weightScaleSteps).showSkipButton).isTrue()
        assertThat(babyStateAt(babies = emptyList(), step = SignupStep.BABY_ADDED).showSkipButton).isTrue()
        // BABY_ADDED with babies present hides Skip.
        assertThat(
            babyStateAt(babies = listOf(BabyProfile(name = "Sal")), step = SignupStep.BABY_ADDED).showSkipButton,
        ).isFalse()
        assertThat(stateAt(step = SignupStep.EMAIL).showSkipButton).isFalse()
    }

    // -------------------------------------------------------------------------
    // SignupState.isCurrentStepValid — per-step validation arms
    // -------------------------------------------------------------------------

    @Test
    fun `isCurrentStepValid NAME requires first and last name`() {
        val state = stateAt(step = SignupStep.NAME)
        assertThat(state.isCurrentStepValid).isFalse() // blank by default

        state.form.controls.firstName.onValueChange("John")
        state.form.controls.lastName.onValueChange("Doe")
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid EMAIL requires a valid email`() {
        val state = stateAt(step = SignupStep.EMAIL)
        state.form.controls.email.onValueChange("not-an-email")
        assertThat(state.isCurrentStepValid).isFalse()

        state.form.controls.email.onValueChange("john@example.com")
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid BIRTHDAY and HEIGHT are always valid`() {
        assertThat(stateAt(step = SignupStep.BIRTHDAY).isCurrentStepValid).isTrue()
        assertThat(stateAt(step = SignupStep.HEIGHT, steps = weightScaleSteps).isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid PICK_DEVICE requires a selected device`() {
        val state = stateAt(step = SignupStep.PICK_DEVICE)
        assertThat(state.isCurrentStepValid).isFalse()

        state.form.controls.device.onValueChange(ProductType.MY_WEIGHT.id)
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid GENDER requires a selected sex`() {
        val state = stateAt(step = SignupStep.GENDER, steps = weightScaleSteps)
        assertThat(state.isCurrentStepValid).isFalse()

        state.form.controls.sex.onValueChange("male")
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid GOAL is true when goal is skipped`() {
        val state = stateAt(step = SignupStep.GOAL, goalSkipped = true, steps = weightScaleSteps)
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid GOAL with MAINTAIN ignores current weight`() {
        val state = stateAt(step = SignupStep.GOAL, steps = weightScaleSteps)
        state.form.controls.goalType.onValueChange(GoalType.MAINTAIN.value)
        state.form.controls.goalWeight.onValueChange("1600")
        // currentWeight intentionally left blank — MAINTAIN must not require it.
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid GOAL with LOSE requires current and goal weight`() {
        val state = stateAt(step = SignupStep.GOAL, steps = weightScaleSteps)
        state.form.controls.goalType.onValueChange(GoalType.LOSE.value)
        state.form.controls.goalWeight.onValueChange("1600")
        assertThat(state.isCurrentStepValid).isFalse() // currentWeight still blank

        state.form.controls.currentWeight.onValueChange("1800")
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid PASSWORD requires password confirm and zipcode`() {
        val state = stateAt(step = SignupStep.PASSWORD, steps = weightScaleSteps)
        assertThat(state.isCurrentStepValid).isFalse()

        state.form.controls.password.onValueChange("password123")
        state.form.controls.confirmPassword.onValueChange("password123")
        state.form.controls.zipcode.onValueChange("12345")
        assertThat(state.isCurrentStepValid).isTrue()
    }

    @Test
    fun `isCurrentStepValid ADD_BABY is false without a baby state`() {
        // No BabyState → the elvis guard returns false.
        assertThat(stateAt(step = SignupStep.ADD_BABY, steps = babySteps).isCurrentStepValid).isFalse()
        // Empty baby form is also invalid (name/sex required).
        assertThat(babyStateAt(step = SignupStep.ADD_BABY).isCurrentStepValid).isFalse()
    }

    @Test
    fun `isCurrentStepValid BABY_ADDED requires at least one baby`() {
        assertThat(babyStateAt(babies = emptyList(), step = SignupStep.BABY_ADDED).isCurrentStepValid).isFalse()
        assertThat(
            babyStateAt(babies = listOf(BabyProfile(name = "Sal")), step = SignupStep.BABY_ADDED).isCurrentStepValid,
        ).isTrue()
    }

    @Test
    fun `isCurrentStepValid terminal steps are always valid`() {
        assertThat(stateAt(step = SignupStep.DEVICE_READY, steps = weightScaleSteps).isCurrentStepValid).isTrue()
        assertThat(stateAt(step = SignupStep.ERROR, steps = weightScaleSteps).isCurrentStepValid).isTrue()
    }

    // -------------------------------------------------------------------------
    // showProgressBar — hidden on terminal ERROR screen (MOB-1161)
    // -------------------------------------------------------------------------

    @Test
    fun `showProgressBar is false on the terminal ERROR screen`() {
        assertThat(stateAt(step = SignupStep.ERROR, steps = weightScaleSteps).showProgressBar).isFalse()
    }

    @Test
    fun `showProgressBar is true on every non-error step`() {
        SignupStep.entries
            .filter { it != SignupStep.ERROR }
            .forEach { step ->
                assertThat(stateAt(step = step, steps = weightScaleSteps).showProgressBar).isTrue()
            }
    }
}
