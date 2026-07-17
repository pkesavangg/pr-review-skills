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
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.google.common.truth.Truth.assertThat
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFailsWith
import retrofit2.HttpException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionManagerTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private val sessionManager = AccountSessionManager(
        accountRepository,
        analyticsService,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
    )

    private fun account(id: String, active: Boolean = true) = Account(
        id = id,
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "$id@example.com",
        gender = "male",
        isActiveAccount = active,
        isLoggedIn = true,
        isExpired = false,
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1750,
        activityLevel = "normal",
    )

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `logout delegates to repository and emits LoggedOut`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val active = account("acc-1")
        every { accountRepository.getActiveAccount() } returns flowOf(active)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(active))
        coEvery { accountRepository.logoutAccount("acc-1", null, true) } returns true

        val result = sessionManager.logout("acc-1", null)

        assertThat(result).isTrue()
        coVerify { accountRepository.logoutAccount("acc-1", null, true) }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
    }

    @Test
    fun `logoutAll resets notification alerts and emits LoggedOut`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val accounts = listOf(account("acc-1"), account("acc-2", active = false))
        every { accountRepository.getLoggedInAccounts() } returns flowOf(accounts)
        coEvery { accountRepository.logoutAllAccounts() } returns true

        val result = sessionManager.logoutAll()

        assertThat(result).isTrue()
        coVerify { accountRepository.setNotificationAlertShownForAccount("acc-1", false) }
        coVerify { accountRepository.setNotificationAlertShownForAccount("acc-2", false) }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
    }

    @Test
    fun `switchAccount switches and emits AccountSwitched on success`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val target = account("acc-2", active = false)
        coEvery { accountRepository.getAccountFromAPI("acc-2") } returns mockk<AccountInfo>()

        val result = sessionManager.switchAccount(target, showToast = false)

        assertThat(result).isTrue()
        coVerify { accountRepository.switchToAccount("acc-2") }
        verify { analyticsService.logEvent(IAnalyticsService.Events.ACCOUNT_SWITCHED) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `switchAccount marks expired on 401 and returns false`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val target = account("acc-2", active = false)
        coEvery { accountRepository.getAccountFromAPI("acc-2") } throws httpException(401)

        val result = sessionManager.switchAccount(target, showToast = false)

        assertThat(result).isFalse()
        coVerify { accountRepository.markAccountExpired("acc-2") }
        coVerify { accountRepository.removeAccount("acc-2") }
    }

    @Test
    fun `deleteAccount delegates when network available`() = runTest {
        connectivityObserver.stubNetworkAvailable()

        sessionManager.deleteAccount("acc-1", isActiveAccount = true)

        coVerify { accountRepository.deleteAccount("acc-1", true) }
    }

    @Test
    fun `deleteAccount throws when offline`() = runTest {
        connectivityObserver.stubNetworkUnavailable()

        assertFailsWith<Exception> { sessionManager.deleteAccount("acc-1", true) }
        coVerify(exactly = 0) { accountRepository.deleteAccount(any(), any()) }
    }

    @Test
    fun `handleUnauthorizedLogout marks active account expired and clears tokens`() = runTest {
        val active = account("acc-1")
        every { accountRepository.getActiveAccount() } returns flowOf(active)

        val result = sessionManager.handleUnauthorizedLogout("acc-1")

        assertThat(result).isEqualTo(active)
        coVerify { accountRepository.markAccountExpired("acc-1") }
        coVerify { accountRepository.clearAccountTokens("acc-1") }
    }

    @Test
    fun `handleUnauthorizedLogout returns null when accountId is null`() = runTest {
        val result = sessionManager.handleUnauthorizedLogout(null)

        assertThat(result).isNull()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `removeAccountFromDevice delegates to repository`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        val active = account("acc-1")
        every { accountRepository.getActiveAccount() } returns flowOf(active)

        val result = sessionManager.removeAccountFromDevice("acc-1", null)

        assertThat(result).isTrue()
        coVerify { accountRepository.removeAccountFromDevice("acc-1", null, true) }
    }
}
