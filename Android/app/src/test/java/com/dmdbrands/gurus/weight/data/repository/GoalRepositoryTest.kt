package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IGoalAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account as RoomAccount
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.GoalSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalResponse
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GoalRepositoryTest {

    companion object {
        private const val ACCOUNT_ID = "acc-123"
    }

    @MockK
    lateinit var goalAPI: IGoalAPI

    @MockK
    lateinit var accountDao: AccountDao

    @MockK
    lateinit var accountRepository: IAccountRepository

    private lateinit var repository: GoalRepository

    private val mockAccountEntity: AccountEntity = mockk(relaxed = true) {
        every { id } returns ACCOUNT_ID
    }
    private val mockRoomAccount: RoomAccount = mockk(relaxed = true) {
        every { account } returns mockAccountEntity
    }
    private val mockDomainAccount: Account = mockk(relaxed = true) {
        every { id } returns ACCOUNT_ID
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = GoalRepository(goalAPI, accountDao, accountRepository)
    }

    // -----------------------------------------------------------------------
    // updateGoalSettingsInDB
    // -----------------------------------------------------------------------

    @Test
    fun `updateGoalSettingsInDB with active account calls updateGoalSettings with correct accountId`() = runTest {
        val inputEntity = GoalSettingsEntity(
            accountId = "",
            goalType = "lose",
            weight = 180f,
            goalWeight = "160.0",
            goalPercent = 0f,
            isSynced = true
        )
        every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
        coEvery { accountDao.updateGoalSettings(any()) } returns Unit

        repository.updateGoalSettingsInDB(inputEntity)

        coVerify {
            accountDao.updateGoalSettings(
                match { it.accountId == ACCOUNT_ID && it.goalType == "lose" && it.weight == 180f }
            )
        }
    }

    @Test
    fun `updateGoalSettingsInDB with no active account does not call updateGoalSettings`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        repository.updateGoalSettingsInDB(mockk(relaxed = true))

        coVerify(exactly = 0) { accountDao.updateGoalSettings(any()) }
    }

    // -----------------------------------------------------------------------
    // updateGoalSetting
    // -----------------------------------------------------------------------

    @Test
    fun `updateGoalSetting returns account after successful API call`() = runTest {
        val goalData = GoalData(goalWeight = 160.0, initialWeight = 180.0, type = "lose")
        val goalResponse = GoalResponse(
            goalWeight = 160.0,
            initialWeight = 180.0,
            type = "lose",
            goalType = "lose"
        )
        coEvery { goalAPI.updateGoal(goalData) } returns goalResponse
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
        coEvery { accountDao.updateGoalSettings(any()) } returns Unit

        val result = repository.updateGoalSetting(goalData)

        assertThat(result).isEqualTo(mockDomainAccount)
        coVerify { goalAPI.updateGoal(goalData) }
        coVerify { accountDao.updateGoalSettings(match { it.accountId == ACCOUNT_ID && it.isSynced }) }
    }

    @Test
    fun `updateGoalSetting with no active account returns null`() = runTest {
        val goalData = GoalData(goalWeight = 160.0, initialWeight = 180.0, type = "lose")
        coEvery { goalAPI.updateGoal(goalData) } returns mockk(relaxed = true)
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val result = repository.updateGoalSetting(goalData)

        assertThat(result).isNull()
    }

    @Test
    fun `updateGoalSetting rethrows exception when API call fails`() = runTest {
        val goalData = GoalData(goalWeight = 160.0, initialWeight = 180.0, type = "lose")
        coEvery { goalAPI.updateGoal(goalData) } throws IOException("Network error")

        var threw = false
        try {
            repository.updateGoalSetting(goalData)
        } catch (e: IOException) {
            threw = true
        }

        assertThat(threw).isTrue()
    }

    // -----------------------------------------------------------------------
    // updateGoalSettingOffline
    // -----------------------------------------------------------------------

    @Test
    fun `updateGoalSettingOffline stores goal with isSynced false and returns account`() = runTest {
        val request = GoalData(goalWeight = 160.0, initialWeight = 180.0, type = "lose")
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        every { accountDao.getActiveAccount() } returns flowOf(mockRoomAccount)
        coEvery { accountDao.updateGoalSettings(any()) } returns Unit

        val result = repository.updateGoalSettingOffline(request)

        assertThat(result).isEqualTo(mockDomainAccount)
        coVerify { accountDao.updateGoalSettings(match { !it.isSynced }) }
    }

    @Test
    fun `updateGoalSettingOffline with no active account returns null`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val result = repository.updateGoalSettingOffline(
            GoalData(goalWeight = 160.0, initialWeight = 180.0, type = "lose")
        )

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // getCurrentGoal
    // -----------------------------------------------------------------------

    @Test
    fun `getCurrentGoal with active account emits non-null goal`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)

        val result = repository.getCurrentGoal().first()

        assertThat(result).isNotNull()
    }

    @Test
    fun `getCurrentGoal with no active account emits null`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val result = repository.getCurrentGoal().first()

        assertThat(result).isNull()
    }

    @Test
    fun `getCurrentGoal emits null when flow throws exception`() = runTest {
        every { accountRepository.getActiveAccount() } returns flow {
            throw RuntimeException("DB error")
        }

        val result = repository.getCurrentGoal().first()

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // calculateGoalPercent
    // -----------------------------------------------------------------------

    @Test
    fun `calculateGoalPercent for lose type returns correct percent`() {
        val goal = Goal(goalWeight = 160.0, initialWeight = 200.0, type = "lose")

        val result = repository.calculateGoalPercent(goal, currentWeight = 170.0)

        // (170 - 160) / (200 - 160) = 0.25 → 100 - floor(25) = 75
        assertThat(result).isEqualTo(75)
    }

    @Test
    fun `calculateGoalPercent for gain type returns correct percent`() {
        val goal = Goal(goalWeight = 200.0, initialWeight = 160.0, type = "gain")

        val result = repository.calculateGoalPercent(goal, currentWeight = 180.0)

        // (180 - 160) / (200 - 160) = 0.5 → floor(50) = 50
        assertThat(result).isEqualTo(50)
    }

    @Test
    fun `calculateGoalPercent for lose type clamps to 0 when weight exceeds initial`() {
        // currentWeight > initialWeight → percent > 1 → result < 0 → clamp to 0
        val goal = Goal(goalWeight = 160.0, initialWeight = 180.0, type = "lose")

        val result = repository.calculateGoalPercent(goal, currentWeight = 190.0)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `calculateGoalPercent for gain type clamps to 0 when weight below initial`() {
        // currentWeight < initialWeight → percent < 0 → result < 0 → clamp to 0
        val goal = Goal(goalWeight = 200.0, initialWeight = 160.0, type = "gain")

        val result = repository.calculateGoalPercent(goal, currentWeight = 150.0)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `calculateGoalPercent for maintain type returns null`() {
        val goal = Goal(goalWeight = 180.0, initialWeight = 180.0, type = "maintain")

        val result = repository.calculateGoalPercent(goal, currentWeight = 180.0)

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // getUnsyncedActiveGoalAccountFromDB
    // -----------------------------------------------------------------------

    @Test
    fun `getUnsyncedActiveGoalAccountFromDB returns null when no unsynced account`() = runTest {
        every { accountDao.getUnsyncedActiveGoalAccount() } returns flowOf(null)

        val result = repository.getUnsyncedActiveGoalAccountFromDB()

        assertThat(result).isNull()
    }

    @Test
    fun `getUnsyncedActiveGoalAccountFromDB returns mapped domain account`() = runTest {
        mockkObject(AccountEntityMapper)
        every { accountDao.getUnsyncedActiveGoalAccount() } returns flowOf(mockRoomAccount)
        every { AccountEntityMapper.toDomainFromAccountWithRelations(mockRoomAccount) } returns mockDomainAccount

        val result = repository.getUnsyncedActiveGoalAccountFromDB()

        assertThat(result).isEqualTo(mockDomainAccount)
    }
}
