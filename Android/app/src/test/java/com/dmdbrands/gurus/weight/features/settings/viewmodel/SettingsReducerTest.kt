package com.dmdbrands.gurus.weight.features.settings.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * This serves as the project's primary smoke test verifying the test
 * infrastructure (Truth, JUnit 4) is wired up correctly.
 */
class SettingsReducerTest {

    private lateinit var reducer: SettingsReducer

    @Before
    fun setUp() {
        reducer = SettingsReducer()
    }

    @Test
    fun `SetError sets errorMessage and clears isLoading`() {
        val state = SettingsState(isLoading = true, errorMessage = null)

        val result = reducer.reduce(state, SettingsIntent.SetError("Network failure"))

        assertThat(result?.errorMessage).isEqualTo("Network failure")
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `ClearError nullifies errorMessage while preserving other fields`() {
        val state = SettingsState(errorMessage = "Some error", isLoading = false)

        val result = reducer.reduce(state, SettingsIntent.ClearError)

        assertThat(result?.errorMessage).isNull()
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `LoadSettings sets isLoading to true`() {
        val state = SettingsState(isLoading = false)

        val result = reducer.reduce(state, SettingsIntent.LoadSettings)

        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `UpdateThemeMode updates currentThemeMode`() {
        val state = SettingsState()

        val result = reducer.reduce(state, SettingsIntent.UpdateThemeMode("Dark"))

        assertThat(result?.currentThemeMode).isEqualTo("Dark")
    }

    @Test
    fun `UpdateSelectedMacAddress updates selectedMacAddress`() {
        val state = SettingsState()

        val result = reducer.reduce(state, SettingsIntent.UpdateSelectedMacAddress("AA:BB:CC:DD:EE:FF"))

        assertThat(result?.selectedMacAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `UpdateTestingFeatures toggles enableTestingFeatures`() {
        val state = SettingsState(enableTestingFeatures = false)

        val result = reducer.reduce(state, SettingsIntent.UpdateTestingFeatures(enabled = true))

        assertThat(result?.enableTestingFeatures).isTrue()
    }

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() {
        val state = SettingsState(unreadFeedCount = 0)

        val result = reducer.reduce(state, SettingsIntent.SetUnreadFeedCount(count = 5))

        assertThat(result?.unreadFeedCount).isEqualTo(5)
    }

    @Test
    fun `SetShowUnreadFeedIndication updates showUnreadFeedIndication`() {
        val state = SettingsState(showUnreadFeedIndication = false)

        val result = reducer.reduce(state, SettingsIntent.SetShowUnreadFeedIndication(show = true))

        assertThat(result?.showUnreadFeedIndication).isTrue()
    }

    @Test
    fun `SetExportEnabled updates isExportEnabled`() {
        val state = SettingsState(isExportEnabled = false)

        val result = reducer.reduce(state, SettingsIntent.SetExportEnabled(enabled = true))

        assertThat(result?.isExportEnabled).isTrue()
    }

    @Test
    fun `navigation-only intents return null — state is unchanged by reducer`() {
        val state = SettingsState()

        // Navigation/side-effect intents must not alter state in the reducer
        assertThat(reducer.reduce(state, SettingsIntent.Logout)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.OpenAddScales)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.SwitchAccount)).isNull()
        assertThat(reducer.reduce(state, SettingsIntent.OpenPrivacyPolicy)).isNull()
    }

    @Test
    fun `default SettingsState has expected initial values`() {
        val state = SettingsState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.account).isNull()
        assertThat(state.hasMultipleAccounts).isFalse()
        assertThat(state.currentThemeMode).isEqualTo("System Settings")
        assertThat(state.selectedMacAddress).isEqualTo("All")
        assertThat(state.enableTestingFeatures).isFalse()
        assertThat(state.unreadFeedCount).isEqualTo(0)
        assertThat(state.showUnreadFeedIndication).isFalse()
        assertThat(state.isExportEnabled).isFalse()
    }

    @Test
    fun `currentNotificationStatus is Off when account is null`() {
        val state = SettingsState(account = null)

        assertThat(state.currentNotificationStatus).isEqualTo("Off")
    }
}
