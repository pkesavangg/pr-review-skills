package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppsyncScaleSetupReducer].
 *
 * AppsyncScaleSetupReducer is a self-contained reducer with notable logic in:
 *  - [AppsyncScaleSetupIntent.SetBodyComp] — regenerates the step list
 *  - [AppsyncScaleSetupIntent.Next] / [AppsyncScaleSetupIntent.Back] — index-based navigation
 *    with a special skip-one-step rule when going Back from ACTIVATE_SCALE or SETUP_FINISHED
 *    when the required permissions are already granted.
 */
class AppsyncScaleSetupReducerTest {

    private lateinit var reducer: AppsyncScaleSetupReducer

    companion object {
        private const val TEST_SKU = "0341"
        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
    }

    private fun defaultState() = AppsyncScaleSetupState()

    // Full body-comp step list (7 steps)
    private val bodyCompSteps = listOf(
        AppsyncScaleSetupStep.SCALE_INFO,
        AppsyncScaleSetupStep.PERMISSIONS,
        AppsyncScaleSetupStep.ACTIVATE_SCALE,
        AppsyncScaleSetupStep.ADD_INFO,
        AppsyncScaleSetupStep.STEP_ON,
        AppsyncScaleSetupStep.OPEN_CAMERA,
        AppsyncScaleSetupStep.SETUP_FINISHED,
    )

    // Basic (non-body-comp) step list (6 steps)
    private val basicSteps = listOf(
        AppsyncScaleSetupStep.SCALE_INFO,
        AppsyncScaleSetupStep.PERMISSIONS,
        AppsyncScaleSetupStep.ACTIVATE_SCALE,
        AppsyncScaleSetupStep.STEP_ON,
        AppsyncScaleSetupStep.OPEN_CAMERA,
        AppsyncScaleSetupStep.SETUP_FINISHED,
    )

    // A state with a full steps list initialised and on the first step
    private fun bodyCompState() = defaultState().copy(
        bodyComp = true,
        steps = persistentListOf(*bodyCompSteps.toTypedArray()),
        currentStep = AppsyncScaleSetupStep.SCALE_INFO,
    )

    private fun basicState() = defaultState().copy(
        bodyComp = false,
        steps = persistentListOf(*basicSteps.toTypedArray()),
        currentStep = AppsyncScaleSetupStep.SCALE_INFO,
    )

    @BeforeEach
    fun setUp() {
        reducer = AppsyncScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default AppsyncScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
        assertThat(state.sku).isEqualTo("0341")
        assertThat(state.bodyComp).isTrue()
        assertThat(state.steps).isEmpty() // not yet generated
        assertThat(state.isNextEnabled).isTrue()
        assertThat(state.error).isNull()
        assertThat(state.isSetupFinished).isFalse()
        assertThat(state.permissions).isEmpty()
        assertThat(state.scanResult).isNull()
        assertThat(state.isFirstStep).isFalse() // steps is empty → indexOf returns -1 ≠ 0
    }

    // -------------------------------------------------------------------------
    // SetScaleSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleSku updates sku`() {
        val result = reducer.reduce(defaultState(), AppsyncScaleSetupIntent.SetScaleSku(TEST_SKU))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // SetBodyComp — generates steps
    // -------------------------------------------------------------------------

    @Test
    fun `SetBodyComp true generates full body-comp step list with ADD_INFO`() {
        val result = reducer.reduce(
            defaultState().copy(bodyComp = false),
            AppsyncScaleSetupIntent.SetBodyComp(true),
        )

        assertThat(result?.bodyComp).isTrue()
        assertThat(result?.steps).containsExactlyElementsIn(bodyCompSteps).inOrder()
        assertThat(result?.steps).contains(AppsyncScaleSetupStep.ADD_INFO)
    }

    @Test
    fun `SetBodyComp false generates basic step list without ADD_INFO`() {
        val result = reducer.reduce(
            defaultState().copy(bodyComp = true),
            AppsyncScaleSetupIntent.SetBodyComp(false),
        )

        assertThat(result?.bodyComp).isFalse()
        assertThat(result?.steps).containsExactlyElementsIn(basicSteps).inOrder()
        assertThat(result?.steps).doesNotContain(AppsyncScaleSetupStep.ADD_INFO)
    }

    @Test
    fun `SetBodyComp preserves currentStep when it is still in new steps list`() {
        val state = defaultState().copy(
            bodyComp = true,
            currentStep = AppsyncScaleSetupStep.SCALE_INFO,
        )

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.SetBodyComp(false))

        // SCALE_INFO is present in both lists
        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `SetBodyComp resets currentStep to first when current step not in new steps`() {
        // ADD_INFO is only in the body-comp list
        val state = defaultState().copy(
            bodyComp = true,
            currentStep = AppsyncScaleSetupStep.ADD_INFO,
        )

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.SetBodyComp(false))

        // ADD_INFO is not in basic list → reset to first
        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // SetCurrentStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetCurrentStep changes current step`() {
        val result = reducer.reduce(
            bodyCompState(),
            AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.PERMISSIONS),
        )

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.PERMISSIONS)
    }

    // -------------------------------------------------------------------------
    // SetNextButtonState
    // -------------------------------------------------------------------------

    @Test
    fun `SetNextButtonState false disables next button`() {
        val result = reducer.reduce(
            defaultState(),
            AppsyncScaleSetupIntent.SetNextButtonState(false),
        )

        assertThat(result?.isNextEnabled).isFalse()
    }

    @Test
    fun `SetNextButtonState true enables next button`() {
        val state = defaultState().copy(isNextEnabled = false)

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.SetNextButtonState(true))

        assertThat(result?.isNextEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetError
    // -------------------------------------------------------------------------

    @Test
    fun `SetError stores error message`() {
        val result = reducer.reduce(
            defaultState(), AppsyncScaleSetupIntent.SetError("Something went wrong")
        )

        assertThat(result?.error).isEqualTo("Something went wrong")
    }

    @Test
    fun `SetError with null clears error`() {
        val state = defaultState().copy(error = "Previous error")

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.SetError(null))

        assertThat(result?.error).isNull()
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_CAMERA to "true")

        val result = reducer.reduce(defaultState(), AppsyncScaleSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_CAMERA, "true")
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup true sets isSetupFinished`() {
        val result = reducer.reduce(
            defaultState(), AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = true)
        )

        assertThat(result?.isSetupFinished).isTrue()
    }

    @Test
    fun `ExitSetup false clears isSetupFinished`() {
        val state = defaultState().copy(isSetupFinished = true)

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = false))

        assertThat(result?.isSetupFinished).isFalse()
    }

    // -------------------------------------------------------------------------
    // Next — index-based navigation
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO advances to PERMISSIONS`() {
        val state = bodyCompState()

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `Next from PERMISSIONS advances to ACTIVATE_SCALE`() {
        val state = bodyCompState().copy(currentStep = AppsyncScaleSetupStep.PERMISSIONS)

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Next)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.ACTIVATE_SCALE)
    }

    @Test
    fun `Next from last step returns state unchanged`() {
        val state = bodyCompState().copy(
            currentStep = AppsyncScaleSetupStep.SETUP_FINISHED
        )

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Next)

        // No change — already at last step
        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.SETUP_FINISHED)
    }

    // -------------------------------------------------------------------------
    // Back — index-based navigation with skip logic
    // -------------------------------------------------------------------------

    @Test
    fun `Back from PERMISSIONS goes to SCALE_INFO`() {
        val state = bodyCompState().copy(currentStep = AppsyncScaleSetupStep.PERMISSIONS)

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `Back from ACTIVATE_SCALE with NO required permissions goes to PERMISSIONS`() {
        // No permissions granted → skip count = 1 (normal -1 navigation)
        val state = bodyCompState().copy(
            currentStep = AppsyncScaleSetupStep.ACTIVATE_SCALE,
            permissions = mutableMapOf(), // nothing granted → areRequiredPermissionsEnabled = false
        )

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `Back from SETUP_FINISHED skips two steps and goes to STEP_ON in body-comp flow`() {
        // SETUP_FINISHED is index 6; skip 2 → index 4 = STEP_ON
        val state = bodyCompState().copy(
            currentStep = AppsyncScaleSetupStep.SETUP_FINISHED
        )

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.STEP_ON)
    }

    @Test
    fun `Back from first step returns state unchanged`() {
        val state = bodyCompState() // SCALE_INFO = index 0

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.Back)

        assertThat(result?.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents return state unchanged (else -> state.copy())
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, AppsyncScaleSetupIntent.OpenHelp)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `RequestPermission returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(
            state, AppsyncScaleSetupIntent.RequestPermission(PERMISSION_CAMERA)
        )

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Computed properties
    // -------------------------------------------------------------------------

    @Test
    fun `isFirstStep is true when on SCALE_INFO`() {
        assertThat(bodyCompState().isFirstStep).isTrue()
    }

    @Test
    fun `isLastStep is true when on SETUP_FINISHED`() {
        val state = bodyCompState().copy(currentStep = AppsyncScaleSetupStep.SETUP_FINISHED)

        assertThat(state.isLastStep).isTrue()
    }

    @Test
    fun `currentStepIndex returns correct index for ACTIVATE_SCALE in body-comp list`() {
        val state = bodyCompState().copy(currentStep = AppsyncScaleSetupStep.ACTIVATE_SCALE)

        assertThat(state.currentStepIndex).isEqualTo(2)
    }
}
