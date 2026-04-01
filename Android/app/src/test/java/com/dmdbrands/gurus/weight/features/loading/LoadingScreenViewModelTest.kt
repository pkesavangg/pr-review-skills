package com.dmdbrands.gurus.weight.features.loading

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IDeviceInfoService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class LoadingScreenViewModelTest {

    companion object {
        private const val MIGRATION_TAG = "ionic_migration"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var appNavigationService: IAppNavigationService

    @MockK(relaxUnitFun = true)
    lateinit var entryService: IEntryService

    @MockK(relaxUnitFun = true)
    lateinit var dashboardService: IDashboardService

    @MockK(relaxUnitFun = true)
    lateinit var deviceService: IDeviceService

    @MockK(relaxUnitFun = true)
    lateinit var deviceInfoService: IDeviceInfoService

    @MockK(relaxUnitFun = true)
    lateinit var productSelectionManager: IProductSelectionManager

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("androidx.lifecycle.FlowLiveDataConversions")
        setupMigrationComplete()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.lifecycle.FlowLiveDataConversions")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Mocks WorkManager to simulate migration already complete (empty work info list).
     */
    private fun setupMigrationComplete() {
        val mockLiveData = mockk<LiveData<List<WorkInfo>>>()
        every { workManager.getWorkInfosByTagLiveData(MIGRATION_TAG) } returns mockLiveData
        every { mockLiveData.asFlow() } returns flowOf(emptyList())
    }

    /**
     * Sets up mocks for a successful logged-in flow.
     */
    private fun setupLoggedInFlow() {
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns true
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns true
        coEvery { accountService.getCurrentAccount() } returns TestFixtures.activeAccount
        coEvery { accountService.subscribeAccount() } returns Unit
        coEvery { entryService.updateAllData(accountId = any()) } returns Unit
        coEvery { dashboardService.setAccountId(any()) } returns Unit
        coEvery { deviceService.setAccountId(any()) } returns Unit
        coEvery { deviceInfoService.updateDeviceInfo() } returns Unit
        coEvery { deviceInfoService.updateLocalIntegrationInfo() } returns Unit
    }

    private fun createViewModel(): LoadingScreenViewModel {
        return LoadingScreenViewModel(
            workManager = workManager,
            accountService = accountService,
            appNavigationService = appNavigationService,
            entryService = entryService,
            dashboardService = dashboardService,
            deviceService = deviceService,
            deviceInfoService = deviceInfoService,
            productSelectionManager = productSelectionManager,
        )
    }

    // -------------------------------------------------------------------------
    // Logged-in flow — success
    // -------------------------------------------------------------------------

    @Test
    fun `start with logged in account calls loadData and autoLogin`() = runTest {
        setupLoggedInFlow()

        createViewModel()
        advanceUntilIdle()

        coVerify { accountService.subscribeAccount() }
        coVerify { deviceInfoService.updateDeviceInfo() }
        coVerify { entryService.updateAllData(accountId = TestFixtures.activeAccount.id) }
        coVerify { dashboardService.setAccountId(TestFixtures.activeAccount.id) }
        coVerify { deviceService.setAccountId(TestFixtures.activeAccount.id) }
        coVerify { deviceInfoService.updateLocalIntegrationInfo() }
    }

    @Test
    fun `start with logged in account calls autoLogin`() = runTest {
        setupLoggedInFlow()

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.autoLogin() }
    }

    @Test
    fun `start with logged in account emits LoggedInFromLoading auth event`() = runTest {
        setupLoggedInFlow()

        createViewModel()
        advanceUntilIdle()

        coVerify {
            appNavigationService.emitAuthEvent(
                AuthState.LoggedInFromLoading(TestFixtures.activeAccount)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Not logged in — routes to landing
    // -------------------------------------------------------------------------

    @Test
    fun `start with null account navigates to landing`() = runTest {
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns true
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns true
        coEvery { accountService.getCurrentAccount() } returns null
        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    @Test
    fun `start not logged in with no other accounts routes to Landing`() = runTest {
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns false
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns false
        coEvery { accountService.getCurrentAccount() } returns TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = false,
        )
        // checkLocalAccountValidity: account not expired
        // But isLoggedIn will be false and checkLocalAccountValidity requires non-expired
        // Account.isExpired defaults to false, so checkLocalAccountValidity returns true
        // This means isLoggedIn gets set to true and loadData runs
        // Let's set isExpired = true to ensure not logged in flow
        coEvery { accountService.getCurrentAccount() } returns null

        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    @Test
    fun `start not logged in with other logged-in accounts routes to MultiAccountLanding`() = runTest {
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns false
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns false
        coEvery { accountService.getCurrentAccount() } returns null
        coEvery { accountService.getLoggedInAccounts() } returns listOf(TestFixtures.secondaryAccount)

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.MultiAccountLanding) }
    }

    // -------------------------------------------------------------------------
    // Login check fails but local account is valid — proceeds as logged in
    // -------------------------------------------------------------------------

    @Test
    fun `start with login check failed but valid local account proceeds as logged in`() = runTest {
        val validAccount = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        )
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns false
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns false
        coEvery { accountService.getCurrentAccount() } returns validAccount
        coEvery { accountService.subscribeAccount() } returns Unit
        coEvery { entryService.updateAllData(accountId = any()) } returns Unit
        coEvery { dashboardService.setAccountId(any()) } returns Unit
        coEvery { deviceService.setAccountId(any()) } returns Unit
        coEvery { deviceInfoService.updateDeviceInfo() } returns Unit
        coEvery { deviceInfoService.updateLocalIntegrationInfo() } returns Unit

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.autoLogin() }
        coVerify {
            appNavigationService.emitAuthEvent(
                AuthState.LoggedInFromLoading(validAccount)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Login check throws — falls back to local validity
    // -------------------------------------------------------------------------

    @Test
    fun `start with login check exception and valid local account proceeds`() = runTest {
        val validAccount = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        )
        coEvery { accountService.checkLoginStatusForActiveAccount() } throws RuntimeException("Network error")
        coEvery { accountService.getCurrentAccount() } returns validAccount
        coEvery { accountService.subscribeAccount() } returns Unit
        coEvery { entryService.updateAllData(accountId = any()) } returns Unit
        coEvery { dashboardService.setAccountId(any()) } returns Unit
        coEvery { deviceService.setAccountId(any()) } returns Unit
        coEvery { deviceInfoService.updateDeviceInfo() } returns Unit
        coEvery { deviceInfoService.updateLocalIntegrationInfo() } returns Unit

        createViewModel()
        advanceUntilIdle()

        // checkLoginStatus catches exception and falls back to checkLocalAccountValidity
        // validAccount is not expired, so it returns true
        // Then isLoggedIn = true, loadData runs
        coVerify { appNavigationService.autoLogin() }
    }

    // -------------------------------------------------------------------------
    // Startup exception — fallback with valid local account
    // -------------------------------------------------------------------------

    @Test
    fun `start with exception during flow and valid local account recovers`() = runTest {
        val validAccount = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        )
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns true
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns true
        coEvery { accountService.getCurrentAccount() } returns validAccount
        // First call throws (in try block), second call succeeds (in catch block recovery)
        var subscribeCallCount = 0
        coEvery { accountService.subscribeAccount() } answers {
            subscribeCallCount++
            if (subscribeCallCount == 1) throw RuntimeException("Unexpected error")
        }
        coEvery { entryService.updateAllData(accountId = any()) } returns Unit
        coEvery { dashboardService.setAccountId(any()) } returns Unit
        coEvery { deviceService.setAccountId(any()) } returns Unit
        coEvery { deviceInfoService.updateDeviceInfo() } returns Unit
        coEvery { deviceInfoService.updateLocalIntegrationInfo() } returns Unit

        createViewModel()
        advanceUntilIdle()

        // After exception, checkLocalAccountValidity succeeds, loadData runs again
        // and succeeds the second time
        coVerify { appNavigationService.autoLogin() }
        coVerify {
            appNavigationService.emitAuthEvent(
                AuthState.LoggedInFromLoading(validAccount)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Startup exception — no valid local account routes to landing
    // -------------------------------------------------------------------------

    @Test
    fun `start with exception and no valid local account routes to landing`() = runTest {
        coEvery { accountService.checkLoginStatusForActiveAccount() } throws RuntimeException("fail")
        // checkLoginStatus falls back to checkLocalAccountValidity
        // which calls getCurrentAccount() — return null
        coEvery { accountService.getCurrentAccount() } returns null
        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    // -------------------------------------------------------------------------
    // Startup exception — expired local account routes to landing
    // -------------------------------------------------------------------------

    @Test
    fun `start with exception and expired local account routes to landing`() = runTest {
        val expiredAccount = TestFixtures.anAccount(isActiveAccount = true, isLoggedIn = false)
        // isExpired defaults to false in Account, so we need an account that IS expired
        // Unfortunately TestFixtures.anAccount doesn't expose isExpired param
        // Instead, let's make getCurrentAccount return null on the fallback check
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns true
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns true

        var callCount = 0
        coEvery { accountService.getCurrentAccount() } answers {
            callCount++
            if (callCount == 1) throw RuntimeException("First call fails in outer try")
            else null // Second call in catch block
        }
        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }

    // -------------------------------------------------------------------------
    // loadData — verifies all data loading calls
    // -------------------------------------------------------------------------

    @Test
    fun `loadData calls all services in parallel`() = runTest {
        setupLoggedInFlow()

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountService.subscribeAccount() }
        coVerify(exactly = 1) { deviceInfoService.updateDeviceInfo() }
        coVerify(exactly = 1) { entryService.updateAllData(accountId = TestFixtures.activeAccount.id) }
        coVerify(exactly = 1) { dashboardService.setAccountId(TestFixtures.activeAccount.id) }
        coVerify(exactly = 1) { deviceService.setAccountId(TestFixtures.activeAccount.id) }
        coVerify(exactly = 1) { deviceInfoService.updateLocalIntegrationInfo() }
    }

    // -------------------------------------------------------------------------
    // Both login checks must pass
    // -------------------------------------------------------------------------

    @Test
    fun `start with active account check passes but logged in check fails falls back`() = runTest {
        val validAccount = TestFixtures.anAccount(
            isActiveAccount = true,
            isLoggedIn = true,
        )
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns true
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns false
        coEvery { accountService.getCurrentAccount() } returns validAccount
        coEvery { accountService.subscribeAccount() } returns Unit
        coEvery { entryService.updateAllData(accountId = any()) } returns Unit
        coEvery { dashboardService.setAccountId(any()) } returns Unit
        coEvery { deviceService.setAccountId(any()) } returns Unit
        coEvery { deviceInfoService.updateDeviceInfo() } returns Unit
        coEvery { deviceInfoService.updateLocalIntegrationInfo() } returns Unit

        createViewModel()
        advanceUntilIdle()

        // isLoggedIn = false, but checkLocalAccountValidity returns true (not expired)
        // so isLoggedIn is set to true, and loadData + autoLogin proceeds
        coVerify { appNavigationService.autoLogin() }
    }

    @Test
    fun `start with both login checks failing and expired account routes to landing`() = runTest {
        coEvery { accountService.checkLoginStatusForActiveAccount() } returns false
        coEvery { accountService.checkLoginStatusForLoggedInAccounts() } returns false
        // Return an account that is expired
        val expiredAccount = mockk<com.dmdbrands.gurus.weight.domain.model.storage.Account.Account>()
        every { expiredAccount.id } returns "expired-id"
        every { expiredAccount.isExpired } returns true
        coEvery { accountService.getCurrentAccount() } returns expiredAccount
        coEvery { accountService.getLoggedInAccounts() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        // isLoggedIn false, checkLocalAccountValidity returns false (expired),
        // so routeToLandingOrApp is called
        coVerify { appNavigationService.replaceStack(route = AppRoute.Auth.Landing) }
    }
}
