package com.dmdbrands.gurus.weight.core.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AnalyticsService].
 *
 * Verifies that logEvent delegates to FirebaseAnalytics correctly.
 */
class AnalyticsServiceTest {

    private lateinit var analyticsService: AnalyticsService
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    companion object {
        private const val TEST_EVENT_NAME = "test_event"
        private const val TEST_EVENT_LOGIN = "login_success"
        private const val TEST_PARAM_KEY = "error_type"
        private const val TEST_PARAM_VALUE = "http_401"
    }

    @BeforeEach
    fun setUp() {
        firebaseAnalytics = mockk(relaxed = true)
        analyticsService = AnalyticsService(firebaseAnalytics)
    }

    // -------------------------------------------------------------------------
    // logEvent without params
    // -------------------------------------------------------------------------

    @Test
    fun `logEvent delegates to FirebaseAnalytics with event name`() {
        analyticsService.logEvent(TEST_EVENT_NAME)

        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_NAME, null) }
    }

    @Test
    fun `logEvent with null params passes null to FirebaseAnalytics`() {
        analyticsService.logEvent(TEST_EVENT_LOGIN, null)

        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_LOGIN, null) }
    }

    // -------------------------------------------------------------------------
    // logEvent with params
    // -------------------------------------------------------------------------

    @Test
    fun `logEvent with Bundle passes params to FirebaseAnalytics`() {
        val bundle: Bundle = mockk(relaxed = true)

        analyticsService.logEvent(TEST_EVENT_NAME, bundle)

        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_NAME, bundle) }
    }

    @Test
    fun `logEvent is called exactly once per invocation`() {
        analyticsService.logEvent(TEST_EVENT_NAME)
        analyticsService.logEvent(TEST_EVENT_LOGIN)

        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_NAME, null) }
        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_LOGIN, null) }
    }

    // -------------------------------------------------------------------------
    // Multiple events
    // -------------------------------------------------------------------------

    @Test
    fun `multiple logEvent calls each delegate independently`() {
        val bundle1: Bundle = mockk(relaxed = true)
        val bundle2: Bundle = mockk(relaxed = true)

        analyticsService.logEvent(TEST_EVENT_NAME, bundle1)
        analyticsService.logEvent(TEST_EVENT_LOGIN, bundle2)

        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_NAME, bundle1) }
        verify(exactly = 1) { firebaseAnalytics.logEvent(TEST_EVENT_LOGIN, bundle2) }
    }

    @Test
    fun `logEvent does not throw when FirebaseAnalytics is relaxed`() {
        // Verifies that the service gracefully handles the call
        analyticsService.logEvent(TEST_EVENT_NAME)
        analyticsService.logEvent(TEST_EVENT_LOGIN, null)

        verify(exactly = 2) { firebaseAnalytics.logEvent(any(), any()) }
    }
}
