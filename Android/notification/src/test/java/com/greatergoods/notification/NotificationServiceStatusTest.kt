package com.greatergoods.notification

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Status-map management and observation tests for [NotificationService]:
 * `checkNoActiveNotification`, `update`, and `subscribeStatus`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceStatusTest : NotificationServiceTestBase() {

    @Test
    fun `checkNoActiveNotification returns true when handler says no active`() {
        every { notificationHandler.checkNoActiveNotifications(5) } returns true

        val result = service.checkNoActiveNotification(5)

        assertThat(result).isTrue()
    }

    @Test
    fun `checkNoActiveNotification returns false when handler says active`() {
        every { notificationHandler.checkNoActiveNotifications(5) } returns false

        val result = service.checkNoActiveNotification(5)

        assertThat(result).isFalse()
    }

    @Test
    fun `update sets notification status to true`() = runTest {
        service.update("notif_1", true)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["notif_1"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update sets notification status to false`() = runTest {
        service.update("notif_1", true)
        service.update("notif_1", false)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["notif_1"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update manages multiple notification statuses independently`() = runTest {
        service.update("notif_a", true)
        service.update("notif_b", false)
        service.update("notif_c", true)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status).hasSize(3)
            assertThat(status["notif_a"]).isTrue()
            assertThat(status["notif_b"]).isFalse()
            assertThat(status["notif_c"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update overwrites previous status for same key`() = runTest {
        service.update("key", true)
        service.update("key", false)
        service.update("key", true)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["key"]).isTrue()
            assertThat(status).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update emits new immutable map each time`() = runTest {
        service.update("a", true)
        val first = service.subscribeStatus().value

        service.update("b", true)
        val second = service.subscribeStatus().value

        assertThat(first).isNotSameInstanceAs(second)
        assertThat(first).hasSize(1)
        assertThat(second).hasSize(2)
    }

    @Test
    fun `subscribeStatus returns empty map initially`() = runTest {
        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscribeStatus reflects status changes in real time`() = runTest {
        val flow = service.subscribeStatus()

        assertThat(flow.value).isEmpty()

        service.update("x", true)
        assertThat(flow.value["x"]).isTrue()

        service.update("x", false)
        assertThat(flow.value["x"]).isFalse()

        service.update("y", true)
        assertThat(flow.value).hasSize(2)
    }
}
