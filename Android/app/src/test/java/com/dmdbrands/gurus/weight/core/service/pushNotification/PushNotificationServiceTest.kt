package com.dmdbrands.gurus.weight.core.service.pushNotification

import android.app.PendingIntent
import android.content.Context
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.NotificationChannel
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.firebase.messaging.RemoteMessage
import com.greatergoods.notification.NotificationService
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushNotificationServiceTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val context: Context = mockk(relaxed = true)
  private val notificationService: NotificationService = mockk(relaxed = true)
  private val appRepository: IAppRepository = mockk()

  private lateinit var service: PushNotificationService

  @Before
  fun setUp() {
    mockkObject(AppLog)
    every { AppLog.v(any(), any<String>()) } just Runs
    every { AppLog.d(any(), any<String>()) } just Runs
    every { AppLog.e(any(), any<String>(), any<Throwable>()) } just Runs
    every { AppLog.e(any(), any<String>(), any<String>()) } just Runs
    every { AppLog.i(any(), any<String>()) } just Runs
    every { AppLog.w(any(), any<String>()) } just Runs

    mockkObject(AppNotificationEventService)
    coEvery { AppNotificationEventService.emit(any()) } just Runs

    mockkStatic(PendingIntent::class)
    every {
      PendingIntent.getActivity(any(), any(), any(), any())
    } returns mockk()

    service = PushNotificationService()
    service.context = context
    service.notificationService = notificationService
    service.appRepository = appRepository
  }

  @After
  fun tearDown() {
    unmockkStatic(PendingIntent::class)
    clearAllMocks()
  }

  // -------------------------------------------------------------------------
  // Shared Helpers
  // -------------------------------------------------------------------------

  private fun createMessage(
    msgId: String? = "msg-1",
    destination: String? = null,
    channelId: String? = null,
    title: String? = null,
    body: String? = null,
    hasNotification: Boolean = false,
  ): RemoteMessage {
    val notification = if (hasNotification) {
      mockk<RemoteMessage.Notification> {
        every { this@mockk.channelId } returns channelId
        every { this@mockk.title } returns title
        every { this@mockk.body } returns body
      }
    } else {
      null
    }
    return mockk {
      every { messageId } returns msgId
      every { data } returns if (destination != null) mapOf("destination" to destination) else emptyMap()
      every { getNotification() } returns notification
      every { this@mockk.notification } returns notification
    }
  }

  // -------------------------------------------------------------------------
  // onNewToken
  // -------------------------------------------------------------------------

  @Test
  fun `onNewToken updates token when current token differs`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "old-token"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("new-token")
    Thread.sleep(200)

    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 1) { appRepository.setFcmToken("new-token") }
  }

  @Test
  fun `onNewToken does not update when token is same`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "same-token"

    service.onNewToken("same-token")
    Thread.sleep(200)

    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 0) { appRepository.setFcmToken(any()) }
  }

  @Test
  fun `onNewToken handles getFcmToken exception gracefully`() = runTest {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("DataStore error")

    service.onNewToken("new-token")
    Thread.sleep(200)

    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 0) { appRepository.setFcmToken(any()) }
  }

  @Test
  fun `onNewToken handles setFcmToken exception gracefully`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "old-token"
    coEvery { appRepository.setFcmToken(any()) } throws RuntimeException("Write error")

    service.onNewToken("new-token")
    Thread.sleep(200)

    coVerify(exactly = 1) { appRepository.setFcmToken("new-token") }
  }

  @Test
  fun `onNewToken updates when current token is empty`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("brand-new-token")
    Thread.sleep(200)

    coVerify(exactly = 1) { appRepository.setFcmToken("brand-new-token") }
  }

  @Test
  fun `onNewToken logs the new token`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "old"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("abc-123")
    Thread.sleep(200)

    verify { AppLog.v("PushNotificationService", "New FCM token: abc-123") }
  }

  @Test
  fun `onNewToken logs success after token update`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "old"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("new")
    Thread.sleep(200)

    verify { AppLog.v("PushNotificationService", "FCM token updated: new") }
  }

  @Test
  fun `onNewToken logs error when getFcmToken throws`() = runTest {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("inner fail")

    service.onNewToken("token")
    Thread.sleep(200)

    verify {
      AppLog.e("PushNotificationService", "Failed to check/update FCM token", any<Throwable>())
    }
  }

  // -------------------------------------------------------------------------
  // onMessageReceived
  // -------------------------------------------------------------------------

  @Test
  fun `onMessageReceived shows notification with correct title and body`() {
    val message = createMessage(
      hasNotification = true,
      channelId = "test-channel",
      title = "Test Title",
      body = "Test Body",
      destination = "dashboard",
    )

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        "test-channel",
        "PUSH_TEST",
        "Test Title",
        "Test Body",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived uses default channel when channelId is null`() {
    val message = createMessage(hasNotification = true, title = "Title", body = "Body")

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        NotificationChannel.DEFAULT,
        "PUSH_TEST",
        "Title",
        "Body",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived uses default title when notification title is null`() {
    val message = createMessage(hasNotification = true, channelId = "ch-1", body = "Some Body")

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        "ch-1",
        "PUSH_TEST",
        "Default Title",
        "Some Body",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived uses default body when notification body is null`() {
    val message = createMessage(hasNotification = true, channelId = "ch-1", title = "Title")

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        "ch-1",
        "PUSH_TEST",
        "Title",
        "You have a new message",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived uses all defaults when notification is null`() {
    val message = createMessage(msgId = "msg-5")

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        NotificationChannel.DEFAULT,
        "PUSH_TEST",
        "Default Title",
        "You have a new message",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived emits NOTIFICATION_RECEIVED event`() {
    val message = createMessage(msgId = "msg-6")

    service.onMessageReceived(message)
    Thread.sleep(200)

    coVerify(exactly = 1) {
      AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
    }
  }

  @Test
  fun `onMessageReceived logs the message id`() {
    val message = createMessage(msgId = "test-msg-id-999")

    service.onMessageReceived(message)

    verify { AppLog.d("PushNotificationService", "Received message: test-msg-id-999") }
  }

  @Test
  fun `onMessageReceived with all notification fields populated`() {
    val message = createMessage(
      hasNotification = true,
      channelId = "custom-channel",
      title = "Custom Title",
      body = "Custom Body",
      destination = "settings",
    )

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showTextWithTapAction(
        "custom-channel",
        "PUSH_TEST",
        "Custom Title",
        "Custom Body",
        any(),
      )
    }
  }

  @Test
  fun `onMessageReceived with null messageId does not crash`() {
    val message = createMessage(msgId = null)

    service.onMessageReceived(message)

    verify { AppLog.d("PushNotificationService", "Received message: null") }
    verify(exactly = 1) {
      notificationService.showTextWithTapAction(any(), any(), any(), any(), any())
    }
  }

  @Test
  fun `onMessageReceived always uses PUSH_TEST as notification name`() {
    val message = createMessage()

    service.onMessageReceived(message)

    verify {
      notificationService.showTextWithTapAction(
        any(),
        eq("PUSH_TEST"),
        any(),
        any(),
        any(),
      )
    }
  }

  // -------------------------------------------------------------------------
  // AppNotificationEventService
  // -------------------------------------------------------------------------

  @Test
  fun `AppNotificationEventService emits events to flow`() = runTest {
    clearAllMocks()

    AppNotificationEventService.events.test {
      AppNotificationEventService.emit(NotificationEventType.NOTIFICATION_RECEIVED)
      assertThat(awaitItem()).isEqualTo(NotificationEventType.NOTIFICATION_RECEIVED)

      AppNotificationEventService.emit(NotificationEventType.TOKEN_UPDATED)
      assertThat(awaitItem()).isEqualTo(NotificationEventType.TOKEN_UPDATED)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `NotificationEventType has all expected values`() {
    val values = NotificationEventType.entries
    assertThat(values).hasSize(4)
    assertThat(values).containsExactly(
      NotificationEventType.NOTIFICATION_RECEIVED,
      NotificationEventType.NOTIFICATION_TAPPED,
      NotificationEventType.TOKEN_UPDATED,
      NotificationEventType.ERROR_OCCURRED,
    )
  }
}
