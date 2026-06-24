package com.greatergoods.notification

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.example.notification.NotificationHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Focused coverage for [NotificationService.showBrandedNotification] (MOB-434), kept in its
 * own class so the primary [NotificationServiceTest] stays within size limits.
 */
class NotificationServiceBrandedTest {

    private val notificationHandler: NotificationHandler = mockk(relaxed = true)
    private val service = NotificationService(notificationHandler)

    @Test
    fun `showBrandedNotification delegates to handler with branded defaults`() {
        val intent: PendingIntent = mockk(relaxed = true)

        service.showBrandedNotification(
            channelId = "entry",
            notificationName = "msg-1",
            textTitle = "me.App",
            textContent = "New entry of 28.6 lb has been synced to John's account",
            smallIcon = 42,
            contentIntent = intent,
            groupKey = "group-key",
        )

        verify(exactly = 1) {
            notificationHandler.showBrandedNotification(
                channelId = "entry",
                notificationName = "msg-1",
                textTitle = "me.App",
                textContent = "New entry of 28.6 lb has been synced to John's account",
                smallIcon = 42,
                contentIntent = intent,
                groupKey = "group-key",
                visibility = NotificationCompat.VISIBILITY_PRIVATE,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
        }
    }

    @Test
    fun `showBrandedNotification marks notification active in status map`() {
        service.showBrandedNotification(
            channelId = "entry",
            notificationName = "msg-2",
            textTitle = "me.App",
            textContent = "body",
            smallIcon = 1,
            contentIntent = mockk(relaxed = true),
            groupKey = "group-key",
        )

        assertThat(service.subscribeStatus().value["msg-2"]).isTrue()
    }

    @Test
    fun `showBrandedNotification honours explicit visibility and priority`() {
        val intent: PendingIntent = mockk(relaxed = true)

        service.showBrandedNotification(
            channelId = "entry",
            notificationName = "msg-3",
            textTitle = "me.App",
            textContent = "body",
            smallIcon = 7,
            contentIntent = intent,
            groupKey = "group-key",
            visibility = NotificationCompat.VISIBILITY_PUBLIC,
            priority = NotificationCompat.PRIORITY_HIGH,
        )

        verify(exactly = 1) {
            notificationHandler.showBrandedNotification(
                channelId = "entry",
                notificationName = "msg-3",
                textTitle = "me.App",
                textContent = "body",
                smallIcon = 7,
                contentIntent = intent,
                groupKey = "group-key",
                visibility = NotificationCompat.VISIBILITY_PUBLIC,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
        }
    }
}
