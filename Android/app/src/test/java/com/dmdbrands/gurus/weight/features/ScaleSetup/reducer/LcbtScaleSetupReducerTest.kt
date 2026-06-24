package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.common.model.ScaleInfo
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LcbtScaleSetupReducer].
 *
 * LcbtScaleSetupReducer has no own intents; it delegates everything to [ScaleSetupReducer].
 * All tests verify that the delegation works correctly and that the default step list
 * for the LCBT flow is correct.
 */
class LcbtScaleSetupReducerTest {

    private lateinit var reducer: LcbtScaleSetupReducer

    companion object {
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH_SCAN"
        private const val TEST_SKU = "0378"
    }

    private fun defaultState() = LCBTScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = LcbtScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default LCBTScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.step).isEqualTo(LcbtScaleSetupStep.SCALE_INFO)
        assertThat(state.steps).hasSize(5)
        assertThat(state.steps.first()).isEqualTo(LcbtScaleSetupStep.SCALE_INFO)
        assertThat(state.steps.last()).isEqualTo(LcbtScaleSetupStep.SETUP_FINISHED)
        assertThat(state.isLoading).isTrue()
        assertThat(state.backEnabled).isFalse()
        assertThat(state.nextEnabled).isTrue()
        assertThat(state.isFirstStep).isTrue()
        assertThat(state.isLastStep).isFalse()
        assertThat(state.permissions).isEmpty()
    }

    @Test
    fun `default steps list contains all expected LCBT flow steps`() {
        val state = defaultState()

        assertThat(state.steps).containsExactly(
            LcbtScaleSetupStep.SCALE_INFO,
            LcbtScaleSetupStep.PERMISSIONS,
            LcbtScaleSetupStep.WAKEUP,
            LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
            LcbtScaleSetupStep.SETUP_FINISHED,
        ).inOrder()
    }

    // -------------------------------------------------------------------------
    // AlterConnectionState — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `AlterConnectionState to Success changes connectionState`() {
        val result = reducer.reduce(
            defaultState(), ScaleSetupIntent.AlterConnectionState(ConnectionState.Success)
        )

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `AlterConnectionState to Failed_Error changes connectionState`() {
        val result = reducer.reduce(
            defaultState(), ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error)
        )

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Failed.Error)
    }

    @Test
    fun `AlterConnectionState to Loading changes connectionState`() {
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(
                setupState = SetupState(
                    step = LcbtScaleSetupStep.SCALE_INFO,
                    connectionState = ConnectionState.Success,
                )
            )
        )

        val result = reducer.reduce(
            state, ScaleSetupIntent.AlterConnectionState(ConnectionState.Loading)
        )

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Loading)
    }

    // -------------------------------------------------------------------------
    // SetPermissions — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")

        val result = reducer.reduce(defaultState(), ScaleSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "true")
    }

    @Test
    fun `SetPermissions with empty map clears existing permissions`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")
        val state = reducer.reduce(defaultState(), ScaleSetupIntent.SetPermissions(perms))!!

        val result = reducer.reduce(state, ScaleSetupIntent.SetPermissions(mutableMapOf()))

        assertThat(result?.permissions).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetSku — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `SetSku updates sku field`() {
        val result = reducer.reduce(defaultState(), ScaleSetupIntent.SetSku(TEST_SKU))

        assertThat(result?.scaleSetupState?.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // SetScaleInfo — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleInfo stores scale info`() {
        val info = ScaleInfo(
            productName = "LCBT Scale",
            sku = TEST_SKU,
            setupType = com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType.Lcbt,
            bodyComp = true,
        )

        val result = reducer.reduce(defaultState(), ScaleSetupIntent.SetScaleInfo(info))

        assertThat(result?.scaleSetupState?.scaleInfo).isEqualTo(info)
    }

    @Test
    fun `SetScaleInfo with null clears scale info`() {
        val info = ScaleInfo(
            productName = "LCBT Scale",
            sku = TEST_SKU,
            setupType = com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType.Lcbt,
            bodyComp = false,
        )
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(scaleInfo = info)
        )

        val result = reducer.reduce(state, ScaleSetupIntent.SetScaleInfo(null))

        assertThat(result?.scaleSetupState?.scaleInfo).isNull()
    }

    // -------------------------------------------------------------------------
    // BackEnabled / NextEnabled — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `BackEnabled true enables back`() {
        val result = reducer.reduce(defaultState(), ScaleSetupIntent.BackEnabled(true))

        assertThat(result?.backEnabled).isTrue()
    }

    @Test
    fun `BackEnabled false disables back`() {
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(backEnabled = true)
        )

        val result = reducer.reduce(state, ScaleSetupIntent.BackEnabled(false))

        assertThat(result?.backEnabled).isFalse()
    }

    @Test
    fun `NextEnabled false disables next`() {
        val result = reducer.reduce(defaultState(), ScaleSetupIntent.NextEnabled(false))

        assertThat(result?.nextEnabled).isFalse()
    }

    @Test
    fun `NextEnabled true re-enables next`() {
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(nextEnabled = false)
        )

        val result = reducer.reduce(state, ScaleSetupIntent.NextEnabled(true))

        assertThat(result?.nextEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetNewStep — delegated
    // -------------------------------------------------------------------------

    @Test
    fun `SetNewStep navigates to WAKEUP step`() {
        val result = reducer.reduce(
            defaultState(), ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP)
        )

        assertThat(result?.step).isEqualTo(LcbtScaleSetupStep.WAKEUP)
    }

    @Test
    fun `SetNewStep navigates to CONNECTING_BLUETOOTH step`() {
        val result = reducer.reduce(
            defaultState(), ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.CONNECTING_BLUETOOTH)
        )

        assertThat(result?.step).isEqualTo(LcbtScaleSetupStep.CONNECTING_BLUETOOTH)
    }

    @Test
    fun `SetNewStep navigates to SETUP_FINISHED step and isLastStep becomes true`() {
        val result = reducer.reduce(
            defaultState(), ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.SETUP_FINISHED)
        )

        assertThat(result?.step).isEqualTo(LcbtScaleSetupStep.SETUP_FINISHED)
        assertThat(result?.isLastStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // Computed navigation helpers
    // -------------------------------------------------------------------------

    @Test
    fun `nextStep from SCALE_INFO is PERMISSIONS`() {
        assertThat(defaultState().nextStep).isEqualTo(LcbtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `previousStep is null when on first step`() {
        assertThat(defaultState().previousStep).isNull()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `Next intent returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(state, ScaleSetupIntent.Next)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `Back intent returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(state, ScaleSetupIntent.Back)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `TryAgain intent returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(state, ScaleSetupIntent.TryAgain)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ExitSetup intent returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(
            state, ScaleSetupIntent.ExitSetup(isSetupFinished = true)
        )

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OpenHelp intent returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(state, ScaleSetupIntent.OpenHelp)

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // copyBaseState
    // -------------------------------------------------------------------------

    @Test
    fun `copyBaseState produces new instance with updated scaleSetupState`() {
        val state = defaultState()
        val updatedBase = state.scaleSetupState.copy(sku = TEST_SKU)

        val copied = state.copyBaseState(updatedBase)

        assertThat(copied.scaleSetupState.sku).isEqualTo(TEST_SKU)
    }
}
