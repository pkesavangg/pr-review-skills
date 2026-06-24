package com.dmdbrands.gurus.weight.features.home.reducer

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HomeReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class HomeReducerTest {

    private lateinit var reducer: HomeReducer

    @BeforeEach
    fun setUp() {
        reducer = HomeReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default HomeState has expected initial values`() {
        val state = HomeState()

        assertThat(state.showAppsync).isFalse()
        assertThat(state.isAppSyncPermissionsEnabled).isFalse()
        assertThat(state.showWeightOnlyModeBottomSheet).isFalse()
        assertThat(state.openWeightOnlyModePopup).isFalse()
        assertThat(state.isWeightOnlyModeDismissed).isFalse()
        assertThat(state.isBodyMetricsEnabled).isFalse()
        assertThat(state.showUnreadFeedIndicator).isFalse()
        assertThat(state.shouldAskForReview).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetShowAppsync
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowAppsync true sets showAppsync to true`() {
        val state = HomeState(showAppsync = false)

        val result = reducer.reduce(state, HomeIntent.SetShowAppsync(true))

        assertThat(result?.showAppsync).isTrue()
    }

    @Test
    fun `SetShowAppsync false sets showAppsync to false`() {
        val state = HomeState(showAppsync = true)

        val result = reducer.reduce(state, HomeIntent.SetShowAppsync(false))

        assertThat(result?.showAppsync).isFalse()
    }

    @Test
    fun `SetShowAppsync preserves all other fields`() {
        val state = HomeState(
            showAppsync = false,
            isAppSyncPermissionsEnabled = true,
            showUnreadFeedIndicator = true,
            shouldAskForReview = true,
        )

        val result = reducer.reduce(state, HomeIntent.SetShowAppsync(true))

        assertThat(result?.isAppSyncPermissionsEnabled).isTrue()
        assertThat(result?.showUnreadFeedIndicator).isTrue()
        assertThat(result?.shouldAskForReview).isTrue()
    }

    // -------------------------------------------------------------------------
    // isAppSyncPermissionsEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `isAppSyncPermissionsEnabled true sets flag to true`() {
        val state = HomeState(isAppSyncPermissionsEnabled = false)

        val result = reducer.reduce(state, HomeIntent.isAppSyncPermissionsEnabled(true))

        assertThat(result?.isAppSyncPermissionsEnabled).isTrue()
    }

    @Test
    fun `isAppSyncPermissionsEnabled false sets flag to false`() {
        val state = HomeState(isAppSyncPermissionsEnabled = true)

        val result = reducer.reduce(state, HomeIntent.isAppSyncPermissionsEnabled(false))

        assertThat(result?.isAppSyncPermissionsEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetShowWeightOnlyModeBottomSheet
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowWeightOnlyModeBottomSheet true sets showWeightOnlyModeBottomSheet to true`() {
        val state = HomeState(showWeightOnlyModeBottomSheet = false)

        val result = reducer.reduce(state, HomeIntent.SetShowWeightOnlyModeBottomSheet(true))

        assertThat(result?.showWeightOnlyModeBottomSheet).isTrue()
    }

    @Test
    fun `SetShowWeightOnlyModeBottomSheet false sets showWeightOnlyModeBottomSheet to false`() {
        val state = HomeState(showWeightOnlyModeBottomSheet = true)

        val result = reducer.reduce(state, HomeIntent.SetShowWeightOnlyModeBottomSheet(false))

        assertThat(result?.showWeightOnlyModeBottomSheet).isFalse()
    }

    // -------------------------------------------------------------------------
    // OpenWeightOnlyModePopup
    // -------------------------------------------------------------------------

    @Test
    fun `OpenWeightOnlyModePopup true sets openWeightOnlyModePopup to true`() {
        val state = HomeState(openWeightOnlyModePopup = false)

        val result = reducer.reduce(state, HomeIntent.OpenWeightOnlyModePopup(true))

        assertThat(result?.openWeightOnlyModePopup).isTrue()
    }

    @Test
    fun `OpenWeightOnlyModePopup false sets openWeightOnlyModePopup to false`() {
        val state = HomeState(openWeightOnlyModePopup = true)

        val result = reducer.reduce(state, HomeIntent.OpenWeightOnlyModePopup(false))

        assertThat(result?.openWeightOnlyModePopup).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetWeightOnlyModeDismissed
    // -------------------------------------------------------------------------

    @Test
    fun `SetWeightOnlyModeDismissed true sets isWeightOnlyModeDismissed to true`() {
        val state = HomeState(isWeightOnlyModeDismissed = false)

        val result = reducer.reduce(state, HomeIntent.SetWeightOnlyModeDismissed(true))

        assertThat(result?.isWeightOnlyModeDismissed).isTrue()
    }

    @Test
    fun `SetWeightOnlyModeDismissed false sets isWeightOnlyModeDismissed to false`() {
        val state = HomeState(isWeightOnlyModeDismissed = true)

        val result = reducer.reduce(state, HomeIntent.SetWeightOnlyModeDismissed(false))

        assertThat(result?.isWeightOnlyModeDismissed).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetShowUnreadFeedIndicator
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowUnreadFeedIndicator true sets showUnreadFeedIndicator to true`() {
        val state = HomeState(showUnreadFeedIndicator = false)

        val result = reducer.reduce(state, HomeIntent.SetShowUnreadFeedIndicator(true))

        assertThat(result?.showUnreadFeedIndicator).isTrue()
    }

    @Test
    fun `SetShowUnreadFeedIndicator false sets showUnreadFeedIndicator to false`() {
        val state = HomeState(showUnreadFeedIndicator = true)

        val result = reducer.reduce(state, HomeIntent.SetShowUnreadFeedIndicator(false))

        assertThat(result?.showUnreadFeedIndicator).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetShouldAskForReview
    // -------------------------------------------------------------------------

    @Test
    fun `SetShouldAskForReview true sets shouldAskForReview to true`() {
        val state = HomeState(shouldAskForReview = false)

        val result = reducer.reduce(state, HomeIntent.SetShouldAskForReview(true))

        assertThat(result?.shouldAskForReview).isTrue()
    }

    @Test
    fun `SetShouldAskForReview false sets shouldAskForReview to false`() {
        val state = HomeState(shouldAskForReview = true)

        val result = reducer.reduce(state, HomeIntent.SetShouldAskForReview(false))

        assertThat(result?.shouldAskForReview).isFalse()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state.copy()
    // -------------------------------------------------------------------------

    @Test
    fun `CheckAndRequestPermission returns state unchanged`() {
        val state = HomeState(showAppsync = true, shouldAskForReview = true)

        val result = reducer.reduce(state, HomeIntent.CheckAndRequestPermission(onResult = {}))

        assertThat(result?.showAppsync).isTrue()
        assertThat(result?.shouldAskForReview).isTrue()
    }

    @Test
    fun `OnWeightOnlyModeEnable returns state unchanged`() {
        val state = HomeState(isWeightOnlyModeDismissed = true)

        val result = reducer.reduce(state, HomeIntent.OnWeightOnlyModeEnable)

        assertThat(result?.isWeightOnlyModeDismissed).isTrue()
    }

    @Test
    fun `OnWeightOnlyModeAlertDismiss returns state unchanged`() {
        val state = HomeState(openWeightOnlyModePopup = true)

        val result = reducer.reduce(state, HomeIntent.OnWeightOnlyModeAlertDismiss)

        assertThat(result?.openWeightOnlyModePopup).isTrue()
    }

    @Test
    fun `HandleAppSyncResult returns state unchanged`() {
        val state = HomeState(showAppsync = true)

        val result = reducer.reduce(state, HomeIntent.HandleAppSyncResult(mockk(relaxed = true)))

        assertThat(result?.showAppsync).isTrue()
    }

    @Test
    fun `LaunchAppReview returns state unchanged`() {
        val state = HomeState(shouldAskForReview = true)

        val result = reducer.reduce(state, HomeIntent.LaunchAppReview(mockk(relaxed = true)))

        assertThat(result?.shouldAskForReview).isTrue()
    }
}
