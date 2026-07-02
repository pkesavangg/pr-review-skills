package com.greatergoods.notification

import android.service.notification.StatusBarNotification
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `activeNotification` flow-collection tests for [NotificationService] (API 26+).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceActiveNotificationTest : NotificationServiceTestBase() {

    @Test
    fun `activeNotification collects from handler and updates statusMap`() = runTest {
        val sbn: StatusBarNotification = mockk {
            every { tag } returns "active_tag"
            every { isOngoing } returns true
        }
        every { notificationHandler.activeNotifications(testChannelId) } returns flowOf(sbn)

        service.activeNotification(testChannelId)
        Thread.sleep(200)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["active_tag"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeNotification sets status false for non-ongoing notification`() = runTest {
        val sbn: StatusBarNotification = mockk {
            every { tag } returns "done_tag"
            every { isOngoing } returns false
        }
        every { notificationHandler.activeNotifications(testChannelId) } returns flowOf(sbn)

        service.activeNotification(testChannelId)
        Thread.sleep(200)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["done_tag"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeNotification with empty flow does not update statusMap`() = runTest {
        every { notificationHandler.activeNotifications(testChannelId) } returns emptyFlow()

        service.activeNotification(testChannelId)
        Thread.sleep(200)

        service.subscribeStatus().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activeNotification collects multiple notifications and updates all`() = runTest {
        val sbn1: StatusBarNotification = mockk {
            every { tag } returns "tag_1"
            every { isOngoing } returns true
        }
        val sbn2: StatusBarNotification = mockk {
            every { tag } returns "tag_2"
            every { isOngoing } returns false
        }
        every { notificationHandler.activeNotifications(testChannelId) } returns flowOf(sbn1, sbn2)

        service.activeNotification(testChannelId)
        Thread.sleep(200)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["tag_1"]).isTrue()
            assertThat(status["tag_2"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
