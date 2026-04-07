package com.dmdbrands.gurus.weight.features.integration.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HealthConnectReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class HealthConnectReducerTest {

    private lateinit var reducer: HealthConnectReducer

    @BeforeEach
    fun setUp() {
        reducer = HealthConnectReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default HealthConnectUiState has expected initial values`() {
        val state = HealthConnectUiState()

        assertThat(state.healthConnectSetupState).isEqualTo(HealthConnectSetup.NONE)
        assertThat(state.currentSlide).isEqualTo(0)
        assertThat(state.isHealthConnectAvailable).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.alertPresented).isFalse()
        assertThat(state.isHealthConnectOpened).isFalse()
        assertThat(state.isOutOfSync).isFalse()
    }

    // -------------------------------------------------------------------------
    // ConnectSuccess
    // -------------------------------------------------------------------------

    @Test
    fun `ConnectSuccess clears isLoading`() {
        val state = HealthConnectUiState(isLoading = true)

        val result = reducer.reduce(state, HealthConnectIntent.ConnectSuccess)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `ConnectSuccess clears errorMessage`() {
        val state = HealthConnectUiState(errorMessage = "previous error")

        val result = reducer.reduce(state, HealthConnectIntent.ConnectSuccess)

        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `ConnectSuccess preserves other fields`() {
        val state = HealthConnectUiState(
            currentSlide = 2,
            isHealthConnectAvailable = true,
            healthConnectSetupState = HealthConnectSetup.FINISH_CONNECT,
        )

        val result = reducer.reduce(state, HealthConnectIntent.ConnectSuccess)

        assertThat(result.currentSlide).isEqualTo(2)
        assertThat(result.isHealthConnectAvailable).isTrue()
        assertThat(result.healthConnectSetupState).isEqualTo(HealthConnectSetup.FINISH_CONNECT)
    }

    // -------------------------------------------------------------------------
    // ConnectError
    // -------------------------------------------------------------------------

    @Test
    fun `ConnectError clears isLoading`() {
        val state = HealthConnectUiState(isLoading = true)

        val result = reducer.reduce(state, HealthConnectIntent.ConnectError)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `ConnectError sets healthConnectSetupState to START_CONNECT`() {
        val state = HealthConnectUiState(healthConnectSetupState = HealthConnectSetup.NONE)

        val result = reducer.reduce(state, HealthConnectIntent.ConnectError)

        assertThat(result.healthConnectSetupState).isEqualTo(HealthConnectSetup.START_CONNECT)
    }

    @Test
    fun `ConnectError sets errorMessage`() {
        val state = HealthConnectUiState(errorMessage = null)

        val result = reducer.reduce(state, HealthConnectIntent.ConnectError)

        assertThat(result.errorMessage).isEqualTo("Failed to connect to Health Connect")
        assertThat(result.errorMessage).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // AppResumed
    // -------------------------------------------------------------------------

    @Test
    fun `AppResumed clears isHealthConnectOpened`() {
        val state = HealthConnectUiState(isHealthConnectOpened = true)

        val result = reducer.reduce(state, HealthConnectIntent.AppResumed)

        assertThat(result.isHealthConnectOpened).isFalse()
    }

    @Test
    fun `AppResumed preserves other fields`() {
        val state = HealthConnectUiState(
            isLoading = true,
            alertPresented = true,
            currentSlide = 1,
        )

        val result = reducer.reduce(state, HealthConnectIntent.AppResumed)

        assertThat(result.isLoading).isTrue()
        assertThat(result.alertPresented).isTrue()
        assertThat(result.currentSlide).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // SetAlertPresented / ClearAlertPresented
    // -------------------------------------------------------------------------

    @Test
    fun `SetAlertPresented sets alertPresented to true`() {
        val state = HealthConnectUiState(alertPresented = false)

        val result = reducer.reduce(state, HealthConnectIntent.SetAlertPresented)

        assertThat(result.alertPresented).isTrue()
    }

    @Test
    fun `ClearAlertPresented sets alertPresented to false`() {
        val state = HealthConnectUiState(alertPresented = true)

        val result = reducer.reduce(state, HealthConnectIntent.ClearAlertPresented)

        assertThat(result.alertPresented).isFalse()
    }

    @Test
    fun `SetAlertPresented preserves other fields`() {
        val state = HealthConnectUiState(isLoading = true, currentSlide = 3)

        val result = reducer.reduce(state, HealthConnectIntent.SetAlertPresented)

        assertThat(result.isLoading).isTrue()
        assertThat(result.currentSlide).isEqualTo(3)
    }

    // -------------------------------------------------------------------------
    // SetHealthConnectOpened / ClearHealthConnectOpened
    // -------------------------------------------------------------------------

    @Test
    fun `SetHealthConnectOpened sets isHealthConnectOpened to true`() {
        val state = HealthConnectUiState(isHealthConnectOpened = false)

        val result = reducer.reduce(state, HealthConnectIntent.SetHealthConnectOpened)

        assertThat(result.isHealthConnectOpened).isTrue()
    }

    @Test
    fun `ClearHealthConnectOpened sets isHealthConnectOpened to false`() {
        val state = HealthConnectUiState(isHealthConnectOpened = true)

        val result = reducer.reduce(state, HealthConnectIntent.ClearHealthConnectOpened)

        assertThat(result.isHealthConnectOpened).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateSlide
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateSlide sets currentSlide to specified value`() {
        val state = HealthConnectUiState(currentSlide = 0)

        val result = reducer.reduce(state, HealthConnectIntent.UpdateSlide(3))

        assertThat(result.currentSlide).isEqualTo(3)
    }

    @Test
    fun `UpdateSlide to zero resets currentSlide`() {
        val state = HealthConnectUiState(currentSlide = 5)

        val result = reducer.reduce(state, HealthConnectIntent.UpdateSlide(0))

        assertThat(result.currentSlide).isEqualTo(0)
    }

    @Test
    fun `UpdateSlide preserves other fields`() {
        val state = HealthConnectUiState(isLoading = true, alertPresented = true)

        val result = reducer.reduce(state, HealthConnectIntent.UpdateSlide(2))

        assertThat(result.isLoading).isTrue()
        assertThat(result.alertPresented).isTrue()
    }

    // -------------------------------------------------------------------------
    // ConfirmExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ConfirmExitSetup clears isLoading`() {
        val state = HealthConnectUiState(isLoading = true)

        val result = reducer.reduce(state, HealthConnectIntent.ConfirmExitSetup)

        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `ConfirmExitSetup clears errorMessage`() {
        val state = HealthConnectUiState(errorMessage = "some error")

        val result = reducer.reduce(state, HealthConnectIntent.ConfirmExitSetup)

        assertThat(result.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — CONNECT
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction CONNECT sets isLoading to true`() {
        val state = HealthConnectUiState(isLoading = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT),
        )

        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `PrimaryAction CONNECT clears errorMessage`() {
        val state = HealthConnectUiState(errorMessage = "previous error")

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT),
        )

        assertThat(result.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — FINISH
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction FINISH returns state unchanged`() {
        val state = HealthConnectUiState(currentSlide = 2, isLoading = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH),
        )

        assertThat(result.currentSlide).isEqualTo(2)
        assertThat(result.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — OPEN_HEALTH_CONNECT
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction OPEN_HEALTH_CONNECT sets isHealthConnectOpened to true`() {
        val state = HealthConnectUiState(isHealthConnectOpened = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT),
        )

        assertThat(result.isHealthConnectOpened).isTrue()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — UPDATE_PERMISSIONS
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction UPDATE_PERMISSIONS sets isLoading to true`() {
        val state = HealthConnectUiState(isLoading = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS),
        )

        assertThat(result.isLoading).isTrue()
    }

    @Test
    fun `PrimaryAction UPDATE_PERMISSIONS clears errorMessage`() {
        val state = HealthConnectUiState(errorMessage = "error")

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS),
        )

        assertThat(result.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — EXIT
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction EXIT sets alertPresented to true`() {
        val state = HealthConnectUiState(alertPresented = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.EXIT),
        )

        assertThat(result.alertPresented).isTrue()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction — unhandled actions (SKIP, REMOVE) return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction SKIP returns state unchanged`() {
        val state = HealthConnectUiState(currentSlide = 1, alertPresented = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.PrimaryAction(HealthConnectAction.SKIP),
        )

        assertThat(result.currentSlide).isEqualTo(1)
        assertThat(result.alertPresented).isFalse()
    }

    // -------------------------------------------------------------------------
    // SecondaryAction — SKIP
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction SKIP sets healthConnectSetupState to FINISH_INCOMPLETE_RECONNECTION`() {
        val state = HealthConnectUiState(healthConnectSetupState = HealthConnectSetup.NONE)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.SecondaryAction(HealthConnectAction.SKIP),
        )

        assertThat(result.healthConnectSetupState).isEqualTo(HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION)
    }

    // -------------------------------------------------------------------------
    // SecondaryAction — EXIT
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction EXIT sets alertPresented to true`() {
        val state = HealthConnectUiState(alertPresented = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.SecondaryAction(HealthConnectAction.EXIT),
        )

        assertThat(result.alertPresented).isTrue()
    }

    // -------------------------------------------------------------------------
    // SecondaryAction — OPEN_HEALTH_CONNECT
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction OPEN_HEALTH_CONNECT sets isHealthConnectOpened to true`() {
        val state = HealthConnectUiState(isHealthConnectOpened = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.SecondaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT),
        )

        assertThat(result.isHealthConnectOpened).isTrue()
    }

    // -------------------------------------------------------------------------
    // SecondaryAction — unhandled actions (CONNECT, FINISH, etc.) return state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction CONNECT returns state unchanged`() {
        val state = HealthConnectUiState(currentSlide = 2, isLoading = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.SecondaryAction(HealthConnectAction.CONNECT),
        )

        assertThat(result.currentSlide).isEqualTo(2)
        assertThat(result.isLoading).isFalse()
    }

    @Test
    fun `SecondaryAction FINISH returns state unchanged`() {
        val state = HealthConnectUiState(isLoading = true, alertPresented = false)

        val result = reducer.reduce(
            state,
            HealthConnectIntent.SecondaryAction(HealthConnectAction.FINISH),
        )

        assertThat(result.isLoading).isTrue()
        assertThat(result.alertPresented).isFalse()
    }
}
