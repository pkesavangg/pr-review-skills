package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)

class AppNotificationEventServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    // -------------------------------------------------------------------------
    // emit — each event type is delivered to collectors
    // -------------------------------------------------------------------------

    @Test
    fun `emit delivers NOTIFICATION_RECEIVED to collector`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.NOTIFICATION_RECEIVED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit delivers NOTIFICATION_TAPPED to collector`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_TAPPED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.NOTIFICATION_TAPPED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit delivers TOKEN_UPDATED to collector`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.TOKEN_UPDATED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.TOKEN_UPDATED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit delivers ERROR_OCCURRED to collector`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.ERROR_OCCURRED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.ERROR_OCCURRED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emit — ordering guarantees
    // -------------------------------------------------------------------------

    @Test
    fun `emit delivers multiple events in order`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
            AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_TAPPED)
            AppNotificationEventService.emit(NotificationEventType.TOKEN_UPDATED)

            assertThat(awaitItem()).isEqualTo(NotificationEventType.NOTIFICATION_RECEIVED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.NOTIFICATION_TAPPED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.TOKEN_UPDATED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit delivers all enum values in sequence`() = runTest {
        val allEvents = NotificationEventType.entries.toList()

        AppNotificationEventService.events.test {
            allEvents.forEach { AppNotificationEventService.emit(it) }
            allEvents.forEach { expected ->
                assertThat(awaitItem()).isEqualTo(expected)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit delivers same event type multiple times in order`() = runTest {
        AppNotificationEventService.events.test {
            AppNotificationEventService.emit(NotificationEventType.ERROR_OCCURRED)
            AppNotificationEventService.emit(NotificationEventType.ERROR_OCCURRED)

            assertThat(awaitItem()).isEqualTo(NotificationEventType.ERROR_OCCURRED)
            assertThat(awaitItem()).isEqualTo(NotificationEventType.ERROR_OCCURRED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // events — flow identity and multi-subscriber behaviour
    // -------------------------------------------------------------------------

    @Test
    fun `events always returns the same flow instance`() {
        val first = AppNotificationEventService.events
        val second = AppNotificationEventService.events
        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun `two concurrent collectors both receive the same emitted event`() = runTest {
        val received1 = mutableListOf<NotificationEventType>()
        val received2 = mutableListOf<NotificationEventType>()

        val job1 = launch {
            AppNotificationEventService.events.collect { received1.add(it) }
        }
        val job2 = launch {
            AppNotificationEventService.events.collect { received2.add(it) }
        }

        // Let both collectors reach and register at the `collect` suspension point
        // before we emit, otherwise they may miss the event (SharedFlow, no replay)
        advanceUntilIdle()

        AppNotificationEventService.emit(NotificationEventType.TOKEN_UPDATED)

        advanceUntilIdle()

        job1.cancel()
        job2.cancel()

        assertThat(received1).containsExactly(NotificationEventType.TOKEN_UPDATED)
        assertThat(received2).containsExactly(NotificationEventType.TOKEN_UPDATED)
    }

    @Test
    fun `late subscriber does not receive previously emitted event`() = runTest {
        // Emit before any collector is attached
        AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)

        // Now subscribe — SharedFlow with no replay should deliver nothing
        AppNotificationEventService.events.test {
            // No item should be available immediately
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
