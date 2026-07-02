package com.dmdbrands.gurus.weight.core.service.pushNotification

import android.app.PendingIntent
import android.content.Context
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.AppNotificationEventService
import com.dmdbrands.gurus.weight.core.service.NotificationEventType
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.NotificationChannel
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushNotificationServiceTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val context: Context = mockk(relaxed = true)
  private val notificationService: NotificationService = mockk(relaxed = true)
  private val appRepository: IAppRepository = mockk()
  private val accountService: IAccountService = mockk(relaxed = true)

  private lateinit var service: PushNotificationService

  @BeforeEach
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

    coEvery { accountService.getLoggedInAccounts() } returns emptyList()

    service = PushNotificationService()
    service.context = context
    service.notificationService = notificationService
    service.appRepository = appRepository
    service.accountService = accountService
    service.appScope = CoroutineScope(mainDispatcherRule.dispatcher)
  }

  private fun account(id: String, firstName: String): Account =
    mockk(relaxed = true) {
      every { this@mockk.id } returns id
      every { this@mockk.firstName } returns firstName
    }

  @AfterEach
  fun tearDown() {
    unmockkStatic(PendingIntent::class)
    clearAllMocks()
  }

  // -------------------------------------------------------------------------
  // Shared Helpers
  // -------------------------------------------------------------------------

  private fun createMessage(
    msgId: String? = "msg-1",
    data: Map<String, String> = emptyMap(),
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
      every { this@mockk.data } returns data
      every { getNotification() } returns notification
      every { this@mockk.notification } returns notification
    }
  }

  // -------------------------------------------------------------------------
  // onNewToken
  // -------------------------------------------------------------------------

  @Test
  fun `onNewToken updates token when current token differs`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "old-token"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("new-token")


    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 1) { appRepository.setFcmToken("new-token") }
  }

  @Test
  fun `onNewToken does not update when token is same`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "same-token"

    service.onNewToken("same-token")


    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 0) { appRepository.setFcmToken(any()) }
  }

  @Test
  fun `onNewToken handles getFcmToken exception gracefully`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("DataStore error")

    service.onNewToken("new-token")


    coVerify(exactly = 1) { appRepository.getFcmToken() }
    coVerify(exactly = 0) { appRepository.setFcmToken(any()) }
  }

  @Test
  fun `onNewToken handles setFcmToken exception gracefully`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "old-token"
    coEvery { appRepository.setFcmToken(any()) } throws RuntimeException("Write error")

    service.onNewToken("new-token")


    coVerify(exactly = 1) { appRepository.setFcmToken("new-token") }
  }

  @Test
  fun `onNewToken updates when current token is empty`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("brand-new-token")


    coVerify(exactly = 1) { appRepository.setFcmToken("brand-new-token") }
  }

  @Test
  fun `onNewToken logs the new token`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "old"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("abc-123")


    verify { AppLog.v("PushNotificationService", "New FCM token received") }
  }

  @Test
  fun `onNewToken logs success after token update`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "old"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("new")


    verify { AppLog.v("PushNotificationService", "FCM token updated") }
  }

  @Test
  fun `onNewToken logs error when getFcmToken throws`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("inner fail")

    service.onNewToken("token")


    verify {
      AppLog.e("PushNotificationService", "Failed to check/update FCM token", any<Throwable>())
    }
  }

  // -------------------------------------------------------------------------
  // onMessageReceived
  // -------------------------------------------------------------------------

  @Test
  fun `onMessageReceived shows branded notification with me_App title on entry channel`() {
    val message = createMessage(msgId = "msg-1")

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = NotificationChannel.ENTRY_NOTIFICATION,
        notificationName = "msg-1",
        textTitle = "me.App",
        textContent = any(),
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived builds body with measurement and resolved account name`() {
    coEvery { accountService.getLoggedInAccounts() } returns listOf(account("acc-1", "John"))
    val message = createMessage(
      data = mapOf("accountId" to "acc-1", "measurement" to "149.2 lb"),
    )

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = any(),
        textTitle = any(),
        textContent = "New entry of 149.2 lb has been synced to John's account",
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived caps long account names at 20 chars with ellipsis`() {
    coEvery { accountService.getLoggedInAccounts() } returns
      listOf(account("acc-1", "Maximilianabcdefghijklmnop"))
    val message = createMessage(data = mapOf("accountId" to "acc-1", "measurement" to "10 lb"))

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = any(),
        textTitle = any(),
        textContent = "New entry of 10 lb has been synced to Maximilianabcdefghij…'s account",
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived falls back to your account when accountId unknown`() {
    val message = createMessage(data = mapOf("measurement" to "55 kg"))

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = any(),
        textTitle = any(),
        textContent = "New entry of 55 kg has been synced to your account",
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived uses notification body fallback when no data present`() {
    val message = createMessage(
      hasNotification = true,
      title = "Weight Gurus",
      body = "New entry of 28.6 lb has been synced to your account",
    )

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = any(),
        textTitle = "me.App",
        textContent = "New entry of 28.6 lb has been synced to your account",
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived emits NOTIFICATION_RECEIVED event`() {
    val message = createMessage(msgId = "msg-6")

    service.onMessageReceived(message)

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
  fun `onMessageReceived falls back to account id as notification name when messageId null`() {
    val message = createMessage(msgId = null, data = mapOf("accountId" to "acc-9"))

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = "acc-9",
        textTitle = any(),
        textContent = any(),
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived builds per-entry-unique notification name from account month and measurement when messageId null`() {
    val message = createMessage(
      msgId = null,
      data = mapOf(
        "accountId" to "acc-9",
        "monthKey" to "2026-06",
        "measurement" to "149.2 lb",
      ),
    )

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = "acc-9:2026-06:149.2 lb",
        textTitle = any(),
        textContent = any(),
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived falls back to TAG notification name when messageId and data empty`() {
    val message = createMessage(msgId = null, data = emptyMap())

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = "PushNotificationService",
        textTitle = any(),
        textContent = any(),
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  @Test
  fun `onMessageReceived still shows notification when account lookup throws`() {
    coEvery { accountService.getLoggedInAccounts() } throws RuntimeException("db error")
    val message = createMessage(data = mapOf("accountId" to "acc-1", "measurement" to "12 lb"))

    service.onMessageReceived(message)

    verify(exactly = 1) {
      notificationService.showBrandedNotification(
        channelId = any(),
        notificationName = any(),
        textTitle = any(),
        textContent = "New entry of 12 lb has been synced to your account",
        smallIcon = any(),
        contentIntent = any(),
        groupKey = any(),
      )
    }
  }

  // -------------------------------------------------------------------------
  // AppNotificationEventService
  // -------------------------------------------------------------------------

  @Test
  fun `AppNotificationEventService emits events to flow`() = runTest(mainDispatcherRule.scheduler) {
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

  // -------------------------------------------------------------------------
  // updateFcmToken — additional edge cases via onNewToken
  // -------------------------------------------------------------------------

  @Test
  fun `onNewToken with blank current token updates to new token`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "   "
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("fresh-token")


    coVerify(exactly = 1) { appRepository.setFcmToken("fresh-token") }
  }

  @Test
  fun `onNewToken with empty new token still checks current token`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "existing"
    coEvery { appRepository.setFcmToken(any()) } just Runs

    service.onNewToken("")


    // "" != "existing", so setFcmToken is called
    coVerify(exactly = 1) { appRepository.setFcmToken("") }
  }
}
