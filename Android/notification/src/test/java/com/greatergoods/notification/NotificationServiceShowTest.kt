package com.greatergoods.notification

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `show*` helper tests for [NotificationService] — simple text, tap action,
 * buttons, large text, icon, and thumbnail — covering both delegation with the
 * default priority/status update and custom priority overloads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceShowTest : NotificationServiceTestBase() {

    @Test
    fun `showSimpleText delegates to handler and updates status to true`() = runTest {
        service.showSimpleText(testChannelId, testNotificationName, testTitle, testContent)

        verify(exactly = 1) {
            notificationHandler.showSimpleText(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showSimpleText uses custom priority`() {
        service.showSimpleText(
            testChannelId, testNotificationName, testTitle, testContent,
            priority = NotificationCompat.PRIORITY_HIGH,
        )

        verify(exactly = 1) {
            notificationHandler.showSimpleText(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
        }
    }

    @Test
    fun `showTextWithTapAction with custom priority delegates correctly`() {
        val pendingIntent: PendingIntent = mockk()

        service.showTextWithTapAction(
            testChannelId, testNotificationName, testTitle, testContent,
            pendingIntent, priority = NotificationCompat.PRIORITY_LOW,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithTapAction(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                contentIntent = pendingIntent,
                priority = NotificationCompat.PRIORITY_LOW,
            )
        }
    }

    @Test
    fun `showTextWithTapAction delegates to handler and updates status`() = runTest {
        val pendingIntent: PendingIntent = mockk()

        service.showTextWithTapAction(
            testChannelId, testNotificationName, testTitle, testContent, pendingIntent,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithTapAction(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                contentIntent = pendingIntent,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showTextWithButtons with custom priority delegates correctly`() {
        val action: NotificationCompat.Action = mockk()

        service.showTextWithButtons(
            testChannelId, testNotificationName, testTitle, testContent,
            action, priority = NotificationCompat.PRIORITY_HIGH,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithButtons(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                action = action,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
        }
    }

    @Test
    fun `showTextWithButtons delegates to handler and updates status`() = runTest {
        val action: NotificationCompat.Action = mockk()

        service.showTextWithButtons(
            testChannelId, testNotificationName, testTitle, testContent, action,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithButtons(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                action = action,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showLargeText with custom priority delegates correctly`() {
        service.showLargeText(
            testChannelId, testNotificationName, testTitle, testContent,
            priority = NotificationCompat.PRIORITY_MIN,
        )

        verify(exactly = 1) {
            notificationHandler.showLargeText(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                priority = NotificationCompat.PRIORITY_MIN,
            )
        }
    }

    @Test
    fun `showLargeText delegates to handler and updates status`() = runTest {
        service.showLargeText(testChannelId, testNotificationName, testTitle, testContent)

        verify(exactly = 1) {
            notificationHandler.showLargeText(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showTextWithIcon with custom priority delegates correctly`() {
        service.showTextWithIcon(
            testChannelId, testNotificationName, testTitle, testContent,
            icon = 789, priority = NotificationCompat.PRIORITY_MAX,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithIcon(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                icon = 789,
                priority = NotificationCompat.PRIORITY_MAX,
            )
        }
    }

    @Test
    fun `showTextWithIcon delegates to handler and updates status`() = runTest {
        service.showTextWithIcon(
            testChannelId, testNotificationName, testTitle, testContent, icon = 123,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithIcon(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                icon = 123,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showTextWithThumbnail with custom priority delegates correctly`() {
        service.showTextWithThumbnail(
            testChannelId, testNotificationName, testTitle, testContent,
            icon = 111, priority = NotificationCompat.PRIORITY_HIGH,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithThumbnail(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                icon = 111,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
        }
    }

    @Test
    fun `showTextWithThumbnail delegates to handler and updates status`() = runTest {
        service.showTextWithThumbnail(
            testChannelId, testNotificationName, testTitle, testContent, icon = 456,
        )

        verify(exactly = 1) {
            notificationHandler.showTextWithThumbnail(
                channelId = testChannelId,
                notificationName = testNotificationName,
                textTitle = testTitle,
                textContent = testContent,
                icon = 456,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
        service.subscribeStatus().test {
            assertThat(awaitItem()[testNotificationName]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
