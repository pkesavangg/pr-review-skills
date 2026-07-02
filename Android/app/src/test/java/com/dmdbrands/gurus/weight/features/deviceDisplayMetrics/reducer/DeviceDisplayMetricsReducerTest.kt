package com.dmdbrands.gurus.weight.features.deviceDisplayMetrics.reducer

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceDisplayMetricsReducer].
 *
 * The reducer is a pure function — no coroutines or complex mocks needed.
 * Key logic under test:
 *  - [DeviceDisplayMetricsIntent.SetScale] populates enabledMetrics from device preferences
 *  - [DeviceDisplayMetricsIntent.UpdateMetrics] tracks whether changes differ from the saved metrics
 */
class DeviceDisplayMetricsReducerTest {

    private lateinit var reducer: DeviceDisplayMetricsReducer

    companion object {
        private val METRIC_WEIGHT = "weight"
        private val METRIC_BMI = "bmi"
        private val METRIC_BODY_FAT = "bodyFat"
        private val INITIAL_METRICS = listOf(METRIC_WEIGHT, METRIC_BMI)
    }

    private fun defaultState() = DeviceDisplayMetricsState()

    /** Builds a [Device] whose preferences contain the given displayMetrics list. */
    private fun deviceWithMetrics(metrics: List<String>): Device {
        val prefs = Preferences(displayMetrics = metrics)
        return Device(preferences = prefs)
    }

    /** Builds a [Device] whose preferences have no displayMetrics (null). */
    private fun deviceWithNoMetrics(): Device = Device(preferences = Preferences(displayMetrics = null))

    /** Builds a [Device] with no preferences at all. */
    private fun deviceWithNoPreferences(): Device = Device(preferences = null)

    @BeforeEach
    fun setUp() {
        reducer = DeviceDisplayMetricsReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default DeviceDisplayMetricsState has expected initial values`() {
        val state = defaultState()

        assertThat(state.scale).isNull()
        assertThat(state.enabledMetrics).isEmpty()
        assertThat(state.hasUpdated).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScale
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale stores device and populates enabledMetrics from preferences`() {
        val device = deviceWithMetrics(INITIAL_METRICS)

        val result = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))

        assertThat(result?.scale).isEqualTo(device)
        assertThat(result?.enabledMetrics).containsExactlyElementsIn(INITIAL_METRICS).inOrder()
    }

    @Test
    fun `SetScale with empty displayMetrics stores empty enabledMetrics`() {
        val device = deviceWithMetrics(emptyList())

        val result = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))

        assertThat(result?.enabledMetrics).isEmpty()
    }

    @Test
    fun `SetScale with null displayMetrics in preferences stores empty enabledMetrics`() {
        val device = deviceWithNoMetrics()

        val result = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))

        assertThat(result?.enabledMetrics).isEmpty()
    }

    @Test
    fun `SetScale with null preferences stores empty enabledMetrics`() {
        val device = deviceWithNoPreferences()

        val result = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))

        assertThat(result?.enabledMetrics).isEmpty()
    }

    @Test
    fun `SetScale resets hasUpdated to false`() {
        val state = defaultState().copy(hasUpdated = true)
        val device = deviceWithMetrics(INITIAL_METRICS)

        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.SetScale(device))

        assertThat(result?.hasUpdated).isFalse()
    }

    @Test
    fun `SetScale replaces previously loaded device`() {
        val deviceA = deviceWithMetrics(listOf(METRIC_WEIGHT))
        val deviceB = deviceWithMetrics(listOf(METRIC_BMI, METRIC_BODY_FAT))
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(deviceA))!!

        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.SetScale(deviceB))

        assertThat(result?.scale).isEqualTo(deviceB)
        assertThat(result?.enabledMetrics).containsExactly(METRIC_BMI, METRIC_BODY_FAT).inOrder()
    }

    // -------------------------------------------------------------------------
    // UpdateMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateMetrics stores new metrics list`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        val newMetrics = listOf(METRIC_WEIGHT, METRIC_BMI, METRIC_BODY_FAT)
        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.UpdateMetrics(newMetrics))

        assertThat(result?.enabledMetrics).containsExactlyElementsIn(newMetrics).inOrder()
    }

    @Test
    fun `UpdateMetrics sets hasUpdated to true when metrics differ from saved`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        // Change to a different metric selection
        val newMetrics = listOf(METRIC_BODY_FAT)
        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.UpdateMetrics(newMetrics))

        assertThat(result?.hasUpdated).isTrue()
    }

    @Test
    fun `UpdateMetrics sets hasUpdated to false when metrics match saved preferences`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        // Same list as in preferences → no change
        val result = reducer.reduce(
            state, DeviceDisplayMetricsIntent.UpdateMetrics(INITIAL_METRICS)
        )

        assertThat(result?.hasUpdated).isFalse()
    }

    @Test
    fun `UpdateMetrics with empty list when original was non-empty marks hasUpdated`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.UpdateMetrics(emptyList()))

        assertThat(result?.enabledMetrics).isEmpty()
        assertThat(result?.hasUpdated).isTrue()
    }

    @Test
    fun `UpdateMetrics when scale has null preferences treats original as empty`() {
        val device = deviceWithNoPreferences()
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        // New non-empty list → differs from empty original
        val result = reducer.reduce(
            state, DeviceDisplayMetricsIntent.UpdateMetrics(listOf(METRIC_WEIGHT))
        )

        assertThat(result?.hasUpdated).isTrue()
    }

    @Test
    fun `UpdateMetrics preserves the loaded scale reference`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        val result = reducer.reduce(
            state, DeviceDisplayMetricsIntent.UpdateMetrics(listOf(METRIC_BODY_FAT))
        )

        assertThat(result?.scale).isEqualTo(device)
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    @Test
    fun `Save sets hasUpdated to false`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!
        val modifiedState = reducer.reduce(
            state, DeviceDisplayMetricsIntent.UpdateMetrics(listOf(METRIC_BODY_FAT))
        )!!
        assertThat(modifiedState.hasUpdated).isTrue()

        val result = reducer.reduce(modifiedState, DeviceDisplayMetricsIntent.Save)

        assertThat(result?.hasUpdated).isFalse()
    }

    @Test
    fun `Save preserves enabledMetrics`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!
        val modifiedState = reducer.reduce(
            state, DeviceDisplayMetricsIntent.UpdateMetrics(listOf(METRIC_BODY_FAT))
        )!!

        val result = reducer.reduce(modifiedState, DeviceDisplayMetricsIntent.Save)

        assertThat(result?.enabledMetrics).containsExactly(METRIC_BODY_FAT)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents (Back, UpdateScaleMode) — return state.copy()
    // -------------------------------------------------------------------------

    @Test
    fun `Back returns state unchanged`() {
        val device = deviceWithMetrics(INITIAL_METRICS)
        val state = reducer.reduce(defaultState(), DeviceDisplayMetricsIntent.SetScale(device))!!

        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.Back)

        assertThat(result?.scale).isEqualTo(device)
        assertThat(result?.enabledMetrics).containsExactlyElementsIn(INITIAL_METRICS)
    }

    @Test
    fun `UpdateScaleMode returns state unchanged`() {
        val state = defaultState().copy(hasUpdated = true)

        val result = reducer.reduce(state, DeviceDisplayMetricsIntent.UpdateScaleMode)

        assertThat(result?.hasUpdated).isTrue()
    }
}
