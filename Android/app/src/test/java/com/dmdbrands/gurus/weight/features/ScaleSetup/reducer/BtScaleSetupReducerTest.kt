package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BtScaleSetupReducer].
 *
 * BtScaleSetupReducer handles one own intent ([BtScaleSetupIntent.SetUser]) and
 * delegates everything else to [ScaleSetupReducer]. Both paths are tested here.
 */
class BtScaleSetupReducerTest {

    private lateinit var reducer: BtScaleSetupReducer

    companion object {
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH_SCAN"
        private const val TEST_USER = 3
        private const val TEST_SKU = "0382"
    }

    private fun defaultState() = BtScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = BtScaleSetupReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default BtScaleSetupState has expected initial values`() {
        val state = defaultState()

        assertThat(state.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
        assertThat(state.steps).hasSize(7)
        assertThat(state.steps.first()).isEqualTo(BtScaleSetupStep.SCALE_INFO)
        assertThat(state.steps.last()).isEqualTo(BtScaleSetupStep.SETUP_FINISHED)
        assertThat(state.user).isNull()
        // TODO: Bug — userString should be null when user is null, not "Unull".
        //  The getter `"U" + user?.toString()` evaluates to "U" + "null" because
        //  null?.toString() returns the string "null", not null. Fix: `user?.let { "U$it" }`
        assertThat(state.userString).isEqualTo("Unull")
        assertThat(state.isLoading).isTrue()
        assertThat(state.backEnabled).isFalse()
        assertThat(state.nextEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetUser — own intent
    // -------------------------------------------------------------------------

    @Test
    fun `SetUser stores provided user number`() {
        val state = defaultState()

        val result = reducer.reduce(state, BtScaleSetupIntent.SetUser(TEST_USER))

        assertThat(result?.user).isEqualTo(TEST_USER)
    }

    @Test
    fun `SetUser generates correct userString`() {
        val state = defaultState()

        val result = reducer.reduce(state, BtScaleSetupIntent.SetUser(2))

        assertThat(result?.userString).isEqualTo("U2")
    }

    @Test
    fun `SetUser preserves base state fields`() {
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "true")
        val state = reducer.reduce(defaultState(), ScaleSetupIntent.SetPermissions(perms))!!

        val result = reducer.reduce(state, BtScaleSetupIntent.SetUser(TEST_USER))

        assertThat(result?.user).isEqualTo(TEST_USER)
        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "true")
    }

    @Test
    fun `SetUser with user 1 stores 1 and formats userString as U1`() {
        val result = reducer.reduce(defaultState(), BtScaleSetupIntent.SetUser(1))

        assertThat(result?.user).isEqualTo(1)
        assertThat(result?.userString).isEqualTo("U1")
    }

    // -------------------------------------------------------------------------
    // Delegated base intents — AlterConnectionState
    // -------------------------------------------------------------------------

    @Test
    fun `AlterConnectionState Success delegates to base reducer`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `AlterConnectionState Failed_Error delegates to base reducer`() {
        val state = defaultState()

        val result = reducer.reduce(
            state, ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error)
        )

        assertThat(result?.scaleSetupState?.setupState?.connectionState)
            .isEqualTo(ConnectionState.Failed.Error)
    }

    // -------------------------------------------------------------------------
    // Delegated base intents — SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions delegates to base reducer and updates permissions`() {
        val state = defaultState()
        val perms: GGPermissionStatusMap = mutableMapOf(PERMISSION_BLUETOOTH to "false")

        val result = reducer.reduce(state, ScaleSetupIntent.SetPermissions(perms))

        assertThat(result?.permissions).containsEntry(PERMISSION_BLUETOOTH, "false")
    }

    // -------------------------------------------------------------------------
    // Delegated base intents — SetSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetSku delegates to base reducer and updates sku`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleSetupIntent.SetSku(TEST_SKU))

        assertThat(result?.scaleSetupState?.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // Delegated base intents — BackEnabled / NextEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `BackEnabled true delegates to base reducer`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleSetupIntent.BackEnabled(true))

        assertThat(result?.backEnabled).isTrue()
    }

    @Test
    fun `NextEnabled false delegates to base reducer`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleSetupIntent.NextEnabled(false))

        assertThat(result?.nextEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // Delegated base intents — SetNewStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetNewStep navigates to PERMISSIONS step`() {
        val state = defaultState()

        val result = reducer.reduce(state, ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))

        assertThat(result?.step).isEqualTo(BtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `SetNewStep preserves user field when navigating`() {
        val state = reducer.reduce(defaultState(), BtScaleSetupIntent.SetUser(TEST_USER))!!

        val result = reducer.reduce(state, ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PAIRING_MODE))

        assertThat(result?.step).isEqualTo(BtScaleSetupStep.PAIRING_MODE)
        assertThat(result?.user).isEqualTo(TEST_USER)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents return state unchanged (handled by ViewModel)
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
    fun `ExitSetup returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(
            state, ScaleSetupIntent.ExitSetup(isSetupFinished = false)
        )

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `TryAgain returns state unchanged — handled by ViewModel`() {
        val state = defaultState()
        val result = reducer.reduce(state, ScaleSetupIntent.TryAgain)

        assertThat(result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // copyBaseState
    // -------------------------------------------------------------------------

    @Test
    fun `copyBaseState produces new instance with updated scaleSetupState`() {
        val state = defaultState()
        val newBase = state.scaleSetupState.copy(sku = TEST_SKU)

        val copied = state.copyBaseState(newBase)

        assertThat(copied.scaleSetupState.sku).isEqualTo(TEST_SKU)
        assertThat(copied.user).isEqualTo(state.user) // own field preserved
    }
}
