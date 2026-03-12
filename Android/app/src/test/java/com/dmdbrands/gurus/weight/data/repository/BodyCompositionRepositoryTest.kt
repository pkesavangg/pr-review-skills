package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IBodyCompAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.Account
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.AccountEntityMapper
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account as DomainAccount
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BodyCompositionRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var accountDao: AccountDao

    @MockK(relaxUnitFun = true)
    private lateinit var bodyCompAPI: IBodyCompAPI

    private lateinit var repository: BodyCompositionRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(AccountEntityMapper)
        repository = BodyCompositionRepository(accountDao, bodyCompAPI)
    }

    @After
    fun tearDown() {
        unmockkObject(AccountEntityMapper)
    }

    // ── updateBodyCompInAPI ────────────────────────────────────────────────────

    @Test
    fun `updateBodyCompInAPI returns AccountResponse on success`() = runTest {
        val request = BodyCompUpdateRequest(height = 180, activityLevel = "normal", weightUnit = "LB")
        val response = buildAccountResponse()
        coEvery { bodyCompAPI.updateBodyComp(request) } returns response

        val result = repository.updateBodyCompInAPI(request)

        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `updateBodyCompInAPI calls api with correct request`() = runTest {
        val request = BodyCompUpdateRequest(height = 170, activityLevel = "athlete", weightUnit = "KG")
        coEvery { bodyCompAPI.updateBodyComp(any()) } returns buildAccountResponse()

        repository.updateBodyCompInAPI(request)

        coVerify { bodyCompAPI.updateBodyComp(request) }
    }

    @Test(expected = IOException::class)
    fun `updateBodyCompInAPI propagates IOException`() = runTest {
        coEvery { bodyCompAPI.updateBodyComp(any()) } throws IOException("Network error")

        repository.updateBodyCompInAPI(BodyCompUpdateRequest(height = 180, activityLevel = "normal", weightUnit = "LB"))
    }

    @Test(expected = RuntimeException::class)
    fun `updateBodyCompInAPI propagates RuntimeException`() = runTest {
        coEvery { bodyCompAPI.updateBodyComp(any()) } throws RuntimeException("Server error")

        repository.updateBodyCompInAPI(BodyCompUpdateRequest(height = 180, activityLevel = "normal", weightUnit = "LB"))
    }

    // ── updateBodyCompInDB ─────────────────────────────────────────────────────

    @Test
    fun `updateBodyCompInDB calls dao with correct entity`() = runTest {
        val inputEntity = WeightCompSettingsEntity(
            accountId = "ignored",
            height = 180,
            activityLevel = "normal",
            weightUnit = "LB",
            isSynced = false
        )

        repository.updateBodyCompInDB("account1", inputEntity)

        coVerify {
            accountDao.updateWeightCompSettings(
                WeightCompSettingsEntity(
                    accountId = "account1",
                    height = 180,
                    activityLevel = "normal",
                    weightUnit = "LB",
                    isSynced = false
                )
            )
        }
    }

    @Test
    fun `updateBodyCompInDB uses provided accountId regardless of entity accountId`() = runTest {
        val inputEntity = WeightCompSettingsEntity(
            accountId = "old-account",
            height = 165,
            activityLevel = "athlete",
            weightUnit = "KG",
            isSynced = true
        )

        repository.updateBodyCompInDB("new-account", inputEntity)

        coVerify {
            accountDao.updateWeightCompSettings(
                match { it.accountId == "new-account" }
            )
        }
    }

    @Test(expected = RuntimeException::class)
    fun `updateBodyCompInDB propagates exception from dao`() = runTest {
        val inputEntity = WeightCompSettingsEntity(
            accountId = "account1",
            height = 180,
            activityLevel = "normal",
            weightUnit = "LB",
            isSynced = false
        )
        coEvery { accountDao.updateWeightCompSettings(any()) } throws RuntimeException("DB error")

        repository.updateBodyCompInDB("account1", inputEntity)
    }

    // ── getUnsyncedActiveBodyCompAccountFromDB ─────────────────────────────────

    @Test
    fun `getUnsyncedActiveBodyCompAccountFromDB returns null when no unsynced account`() = runTest {
        every { accountDao.getUnsyncedActiveBodyCompAccount() } returns flowOf(null)

        val result = repository.getUnsyncedActiveBodyCompAccountFromDB()

        assertThat(result).isNull()
    }

    @Test
    fun `getUnsyncedActiveBodyCompAccountFromDB returns mapped account when found`() = runTest {
        val entityAccount = mockk<Account>()
        val domainAccount = mockk<DomainAccount>(relaxed = true)
        every { accountDao.getUnsyncedActiveBodyCompAccount() } returns flowOf(entityAccount)
        every { AccountEntityMapper.toDomainFromAccountWithRelations(any()) } returns domainAccount

        val result = repository.getUnsyncedActiveBodyCompAccountFromDB()

        assertThat(result).isEqualTo(domainAccount)
    }

    // ── getActiveAccountFromDB ─────────────────────────────────────────────────

    @Test
    fun `getActiveAccountFromDB returns null when no active account`() = runTest {
        every { accountDao.getActiveAccount() } returns flowOf(null)

        val result = repository.getActiveAccountFromDB()

        assertThat(result).isNull()
    }

    @Test
    fun `getActiveAccountFromDB returns mapped account when found`() = runTest {
        val entityAccount = mockk<Account>()
        val domainAccount = mockk<DomainAccount>(relaxed = true)
        every { accountDao.getActiveAccount() } returns flowOf(entityAccount)
        every { AccountEntityMapper.toDomainFromAccountWithRelations(any()) } returns domainAccount

        val result = repository.getActiveAccountFromDB()

        assertThat(result).isEqualTo(domainAccount)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildAccountResponse(): AccountResponse = AccountResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = "2024-12-31",
        account = AccountInfo(
            id = "account1",
            email = "user@example.com",
            firstName = "John",
            lastName = "Doe",
            gender = "M",
            zipcode = "12345",
            weightUnit = "LB",
            isWeightlessOn = false,
            height = 180,
            activityLevel = "normal",
            dob = "1990-01-01",
            weightlessTimestamp = null,
            weightlessWeight = null,
            isStreakOn = false,
            dashboardType = "DASHBOARD_4_METRICS",
            dashboardMetrics = listOf("weight"),
            goalWeight = 160f,
            initialWeight = 200f,
            shouldSendEntryNotifications = true,
            shouldSendWeightInEntryNotifications = false,
        )
    )
}
