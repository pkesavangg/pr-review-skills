package com.dmdbrands.gurus.weight.features.debugMenu.model

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DebugMenuReducer].
 *
 * The reducer is a pure function — no coroutines or mocking needed for the reducer itself.
 */
class DebugMenuReducerTest {

    private lateinit var reducer: DebugMenuReducer

    private fun makeState(
        isLoading: Boolean = false,
        hasScales: Boolean = false,
        isSendScaleLogEnabled: Boolean = false,
    ): DebugMenuState = DebugMenuState(
        isLoading = isLoading,
        hasScales = hasScales,
        isSendScaleLogEnabled = isSendScaleLogEnabled,
    )

    private fun makeDevice(
        id: String = "device-${ System.nanoTime() }",
        connectionStatus: BLEStatus = BLEStatus.DISCONNECTED,
        createdAt: String? = "1000",
    ): Device = Device(
        id = id,
        connectionStatus = connectionStatus,
        createdAt = createdAt,
    )

    @BeforeEach
    fun setUp() {
        reducer = DebugMenuReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default DebugMenuState has expected initial values`() {
        val state = makeState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.hasScales).isFalse()
        assertThat(state.isSendScaleLogEnabled).isFalse()
        assertThat(state.scaleList).isEmpty()
        assertThat(state.scaleListScaleInfo).isEmpty()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack sets isLoading to false`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, DebugMenuIntent.OnBack)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `OnBack when already not loading keeps isLoading false`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.OnBack)

        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SendLogs
    // -------------------------------------------------------------------------

    @Test
    fun `SendLogs sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.SendLogs)

        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `SendLogs when already loading keeps isLoading true`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, DebugMenuIntent.SendLogs)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // ResyncEntries
    // -------------------------------------------------------------------------

    @Test
    fun `ResyncEntries sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.ResyncEntries)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // ClearAllData
    // -------------------------------------------------------------------------

    @Test
    fun `ClearAllData sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.ClearAllData(onDismiss = {}))

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SendScaleLogs
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogs sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.SendScaleLogs)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SendScaleLogForScale
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogForScale sets isLoading to true`() {
        val state = makeState(isLoading = false)
        val device = makeDevice()

        val result = reducer.reduce(state, DebugMenuIntent.SendScaleLogForScale(device))

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // ShowAppReview
    // -------------------------------------------------------------------------

    @Test
    fun `ShowAppReview sets isLoading to true`() {
        val state = makeState(isLoading = false)

        val result = reducer.reduce(state, DebugMenuIntent.ShowAppReview)

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // ShowAppReviewWithActivity
    // -------------------------------------------------------------------------

    @Test
    fun `ShowAppReviewWithActivity sets isLoading to true`() {
        val state = makeState(isLoading = false)
        val activity = mockk<android.app.Activity>(relaxed = true)

        val result = reducer.reduce(state, DebugMenuIntent.ShowAppReviewWithActivity(activity))

        assertThat(result.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetScaleList — empty list
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleList with empty list sets hasScales to false and disables send log`() {
        val state = makeState(hasScales = true, isSendScaleLogEnabled = true)

        val result = reducer.reduce(state, DebugMenuIntent.SetScaleList(emptyList()))

        assertThat(result.hasScales).isFalse()
        assertThat(result.isSendScaleLogEnabled).isFalse()
        assertThat(result.scaleList).isEmpty()
        assertThat(result.scaleListScaleInfo).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetScaleList — single disconnected scale
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleList with single disconnected scale disables send log`() {
        val state = makeState()
        val device = makeDevice(id = "scale-1", connectionStatus = BLEStatus.DISCONNECTED)

        val result = reducer.reduce(state, DebugMenuIntent.SetScaleList(listOf(device)))

        assertThat(result.hasScales).isTrue()
        assertThat(result.isSendScaleLogEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScaleList — single connected scale
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleList with single connected scale enables send log`() {
        val state = makeState()
        val device = makeDevice(id = "scale-1", connectionStatus = BLEStatus.CONNECTED)

        val result = reducer.reduce(state, DebugMenuIntent.SetScaleList(listOf(device)))

        assertThat(result.hasScales).isTrue()
        assertThat(result.isSendScaleLogEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetScaleList — multiple scales
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleList with multiple scales enables send log regardless of connection`() {
        val state = makeState()
        val device1 = makeDevice(id = "scale-1", connectionStatus = BLEStatus.DISCONNECTED, createdAt = "2000")
        val device2 = makeDevice(id = "scale-2", connectionStatus = BLEStatus.DISCONNECTED, createdAt = "1000")

        val result = reducer.reduce(state, DebugMenuIntent.SetScaleList(listOf(device1, device2)))

        assertThat(result.hasScales).isTrue()
        assertThat(result.isSendScaleLogEnabled).isTrue()
        assertThat(result.scaleListScaleInfo).hasSize(2)
    }

    @Test
    fun `SetScaleList preserves other state fields`() {
        val state = makeState(isLoading = true)

        val result = reducer.reduce(state, DebugMenuIntent.SetScaleList(emptyList()))

        // isLoading is not modified by SetScaleList
        assertThat(result.isLoading).isTrue()
    }
}
