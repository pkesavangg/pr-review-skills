package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.IAuthAPI
import com.dmdbrands.gurus.weight.data.api.IIntegrationAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.AccountDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.IntegrationsSettingsEntity
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class IntegrationRepositoryTest {

    @MockK
    lateinit var accountRepository: IAccountRepository

    @MockK
    lateinit var authAPI: IAuthAPI

    @MockK
    lateinit var integrationAPI: IIntegrationAPI

    @MockK
    lateinit var accountDao: AccountDao

    @MockK
    lateinit var healthConnectRepository: IHealthConnectRepository

    private lateinit var repository: IntegrationRepository

    private val accountId = "acc-123"
    private val mockDomainAccount: Account = mockk(relaxed = true) {
        every { id } returns accountId
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        // Required before construction — init block launches a coroutine that calls these
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        coEvery { healthConnectRepository.getAccountByID(any()) } returns null
        repository = IntegrationRepository(
            accountRepository, authAPI, integrationAPI, accountDao, healthConnectRepository
        )
    }

    // -----------------------------------------------------------------------
    // getAccount
    // -----------------------------------------------------------------------

    @Test
    fun `getAccount returns AccountInfo from authAPI`() = runTest {
        val mockAccountInfo: AccountInfo = mockk(relaxed = true) {
            every { id } returns accountId
        }
        coEvery { authAPI.getAccountWithToken(accountId) } returns mockAccountInfo

        val result = repository.getAccount(accountId)

        assertThat(result).isEqualTo(mockAccountInfo)
        coVerify { authAPI.getAccountWithToken(accountId) }
    }

    @Test
    fun `getAccount rethrows exception when API fails`() = runTest {
        coEvery { authAPI.getAccountWithToken(accountId) } throws IOException("Network error")

        var threw = false
        try {
            repository.getAccount(accountId)
        } catch (e: IOException) {
            threw = true
        }

        assertThat(threw).isTrue()
    }

    // -----------------------------------------------------------------------
    // removeIntegration
    // -----------------------------------------------------------------------

    @Test
    fun `removeIntegration delegates to integrationAPI with correct args`() = runTest {
        val provider = "fitbit"
        val suggestion = mapOf("key" to "value")
        coEvery { integrationAPI.removeIntegration(provider, suggestion) } returns Unit

        repository.removeIntegration(provider, suggestion)

        coVerify { integrationAPI.removeIntegration(provider, suggestion) }
    }

    @Test
    fun `removeIntegration rethrows exception when API fails`() = runTest {
        coEvery { integrationAPI.removeIntegration(any(), any()) } throws IOException("Network error")

        var threw = false
        try {
            repository.removeIntegration("fitbit", emptyMap())
        } catch (e: IOException) {
            threw = true
        }

        assertThat(threw).isTrue()
    }

    // -----------------------------------------------------------------------
    // updateLocalAccount
    // -----------------------------------------------------------------------

    @Test
    fun `updateLocalAccount updates DAO and state flows on success`() = runTest {
        val mockAccountInfo: AccountInfo = mockk(relaxed = true) {
            every { id } returns accountId
            every { isFitbitOn } returns true
            every { isFitbitValid } returns true
            every { isHealthConnectOn } returns false
            every { isHealthKitOn } returns false
            every { isMFPOn } returns false
            every { isMFPValid } returns false
        }
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        coEvery { accountRepository.getAccountFromAPI(accountId) } returns mockAccountInfo
        coEvery { healthConnectRepository.getAccountByID(accountId) } returns null
        coEvery { accountDao.updateIntegrationsSettings(any()) } returns Unit

        repository.updateLocalAccount()

        coVerify { accountRepository.getAccountFromAPI(accountId) }
        coVerify { accountDao.updateIntegrationsSettings(match { it.accountId == accountId && it.isFitbitOn }) }
        assertThat(repository.integrationsFromServer.value.isFitbitOn).isTrue()
    }

    @Test
    fun `updateLocalAccount swallows exception when API fails`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        coEvery { accountRepository.getAccountFromAPI(accountId) } throws IOException("Network error")

        // Should not throw
        repository.updateLocalAccount()
    }

    @Test
    fun `updateLocalAccount returns early when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        repository.updateLocalAccount()

        coVerify(exactly = 0) { accountDao.updateIntegrationsSettings(any()) }
    }

    // -----------------------------------------------------------------------
    // updateHealthConnectIntegrationOffline
    // -----------------------------------------------------------------------

    @Test
    fun `updateHealthConnectIntegrationOffline updates DAO with isSynced false`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        coEvery { accountDao.updateIntegrationsSettings(any()) } returns Unit

        repository.updateHealthConnectIntegrationOffline(isHealthConnectOn = true)

        coVerify {
            accountDao.updateIntegrationsSettings(
                match { it.accountId == accountId && it.isHealthConnectOn && !it.isSynced }
            )
        }
        assertThat(repository.integrations.value?.isHealthConnectOn).isTrue()
    }

    @Test
    fun `updateHealthConnectIntegrationOffline returns early when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        repository.updateHealthConnectIntegrationOffline(isHealthConnectOn = true)

        coVerify(exactly = 0) { accountDao.updateIntegrationsSettings(any()) }
    }

    @Test
    fun `updateHealthConnectIntegrationOffline swallows exception when DAO fails`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockDomainAccount)
        coEvery { accountDao.updateIntegrationsSettings(any()) } throws RuntimeException("DB error")

        // Should not throw
        repository.updateHealthConnectIntegrationOffline(isHealthConnectOn = false)
    }

    // -----------------------------------------------------------------------
    // integrations / integrationsFromServer StateFlow defaults
    // -----------------------------------------------------------------------

    @Test
    fun `integrations StateFlow has default value`() {
        assertThat(repository.integrations.value).isNotNull()
        assertThat(repository.integrations.value?.isFitbitOn).isFalse()
    }

    @Test
    fun `integrationsFromServer StateFlow has default value`() {
        assertThat(repository.integrationsFromServer.value.isFitbitOn).isFalse()
    }
}
