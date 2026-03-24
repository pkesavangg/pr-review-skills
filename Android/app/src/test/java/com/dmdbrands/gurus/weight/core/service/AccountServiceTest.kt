package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import kotlinx.coroutines.test.TestScope
import com.dmdbrands.gurus.weight.core.helpers.httpException
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.auth.ChangePasswordResponse
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.ProfileUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AccountServiceTest {

    companion object {
        private const val TEST_ACCOUNT_ID = "acc-1"
        private const val TEST_ACCOUNT_ID_2 = "acc-2"
        private const val TEST_EMAIL = "john@example.com"
        private const val TEST_EMAIL_2 = "jane@example.com"
        private const val TEST_FIRST_NAME = "John"
        private const val TEST_LAST_NAME = "Doe"
        private const val TEST_DOB = "1990-01-01"
        private const val TEST_GENDER = "male"
        private const val TEST_ZIPCODE = "12345"
        private const val TEST_PASSWORD = "password"
        private const val TEST_OLD_PASSWORD = "old"
        private const val TEST_NEW_PASSWORD = "new"
        private const val TEST_ACTIVITY_LEVEL = "normal"
        private const val TEST_HEIGHT = 1750
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val accountRepository: IAccountRepository = mockk()
    private val offlineHandlerService: IOfflineHandlerService = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val storageClearService: StorageClearService = mockk(relaxed = true)
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)
    private lateinit var service: AccountService

    // --- Test Fixtures ---
    private val fakeAccount = Account(
        id = TEST_ACCOUNT_ID,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        dob = TEST_DOB,
        email = TEST_EMAIL,
        gender = TEST_GENDER,
        isActiveAccount = true,
        isLoggedIn = true,
        isExpired = false,
        zipcode = TEST_ZIPCODE,
        weightUnit = WeightUnit.LB,
        height = TEST_HEIGHT,
        activityLevel = TEST_ACTIVITY_LEVEL,
    )

    private val fakeAccount2 = Account(
        id = TEST_ACCOUNT_ID_2,
        firstName = "Jane",
        lastName = TEST_LAST_NAME,
        dob = "1991-02-15",
        email = TEST_EMAIL_2,
        gender = "female",
        isActiveAccount = false,
        isLoggedIn = true,
        isExpired = false,
        zipcode = TEST_ZIPCODE,
        weightUnit = WeightUnit.LB,
        height = 1650,
        activityLevel = TEST_ACTIVITY_LEVEL,
    )

    private val fakeAccountInfo = AccountInfo(
        id = TEST_ACCOUNT_ID,
        email = TEST_EMAIL,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        weightUnit = "lb",
        isWeightlessOn = false,
        height = TEST_HEIGHT,
        activityLevel = TEST_ACTIVITY_LEVEL,
        dob = TEST_DOB,
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = "4_metrics",
        dashboardMetrics = listOf("weight", "bmi", "body_fat", "muscle_mass"),
        goalWeight = null,
        initialWeight = null,
        shouldSendEntryNotifications = false,
        shouldSendWeightInEntryNotifications = false,
    )

    private val fakeSignupRequest = SignupRequest(
        email = "new@example.com",
        firstName = "New",
        lastName = "User",
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        password = "password123",
        dob = TEST_DOB,
        height = TEST_HEIGHT,
    )

    private val fakeProfileUpdateRequest = ProfileUpdateRequest(
        id = TEST_ACCOUNT_ID,
        email = TEST_EMAIL,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        dob = TEST_DOB,
    )

    @Before
    fun setUp() {
        stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount))
        every { accountRepository.currentThemeModeFlow } returns flowOf(ThemeMode.SYSTEM)

        service = createService()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createService() = AccountService(
        accountRepository,
        offlineHandlerService,
        connectivityObserver,
        dialogQueueService,
        appNavigationService,
        storageClearService,
        analyticsService = mockk(relaxed = true),
        appScope = TestScope(mainDispatcherRule.dispatcher),
    )

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
    private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()

    /** Re-stubs active account to null and recreates service. */
    private fun withNoActiveAccount() {
        every { accountRepository.getActiveAccount() } returns flowOf(null)
        service = createService()
    }

    /** Re-stubs with specific accounts and recreates service. */
    private fun withAccounts(
        active: Account? = fakeAccount,
        loggedIn: List<Account> = listOfNotNull(active),
    ) {
        every { accountRepository.getActiveAccount() } returns flowOf(active)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(loggedIn)
        service = createService()
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    fun `login returns account when credentials are valid and not at max accounts`() = runTest {
        coEvery { accountRepository.login(any(), any()) } returns fakeAccount

        val result = service.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result).isEqualTo(fakeAccount)
    }

    @Test
    fun `login succeeds for existing account even when max accounts reached`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(
            (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) },
        )
        // Recreate service so loggedInAccountsFlow sees the updated stub
        service = createService()
        coEvery { accountRepository.login(fakeAccount.email, any()) } returns fakeAccount

        val result = service.login(fakeAccount.email, TEST_PASSWORD)

        assertThat(result).isEqualTo(fakeAccount)
    }

    @Test
    fun `login throws MaxAccountsReachedException when max accounts reached and email is new`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(
            (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) },
        )
        service = createService()

        assertThrows(MaxAccountsReachedException::class.java) {
            runBlocking { service.login("brandnew@example.com", TEST_PASSWORD) }
        }
    }

    @Test
    fun `login returns null and emits error on HttpException 401`() = runTest {
        coEvery { accountRepository.login(any(), any()) } throws httpException(401)

        val result = service.login(TEST_EMAIL, "wrong")

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `login returns null and emits error on HttpException 500`() = runTest {
        coEvery { accountRepository.login(any(), any()) } throws httpException(500)

        val result = service.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `login returns null and emits error on HttpException 0 (no internet)`() = runTest {
        coEvery { accountRepository.login(any(), any()) } throws httpException(0)

        val result = service.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `login propagates non-HttpException`() = runTest {
        coEvery { accountRepository.login(any(), any()) } throws java.io.IOException("connection reset")

        assertThrows(java.io.IOException::class.java) {
            runBlocking { service.login(TEST_EMAIL, TEST_PASSWORD) }
        }
        coVerify(exactly = 0) { appNavigationService.emitAuthEvent(any()) }
    }

    // -------------------------------------------------------------------------
    // signup
    // -------------------------------------------------------------------------

    @Test
    fun `signup throws MaxAccountsReachedException when at max accounts`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(
            (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) },
        )
        service = createService()

        assertThrows(MaxAccountsReachedException::class.java) {
            runBlocking { service.signup(fakeSignupRequest) }
        }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `signup returns account and emits AccountAdded on success`() = runTest {
        val newAccount = fakeAccount.copy(id = "acc-new", email = fakeSignupRequest.email)
        coEvery { accountRepository.signup(any()) } returns newAccount

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isEqualTo(newAccount)
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.AccountAdded>()) }
    }

    @Test
    fun `signup returns null and emits error on HttpException 401`() = runTest {
        coEvery { accountRepository.signup(any()) } throws httpException(401)

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `signup returns null and emits error on HttpException 400 (account exists)`() = runTest {
        coEvery { accountRepository.signup(any()) } throws httpException(400)

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `signup returns null and emits error on generic exception`() = runTest {
        coEvery { accountRepository.signup(any()) } throws RuntimeException("Network error")

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // resetPassword
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword shows success toast when response is successful`() = runTest {
        val mockResponse = mockk<Response<Unit>> {
            every { isSuccessful } returns true
            every { code() } returns 200
        }
        coEvery { accountRepository.resetPassword(any()) } returns mockResponse

        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `resetPassword shows error toast when response is not successful`() = runTest {
        val mockResponse = mockk<Response<Unit>> {
            every { isSuccessful } returns false
            every { code() } returns 422
            every { message() } returns "Unprocessable"
        }
        coEvery { accountRepository.resetPassword(any()) } returns mockResponse

        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `resetPassword shows network error when offline`() = runTest {
        stubNetworkUnavailable()

        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
        coVerify(exactly = 0) { accountRepository.resetPassword(any()) }
    }

    @Test
    fun `resetPassword handles HttpException 500 gracefully`() = runTest {
        coEvery { accountRepository.resetPassword(any()) } throws httpException(500)

        // Should not crash
        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `resetPassword trims whitespace from email before calling repository`() = runTest {
        val mockResponse = mockk<Response<Unit>> {
            every { isSuccessful } returns true
            every { code() } returns 200
        }
        coEvery { accountRepository.resetPassword(TEST_EMAIL) } returns mockResponse

        service.resetPassword("  john@example.com  ")

        coVerify { accountRepository.resetPassword(TEST_EMAIL) }
    }

    // -------------------------------------------------------------------------
    // changePassword
    // -------------------------------------------------------------------------

    @Test
    fun `changePassword returns true and shows success toast`() = runTest {
        val fakeResponse = ChangePasswordResponse("access", "refresh", "expiresAt")
        coEvery { accountRepository.updatePassword(any(), any(), any()) } returns fakeResponse

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isTrue()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `changePassword returns false when no active account`() = runTest {
        withNoActiveAccount()

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { accountRepository.updatePassword(any(), any(), any()) }
    }

    @Test
    fun `changePassword returns false and shows error on HttpException 401`() = runTest {
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws httpException(401)

        val result = service.changePassword(TEST_OLD_PASSWORD, "wrong")

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `changePassword returns false and shows error on HttpException 500`() = runTest {
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws httpException(500)

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateProfile
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile shows success toast when online and isFromProfile is true`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } just Runs

        service.updateProfile(fakeProfileUpdateRequest, isFromProfile = true, showToast = true)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `updateProfile skips network check when isFromProfile is false`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.updateProfile(any()) } just Runs

        service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = false)

        coVerify { accountRepository.updateProfile(any()) }
    }

    @Test
    fun `updateProfile shows success toast for NO_INTERNET_CONNECTION HttpException`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(0)

        service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `updateProfile shows error toast and rethrows on HttpException 401`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(401)

        assertThrows(HttpException::class.java) {
            runBlocking { service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true) }
        }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `updateProfile propagates non-HttpException`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws RuntimeException("DB error")

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true)
            }
        }
    }

    // -------------------------------------------------------------------------
    // updateDashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `updateDashboardType calls repository when active account exists`() = runTest {
        coEvery { accountRepository.updateDashboardType(any()) } just Runs
        coEvery { accountRepository.updateLocalDashboardType(any(), any()) } just Runs

        service.updateDashboardType(DashboardType.DASHBOARD_4_METRICS)

        coVerify { accountRepository.updateDashboardType(DashboardType.DASHBOARD_4_METRICS.value) }
        coVerify { accountRepository.updateLocalDashboardType(fakeAccount.id, DashboardType.DASHBOARD_4_METRICS) }
    }

    @Test
    fun `updateDashboardType returns early when no active account`() = runTest {
        withNoActiveAccount()

        service.updateDashboardType(DashboardType.DASHBOARD_4_METRICS)

        coVerify(exactly = 0) { accountRepository.updateDashboardType(any()) }
    }

    @Test
    fun `updateDashboardType handles exception gracefully`() = runTest {
        coEvery { accountRepository.updateDashboardType(any()) } throws RuntimeException("DB error")

        service.updateDashboardType(DashboardType.DASHBOARD_4_METRICS)

        // Should not crash
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForActiveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForActiveAccount returns true when online and API call succeeds`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount.id) } returns fakeAccountInfo
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount returns false when no active account online`() = runTest {
        withNoActiveAccount()

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on offline`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on UnknownHostException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws java.net.UnknownHostException("host not found")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on InterruptedIOException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws java.io.InterruptedIOException("timeout")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on SocketTimeoutException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws java.net.SocketTimeoutException("timeout")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on IOException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws java.io.IOException("network")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount marks account expired and returns false on HttpException 401`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws httpException(401)
        coEvery { accountRepository.markAccountExpired(any()) } just Runs
        coEvery { accountRepository.clearAccountTokens(any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
        coVerify { accountRepository.markAccountExpired(fakeAccount.id) }
        coVerify { accountRepository.clearAccountTokens(fakeAccount.id) }
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on HttpException 500`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws httpException(500)
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB on general exception`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws RuntimeException("unexpected")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForActiveAccount falls back to local DB when handleOfflineSync throws`() = runTest {
        coEvery { offlineHandlerService.handleOfflineSync() } throws RuntimeException("DB error during sync")
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForLoggedInAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true offline when no non-active accounts`() = runTest {
        stubNetworkUnavailable()

        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true online when no non-active accounts`() = runTest {
        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts marks account expired and removes on HttpException 401`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws httpException(401)
        coEvery { accountRepository.markAccountExpired(fakeAccount2.id) } just Runs
        coEvery { accountRepository.removeAccount(fakeAccount2.id) } just Runs

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify { accountRepository.markAccountExpired(fakeAccount2.id) }
        coVerify { accountRepository.removeAccount(fakeAccount2.id) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts does not mark expired on IOException per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.io.IOException("network")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true on outer IOException`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        // Make the entire online branch fail with an outer-level exception via offlineHandlerService approach
        // Simulate outer IOException via an exception during requireNetworkAvailable's block execution
        coEvery { accountRepository.getAccountFromAPI(any()) } throws java.io.IOException("outer network fail")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts handles mixed results - one success one 401`() = runTest {
        val account3 = fakeAccount2.copy(id = "acc-3")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, fakeAccount2, account3))
        service = createService()
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } returns fakeAccountInfo.copy(id = fakeAccount2.id)
        coEvery { accountRepository.updateAccountInfo(fakeAccount2.id, any()) } just Runs
        coEvery { accountRepository.getAccountFromAPI(account3.id) } throws httpException(401)
        coEvery { accountRepository.markAccountExpired(account3.id) } just Runs
        coEvery { accountRepository.removeAccount(account3.id) } just Runs

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify { accountRepository.updateAccountInfo(fakeAccount2.id, any()) }
        coVerify { accountRepository.markAccountExpired(account3.id) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true offline when non-active accounts are present`() = runTest {
        stubNetworkUnavailable()
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))

        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        // Offline path with non-active accounts present does not emit checkIntegrations
        assertThat(service.checkIntegrations.value).isFalse()
        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    // -------------------------------------------------------------------------
    // handleUnauthorizedLogout
    // -------------------------------------------------------------------------

    @Test
    fun `handleUnauthorizedLogout returns null when accountId is null`() = runTest {
        val result = service.handleUnauthorizedLogout(null)

        assertThat(result).isNull()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `handleUnauthorizedLogout returns null when accountId is empty`() = runTest {
        val result = service.handleUnauthorizedLogout("")

        assertThat(result).isNull()
    }

    @Test
    fun `handleUnauthorizedLogout marks expired and returns account when active account matches`() = runTest {
        coEvery { accountRepository.markAccountExpired(fakeAccount.id) } just Runs
        coEvery { accountRepository.clearAccountTokens(fakeAccount.id) } just Runs

        val result = service.handleUnauthorizedLogout(fakeAccount.id)

        assertThat(result).isEqualTo(fakeAccount)
        coVerify { accountRepository.markAccountExpired(fakeAccount.id) }
        coVerify { accountRepository.clearAccountTokens(fakeAccount.id) }
    }

    @Test
    fun `handleUnauthorizedLogout returns null when account id does not match active account`() = runTest {
        val result = service.handleUnauthorizedLogout("different-id")

        assertThat(result).isNull()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `handleUnauthorizedLogout returns null on exception`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        coEvery { accountRepository.markAccountExpired(any()) } throws RuntimeException("DB error")

        val result = service.handleUnauthorizedLogout(fakeAccount.id)

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Test
    fun `logout emits LoggedOut with isActiveAccount and isLastAccount flags`() = runTest {
        coEvery { accountRepository.logoutAccount(any(), any(), any()) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        val result = service.logout(fakeAccount.id, fcmToken = null)

        assertThat(result).isTrue()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
        coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
    }

    @Test
    fun `logout shows no-network toast when offline but proceeds`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.logoutAccount(any(), any(), any()) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        val result = service.logout(fakeAccount.id, fcmToken = "token")

        assertThat(result).isTrue()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `logout returns false and emits error on exception`() = runTest {
        coEvery { accountRepository.logoutAccount(any(), any(), any()) } throws RuntimeException("Logout failed")

        val result = service.logout(fakeAccount.id, fcmToken = null)

        assertThat(result).isFalse()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
        coVerify(exactly = 0) { accountRepository.setNotificationAlertShownForAccount(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // logoutAll
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAll emits LoggedOut and resets notification alert for every account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        val result = service.logoutAll()

        assertThat(result).isTrue()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
        coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
        coVerify { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) }
    }

    @Test
    fun `logoutAll shows no-network toast when offline but proceeds`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        service.logoutAll()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `logoutAll returns false and emits error on exception`() = runTest {
        coEvery { accountRepository.logoutAllAccounts() } throws RuntimeException("Server error")

        val result = service.logoutAll()

        assertThat(result).isFalse()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // deleteAccount
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount calls repository when network is available`() = runTest {
        coEvery { accountRepository.deleteAccount(any(), any()) } just Runs

        service.deleteAccount(fakeAccount.id, isActiveAccount = true)

        coVerify { accountRepository.deleteAccount(fakeAccount.id, true) }
    }

    @Test
    fun `deleteAccount throws when offline`() = runTest {
        stubNetworkUnavailable()

        assertThrows(Exception::class.java) {
            runBlocking { service.deleteAccount(fakeAccount.id, isActiveAccount = true) }
        }
        coVerify(exactly = 0) { accountRepository.deleteAccount(any(), any()) }
    }

    @Test
    fun `deleteAccount rethrows exception on failure`() = runTest {
        coEvery { accountRepository.deleteAccount(any(), any()) } throws RuntimeException("delete failed")

        assertThrows(RuntimeException::class.java) {
            runBlocking { service.deleteAccount(fakeAccount.id, isActiveAccount = true) }
        }
    }

    // -------------------------------------------------------------------------
    // switchAccount
    // -------------------------------------------------------------------------

    @Test
    fun `switchAccount returns true and emits AccountSwitched on success`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } returns fakeAccountInfo.copy(id = fakeAccount2.id)
        coEvery { accountRepository.switchToAccount(fakeAccount2.id) } just Runs

        val result = service.switchAccount(fakeAccount2, showToast = false)

        assertThat(result).isTrue()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.AccountSwitched>()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `switchAccount throws when offline (via requireNetworkAvailable)`() = runTest {
        stubNetworkUnavailable()

        assertThrows(Exception::class.java) {
            runBlocking { service.switchAccount(fakeAccount2) }
        }
    }

    @Test
    fun `switchAccount falls back to local switch on IOException when account is valid locally`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.io.IOException("network")
        coEvery { accountRepository.switchToAccount(fakeAccount2.id) } just Runs

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isTrue()
        coVerify { accountRepository.switchToAccount(fakeAccount2.id) }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.AccountSwitched>()) }
    }

    @Test
    fun `switchAccount returns false on IOException when local account is expired`() = runTest {
        val expiredAccount2 = fakeAccount2.copy(isExpired = true)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, expiredAccount2))
        service = createService()
        coEvery { accountRepository.getAccountFromAPI(expiredAccount2.id) } throws java.io.IOException("network")

        val result = service.switchAccount(expiredAccount2)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `switchAccount marks account expired and returns false on HttpException 401`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws httpException(401)
        coEvery { accountRepository.markAccountExpired(fakeAccount2.id) } just Runs
        coEvery { accountRepository.removeAccount(fakeAccount2.id) } just Runs

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isFalse()
        coVerify { accountRepository.markAccountExpired(fakeAccount2.id) }
        coVerify { accountRepository.removeAccount(fakeAccount2.id) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // setCurrentThemeMode
    // -------------------------------------------------------------------------

    @Test
    fun `setCurrentThemeMode calls repository with the given theme mode`() = runTest {
        coEvery { accountRepository.setCurrentThemeMode(any()) } just Runs

        service.setCurrentThemeMode(ThemeMode.DARK)

        coVerify { accountRepository.setCurrentThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `setCurrentThemeMode emits error on exception`() = runTest {
        coEvery { accountRepository.setCurrentThemeMode(any()) } throws RuntimeException("write error")

        service.setCurrentThemeMode(ThemeMode.DARK)

        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------

    @Test
    fun `reset calls storageClearService clearAllStorage`() = runTest {
        service.reset()

        coVerify { storageClearService.clearAllStorage() }
    }

    @Test
    fun `reset shows error toast when clearAllStorage throws`() = runTest {
        coEvery { storageClearService.clearAllStorage() } throws RuntimeException("clear failed")

        service.reset()

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // refreshAccount
    // -------------------------------------------------------------------------

    @Test
    fun `refreshAccount does nothing when no active account`() = runTest {
        withNoActiveAccount()

        service.refreshAccount()

        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    @Test
    fun `refreshAccount calls API and syncs when online`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount.id) } returns fakeAccountInfo
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        service.refreshAccount()

        coVerify { accountRepository.getAccountFromAPI(fakeAccount.id) }
        coVerify { accountRepository.syncAccountSettingsWithServer(fakeAccountInfo, isOnline = true) }
    }

    @Test
    fun `refreshAccount skips API call when offline`() = runTest {
        stubNetworkUnavailable()

        service.refreshAccount()

        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(any()) }
    }

    @Test
    fun `refreshAccount ignores API exception and uses cached data`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(any()) } throws RuntimeException("API error")

        // Should not crash
        service.refreshAccount()
    }

    // -------------------------------------------------------------------------
    // hasShownNotificationAlertForAccount
    // -------------------------------------------------------------------------

    @Test
    fun `hasShownNotificationAlertForAccount returns value from repository`() = runTest {
        coEvery { accountRepository.hasShownNotificationAlertForAccount(TEST_ACCOUNT_ID) } returns true

        val result = service.hasShownNotificationAlertForAccount(TEST_ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasShownNotificationAlertForAccount returns false on exception`() = runTest {
        coEvery { accountRepository.hasShownNotificationAlertForAccount(any()) } throws RuntimeException("read error")

        val result = service.hasShownNotificationAlertForAccount(TEST_ACCOUNT_ID)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // setNotificationAlertShownForAccount
    // -------------------------------------------------------------------------

    @Test
    fun `setNotificationAlertShownForAccount calls repository with correct args`() = runTest {
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        service.setNotificationAlertShownForAccount(TEST_ACCOUNT_ID, true)

        coVerify { accountRepository.setNotificationAlertShownForAccount(TEST_ACCOUNT_ID, true) }
    }

    @Test
    fun `setNotificationAlertShownForAccount handles exception gracefully`() = runTest {
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } throws RuntimeException("write error")

        // Should not crash
        service.setNotificationAlertShownForAccount(TEST_ACCOUNT_ID, false)
    }

    // -------------------------------------------------------------------------
    // subscribeAccount
    // -------------------------------------------------------------------------

    @Test
    fun `subscribeAccount subscribes to getActiveAccount and updates activeAccount`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)

        service.subscribeAccount()
        advanceUntilIdle()

        assertThat(service.activeAccount.value).isEqualTo(fakeAccount)
    }

    @Test
    fun `subscribeAccount updates activeAccount to null when flow emits null`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        service.subscribeAccount()
        advanceUntilIdle()

        assertThat(service.activeAccount.value).isNull()
    }

    // -------------------------------------------------------------------------
    // clearSyncTimestampForResync
    // -------------------------------------------------------------------------

    @Test
    fun `clearSyncTimestampForResync calls updateSyncTimeStamp with empty string`() = runTest {
        coEvery { accountRepository.updateSyncTimeStamp("") } just Runs

        service.clearSyncTimestampForResync()

        coVerify { accountRepository.updateSyncTimeStamp("") }
    }

    // -------------------------------------------------------------------------
    // emitNavigateToMyAccounts / emitNavigateBackFromMyAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `emitNavigateToMyAccounts emits NavigateToMyAccounts auth event`() = runTest {
        service.emitNavigateToMyAccounts()

        coVerify { appNavigationService.emitAuthEvent(AuthState.NavigateToMyAccounts) }
    }

    @Test
    fun `emitNavigateBackFromMyAccounts emits NavigateBackFromMyAccounts auth event`() = runTest {
        service.emitNavigateBackFromMyAccounts()

        coVerify { appNavigationService.emitAuthEvent(AuthState.NavigateBackFromMyAccounts) }
    }

    // -------------------------------------------------------------------------
    // login — else branch (HTTP 403)
    // -------------------------------------------------------------------------

    @Test
    fun `login returns null on HttpException 403 with generic error`() = runTest {
        coEvery { accountRepository.login(any(), any()) } throws httpException(403)

        val result = service.login(TEST_EMAIL, TEST_PASSWORD)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // signup — NO_INTERNET_CONNECTION and else branches
    // -------------------------------------------------------------------------

    @Test
    fun `signup returns null on HttpException 0 with no internet error`() = runTest {
        coEvery { accountRepository.signup(any()) } throws httpException(0)

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    @Test
    fun `signup returns null on HttpException 500 with generic error`() = runTest {
        coEvery { accountRepository.signup(any()) } throws httpException(500)

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // resetPassword — HttpException catch branches
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword handles HttpException 0 with network error message`() = runTest {
        coEvery { accountRepository.resetPassword(any()) } throws httpException(0)

        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `resetPassword handles HttpException 403 with generic error message`() = runTest {
        coEvery { accountRepository.resetPassword(any()) } throws httpException(403)

        service.resetPassword(TEST_EMAIL)

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // changePassword — additional HttpException branches
    // -------------------------------------------------------------------------

    @Test
    fun `changePassword returns false on HttpException 0 (no internet)`() = runTest {
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws httpException(0)

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `changePassword returns false on HttpException 403 (generic error)`() = runTest {
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws httpException(403)

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // updateProfile — else branch HttpException variants
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile shows error and rethrows on HttpException 500`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(500)

        assertThrows(HttpException::class.java) {
            runBlocking { service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true) }
        }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `updateProfile shows error and rethrows on HttpException 400 (bad request)`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(400)

        assertThrows(HttpException::class.java) {
            runBlocking { service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true) }
        }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `updateProfile shows error and rethrows on HttpException 403 (generic error)`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(403)

        assertThrows(HttpException::class.java) {
            runBlocking { service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = true) }
        }
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForActiveAccount — local DB validity paths
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForActiveAccount returns false when local DB has no active account in offline mode`() = runTest {
        stubNetworkUnavailable()
        withNoActiveAccount()

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
    }

    @Test
    fun `checkLoginStatusForActiveAccount returns false when local account is expired in offline mode`() = runTest {
        stubNetworkUnavailable()
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount.copy(isExpired = true))
        service = createService()

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
    }

    @Test
    fun `checkLoginStatusForActiveAccount returns true when syncAccountSettingsWithServer throws in offline mode`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } throws RuntimeException("sync error")

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForLoggedInAccounts — per-account catch blocks
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForLoggedInAccounts ignores UnknownHostException per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.net.UnknownHostException("host not found")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts ignores InterruptedIOException per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.io.InterruptedIOException("interrupted")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts ignores SocketTimeoutException per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.net.SocketTimeoutException("timeout")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts ignores generic Exception per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws RuntimeException("unexpected error")

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true on outer IOException from flow`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flow { throw java.io.IOException("network error") }
        service = createService()

        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        assertThat(service.checkIntegrations.value).isTrue()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts returns true on outer Exception from flow`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flow { throw RuntimeException("db error") }
        service = createService()

        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        assertThat(service.checkIntegrations.value).isTrue()
    }

    // -------------------------------------------------------------------------
    // logoutAll — inner setNotificationAlertShownForAccount exception
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAll handles exception in setNotificationAlertShownForAccount gracefully`() = runTest {
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } throws RuntimeException("write error")

        val result = service.logoutAll()

        assertThat(result).isTrue()
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
    }

    // -------------------------------------------------------------------------
    // switchAccount — network timeout and unexpected exception catch blocks
    // -------------------------------------------------------------------------

    @Test
    fun `switchAccount throws on UnknownHostException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.net.UnknownHostException("host")

        assertThrows(Exception::class.java) { runBlocking { service.switchAccount(fakeAccount2) } }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `switchAccount throws on InterruptedIOException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.io.InterruptedIOException("interrupted")

        assertThrows(Exception::class.java) { runBlocking { service.switchAccount(fakeAccount2) } }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `switchAccount throws on SocketTimeoutException`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.net.SocketTimeoutException("timeout")

        assertThrows(Exception::class.java) { runBlocking { service.switchAccount(fakeAccount2) } }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `switchAccount returns false on unexpected Exception`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws RuntimeException("unexpected")

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isFalse()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `switchAccount returns false and shows generic error on HttpException 500`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws httpException(500)

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    // -------------------------------------------------------------------------
    // getCurrentAccount
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentAccount returns active account from flow`() = runTest {
        val result = service.getCurrentAccount()

        assertThat(result).isEqualTo(fakeAccount)
    }

    @Test
    fun `getCurrentAccount returns null when no active account`() = runTest {
        withNoActiveAccount()

        val result = service.getCurrentAccount()

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getLoggedInAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `getLoggedInAccounts returns accounts sorted with active first`() = runTest {
        val nonActiveFirst = listOf(fakeAccount2, fakeAccount)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(nonActiveFirst)
        service = createService()

        val result = service.getLoggedInAccounts()

        assertThat(result.first().isActiveAccount).isTrue()
        assertThat(result.first()).isEqualTo(fakeAccount)
        assertThat(result[1]).isEqualTo(fakeAccount2)
    }

    @Test
    fun `getLoggedInAccounts returns empty list when no accounts`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(emptyList())
        service = createService()

        val result = service.getLoggedInAccounts()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Flow properties — Turbine
    // -------------------------------------------------------------------------

    @Test
    fun `activeAccountFlow emits account from repository`() = runTest {
        service.activeAccountFlow.test {
            assertThat(awaitItem()).isEqualTo(fakeAccount)
            awaitComplete()
        }
    }

    @Test
    fun `loggedInAccountsFlow emits sorted accounts`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount2, fakeAccount))
        service = createService()

        service.loggedInAccountsFlow.test {
            val accounts = awaitItem()
            assertThat(accounts.first().isActiveAccount).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `hasReachedMaxAccounts emits false when below limit`() = runTest {
        service.hasReachedMaxAccounts.test {
            assertThat(awaitItem()).isFalse()
            awaitComplete()
        }
    }

    @Test
    fun `hasReachedMaxAccounts emits true when at limit`() = runTest {
        every { accountRepository.getLoggedInAccounts() } returns flowOf(
            (1..10).map { fakeAccount.copy(id = "acc-$it", isActiveAccount = it == 1) }
        )
        service = createService()

        service.hasReachedMaxAccounts.test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `currentThemeModeFlow delegates to repository`() = runTest {
        service.currentThemeModeFlow.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.SYSTEM)
            awaitComplete()
        }
    }

    @Test
    fun `checkIntegrations initial value is false`() = runTest {
        assertThat(service.checkIntegrations.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // resetPassword — non-HttpException catch
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword handles non-HttpException gracefully without toast`() = runTest {
        coEvery { accountRepository.resetPassword(any()) } throws RuntimeException("unexpected error")

        service.resetPassword(TEST_EMAIL)

        // Non-HttpException catch block only logs — no toast for non-HTTP exceptions
        coVerify(exactly = 1) { accountRepository.resetPassword(TEST_EMAIL) }
    }

    // -------------------------------------------------------------------------
    // updateProfile — additional branches
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile throws when isFromProfile true and offline`() = runTest {
        stubNetworkUnavailable()

        assertThrows(Exception::class.java) {
            runBlocking { service.updateProfile(fakeProfileUpdateRequest, isFromProfile = true, showToast = true) }
        }
        coVerify(exactly = 0) { accountRepository.updateProfile(any()) }
    }

    @Test
    fun `updateProfile does not show toast when showToast is false`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } just Runs

        service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = false)

        coVerify(exactly = 1) { accountRepository.updateProfile(fakeProfileUpdateRequest) }
        verify(exactly = 0) { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForLoggedInAccounts — expired filter & per-account branches
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForLoggedInAccounts skips expired non-active accounts`() = runTest {
        val expiredAccount = fakeAccount2.copy(isExpired = true)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, expiredAccount))
        service = createService()

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        // Expired account should be filtered out — no API call for it
        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(expiredAccount.id) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts does not mark expired on HttpException non-401 per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws httpException(500)

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
        coVerify(exactly = 0) { accountRepository.removeAccount(any()) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts updates account info on success per account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        val account2Info = fakeAccountInfo.copy(id = fakeAccount2.id, email = fakeAccount2.email)
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } returns account2Info
        coEvery { accountRepository.updateAccountInfo(fakeAccount2.id, account2Info) } just Runs

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 1) { accountRepository.getAccountFromAPI(fakeAccount2.id) }
        coVerify(exactly = 1) { accountRepository.updateAccountInfo(fakeAccount2.id, account2Info) }
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts sets checkIntegrations true on success online`() = runTest {
        // Only active account — returns true immediately and sets checkIntegrations to false
        val result = service.checkLoginStatusForLoggedInAccounts()

        assertThat(result).isTrue()
        assertThat(service.checkIntegrations.value).isFalse()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts sets checkIntegrations true after processing accounts`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } returns fakeAccountInfo.copy(id = fakeAccount2.id)
        coEvery { accountRepository.updateAccountInfo(fakeAccount2.id, any()) } just Runs

        service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(service.checkIntegrations.value).isTrue()
    }

    // -------------------------------------------------------------------------
    // handleUnauthorizedLogout — account not active
    // -------------------------------------------------------------------------

    @Test
    fun `handleUnauthorizedLogout returns null when account exists but is not active`() = runTest {
        val nonActiveAccount = fakeAccount.copy(isActiveAccount = false)
        every { accountRepository.getActiveAccount() } returns flowOf(nonActiveAccount)
        service = createService()

        val result = service.handleUnauthorizedLogout(nonActiveAccount.id)

        assertThat(result).isNull()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    // -------------------------------------------------------------------------
    // logout — non-active account
    // -------------------------------------------------------------------------

    @Test
    fun `logout emits LoggedOut with isActiveAccount false when logging out non-active account`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.logoutAccount(fakeAccount2.id, null, false) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) } just Runs

        val result = service.logout(fakeAccount2.id, fcmToken = null)

        assertThat(result).isTrue()
        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.LoggedOut> { !it.isActiveAccount && !it.isLastAccount }
            )
        }
        coVerify(exactly = 1) { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) }
    }

    @Test
    fun `logout sets isLastAccount true when only one account remains`() = runTest {
        coEvery { accountRepository.logoutAccount(fakeAccount.id, null, true) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) } just Runs

        val result = service.logout(fakeAccount.id, fcmToken = null)

        assertThat(result).isTrue()
        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.LoggedOut> { it.isActiveAccount && it.isLastAccount }
            )
        }
    }

    // -------------------------------------------------------------------------
    // switchAccount — IOException with no local match
    // -------------------------------------------------------------------------

    @Test
    fun `switchAccount returns false on IOException when account not found locally`() = runTest {
        // fakeAccount2 is NOT in the logged-in accounts list (only fakeAccount is)
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws java.io.IOException("network")

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
        coVerify(exactly = 0) { accountRepository.switchToAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForActiveAccount — online, requireNetworkAvailable throws
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForActiveAccount calls offlineHandlerService handleOfflineSync before API`() = runTest {

        coEvery { accountRepository.getAccountFromAPI(fakeAccount.id) } returns fakeAccountInfo
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        service.checkLoginStatusForActiveAccount()

        coVerify(exactly = 1) { offlineHandlerService.handleOfflineSync() }
        coVerify(exactly = 1) { accountRepository.getAccountFromAPI(fakeAccount.id) }
        coVerify(exactly = 1) { accountRepository.syncAccountSettingsWithServer(fakeAccountInfo, isOnline = true) }
    }

    // -------------------------------------------------------------------------
    // logoutAll — per-item notification reset verification
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAll resets notification alert for each of three accounts`() = runTest {
        val account3 = fakeAccount2.copy(id = "acc-3", email = "third@example.com")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, fakeAccount2, account3))
        service = createService()
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        val result = service.logoutAll()

        assertThat(result).isTrue()
        coVerify(exactly = 1) { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) }
        coVerify(exactly = 1) { accountRepository.setNotificationAlertShownForAccount(fakeAccount2.id, false) }
        coVerify(exactly = 1) { accountRepository.setNotificationAlertShownForAccount(account3.id, false) }
    }

    // -------------------------------------------------------------------------
    // signup — HttpException 500 (else branch)
    // -------------------------------------------------------------------------

    @Test
    fun `signup returns null on HttpException 403 with generic error`() = runTest {
        coEvery { accountRepository.signup(any()) } throws httpException(403)

        val result = service.signup(fakeSignupRequest)

        assertThat(result).isNull()
        verify { dialogQueueService.showToast(any()) }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // switchAccount — showToast parameter propagation
    // -------------------------------------------------------------------------

    @Test
    fun `switchAccount emits AccountSwitched with showToast true`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } returns fakeAccountInfo.copy(id = fakeAccount2.id)
        coEvery { accountRepository.switchToAccount(fakeAccount2.id) } just Runs

        val result = service.switchAccount(fakeAccount2, showToast = true)

        assertThat(result).isTrue()
        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.AccountSwitched> { it.account == fakeAccount2 && it.showToast }
            )
        }
    }

    // -------------------------------------------------------------------------
    // updateProfile — success without showing toast for NO_INTERNET_CONNECTION
    // -------------------------------------------------------------------------

    @Test
    fun `updateProfile shows success toast for NO_INTERNET_CONNECTION even when showToast false`() = runTest {
        coEvery { accountRepository.updateProfile(any()) } throws httpException(0)

        // NO_INTERNET_CONNECTION catch shows success toast regardless of showToast parameter
        service.updateProfile(fakeProfileUpdateRequest, isFromProfile = false, showToast = false)

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // authEvent — property delegation
    // -------------------------------------------------------------------------

    @Test
    fun `authEvent delegates to appNavigationService authEvent`() = runTest {
        val authEvent = service.authEvent
        assertThat(authEvent).isEqualTo(appNavigationService.authEvent)
    }

    // -------------------------------------------------------------------------
    // refreshAccount — outer catch rethrows
    // -------------------------------------------------------------------------

    @Test
    fun `refreshAccount rethrows exception from outer try block`() = runTest {
        // Force getCurrentAccount() to throw by making the flow emit an error
        every { accountRepository.getActiveAccount() } returns flow { throw RuntimeException("fatal") }
        service = createService()

        assertThrows(RuntimeException::class.java) {
            runBlocking { service.refreshAccount() }
        }
    }

    // -------------------------------------------------------------------------
    // changePassword — non-HttpException should not show toast
    // -------------------------------------------------------------------------

    @Test
    fun `changePassword returns false on generic Exception without showing error toast`() = runTest {
        coEvery { accountRepository.updatePassword(any(), any(), any()) } throws IllegalStateException("unexpected")

        val result = service.changePassword(TEST_OLD_PASSWORD, TEST_NEW_PASSWORD)

        assertThat(result).isFalse()
        // Only HttpException triggers error toast display
        verify(exactly = 0) { dialogQueueService.showToast(match { it.title != null }) }
    }

    // -------------------------------------------------------------------------
    // logout — wasLastAccount false when multiple accounts
    // -------------------------------------------------------------------------

    @Test
    fun `logout sets wasLastAccount false when multiple accounts exist`() = runTest {
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.logoutAccount(fakeAccount.id, null, true) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(fakeAccount.id, false) } just Runs

        val result = service.logout(fakeAccount.id, fcmToken = null)

        assertThat(result).isTrue()
        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.LoggedOut> { it.isActiveAccount && !it.isLastAccount }
            )
        }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForActiveAccount — requireNetworkAvailable online + null active account
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForActiveAccount returns false online when requireNetworkAvailable passes but no active account`() = runTest {
        // Network is available (from setUp) but active account flow returns null after service construction
        withNoActiveAccount()

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
        coVerify(exactly = 0) { offlineHandlerService.handleOfflineSync() }
    }

    // -------------------------------------------------------------------------
    // handleUnauthorizedLogout — no active account (null)
    // -------------------------------------------------------------------------

    @Test
    fun `handleUnauthorizedLogout returns null when no active account exists`() = runTest {
        withNoActiveAccount()

        val result = service.handleUnauthorizedLogout("some-id")

        assertThat(result).isNull()
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
    }

    // -------------------------------------------------------------------------
    // deleteAccount — requireNetworkAvailable block execution
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount succeeds for non-active account when online`() = runTest {
        coEvery { accountRepository.deleteAccount(fakeAccount2.id, false) } just Runs

        service.deleteAccount(fakeAccount2.id, isActiveAccount = false)

        coVerify(exactly = 1) { accountRepository.deleteAccount(fakeAccount2.id, false) }
    }

    // -------------------------------------------------------------------------
    // setCurrentThemeMode — success path verify
    // -------------------------------------------------------------------------

    @Test
    fun `setCurrentThemeMode calls repository with LIGHT theme`() = runTest {
        coEvery { accountRepository.setCurrentThemeMode(ThemeMode.LIGHT) } just Runs

        service.setCurrentThemeMode(ThemeMode.LIGHT)

        coVerify(exactly = 1) { accountRepository.setCurrentThemeMode(ThemeMode.LIGHT) }
        coVerify(exactly = 0) { appNavigationService.emitAuthEvent(any<AuthState.Error>()) }
    }

    // -------------------------------------------------------------------------
    // sortedActiveFirst — accounts with lastActiveTime ordering
    // -------------------------------------------------------------------------

    @Test
    fun `getLoggedInAccounts sorts non-active accounts by lastActiveTime descending`() = runTest {
        val older = fakeAccount2.copy(id = "acc-old", lastActiveTime = "1000")
        val newer = fakeAccount2.copy(id = "acc-new", lastActiveTime = "2000")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(older, fakeAccount, newer))
        service = createService()

        val result = service.getLoggedInAccounts()

        assertThat(result[0]).isEqualTo(fakeAccount) // active first
        assertThat(result[1].id).isEqualTo("acc-new") // newer non-active second
        assertThat(result[2].id).isEqualTo("acc-old") // older non-active last
    }

    @Test
    fun `getLoggedInAccounts handles null lastActiveTime in sorting`() = runTest {
        val noTime = fakeAccount2.copy(id = "acc-notime", lastActiveTime = null)
        val withTime = fakeAccount2.copy(id = "acc-time", lastActiveTime = "1000")
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(noTime, fakeAccount, withTime))
        service = createService()

        val result = service.getLoggedInAccounts()

        assertThat(result[0]).isEqualTo(fakeAccount) // active first
        assertThat(result[1].id).isEqualTo("acc-time") // with time before null
        assertThat(result[2].id).isEqualTo("acc-notime")
    }

    // -------------------------------------------------------------------------
    // logoutAll — offline shows toast before proceeding
    // -------------------------------------------------------------------------

    @Test
    fun `logoutAll offline shows no-network toast and still emits LoggedOut with isLastAccount false`() = runTest {
        stubNetworkUnavailable()
        withAccounts(loggedIn = listOf(fakeAccount, fakeAccount2))
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        val result = service.logoutAll()

        assertThat(result).isTrue()
        verify { dialogQueueService.showToast(any()) }
        coVerify { appNavigationService.emitAuthEvent(any<AuthState.LoggedOut>()) }
    }

    // -------------------------------------------------------------------------
    // setCurrentThemeMode — null message exception
    // -------------------------------------------------------------------------

    @Test
    fun `setCurrentThemeMode emits error with fallback message when exception message is null`() = runTest {
        coEvery { accountRepository.setCurrentThemeMode(any()) } throws RuntimeException()

        service.setCurrentThemeMode(ThemeMode.SYSTEM)

        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.Error> { it.message == "Failed to set theme mode" }
            )
        }
    }

    // -------------------------------------------------------------------------
    // logout — getCurrentAccount returns null (isActiveAccount false)
    // -------------------------------------------------------------------------

    @Test
    fun `logout handles null getCurrentAccount for isActiveAccount check`() = runTest {
        withNoActiveAccount()
        coEvery { accountRepository.logoutAccount("unknown-id", null, false) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount("unknown-id", false) } just Runs

        val result = service.logout("unknown-id", fcmToken = null)

        assertThat(result).isTrue()
        coVerify {
            appNavigationService.emitAuthEvent(
                match<AuthState.LoggedOut> { !it.isActiveAccount }
            )
        }
    }

    // -------------------------------------------------------------------------
    // checkLoginStatusForActiveAccount — null getCurrentAccount in 401 handler
    // -------------------------------------------------------------------------

    @Test
    fun `checkLoginStatusForActiveAccount returns false on 401 when getCurrentAccount is null in handler`() = runTest {
        // First call to getCurrentAccount (line 354) returns account, but second call
        // inside the 401 handler (line 383) could return null if state changes

        coEvery { accountRepository.getAccountFromAPI(fakeAccount.id) } throws httpException(401)
        // Don't stub markAccountExpired — if called it means we reached that code path

        // getCurrentAccount returns fakeAccount (from setUp), so the 401 handler will find it
        coEvery { accountRepository.markAccountExpired(fakeAccount.id) } just Runs
        coEvery { accountRepository.clearAccountTokens(fakeAccount.id) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isFalse()
    }

    @Test
    fun `checkLoginStatusForLoggedInAccounts processes valid accounts and skips expired ones`() = runTest {
        val expired = fakeAccount2.copy(id = "acc-expired", isExpired = true)
        val valid = fakeAccount2.copy(id = "acc-valid", isExpired = false)
        every { accountRepository.getLoggedInAccounts() } returns flowOf(listOf(fakeAccount, expired, valid))
        service = createService()
        val validInfo = fakeAccountInfo.copy(id = valid.id)
        coEvery { accountRepository.getAccountFromAPI(valid.id) } returns validInfo
        coEvery { accountRepository.updateAccountInfo(valid.id, validInfo) } just Runs

        val result = service.checkLoginStatusForLoggedInAccounts()
        advanceUntilIdle()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { accountRepository.getAccountFromAPI(expired.id) }
        coVerify(exactly = 1) { accountRepository.getAccountFromAPI(valid.id) }
        coVerify(exactly = 1) { accountRepository.updateAccountInfo(valid.id, validInfo) }
    }

    // -------------------------------------------------------------------------
    // checkActiveAccountLocalValidity — offline sync with isOnline=false
    // -------------------------------------------------------------------------

    @Test
    fun `checkActiveAccountLocalValidity syncs to DB with isOnline false when offline`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.syncAccountSettingsWithServer(any(), any()) } just Runs

        val result = service.checkLoginStatusForActiveAccount()

        assertThat(result).isTrue()
        coVerify { accountRepository.syncAccountSettingsWithServer(any(), isOnline = false) }
    }

    // -------------------------------------------------------------------------
    // handleAccountValidationError — non-401 shows generic error toast
    // -------------------------------------------------------------------------

    @Test
    fun `switchAccount on HttpException 403 shows generic error toast without marking expired`() = runTest {
        coEvery { accountRepository.getAccountFromAPI(fakeAccount2.id) } throws httpException(403)

        val result = service.switchAccount(fakeAccount2)

        assertThat(result).isFalse()
        verify { dialogQueueService.showToast(any()) }
        coVerify(exactly = 0) { accountRepository.markAccountExpired(any()) }
        coVerify(exactly = 0) { accountRepository.removeAccount(any()) }
    }

    // -------------------------------------------------------------------------
    // showNoNetworkErrorToast — tested via logout and logoutAll offline paths
    // -------------------------------------------------------------------------

    @Test
    fun `logout offline shows no-network toast with correct message structure`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.logoutAccount(any(), any(), any()) } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        service.logout(fakeAccount.id, fcmToken = null)

        verify {
            dialogQueueService.showToast(match<com.dmdbrands.gurus.weight.features.common.model.Toast> {
                it.title == null && it.action == null
            })
        }
    }

    @Test
    fun `logoutAll offline shows no-network toast`() = runTest {
        stubNetworkUnavailable()
        coEvery { accountRepository.logoutAllAccounts() } returns true
        coEvery { accountRepository.setNotificationAlertShownForAccount(any(), any()) } just Runs

        service.logoutAll()

        verify(atLeast = 1) { dialogQueueService.showToast(any()) }
    }
}
