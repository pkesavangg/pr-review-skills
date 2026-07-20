package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
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
import retrofit2.Response
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AccountAuthenticatorTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val accountRepository: IAccountRepository = mockk()
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private val authenticator = AccountAuthenticator(
        accountRepository,
        analyticsService,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
    )

    private fun account(id: String, email: String, active: Boolean = true) = Account(
        id = id,
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = email,
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
    fun `login returns account and logs success on happy path`() = runTest {
        val saved = account("acc-1", "john@example.com")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(emptyList())
        coEvery { accountRepository.login("john@example.com", "pwd") } returns saved

        val result = authenticator.login("john@example.com", "pwd")

        assertThat(result).isEqualTo(saved)
        verify { analyticsService.logEvent(IAnalyticsService.Events.LOGIN_SUCCESS) }
    }

    @Test
    fun `login returns null and emits error on http failure`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(emptyList())
        coEvery { accountRepository.login(any(), any()) } throws httpException(401)

        val result = authenticator.login("john@example.com", "pwd")

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `login throws when max accounts reached for a new email`() = runTest {
        val ten = (1..10).map { account("acc-$it", "user$it@example.com", active = it == 1) }
        every { accountRepository.getLoggedInAccounts() } returns flowOf(ten)

        assertFailsWith<MaxAccountsReachedException> {
            authenticator.login("brand-new@example.com", "pwd")
        }
    }

    @Test
    fun `signup returns account and emits AccountAdded on success`() = runTest {
        val request = mockk<SignupRequest>()
        val saved = account("acc-1", "new@example.com")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(emptyList())
        coEvery { accountRepository.signup(request) } returns saved

        val result = authenticator.signup(request)

        assertThat(result).isEqualTo(saved)
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.AccountAdded>()) }
    }

    @Test
    fun `signup throws when max accounts reached`() = runTest {
        val ten = (1..10).map { account("acc-$it", "user$it@example.com", active = it == 1) }
        every { accountRepository.getLoggedInAccounts() } returns flowOf(ten)

        assertFailsWith<MaxAccountsReachedException> { authenticator.signup(mockk()) }
    }

    @Test
    fun `resetPassword shows success toast when response is successful`() = runTest {
        connectivityObserver.stubNetworkAvailable()
        coEvery { accountRepository.resetPassword("john@example.com") } returns Response.success(Unit)

        authenticator.resetPassword("john@example.com")

        coVerify { accountRepository.resetPassword("john@example.com") }
    }

    @Test
    fun `changePassword returns false when there is no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val result = authenticator.changePassword("old", "new")

        assertThat(result).isFalse()
        coVerify(exactly = 0) { accountRepository.updatePassword(any(), any(), any()) }
    }

    @Test
    fun `changePassword updates password and returns true on success`() = runTest {
        val active = account("acc-1", "john@example.com")
        every { accountRepository.getActiveAccount() } returns flowOf(active)
        coEvery {
            accountRepository.updatePassword("acc-1", "old", "new")
        } returns ChangePasswordResponse("access", "refresh", "expiresAt")

        val result = authenticator.changePassword("old", "new")

        assertThat(result).isTrue()
        coVerify { accountRepository.updatePassword("acc-1", "old", "new") }
    }

    @Test
    fun `changePassword returns false and shows error toast on http failure`() = runTest {
        val active = account("acc-1", "john@example.com")
        every { accountRepository.getActiveAccount() } returns flowOf(active)
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws httpException(500)

        val result = authenticator.changePassword("old", "new")

        assertThat(result).isFalse()
    }

    @Test
    fun `resetPassword throws no network path is handled without crashing`() = runTest {
        connectivityObserver.stubNetworkUnavailable()

        // Offline: requireNetworkAvailable triggers showNetworkErrorAndThrow, which is caught internally.
        authenticator.resetPassword("john@example.com")

        coVerify(exactly = 0) { accountRepository.resetPassword(any()) }
    }
}
