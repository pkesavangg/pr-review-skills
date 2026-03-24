package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeightOnlyModeEventServiceTest {

    // -------------------------------------------------------------------------
    // emit — SHOW_ALERT
    // -------------------------------------------------------------------------

    @Test
    fun `emit SHOW_ALERT delivers event to collector`() = runTest {
        WeightOnlyModeEventService.events.test {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.SHOW_ALERT)
            }
            val event = awaitItem()
            assertThat(event).isEqualTo(WeightOnlyModeEventType.SHOW_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emit — HIDE_ALERT
    // -------------------------------------------------------------------------

    @Test
    fun `emit HIDE_ALERT delivers event to collector`() = runTest {
        WeightOnlyModeEventService.events.test {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.HIDE_ALERT)
            }
            val event = awaitItem()
            assertThat(event).isEqualTo(WeightOnlyModeEventType.HIDE_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emit — ENABLE_BODY_METRICS
    // -------------------------------------------------------------------------

    @Test
    fun `emit ENABLE_BODY_METRICS delivers event to collector`() = runTest {
        WeightOnlyModeEventService.events.test {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.ENABLE_BODY_METRICS)
            }
            val event = awaitItem()
            assertThat(event).isEqualTo(WeightOnlyModeEventType.ENABLE_BODY_METRICS)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emit — DISMISS_ALERT
    // -------------------------------------------------------------------------

    @Test
    fun `emit DISMISS_ALERT delivers event to collector`() = runTest {
        WeightOnlyModeEventService.events.test {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.DISMISS_ALERT)
            }
            val event = awaitItem()
            assertThat(event).isEqualTo(WeightOnlyModeEventType.DISMISS_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emit — multiple events delivered in order
    // -------------------------------------------------------------------------

    @Test
    fun `multiple emitted events are delivered in order`() = runTest {
        WeightOnlyModeEventService.events.test {
            launch(UnconfinedTestDispatcher(testScheduler)) {
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.SHOW_ALERT)
                WeightOnlyModeEventService.emit(WeightOnlyModeEventType.DISMISS_ALERT)
            }
            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.SHOW_ALERT)
            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.DISMISS_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // WeightOnlyModeEventType enum values
    // -------------------------------------------------------------------------

    @Test
    fun `enum contains all expected values`() {
        val values = WeightOnlyModeEventType.entries
        assertThat(values).hasSize(4)
        assertThat(values).containsExactly(
            WeightOnlyModeEventType.SHOW_ALERT,
            WeightOnlyModeEventType.HIDE_ALERT,
            WeightOnlyModeEventType.ENABLE_BODY_METRICS,
            WeightOnlyModeEventType.DISMISS_ALERT,
        )
    }
}
