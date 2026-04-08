package com.dmdbrands.gurus.weight.app.viewmodel

import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class AppReducerTest {

    private lateinit var reducer: AppReducer

    companion object {
        private const val TEST_SKU = "1234"
        private const val TEST_FCM_TOKEN = "fcm-token-abc"
        private const val TEST_UNREAD_COUNT = 7
    }

    @BeforeEach
    fun setUp() {
        reducer = AppReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default AppState has expected initial values`() {
        val state = AppState()

        assertThat(state.fcmToken).isEmpty()
        assertThat(state.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(state.isScaleDiscovered).isFalse()
        assertThat(state.hasScanStarted).isFalse()
        assertThat(state.sku).isEqualTo("0412")
        assertThat(state.unreadFeedCount).isEqualTo(0)
        assertThat(state.showUnreadFeedIndication).isFalse()
        assertThat(state.scaleDiscoveredTimestamp).isNull()
    }

    // -------------------------------------------------------------------------
    // SetSku
    // -------------------------------------------------------------------------

    @Test
    fun `SetSku updates sku field`() {
        val state = AppState()

        val result = reducer.reduce(state, AppIntent.SetSku(TEST_SKU))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `SetSku preserves all other fields`() {
        val state = AppState(fcmToken = TEST_FCM_TOKEN, hasScanStarted = true, unreadFeedCount = 3)

        val result = reducer.reduce(state, AppIntent.SetSku(TEST_SKU))

        assertThat(result?.fcmToken).isEqualTo(TEST_FCM_TOKEN)
        assertThat(result?.hasScanStarted).isTrue()
        assertThat(result?.unreadFeedCount).isEqualTo(3)
    }

    // -------------------------------------------------------------------------
    // SetScaleDiscovered
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleDiscovered true sets isScaleDiscovered to true and records timestamp`() {
        val state = AppState(isScaleDiscovered = false, scaleDiscoveredTimestamp = null)

        val result = reducer.reduce(state, AppIntent.SetScaleDiscovered(true))

        assertThat(result?.isScaleDiscovered).isTrue()
        assertThat(result?.scaleDiscoveredTimestamp).isNotNull()
    }

    @Test
    fun `SetScaleDiscovered false sets isScaleDiscovered to false and clears timestamp`() {
        val state = AppState(isScaleDiscovered = true, scaleDiscoveredTimestamp = 1700000000L)

        val result = reducer.reduce(state, AppIntent.SetScaleDiscovered(false))

        assertThat(result?.isScaleDiscovered).isFalse()
        assertThat(result?.scaleDiscoveredTimestamp).isNull()
    }

    @Test
    fun `SetScaleDiscovered true preserves other fields`() {
        val state = AppState(sku = TEST_SKU, hasScanStarted = true, unreadFeedCount = 5)

        val result = reducer.reduce(state, AppIntent.SetScaleDiscovered(true))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
        assertThat(result?.hasScanStarted).isTrue()
        assertThat(result?.unreadFeedCount).isEqualTo(5)
    }

    // -------------------------------------------------------------------------
    // SetScanStatus
    // -------------------------------------------------------------------------

    @Test
    fun `SetScanStatus true sets hasScanStarted to true`() {
        val state = AppState(hasScanStarted = false)

        val result = reducer.reduce(state, AppIntent.SetScanStatus(true))

        assertThat(result?.hasScanStarted).isTrue()
    }

    @Test
    fun `SetScanStatus false sets hasScanStarted to false`() {
        val state = AppState(hasScanStarted = true)

        val result = reducer.reduce(state, AppIntent.SetScanStatus(false))

        assertThat(result?.hasScanStarted).isFalse()
    }

    @Test
    fun `SetScanStatus preserves other fields`() {
        val state = AppState(sku = TEST_SKU, isScaleDiscovered = true, unreadFeedCount = 2)

        val result = reducer.reduce(state, AppIntent.SetScanStatus(true))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
        assertThat(result?.isScaleDiscovered).isTrue()
        assertThat(result?.unreadFeedCount).isEqualTo(2)
    }

    // -------------------------------------------------------------------------
    // SetUnreadFeedCount
    // -------------------------------------------------------------------------

    @Test
    fun `SetUnreadFeedCount updates unreadFeedCount`() {
        val state = AppState(unreadFeedCount = 0)

        val result = reducer.reduce(state, AppIntent.SetUnreadFeedCount(TEST_UNREAD_COUNT))

        assertThat(result?.unreadFeedCount).isEqualTo(TEST_UNREAD_COUNT)
    }

    @Test
    fun `SetUnreadFeedCount to zero clears count`() {
        val state = AppState(unreadFeedCount = 10)

        val result = reducer.reduce(state, AppIntent.SetUnreadFeedCount(0))

        assertThat(result?.unreadFeedCount).isEqualTo(0)
    }

    @Test
    fun `SetUnreadFeedCount preserves other fields`() {
        val state = AppState(sku = TEST_SKU, hasScanStarted = true)

        val result = reducer.reduce(state, AppIntent.SetUnreadFeedCount(TEST_UNREAD_COUNT))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
        assertThat(result?.hasScanStarted).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetShowUnreadFeedIndication
    // -------------------------------------------------------------------------

    @Test
    fun `SetShowUnreadFeedIndication true sets showUnreadFeedIndication to true`() {
        val state = AppState(showUnreadFeedIndication = false)

        val result = reducer.reduce(state, AppIntent.SetShowUnreadFeedIndication(true))

        assertThat(result?.showUnreadFeedIndication).isTrue()
    }

    @Test
    fun `SetShowUnreadFeedIndication false sets showUnreadFeedIndication to false`() {
        val state = AppState(showUnreadFeedIndication = true)

        val result = reducer.reduce(state, AppIntent.SetShowUnreadFeedIndication(false))

        assertThat(result?.showUnreadFeedIndication).isFalse()
    }

    @Test
    fun `SetShowUnreadFeedIndication preserves other fields`() {
        val state = AppState(sku = TEST_SKU, unreadFeedCount = 3)

        val result = reducer.reduce(state, AppIntent.SetShowUnreadFeedIndication(true))

        assertThat(result?.sku).isEqualTo(TEST_SKU)
        assertThat(result?.unreadFeedCount).isEqualTo(3)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — else branch returns state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `OnPopUpConnect returns state unchanged`() {
        val state = AppState(sku = TEST_SKU, unreadFeedCount = 5, isScaleDiscovered = true)

        val result = reducer.reduce(state, AppIntent.OnPopUpConnect)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `OnPopUpDismiss returns state unchanged`() {
        val state = AppState(sku = TEST_SKU, hasScanStarted = true, showUnreadFeedIndication = true)

        val result = reducer.reduce(state, AppIntent.OnPopUpDismiss)

        assertThat(result).isEqualTo(state)
    }
}
