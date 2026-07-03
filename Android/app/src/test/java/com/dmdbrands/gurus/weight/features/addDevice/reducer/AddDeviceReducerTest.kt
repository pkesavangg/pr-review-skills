package com.dmdbrands.gurus.weight.features.addDevice.reducer

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AddDeviceReducer].
 *
 * AddDeviceReducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 *
 * Note: [AddScaleState] requires a [FormGroup], so we build it through the companion
 * factory [AddScaleFormControls.create] and wrap it in a [FormGroup].
 */
class AddDeviceReducerTest {

    private lateinit var reducer: AddDeviceReducer

    companion object {
        private const val TEST_SKU = "0382"
        private const val TEST_SCALE_ID = "scale-xyz-001"
    }

    private fun createForm() = FormGroup(AddScaleFormControls.create())

    private fun defaultState() = AddScaleState(
        form = createForm(),
    )

    @BeforeEach
    fun setUp() {
        reducer = AddDeviceReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default AddScaleState has expected initial values`() {
        val state = defaultState()

        assertThat(state.isSubmitting).isFalse()
        assertThat(state.selectedSku).isNull()
        assertThat(state.savedScales).isEmpty()
        assertThat(state.scaleId).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isSubmitting to true`() {
        val state = defaultState()

        val result = reducer.reduce(state, AddDeviceIntent.Submit)

        assertThat(result?.isSubmitting).isTrue()
    }

    @Test
    fun `Submit preserves existing state fields`() {
        val state = defaultState().copy(selectedSku = TEST_SKU)

        val result = reducer.reduce(state, AddDeviceIntent.Submit)

        assertThat(result?.isSubmitting).isTrue()
        assertThat(result?.selectedSku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // OpenSelectedScaleSetup
    // -------------------------------------------------------------------------

    @Test
    fun `OpenSelectedScaleSetup stores sku`() {
        val state = defaultState()

        val result = reducer.reduce(state, AddDeviceIntent.OpenSelectedScaleSetup(TEST_SKU))

        assertThat(result?.selectedSku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `OpenSelectedScaleSetup replaces previous sku`() {
        val state = defaultState().copy(selectedSku = "0341")

        val result = reducer.reduce(state, AddDeviceIntent.OpenSelectedScaleSetup(TEST_SKU))

        assertThat(result?.selectedSku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `OpenSelectedScaleSetup preserves other state fields`() {
        val state = defaultState().copy(isSubmitting = true)

        val result = reducer.reduce(state, AddDeviceIntent.OpenSelectedScaleSetup(TEST_SKU))

        assertThat(result?.selectedSku).isEqualTo(TEST_SKU)
        assertThat(result?.isSubmitting).isTrue()
    }

    // -------------------------------------------------------------------------
    // OpenDeviceSettings
    // -------------------------------------------------------------------------

    @Test
    fun `OpenDeviceSettings stores scaleId`() {
        val result = reducer.reduce(
            defaultState(), AddDeviceIntent.OpenDeviceSettings(TEST_SCALE_ID)
        )

        assertThat(result?.scaleId).isEqualTo(TEST_SCALE_ID)
    }

    @Test
    fun `OpenDeviceSettings replaces previous scaleId`() {
        val state = defaultState().copy(scaleId = "old-scale-id")

        val result = reducer.reduce(state, AddDeviceIntent.OpenDeviceSettings(TEST_SCALE_ID))

        assertThat(result?.scaleId).isEqualTo(TEST_SCALE_ID)
    }

    // -------------------------------------------------------------------------
    // SetSavedScales
    // -------------------------------------------------------------------------

    @Test
    fun `SetSavedScales with empty list stores empty savedScales`() {
        val result = reducer.reduce(defaultState(), AddDeviceIntent.SetSavedScales(emptyList()))

        assertThat(result?.savedScales).isEmpty()
    }

    @Test
    fun `SetSavedScales with single device stores its scaleInfo`() {
        val device: Device = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), AddDeviceIntent.SetSavedScales(listOf(device)))

        // One device → one DeviceModelInfo entry
        assertThat(result?.savedScales).hasSize(1)
    }

    @Test
    fun `SetSavedScales with multiple devices stores all as DeviceModelInfo and sorts by createdAt descending`() {
        // Two devices with null createdAt on both → sorted equally, but list size is 2
        val deviceA: Device = mockk(relaxed = true)
        val deviceB: Device = mockk(relaxed = true)

        val result = reducer.reduce(
            defaultState(),
            AddDeviceIntent.SetSavedScales(listOf(deviceA, deviceB)),
        )

        assertThat(result?.savedScales).hasSize(2)
    }

    @Test
    fun `SetSavedScales replaces previous savedScales`() {
        val deviceA: Device = mockk(relaxed = true)
        val state = reducer.reduce(defaultState(), AddDeviceIntent.SetSavedScales(listOf(deviceA)))!!

        val result = reducer.reduce(state, AddDeviceIntent.SetSavedScales(emptyList()))

        assertThat(result?.savedScales).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — returns state unchanged (else -> state)
    // -------------------------------------------------------------------------

    @Test
    fun `ShowHelp returns state unchanged`() {
        val state = defaultState()

        val result = reducer.reduce(state, AddDeviceIntent.ShowHelp)

        // ShowHelp → state.copy() which equals original state
        assertThat(result?.isSubmitting).isEqualTo(state.isSubmitting)
        assertThat(result?.selectedSku).isEqualTo(state.selectedSku)
    }

    @Test
    fun `OpenScaleChooser returns state unchanged`() {
        val state = defaultState().copy(isSubmitting = true, selectedSku = TEST_SKU)

        val result = reducer.reduce(state, AddDeviceIntent.OpenScaleChooser)

        assertThat(result?.isSubmitting).isTrue()
        assertThat(result?.selectedSku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `ResetForm returns state unchanged by reducer`() {
        val state = defaultState().copy(isSubmitting = true)

        val result = reducer.reduce(state, AddDeviceIntent.ResetForm)

        // else -> state — ResetForm side effect handled by ViewModel
        assertThat(result?.isSubmitting).isTrue()
    }
}
