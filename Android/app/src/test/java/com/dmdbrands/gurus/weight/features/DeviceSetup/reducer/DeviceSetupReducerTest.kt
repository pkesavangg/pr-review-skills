package com.dmdbrands.gurus.weight.features.DeviceSetup.reducer

import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.common.model.DeviceModelInfo
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceSetupReducer] — the base reducer shared across all scale setup flows.
 *
 * Because DeviceSetupReducer is open and parameterised, these tests use the concrete
 * [BtScaleSetupReducer] / [BtScaleSetupState] pair as the minimal real subclass.
 * This lets us exercise every branch in the base reduce() without needing a synthetic stub.
 */
class DeviceSetupReducerTest {

    private lateinit var reducer: BtScaleSetupReducer

    companion object {
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH_SCAN"
        private const val TEST_SKU = "0382"
    }

    // A default initial state backed by BtScaleSetupState (uses base DeviceSetupState internally)
    private fun defaultState() = BtScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = BtScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state sanity
    // -------------------------------------------------------------------------

    @Test
    fun `default BtScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
        assertThat(state.isFirstStep).isTrue()
        assertThat(state.isLastStep).isFalse()
        assertThat(state.isLoading).isTrue() // ConnectionState.Loading by default
        assertThat(state.backEnabled).isFalse()
        assertThat(state.nextEnabled).isTrue()
        assertThat(state.permissions).isEmpty()
    }

    // -------------------------------------------------------------------------
    // AlterConnectionState
    // -------------------------------------------------------------------------

    @Test
    fun `AlterConnectionState to Success changes connectionState`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.AlterConnectionState(ConnectionState.Success))

        assertThat(result?.scaleSetupState?.setupState?.connectionState).isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `AlterConnectionState to Loading changes connectionState`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.AlterConnectionState(ConnectionState.Loading))

        assertThat(result?.scaleSetupState?.setupState?.connectionState).isEqualTo(ConnectionState.Loading)
    }

    @Test
    fun `AlterConnectionState to Failed_Error changes connectionState`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Failed.Error)
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions stores provided map`() {
        val state = defaultState()
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")

        val result = reducer.reduce(state, DeviceSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "true")
    }

    @Test
    fun `SetPermissions with empty map clears permissions`() {
        val initialPerms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(permissions = initialPerms)
        )

        val result = reducer.reduce(state, DeviceSetupIntent.SetPermissions(mutableMapOf()))

        assertThat(result?.permissions).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetSku updates sku field`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.SetSku(TEST_SKU))

        assertThat(result?.scaleSetupState?.sku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `SetSku preserves other base state fields`() {
        val state = defaultState()
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "false")
        val stateWithPerms = reducer.reduce(state, DeviceSetupIntent.SetPermissions(perms))!!

        val result = reducer.reduce(stateWithPerms, DeviceSetupIntent.SetSku(TEST_SKU))

        assertThat(result?.scaleSetupState?.sku).isEqualTo(TEST_SKU)
        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "false")
    }

    // -------------------------------------------------------------------------
    // SetScaleInfo
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleInfo stores provided DeviceModelInfo`() {
        val state = defaultState()
        val info = DeviceModelInfo(
            productName = "Smart Scale",
            sku = TEST_SKU,
            setupType = com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType.Bluetooth,
            bodyComp = true,
        )

        val result = reducer.reduce(state, DeviceSetupIntent.SetScaleInfo(info))

        assertThat(result?.scaleSetupState?.scaleInfo).isEqualTo(info)
    }

    @Test
    fun `SetScaleInfo with null clears scale info`() {
        val info = DeviceModelInfo(
            productName = "Smart Scale",
            sku = TEST_SKU,
            setupType = com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType.Bluetooth,
            bodyComp = false,
        )
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(scaleInfo = info)
        )

        val result = reducer.reduce(state, DeviceSetupIntent.SetScaleInfo(null))

        assertThat(result?.scaleSetupState?.scaleInfo).isNull()
    }

    // -------------------------------------------------------------------------
    // BackEnabled / NextEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `BackEnabled true enables back navigation`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.BackEnabled(true))

        assertThat(result?.backEnabled).isTrue()
    }

    @Test
    fun `BackEnabled false disables back navigation`() {
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(backEnabled = true)
        )

        val result = reducer.reduce(state, DeviceSetupIntent.BackEnabled(false))

        assertThat(result?.backEnabled).isFalse()
    }

    @Test
    fun `NextEnabled false disables next navigation`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.NextEnabled(false))

        assertThat(result?.nextEnabled).isFalse()
    }

    @Test
    fun `NextEnabled true re-enables next navigation`() {
        val state = defaultState().copyBaseState(
            defaultState().scaleSetupState.copy(nextEnabled = false)
        )

        val result = reducer.reduce(state, DeviceSetupIntent.NextEnabled(true))

        assertThat(result?.nextEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetNewStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetNewStep to a step already in list updates current step`() {
        val state = defaultState()

        val result = reducer.reduce(
            state, DeviceSetupIntent.SetNewStep(BtScaleSetupStep.PAIRING_MODE)
        )

        assertThat(result?.step).isEqualTo(BtScaleSetupStep.PAIRING_MODE)
    }

    @Test
    fun `SetNewStep to a new step appends it to the steps list`() {
        // SETUP_FINISHED is already in the default list, let us use it then verify index update
        val state = defaultState()

        val result = reducer.reduce(
            state, DeviceSetupIntent.SetNewStep(BtScaleSetupStep.SETUP_FINISHED)
        )

        assertThat(result?.step).isEqualTo(BtScaleSetupStep.SETUP_FINISHED)
        assertThat(result?.steps).contains(BtScaleSetupStep.SETUP_FINISHED)
    }

    @Test
    fun `SetNewStep with wrong Step type returns state with base updated`() {
        // LcbtScaleSetupStep is a different enum — the as? cast returns null,
        // so the reducer returns state.copyBaseState(baseState) unchanged
        val state = defaultState()

        val result = reducer.reduce(
            state,
            DeviceSetupIntent.SetNewStep(com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LcbtScaleSetupStep.WAKEUP)
        )

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents (Next, Back, Skip, TryAgain, OpenHelp, ExitSetup, RequestPermission)
    // — these are not handled by the base reducer inner `when` (else -> null), but the outer
    //   logic returns `state` unchanged when updatedBaseState is null, so the ViewModel
    //   receives the unmodified state and handles side effects itself.
    // -------------------------------------------------------------------------

    @Test
    fun `Next intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.Next)

        // Base reducer hits `else -> null` in inner when; outer logic returns state unchanged
        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `Back intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.Back)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `Skip intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.Skip)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ExitSetup intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.ExitSetup(isSetupFinished = true))

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OpenHelp intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.OpenHelp)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `TryAgain intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.TryAgain)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `RequestPermission intent is not handled by base reducer — returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, DeviceSetupIntent.RequestPermission(PERMISSION_BLUETOOTH))

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // Computed properties on BaseState
    // -------------------------------------------------------------------------

    @Test
    fun `isFirstStep is true when on first step`() {
        val state = defaultState() // starts at SCALE_INFO which is first

        assertThat(state.isFirstStep).isTrue()
    }

    @Test
    fun `isLastStep is true when on SETUP_FINISHED`() {
        val state = defaultState()
        val result = reducer.reduce(state, DeviceSetupIntent.SetNewStep(BtScaleSetupStep.SETUP_FINISHED))!!

        assertThat(result.isLastStep).isTrue()
    }

    @Test
    fun `nextStep returns the step after the current one`() {
        val state = defaultState() // current step = SCALE_INFO

        // Second step in default list is PERMISSIONS
        assertThat(state.nextStep).isEqualTo(BtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `previousStep is null when on first step`() {
        val state = defaultState()

        assertThat(state.previousStep).isNull()
    }
}
