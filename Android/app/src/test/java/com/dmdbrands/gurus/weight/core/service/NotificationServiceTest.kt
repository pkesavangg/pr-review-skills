package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.INotificationRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val notificationRepository: INotificationRepository = mockk()
  private val connectivityObserver: IConnectivityObserver = mockk()
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

  private lateinit var service: NotificationService

  // --- Test fixtures ---
  private val onlineState = NetworkState(available = true, unAvailable = false)
  private val offlineState = NetworkState(available = false, unAvailable = true)

  private val testRequest = NotificationSettingsRequest(
    shouldSendEntryNotifications = true,
    shouldSendWeightInEntryNotifications = false,
  )

  private val fakeActiveAccount: Account = mockk {
    every { id } returns "acc-123"
  }

  private val fakeUpdatedAccount: Account = mockk {
    every { id } returns "acc-123"
  }

  private fun createAccountResponse(
    shouldSendEntry: Boolean = true,
    shouldSendWeightInEntry: Boolean = false,
  ): AccountResponse = AccountResponse(
    accessToken = "token",
    refreshToken = "refresh",
    expiresAt = "2099-01-01",
    account = AccountInfo(
      id = "acc-123",
      email = "test@test.com",
      firstName = "Test",
      lastName = "User",
      gender = "M",
      zipcode = "12345",
      weightUnit = "lb",
      isWeightlessOn = false,
      height = 72,
      activityLevel = "moderate",
      dob = "1990-01-01",
      weightlessTimestamp = null,
      weightlessWeight = null,
      isStreakOn = false,
      dashboardType = "standard",
      dashboardMetrics = emptyList(),
      goalWeight = null,
      initialWeight = null,
      shouldSendEntryNotifications = shouldSendEntry,
      shouldSendWeightInEntryNotifications = shouldSendWeightInEntry,
    ),
  )

  @BeforeEach
  fun setUp() {
    mockkObject(AppLog)
    every { AppLog.d(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<Throwable>()) } just Runs
    every { AppLog.w(any(), any(), any<String>()) } just Runs
    every { AppLog.i(any(), any(), any<String>()) } just Runs

    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    coEvery { notificationRepository.getActiveAccountFromDB() } returns fakeActiveAccount

    service = NotificationService(
      notificationRepository = notificationRepository,
      connectivityObserver = connectivityObserver,
      dialogQueueService = dialogQueueService,
      appNavigationService = appNavigationService,
    )
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  // -------------------------------------------------------------------------
  // updateNotificationSettings — online happy path
  // -------------------------------------------------------------------------

  @Test
  fun `updateNotificationSettings online returns updated account from DB`() = runTest {
    val response = createAccountResponse()
    coEvery { notificationRepository.updateNotificationSettingsInAPI(testRequest) } returns response
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isEqualTo(fakeUpdatedAccount)
  }

  @Test
  fun `updateNotificationSettings online calls API then saves to DB with isSynced true`() = runTest {
    val response = createAccountResponse()
    coEvery { notificationRepository.updateNotificationSettingsInAPI(testRequest) } returns response
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(testRequest)

    coVerify(exactly = 1) { notificationRepository.updateNotificationSettingsInAPI(testRequest) }
    val entitySlot = slot<NotificationSettingsEntity>()
    coVerify { notificationRepository.updateNotificationSettingsInDB("acc-123", capture(entitySlot)) }
    assertThat(entitySlot.captured.isSynced).isTrue()
    assertThat(entitySlot.captured.accountId).isEqualTo("acc-123")
    assertThat(entitySlot.captured.shouldSendEntryNotifications).isTrue()
    assertThat(entitySlot.captured.shouldSendWeightInEntryNotifications).isFalse()
  }

  @Test
  fun `updateNotificationSettings online uses response account values for entity`() = runTest {
    val response = createAccountResponse(
      shouldSendEntry = false,
      shouldSendWeightInEntry = true,
    )
    coEvery { notificationRepository.updateNotificationSettingsInAPI(testRequest) } returns response
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(testRequest)

    val entitySlot = slot<NotificationSettingsEntity>()
    coVerify { notificationRepository.updateNotificationSettingsInDB("acc-123", capture(entitySlot)) }
    assertThat(entitySlot.captured.shouldSendEntryNotifications).isFalse()
    assertThat(entitySlot.captured.shouldSendWeightInEntryNotifications).isTrue()
  }

  // -------------------------------------------------------------------------
  // updateNotificationSettings — offline path
  // -------------------------------------------------------------------------

  @Test
  fun `updateNotificationSettings offline saves to DB with isSynced false`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isEqualTo(fakeUpdatedAccount)
    val entitySlot = slot<NotificationSettingsEntity>()
    coVerify { notificationRepository.updateNotificationSettingsInDB("acc-123", capture(entitySlot)) }
    assertThat(entitySlot.captured.isSynced).isFalse()
    assertThat(entitySlot.captured.accountId).isEqualTo("acc-123")
    assertThat(entitySlot.captured.shouldSendEntryNotifications).isTrue()
    assertThat(entitySlot.captured.shouldSendWeightInEntryNotifications).isFalse()
  }

  @Test
  fun `updateNotificationSettings offline does not call API`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(testRequest)

    coVerify(exactly = 0) { notificationRepository.updateNotificationSettingsInAPI(any()) }
  }

  @Test
  fun `updateNotificationSettings offline uses request values for entity`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    val request = NotificationSettingsRequest(
      shouldSendEntryNotifications = false,
      shouldSendWeightInEntryNotifications = true,
    )
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(request)

    val entitySlot = slot<NotificationSettingsEntity>()
    coVerify { notificationRepository.updateNotificationSettingsInDB("acc-123", capture(entitySlot)) }
    assertThat(entitySlot.captured.shouldSendEntryNotifications).isFalse()
    assertThat(entitySlot.captured.shouldSendWeightInEntryNotifications).isTrue()
  }

  // -------------------------------------------------------------------------
  // updateNotificationSettings — error handling
  // -------------------------------------------------------------------------

  @Test
  fun `updateNotificationSettings returns null when no active account`() = runTest {
    coEvery { notificationRepository.getActiveAccountFromDB() } returns null

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isNull()
  }

  @Test
  fun `updateNotificationSettings returns null when API call throws`() = runTest {
    coEvery { notificationRepository.updateNotificationSettingsInAPI(any()) } throws RuntimeException("API error")

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isNull()
  }

  @Test
  fun `updateNotificationSettings returns null when DB update throws online`() = runTest {
    val response = createAccountResponse()
    coEvery { notificationRepository.updateNotificationSettingsInAPI(testRequest) } returns response
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } throws RuntimeException("DB error")

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isNull()
  }

  @Test
  fun `updateNotificationSettings returns null when DB update throws offline`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } throws RuntimeException("DB error")

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isNull()
  }

  @Test
  fun `updateNotificationSettings logs error on exception`() = runTest {
    coEvery { notificationRepository.getActiveAccountFromDB() } returns null

    service.updateNotificationSettings(testRequest)

    // The IllegalStateException("No active account found") is caught and logged
    io.mockk.verify { AppLog.e("NotificationService", "Notification settings update failed", any<Throwable>()) }
  }

  @Test
  fun `updateNotificationSettings returns null when getActiveAccountFromDB throws`() = runTest {
    coEvery { notificationRepository.getActiveAccountFromDB() } throws RuntimeException("DB unavailable")

    val result = service.updateNotificationSettings(testRequest)

    assertThat(result).isNull()
  }

  // -------------------------------------------------------------------------
  // updateNotificationSettings — uses active account ID consistently
  // -------------------------------------------------------------------------

  @Test
  fun `updateNotificationSettings online passes active account ID to DB update`() = runTest {
    val response = createAccountResponse()
    coEvery { notificationRepository.updateNotificationSettingsInAPI(testRequest) } returns response
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(testRequest)

    coVerify { notificationRepository.updateNotificationSettingsInDB(eq("acc-123"), any()) }
  }

  @Test
  fun `updateNotificationSettings offline passes active account ID to DB update`() = runTest {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    coEvery { notificationRepository.updateNotificationSettingsInDB(any(), any()) } returns fakeUpdatedAccount

    service.updateNotificationSettings(testRequest)

    coVerify { notificationRepository.updateNotificationSettingsInDB(eq("acc-123"), any()) }
  }
}
