package com.greatergoods.notification

import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import app.cash.turbine.test
import com.example.notification.NotificationHandler
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.firebase.messaging.FirebaseMessaging
import com.greatergoods.notification.model.BuilderConfig
import com.greatergoods.notification.model.ChannelConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    // --- Mocks ---
    private val notificationHandler: NotificationHandler = mockk(relaxed = true)
    private val firebaseMessaging: FirebaseMessaging = mockk()

    private lateinit var service: NotificationService

    // --- Test fixtures ---
    private val testChannelId = "test_channel"
    private val testNotificationName = "test_notification"
    private val testTitle = "Test Title"
    private val testContent = "Test Content"
    private val channelConfig = ChannelConfig(
        id = testChannelId,
        name = "Test Channel",
        importance = 3,
        description = "Test channel description",
    )
    private val builderConfig = BuilderConfig(
        channelConfig = channelConfig,
        smallIcon = android.R.drawable.ic_notification_overlay,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns firebaseMessaging

        service = NotificationService(notificationHandler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // createInstance — channel initialization
    // -------------------------------------------------------------------------

    @Test
    fun `createInstance with list delegates to initializeChannels`() {
        val channels = listOf(builderConfig)

        service.createInstance(channels)

        verify(exactly = 1) { notificationHandler.initializeChannels(channels) }
    }

    @Test
    fun `createInstance with single channel delegates to initializeChannel`() {
        service.createInstance(builderConfig)

        verify(exactly = 1) { notificationHandler.initializeChannel(builderConfig) }
    }

    @Test
    fun `createInstance with multiple channels passes all to handler`() {
        val channel2 = BuilderConfig(
            channelConfig = ChannelConfig("ch2", "Channel 2", 2),
            smallIcon = android.R.drawable.ic_notification_overlay,
        )
        val channels = listOf(builderConfig, channel2)

        service.createInstance(channels)

        verify(exactly = 1) { notificationHandler.initializeChannels(channels) }
    }

    @Test
    fun `createInstance with empty list delegates to handler`() {
        val emptyList = emptyList<BuilderConfig>()

        service.createInstance(emptyList)

        verify(exactly = 1) { notificationHandler.initializeChannels(emptyList) }
    }

    // -------------------------------------------------------------------------
    // getBuilder — builder delegation
    // -------------------------------------------------------------------------

    @Test
    fun `getBuilder delegates to notificationHandler`() {
        val mockBuilder: NotificationCompat.Builder = mockk()
        every { notificationHandler.getBuilder(testChannelId) } returns mockBuilder

        val result = service.getBuilder(testChannelId)

        assertThat(result).isSameInstanceAs(mockBuilder)
        verify(exactly = 1) { notificationHandler.getBuilder(testChannelId) }
    }

    // -------------------------------------------------------------------------
    // showNotification — direct notification display
    // -------------------------------------------------------------------------

    @Test
    fun `showNotification delegates to notificationHandler`() {
        val notification: Notification = mockk()

        service.showNotification(42, notification)

        verify(exactly = 1) {
            notificationHandler.showNotification(notificationId = 42, notification = notification)
        }
    }

    // -------------------------------------------------------------------------
    // cancelGroupedNotification — grouped cancel delegation
    // -------------------------------------------------------------------------

    @Test
    fun `cancelGroupedNotification delegates to notificationHandler`() {
        service.cancelGroupedNotification(1, 100)

        verify(exactly = 1) { notificationHandler.cancelGroupedNotification(1, 100) }
    }

    // -------------------------------------------------------------------------
    // cancelNotification — cancel with status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // cancelAll — cancel all with status reset
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // checkNoActiveNotification — delegation check
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // update — statusMap management
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // subscribeStatus — StateFlow observation
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeStatus returns empty map initially`() = runTest {
        service.subscribeStatus().test {
            val status = awaitItem()
            assertThat(status).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // activeNotification — flow collection (API 26+)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showSimpleText — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showTextWithTapAction — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showTextWithButtons — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showLargeText — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showTextWithIcon — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // showTextWithThumbnail — delegation + status update
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // fetchFCMToken — Firebase token retrieval
    // -------------------------------------------------------------------------

    @Test
    fun `fetchFCMToken invokes onSuccess with token when task succeeds`() {
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns true
            every { result } returns "test_fcm_token_123"
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedToken: String? = null
        var capturedError: Exception? = null

        service.fetchFCMToken(
            onSuccess = { capturedToken = it },
            onError = { capturedError = it },
        )

        assertThat(capturedToken).isEqualTo("test_fcm_token_123")
        assertThat(capturedError).isNull()
    }

    @Test
    fun `fetchFCMToken invokes onError when task fails`() {
        val expectedException = RuntimeException("FCM token fetch failed")
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns false
            every { exception } returns expectedException
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedToken: String? = null
        var capturedError: Exception? = null

        service.fetchFCMToken(
            onSuccess = { capturedToken = it },
            onError = { capturedError = it },
        )

        assertThat(capturedToken).isNull()
        assertThat(capturedError).isEqualTo(expectedException)
    }

    @Test
    fun `fetchFCMToken invokes onError with null exception when task fails without exception`() {
        val listenerSlot = slot<OnCompleteListener<String>>()
        val mockTask: Task<String> = mockk {
            every { isSuccessful } returns false
            every { exception } returns null
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.token } returns mockTask

        var capturedError: Exception? = RuntimeException("should be replaced")

        service.fetchFCMToken(
            onSuccess = { },
            onError = { capturedError = it },
        )

        assertThat(capturedError).isNull()
    }

    // -------------------------------------------------------------------------
    // subscribeToTopic — Firebase topic subscription
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeToTopic invokes onSuccess when task succeeds`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns true
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("test_topic") } returns mockTask

        var successCalled = false
        var capturedError: Exception? = null

        service.subscribeToTopic(
            topic = "test_topic",
            onSuccess = { successCalled = true },
            onError = { capturedError = it },
        )

        assertThat(successCalled).isTrue()
        assertThat(capturedError).isNull()
    }

    @Test
    fun `subscribeToTopic invokes onError when task fails`() {
        val expectedException = RuntimeException("Subscribe failed")
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns false
            every { exception } returns expectedException
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("fail_topic") } returns mockTask

        var successCalled = false
        var capturedError: Exception? = null

        service.subscribeToTopic(
            topic = "fail_topic",
            onSuccess = { successCalled = true },
            onError = { capturedError = it },
        )

        assertThat(successCalled).isFalse()
        assertThat(capturedError).isEqualTo(expectedException)
    }

    @Test
    fun `subscribeToTopic invokes onError with null when task fails without exception`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns false
            every { exception } returns null
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("topic") } returns mockTask

        var capturedError: Exception? = RuntimeException("should be replaced")

        service.subscribeToTopic(
            topic = "topic",
            onSuccess = { },
            onError = { capturedError = it },
        )

        assertThat(capturedError).isNull()
    }

    // -------------------------------------------------------------------------
    // Integration — show* then cancel flow
    // -------------------------------------------------------------------------

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

    @Test
    fun `showNotification with zero id delegates correctly`() {
        val notification: Notification = mockk()

        service.showNotification(0, notification)

        verify(exactly = 1) {
            notificationHandler.showNotification(notificationId = 0, notification = notification)
        }
    }

    @Test
    fun `cancelGroupedNotification with different ids delegates correctly`() {
        service.cancelGroupedNotification(999, 888)

        verify(exactly = 1) { notificationHandler.cancelGroupedNotification(999, 888) }
    }

    @Test
    fun `getBuilder with different channel ids delegates correctly`() {
        val builder1: NotificationCompat.Builder = mockk()
        val builder2: NotificationCompat.Builder = mockk()
        every { notificationHandler.getBuilder("channel_a") } returns builder1
        every { notificationHandler.getBuilder("channel_b") } returns builder2

        assertThat(service.getBuilder("channel_a")).isSameInstanceAs(builder1)
        assertThat(service.getBuilder("channel_b")).isSameInstanceAs(builder2)
    }

    @Test
    fun `subscribeToTopic with different topics uses correct topic name`() {
        val listenerSlot = slot<OnCompleteListener<Void>>()
        val mockTask: Task<Void> = mockk {
            every { isSuccessful } returns true
            every { addOnCompleteListener(capture(listenerSlot)) } answers {
                listenerSlot.captured.onComplete(this@mockk)
                this@mockk
            }
        }
        every { firebaseMessaging.subscribeToTopic("special/topic") } returns mockTask

        var successCalled = false
        service.subscribeToTopic(
            topic = "special/topic",
            onSuccess = { successCalled = true },
            onError = { },
        )

        assertThat(successCalled).isTrue()
        verify { firebaseMessaging.subscribeToTopic("special/topic") }
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
