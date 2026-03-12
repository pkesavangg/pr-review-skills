package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IUserSettingsAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account as RoomAccount
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.domain.model.api.metrics.StreakRequest
import com.dmdbrands.gurus.weight.domain.model.api.metrics.WeightlessRequest
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

class UserSettingsRepositoryTest {

  @MockK(relaxUnitFun = true)
  lateinit var userSettingsAPI: IUserSettingsAPI

  @MockK(relaxUnitFun = true)
  lateinit var accountDao: AccountDao

  private lateinit var repository: UserSettingsRepository

  private val accountId = "acc-123"
  private val mockAccountEntity = mockk<AccountEntity>(relaxed = true) {
    every { id } returns accountId
  }
  private val mockRoomAccount = mockk<RoomAccount>(relaxed = true) {
    every { account } returns mockAccountEntity
  }
  private val mockDomainAccount = mockk<Account>(relaxed = true)
  private val mockApiAccountInfo = mockk<com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo>(relaxed = true) {
    every { id } returns accountId
    every { isStreakOn } returns true
    every { isWeightlessOn } returns false
    every { weightlessTimestamp } returns null
    every { weightlessWeight } returns null
  }
  private val mockAccountResponse = mockk<AccountResponse>(relaxed = true) {
    every { account } returns mockApiAccountInfo
  }

  @Before
  fun setUp() {
    MockKAnnotations.init(this)
    mockkObject(AccountEntityMapper)
    repository = UserSettingsRepository(userSettingsAPI, accountDao)
  }

  @After
  fun tearDown() {
    unmockkObject(AccountEntityMapper)
    unmockkAll()
  }

  // updateStreakSetting — online path

  @Test
  fun `updateStreakSetting calls API and updates DAO with isSynced true on success`() = runTest {
    val request = StreakRequest(isStreakOn = true, streakTimestamp = "2024-01-01")
    coEvery { userSettingsAPI.updateStreak(request) } returns mockAccountResponse
    coEvery { accountDao.updateStreaksSettings(any()) } just Runs

    repository.updateStreakSetting(request)

    coVerify {
      accountDao.updateStreaksSettings(
        match { it.accountId == accountId && it.isStreakOn == true && it.isSynced == true }
      )
    }
  }

  @Test
  fun `updateStreakSetting does not call getActiveAccount on API success`() = runTest {
    val request = StreakRequest(isStreakOn = true, streakTimestamp = null)
    coEvery { userSettingsAPI.updateStreak(request) } returns mockAccountResponse
    coEvery { accountDao.updateStreaksSettings(any()) } just Runs

    repository.updateStreakSetting(request)

    io.mockk.verify(exactly = 0) { accountDao.getActiveAccount() }
  }

  // updateStreakSetting — offline fallback path

  @Test
  fun `updateStreakSetting falls back to offline update with isSynced false when API throws`() = runTest {
    val request = StreakRequest(isStreakOn = false, streakTimestamp = "2024-01-01")
    coEvery { userSettingsAPI.updateStreak(any()) } throws RuntimeException("Network error")
    every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
    coEvery { accountDao.updateStreaksSettings(any()) } just Runs

    repository.updateStreakSetting(request)

    coVerify {
      accountDao.updateStreaksSettings(
        match { it.accountId == accountId && it.isStreakOn == false && it.isSynced == false }
      )
    }
  }

  @Test
  fun `updateStreakSetting does nothing in offline path when no active account`() = runTest {
    coEvery { userSettingsAPI.updateStreak(any()) } throws RuntimeException("Network error")
    every { accountDao.getActiveAccount() } returns flowOf(null)

    repository.updateStreakSetting(StreakRequest(isStreakOn = true, streakTimestamp = null))

    coVerify(exactly = 0) { accountDao.updateStreaksSettings(any()) }
  }

  // updateWeightlessSetting — online path

  @Test
  fun `updateWeightlessSetting calls API and updates DAO with isSynced true on success`() = runTest {
    val request = mockk<WeightlessRequest>(relaxed = true)
    coEvery { userSettingsAPI.updateWeightless(request) } returns mockAccountResponse
    coEvery { accountDao.updateWeightlessSettings(any()) } just Runs

    repository.updateWeightlessSetting(request)

    coVerify {
      accountDao.updateWeightlessSettings(
        match { it.accountId == accountId && it.isSynced == true }
      )
    }
  }

  @Test
  fun `updateWeightlessSetting falls back to offline with isSynced false when API throws`() = runTest {
    val request = mockk<WeightlessRequest>(relaxed = true) {
      every { isWeightlessOn } returns true
      every { weightlessTimestamp } returns "2024-01-01"
      every { weightlessWeight } returns 70.0
    }
    coEvery { userSettingsAPI.updateWeightless(any()) } throws RuntimeException("Network error")
    every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
    coEvery { accountDao.updateWeightlessSettings(any()) } just Runs

    repository.updateWeightlessSetting(request)

    coVerify {
      accountDao.updateWeightlessSettings(
        match { it.accountId == accountId && it.isWeightlessOn == true && it.isSynced == false }
      )
    }
  }

  @Test
  fun `updateWeightlessSetting does nothing in offline path when no active account`() = runTest {
    coEvery { userSettingsAPI.updateWeightless(any()) } throws RuntimeException("Network error")
    every { accountDao.getActiveAccount() } returns flowOf(null)

    repository.updateWeightlessSetting(mockk(relaxed = true))

    coVerify(exactly = 0) { accountDao.updateWeightlessSettings(any()) }
  }

  // updateStreakSettingOffline

  @Test
  fun `updateStreakSettingOffline updates DAO with isSynced false and returns mapped account`() = runTest {
    val request = StreakRequest(isStreakOn = true, streakTimestamp = "2024-01-01")
    every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
    coEvery { accountDao.updateStreaksSettings(any()) } just Runs
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockRoomAccount) } returns mockDomainAccount

    val result = repository.updateStreakSettingOffline(request)

    coVerify {
      accountDao.updateStreaksSettings(
        match { it.accountId == accountId && it.isStreakOn == true && it.isSynced == false }
      )
    }
    assertThat(result).isEqualTo(mockDomainAccount)
  }

  @Test
  fun `updateStreakSettingOffline returns null when no active account`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(null)

    val result = repository.updateStreakSettingOffline(
      StreakRequest(isStreakOn = true, streakTimestamp = null)
    )

    assertThat(result).isNull()
  }

  // updateWeightlessSettingOffline

  @Test
  fun `updateWeightlessSettingOffline updates DAO with isSynced false and returns mapped account`() = runTest {
    val request = mockk<WeightlessRequest>(relaxed = true) {
      every { isWeightlessOn } returns false
      every { weightlessTimestamp } returns null
      every { weightlessWeight } returns null
    }
    every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
    coEvery { accountDao.updateWeightlessSettings(any()) } just Runs
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockRoomAccount) } returns mockDomainAccount

    val result = repository.updateWeightlessSettingOffline(request)

    coVerify {
      accountDao.updateWeightlessSettings(
        match { it.accountId == accountId && it.isWeightlessOn == false && it.isSynced == false }
      )
    }
    assertThat(result).isEqualTo(mockDomainAccount)
  }

  @Test
  fun `updateWeightlessSettingOffline returns null when no active account`() = runTest {
    every { accountDao.getActiveAccount() } returns flowOf(null)

    val result = repository.updateWeightlessSettingOffline(mockk(relaxed = true))

    assertThat(result).isNull()
  }

  // getUnsyncedActiveStreakAccountFromDB

  @Test
  fun `getUnsyncedActiveStreakAccountFromDB returns mapped account`() = runTest {
    every { accountDao.getUnsyncedActiveStreakAccount() } returns flowOf(mockRoomAccount)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockRoomAccount) } returns mockDomainAccount

    val result = repository.getUnsyncedActiveStreakAccountFromDB()

    assertThat(result).isEqualTo(mockDomainAccount)
  }

  @Test
  fun `getUnsyncedActiveStreakAccountFromDB returns null when no unsynced account`() = runTest {
    every { accountDao.getUnsyncedActiveStreakAccount() } returns flowOf(null)

    val result = repository.getUnsyncedActiveStreakAccountFromDB()

    assertThat(result).isNull()
  }

  // getUnsyncedActiveWeightlessAccountFromDB

  @Test
  fun `getUnsyncedActiveWeightlessAccountFromDB returns mapped account`() = runTest {
    every { accountDao.getUnsyncedActiveWeightlessAccount() } returns flowOf(mockRoomAccount)
    every { AccountEntityMapper.toDomainFromAccountWithRelations(mockRoomAccount) } returns mockDomainAccount

    val result = repository.getUnsyncedActiveWeightlessAccountFromDB()

    assertThat(result).isEqualTo(mockDomainAccount)
  }

  @Test
  fun `getUnsyncedActiveWeightlessAccountFromDB returns null when no unsynced account`() = runTest {
    every { accountDao.getUnsyncedActiveWeightlessAccount() } returns flowOf(null)

    val result = repository.getUnsyncedActiveWeightlessAccountFromDB()

    assertThat(result).isNull()
  }
}
