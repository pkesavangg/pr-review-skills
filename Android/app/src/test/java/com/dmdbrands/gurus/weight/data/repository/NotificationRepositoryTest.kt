package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.INotificationAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account as RoomAccount
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.NotificationSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.notification.NotificationSettingsRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class NotificationRepositoryTest {

  @MockK(relaxUnitFun = true)
  lateinit var notificationAPI: INotificationAPI

  @MockK(relaxUnitFun = true)
  lateinit var accountDao: AccountDao

  private lateinit var repository: NotificationRepository

  private val mockAccount = mockk<Account>(relaxed = true)
  private val mockAccountWithRelations = mockk<RoomAccount>(relaxed = true)
  private val mockAccountResponse = mockk<AccountResponse>(relaxed = true)

  @Before
  fun setUp() {
    MockKAnnotations.init(this)
    mockkObject(AccountEntityMapper)
    repository = NotificationRepository(notificationAPI, accountDao)
  }

  @After
  fun tearDown() {
    unmockkObject(AccountEntityMapper)
    unmockkAll()
  }

  // updateNotificationSettingsInAPI

  @Test
  fun `updateNotificationSettingsInAPI delegates to API and returns response`() = runTest {
    val request = mockk<NotificationSettingsRequest>(relaxed = true)
    coEvery { notificationAPI.updateNotificationSettings(request) } returns mockAccountResponse

    val result = repository.updateNotificationSettingsInAPI(request)

    assertThat(result).isEqualTo(mockAccountResponse)
    coVerify { notificationAPI.updateNotificationSettings(request) }
  }

  @Test
  fun `updateNotificationSettingsInAPI propagates exception from API`() = runTest {
    val request = mockk<NotificationSettingsRequest>(relaxed = true)
    coEvery { notificationAPI.updateNotificationSettings(any()) } throws RuntimeException("API error")

    var thrown: Exception? = null
    try {
      repository.updateNotificationSettingsInAPI(request)
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).isEqualTo("API error")
  }

  // updateNotificationSettingsInDB

  @Test
  fun `updateNotificationSettingsInDB calls accountDao with correct entity`() = runTest {
    val settings = NotificationSettingsEntity(
      accountId = "acc-1",
      shouldSendEntryNotifications = true,
      shouldSendWeightInEntryNotifications = false,
      isSynced = true
    )
    coEvery { accountDao.updateNotificationSettings(any()) } just Runs
    every { accountDao.getAccount("acc-1") } returns flowOf(mockAccountWithRelations)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockAccountWithRelations) } returns mockAccount

    repository.updateNotificationSettingsInDB("acc-1", settings)

    coVerify {
      accountDao.updateNotificationSettings(
        match {
          it.accountId == "acc-1" &&
            it.shouldSendEntryNotifications == true &&
            it.shouldSendWeightInEntryNotifications == false &&
            it.isSynced == true
        }
      )
    }
  }

  @Test
  fun `updateNotificationSettingsInDB returns mapped account from dao`() = runTest {
    val settings = NotificationSettingsEntity(
      accountId = "acc-2",
      shouldSendEntryNotifications = false,
      shouldSendWeightInEntryNotifications = true,
      isSynced = false
    )
    coEvery { accountDao.updateNotificationSettings(any()) } just Runs
    every { accountDao.getAccount("acc-2") } returns flowOf(mockAccountWithRelations)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockAccountWithRelations) } returns mockAccount

    val result = repository.updateNotificationSettingsInDB("acc-2", settings)

    assertThat(result).isEqualTo(mockAccount)
  }

  @Test
  fun `updateNotificationSettingsInDB throws when account not found after update`() = runTest {
    val settings = NotificationSettingsEntity(
      accountId = "acc-missing",
      shouldSendEntryNotifications = true,
      shouldSendWeightInEntryNotifications = true,
      isSynced = true
    )
    coEvery { accountDao.updateNotificationSettings(any()) } just Runs
    every { accountDao.getAccount("acc-missing") } returns flowOf(null)

    var thrown: Exception? = null
    try {
      repository.updateNotificationSettingsInDB("acc-missing", settings)
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
  }

  // getActiveAccountFromDB

  @Test
  fun `getActiveAccountFromDB returns mapped account when active account exists`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(mockAccountWithRelations)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockAccountWithRelations) } returns mockAccount

    val result = repository.getActiveAccountFromDB()

    assertThat(result).isEqualTo(mockAccount)
  }

  @Test
  fun `getActiveAccountFromDB returns null when no active account`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(null)

    val result = repository.getActiveAccountFromDB()

    assertThat(result).isNull()
  }

  // getUnsyncedActiveNotificationAccountFromDB

  @Test
  fun `getUnsyncedActiveNotificationAccountFromDB returns mapped account`() = runTest {
    every { accountDao.getUnsyncedActiveNotificationAccount() } returns flowOf(mockAccountWithRelations)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockAccountWithRelations) } returns mockAccount

    val result = repository.getUnsyncedActiveNotificationAccountFromDB()

    assertThat(result).isEqualTo(mockAccount)
  }

  @Test
  fun `getUnsyncedActiveNotificationAccountFromDB returns null when no unsynced account`() = runTest {
    every { accountDao.getUnsyncedActiveNotificationAccount() } returns flowOf(null)

    val result = repository.getUnsyncedActiveNotificationAccountFromDB()

    assertThat(result).isNull()
  }
}
