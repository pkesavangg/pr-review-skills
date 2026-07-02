package com.dmdbrands.gurus.weight.features.deviceMode.reducer

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceModeReducer].
 *
 * DeviceModeReducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class DeviceModeReducerTest {

    private lateinit var reducer: DeviceModeReducer

    private fun defaultState() = DeviceModeState()

    @BeforeEach
    fun setUp() {
        reducer = DeviceModeReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default DeviceModeState has expected initial values`() {
        val state = defaultState()

        assertThat(state.scale).isNull()
        assertThat(state.isAllBodyMetrics).isTrue()
        assertThat(state.isHeartRateOn).isFalse()
        assertThat(state.hasModeChanged).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScale
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale stores provided device`() {
        val device: Device = mockk(relaxed = true)

        val result = reducer.reduce(defaultState(), DeviceModeIntent.SetScale(device))

        assertThat(result?.scale).isEqualTo(device)
    }

    @Test
    fun `SetScale replaces previous device`() {
        val deviceA: Device = mockk(relaxed = true)
        val deviceB: Device = mockk(relaxed = true)
        val state = defaultState().copy(scale = deviceA)

        val result = reducer.reduce(state, DeviceModeIntent.SetScale(deviceB))

        assertThat(result?.scale).isEqualTo(deviceB)
    }

    @Test
    fun `SetScale preserves mode flags`() {
        val device: Device = mockk(relaxed = true)
        val state = defaultState().copy(
            isAllBodyMetrics = false,
            isHeartRateOn = true,
            hasModeChanged = true,
        )

        val result = reducer.reduce(state, DeviceModeIntent.SetScale(device))

        assertThat(result?.scale).isEqualTo(device)
        assertThat(result?.isAllBodyMetrics).isFalse()
        assertThat(result?.isHeartRateOn).isTrue()
        assertThat(result?.hasModeChanged).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetMode
    // -------------------------------------------------------------------------

    @Test
    fun `SetMode updates isAllBodyMetrics and hasModeChanged`() {
        val state = defaultState()

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true),
        )

        assertThat(result?.isAllBodyMetrics).isFalse()
        assertThat(result?.hasModeChanged).isTrue()
    }

    @Test
    fun `SetMode with isAllBodyMetrics true and hasModeChanged false reflects mode unchanged`() {
        val state = defaultState().copy(isAllBodyMetrics = false, hasModeChanged = true)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetMode(isAllBodyMetrics = true, hasModeChanged = false),
        )

        assertThat(result?.isAllBodyMetrics).isTrue()
        assertThat(result?.hasModeChanged).isFalse()
    }

    @Test
    fun `SetMode preserves isHeartRateOn`() {
        val state = defaultState().copy(isHeartRateOn = true)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true),
        )

        assertThat(result?.isHeartRateOn).isTrue() // unchanged
    }

    @Test
    fun `SetMode preserves scale reference`() {
        val device: Device = mockk(relaxed = true)
        val state = defaultState().copy(scale = device)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true),
        )

        assertThat(result?.scale).isEqualTo(device)
    }

    // -------------------------------------------------------------------------
    // SetHeartRate
    // -------------------------------------------------------------------------

    @Test
    fun `SetHeartRate enables heart rate and marks mode changed`() {
        val state = defaultState()

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetHeartRate(isHeartRateOn = true, hasModeChanged = true),
        )

        assertThat(result?.isHeartRateOn).isTrue()
        assertThat(result?.hasModeChanged).isTrue()
    }

    @Test
    fun `SetHeartRate disables heart rate`() {
        val state = defaultState().copy(isHeartRateOn = true, hasModeChanged = true)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetHeartRate(isHeartRateOn = false, hasModeChanged = false),
        )

        assertThat(result?.isHeartRateOn).isFalse()
        assertThat(result?.hasModeChanged).isFalse()
    }

    @Test
    fun `SetHeartRate preserves isAllBodyMetrics`() {
        val state = defaultState().copy(isAllBodyMetrics = false)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetHeartRate(isHeartRateOn = true, hasModeChanged = true),
        )

        assertThat(result?.isAllBodyMetrics).isFalse() // unchanged
    }

    @Test
    fun `SetHeartRate preserves scale reference`() {
        val device: Device = mockk(relaxed = true)
        val state = defaultState().copy(scale = device)

        val result = reducer.reduce(
            state,
            DeviceModeIntent.SetHeartRate(isHeartRateOn = true, hasModeChanged = true),
        )

        assertThat(result?.scale).isEqualTo(device)
    }

    // -------------------------------------------------------------------------
    // Independent changes — SetMode and SetHeartRate are orthogonal
    // -------------------------------------------------------------------------

    @Test
    fun `SetMode and SetHeartRate together set both flags`() {
        val state = defaultState()

        val afterMode = reducer.reduce(
            state,
            DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true),
        )!!

        val result = reducer.reduce(
            afterMode,
            DeviceModeIntent.SetHeartRate(isHeartRateOn = true, hasModeChanged = true),
        )

        assertThat(result?.isAllBodyMetrics).isFalse()
        assertThat(result?.isHeartRateOn).isTrue()
        assertThat(result?.hasModeChanged).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — return state.copy() (state unchanged)
    // -------------------------------------------------------------------------

    @Test
    fun `Save returns state unchanged`() {
        val state = defaultState().copy(
            isAllBodyMetrics = false,
            isHeartRateOn = true,
            hasModeChanged = true,
        )

        val result = reducer.reduce(state, DeviceModeIntent.Save)

        assertThat(result?.isAllBodyMetrics).isFalse()
        assertThat(result?.isHeartRateOn).isTrue()
        assertThat(result?.hasModeChanged).isTrue()
    }

    @Test
    fun `Back returns state unchanged`() {
        val state = defaultState().copy(isAllBodyMetrics = false)

        val result = reducer.reduce(state, DeviceModeIntent.Back)

        assertThat(result?.isAllBodyMetrics).isFalse()
    }

    @Test
    fun `OpenBiaModal returns state unchanged`() {
        val state = defaultState().copy(isHeartRateOn = true)

        val result = reducer.reduce(state, DeviceModeIntent.OpenBiaModal)

        assertThat(result?.isHeartRateOn).isTrue()
        assertThat(result?.scale).isNull()
    }
}
