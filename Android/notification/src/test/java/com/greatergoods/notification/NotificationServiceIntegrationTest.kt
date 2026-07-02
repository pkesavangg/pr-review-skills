package com.greatergoods.notification

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * End-to-end `show*` then cancel/reset flow tests for [NotificationService].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceIntegrationTest : NotificationServiceTestBase() {

    @Test
    fun `show then cancel updates status correctly`() = runTest {
        service.showSimpleText(testChannelId, "notif_x", testTitle, testContent)
        service.subscribeStatus().test {
            assertThat(awaitItem()["notif_x"]).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        service.cancelNotification(1, "notif_x")
        service.subscribeStatus().test {
            assertThat(awaitItem()["notif_x"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple show then cancelAll resets all statuses`() = runTest {
        service.showSimpleText(testChannelId, "n1", testTitle, testContent)
        service.showLargeText(testChannelId, "n2", testTitle, testContent)

        service.cancelAll()

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["n1"]).isFalse()
            assertThat(status["n2"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `show different notification types updates unique keys`() = runTest {
        val pendingIntent: PendingIntent = mockk()
        val action: NotificationCompat.Action = mockk()

        service.showSimpleText(testChannelId, "simple", testTitle, testContent)
        service.showLargeText(testChannelId, "large", testTitle, testContent)
        service.showTextWithTapAction(testChannelId, "tap", testTitle, testContent, pendingIntent)
        service.showTextWithButtons(testChannelId, "buttons", testTitle, testContent, action)
        service.showTextWithIcon(testChannelId, "icon", testTitle, testContent, 1)
        service.showTextWithThumbnail(testChannelId, "thumb", testTitle, testContent, 2)

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status).hasSize(6)
            assertThat(status.values).containsExactly(true, true, true, true, true, true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelAll after mixed show and manual update resets everything`() = runTest {
        service.showSimpleText(testChannelId, "shown", testTitle, testContent)
        service.update("manual", true)

        service.cancelAll()

        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status["shown"]).isFalse()
            assertThat(status["manual"]).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
