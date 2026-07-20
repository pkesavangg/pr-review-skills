package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.google.common.truth.Truth.assertThat
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class AccountValidationServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val offlineHandlerService: IOfflineHandlerService = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private val validationService = AccountValidationService(
        accountRepository,
        offlineHandlerService,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
    )

    private fun account(id: String, active: Boolean = true, expired: Boolean = false) = Account(
        id = id,
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "$id@example.com",
        gender = "male",
        isActiveAccount = active,
        isLoggedIn = true,
        isExpired = expired,
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1750,
        activityLevel = "normal",
    )

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `checkLoginStatusForActiveAccount returns true and syncs on online success`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(account("acc-1"))
        coEvery { accountRepository.getAccountFromAPI("acc-1") } returns mockk<AccountInfo>()

        val result = validationService.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
        coVerify { accountRepository.syncAccountSettingsWithServer(any(), isOnline = true) }
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local validity when offline`() = runTest {
        connectivityObserver.stubNetworkUnavailable()
        every { accountRepository.getActiveAccount() } returns flowOf(account("acc-1"))

        val result = validationService.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
        coVerify { accountRepository.syncAccountSettingsWithServer(any(), isOnline = false) }
    }

    @Test
    fun `checkLoginStatusForActiveAccount returns false and marks expired on 401`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(account("acc-1"))
        coEvery { accountRepository.getAccountFromAPI("acc-1") } throws httpException(401)

        val result = validationService.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
        coVerify { accountRepository.markAccountExpired("acc-1") }
        coVerify { accountRepository.clearAccountTokens("acc-1") }
    }

    @Test
    fun `checkLoginStatusForActiveAccount returns false when no active account`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        assertThat(validationService.checkLoginStatusForActiveAccount()).isFalse()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts refreshes non-active accounts and sets checkIntegrations`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val accounts = listOf(account("acc-1"), account("acc-2", active = false))
        every { accountRepository.getLoggedInAccounts() } returns flowOf(accounts)
        coEvery { accountRepository.getAccountFromAPI("acc-2") } returns mockk<AccountInfo>()

        val result = validationService.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        assertThat(validationService.checkIntegrations.value).isTrue()
        coVerify { accountRepository.updateAccountInfo("acc-2", any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true and clears flag when no non-active accounts`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(account("acc-1")))

        val result = validationService.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        assertThat(validationService.checkIntegrations.value).isFalse()
    }
}
