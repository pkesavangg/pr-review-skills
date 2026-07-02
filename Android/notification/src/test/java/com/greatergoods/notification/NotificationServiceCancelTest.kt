package com.greatergoods.notification

import android.app.Notification
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Display, grouped cancel, cancel-with-status, and cancel-all tests for
 * [NotificationService].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceCancelTest : NotificationServiceTestBase() {

    @Test
    fun `showNotification delegates to notificationHandler`() {
        val notification: Notification = mockk()

        service.showNotification(42, notification)

        verify(exactly = 1) {
            notificationHandler.showNotification(notificationId = 42, notification = notification)
        }
    }

    @Test
    fun `showNotification with zero id delegates correctly`() {
        val notification: Notification = mockk()

        service.showNotification(0, notification)

        verify(exactly = 1) {
            notificationHandler.showNotification(notificationId = 0, notification = notification)
        }
    }

    @Test
    fun `cancelGroupedNotification delegates to notificationHandler`() {
        service.cancelGroupedNotification(1, 100)

        verify(exactly = 1) { notificationHandler.cancelGroupedNotification(1, 100) }
    }

    @Test
    fun `cancelGroupedNotification with different ids delegates correctly`() {
        service.cancelGroupedNotification(999, 888)

        verify(exactly = 1) { notificationHandler.cancelGroupedNotification(999, 888) }
    }

    @Test
    fun `cancelNotification delegates to handler and updates status when tag provided`() = runTest {
        service.update("my_tag", true)

        service.cancelNotification(10, "my_tag")

        verify(exactly = 1) { notificationHandler.cancelNotification(10, "my_tag") }
        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["my_tag"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelNotification with null tag does not update statusMap`() = runTest {
        service.update("existing", true)

        service.cancelNotification(10, null)

        verify(exactly = 1) { notificationHandler.cancelNotification(10, null) }
        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["existing"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelNotification uses default null tag when not provided`() {
        service.cancelNotification(99)

        verify(exactly = 1) { notificationHandler.cancelNotification(99, null) }
    }

    @Test
    fun `cancelNotification for unknown tag still delegates to handler`() {
        service.cancelNotification(55, "unknown_tag")

        verify(exactly = 1) { notificationHandler.cancelNotification(55, "unknown_tag") }
    }

    @Test
    fun `cancelAll delegates to handler and resets all statuses to false`() = runTest {
        service.update("notif_a", true)
        service.update("notif_b", true)

        service.cancelAll()

        verify(exactly = 1) { notificationHandler.cancelAll() }
        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["notif_a"]).isFalse()
            assertThat(status["notif_b"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelAll with empty statusMap does not crash`() {
        service.cancelAll()

        verify(exactly = 1) { notificationHandler.cancelAll() }
    }
}
