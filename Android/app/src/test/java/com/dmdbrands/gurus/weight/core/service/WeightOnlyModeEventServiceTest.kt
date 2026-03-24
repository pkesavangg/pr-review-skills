package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeightOnlyModeEventServiceTest {

    // -------------------------------------------------------------------------
    // emit — delivers all event types in order
    // -------------------------------------------------------------------------

    @Test
    fun `emit delivers all event types to collector in emission order`() = runTest {
        WeightOnlyModeEventService.events.test {
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.SHOW_ALERT)
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.ENABLE_BODY_METRICS)
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.DISMISS_ALERT)

            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.SHOW_ALERT)
            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.ENABLE_BODY_METRICS)
            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.DISMISS_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // events — SharedFlow has no initial value (no replay)
    // -------------------------------------------------------------------------

    @Test
    fun `events flow has no replay — late collector misses earlier emissions`() = runTest {
        // Emit before collecting — should not be received
        WeightOnlyModeEventService.emit(WeightOnlyModeEventType.SHOW_ALERT)

        WeightOnlyModeEventService.events.test {
            // Only a new emission after subscription should arrive
            WeightOnlyModeEventService.emit(WeightOnlyModeEventType.HIDE_ALERT)
            assertThat(awaitItem()).isEqualTo(WeightOnlyModeEventType.HIDE_ALERT)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
