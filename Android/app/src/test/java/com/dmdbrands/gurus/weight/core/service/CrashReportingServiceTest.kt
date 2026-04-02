package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.services.ICrashReportingService
import com.google.common.truth.Truth.assertThat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CrashReportingServiceTest {

    // --- Mocks ---
    private val crashlytics: FirebaseCrashlytics = mockk(relaxed = true)

    private lateinit var service: CrashReportingService

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics

        service = CrashReportingService()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // initialize
    // -------------------------------------------------------------------------

    @Test
    fun `initialize sets crashlytics collection enabled`() {
        service.initialize()

        verify { crashlytics.isCrashlyticsCollectionEnabled = any() }
    }

    @Test
    fun `initialize logs debug message`() {
        service.initialize()

        verify { AppLog.d("CrashReportingService", match { it.contains("Crashlytics initialized") }) }
    }

    // -------------------------------------------------------------------------
    // recordException — with tag
    // -------------------------------------------------------------------------

    @Test
    fun `recordException sets custom key when tag is provided`() {
        val throwable = RuntimeException("test error")

        service.recordException(throwable, "my_tag")

        verify { crashlytics.setCustomKey("error_tag", "my_tag") }
        verify { crashlytics.recordException(throwable) }
    }

    @Test
    fun `recordException does not set custom key when tag is null`() {
        val throwable = RuntimeException("test error")

        service.recordException(throwable, null)

        verify(exactly = 0) { crashlytics.setCustomKey("error_tag", any<String>()) }
        verify { crashlytics.recordException(throwable) }
    }

    @Test
    fun `recordException records the throwable`() {
        val throwable = IllegalArgumentException("bad arg")

        service.recordException(throwable)

        verify { crashlytics.recordException(throwable) }
    }

    // -------------------------------------------------------------------------
    // setCustomKey
    // -------------------------------------------------------------------------

    @Test
    fun `setCustomKey delegates to crashlytics`() {
        service.setCustomKey("user_id", "abc-123")

        verify { crashlytics.setCustomKey("user_id", "abc-123") }
    }

    // -------------------------------------------------------------------------
    // log
    // -------------------------------------------------------------------------

    @Test
    fun `log delegates to crashlytics`() {
        service.log("some message")

        verify { crashlytics.log("some message") }
    }

    // -------------------------------------------------------------------------
    // setCollectionEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `setCollectionEnabled true delegates to crashlytics`() {
        service.setCollectionEnabled(true)

        verify { crashlytics.isCrashlyticsCollectionEnabled = true }
    }

    @Test
    fun `setCollectionEnabled false delegates to crashlytics`() {
        service.setCollectionEnabled(false)

        verify { crashlytics.isCrashlyticsCollectionEnabled = false }
    }

    // -------------------------------------------------------------------------
    // interface conformance
    // -------------------------------------------------------------------------

    @Test
    fun `service implements ICrashReportingService`() {
        assertThat(service).isInstanceOf(ICrashReportingService::class.java)
    }
}
