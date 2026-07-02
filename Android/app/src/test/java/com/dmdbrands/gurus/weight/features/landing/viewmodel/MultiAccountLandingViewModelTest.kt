package com.dmdbrands.gurus.weight.features.landing.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.landing.reducer.MultiAccountLandingIntent
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MultiAccountLandingViewModelTest {

    companion object {
        private const val ERROR_FAIL = "fail"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var dialogUtility: IDialogUtility

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: MultiAccountLandingViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
        viewModel = createViewModel()
    }

    private fun stubDefaultFlows() {
        every { accountService.loggedInAccountsFlow } returns flowOf(emptyList())
        every { accountService.hasReachedMaxAccounts } returns flowOf(false)
        coEvery { accountService.removeAccountFromDevice(any(), any()) } returns true
    }

    private fun createViewModel(): MultiAccountLandingViewModel =
        MultiAccountLandingViewModel(
            accountService = accountService,
            dialogUtility = dialogUtility,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty accounts and default values`() {
        val state = viewModel.state.value
        assertThat(state.accounts).isEmpty()
        assertThat(state.hasReachedMaxAccounts).isFalse()
        assertThat(state.accountToRemove).isNull()
    }

    // -------------------------------------------------------------------------
    // Init — loadAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to loggedInAccountsFlow and sets accounts`() = runTest(mainDispatcherRule.scheduler) {
        val accounts = listOf(TestFixtures.activeAccount, TestFixtures.secondaryAccount)
        every { accountService.loggedInAccountsFlow } returns flowOf(accounts)
        every { accountService.hasReachedMaxAccounts } returns flowOf(false)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.accounts).isEqualTo(accounts)
        assertThat(viewModel.state.value.hasReachedMaxAccounts).isFalse()
    }

    @Test
    fun `init sets hasReachedMaxAccounts from service`() = runTest(mainDispatcherRule.scheduler) {
        every { accountService.loggedInAccountsFlow } returns flowOf(listOf(TestFixtures.activeAccount))
        every { accountService.hasReachedMaxAccounts } returns flowOf(true)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasReachedMaxAccounts).isTrue()
    }

    @Test
    fun `init with empty accounts navigates to fresh Landing`() = runTest(mainDispatcherRule.scheduler) {
        every { accountService.loggedInAccountsFlow } returns flowOf(emptyList())
        every { accountService.hasReachedMaxAccounts } returns flowOf(false)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.accounts).isEmpty()
        coVerify { navigationService.replaceStack(AppRoute.Auth.Landing) }
    }

    @Test
    fun `loggedInAccountsFlow emitting empty after removal routes to Landing`() = runTest(mainDispatcherRule.scheduler) {
        // Last remaining account removed from this device — no accounts remain.
        every { accountService.loggedInAccountsFlow } returns flowOf(emptyList())
        every { accountService.hasReachedMaxAccounts } returns flowOf(false)

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(AppRoute.Auth.Landing) }
        verify(exactly = 0) { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // SetAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `SetAccounts updates accounts and hasReachedMaxAccounts`() {
        val accounts = listOf(TestFixtures.activeAccount)
        viewModel.handleIntent(MultiAccountLandingIntent.SetAccounts(accounts, true))

        assertThat(viewModel.state.value.accounts).isEqualTo(accounts)
        assertThat(viewModel.state.value.hasReachedMaxAccounts).isTrue()
    }

    @Test
    fun `SetAccounts with default hasReachedMaxAccounts is false`() {
        val accounts = listOf(TestFixtures.activeAccount)
        viewModel.handleIntent(MultiAccountLandingIntent.SetAccounts(accounts))

        assertThat(viewModel.state.value.hasReachedMaxAccounts).isFalse()
    }

    // -------------------------------------------------------------------------
    // SelectAccount — switching
    // -------------------------------------------------------------------------

    @Test
    fun `SelectAccount calls switchAccount on accountService`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns true

        viewModel.handleIntent(MultiAccountLandingIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify { accountService.switchAccount(TestFixtures.secondaryAccount, true) }
    }

    @Test
    fun `SelectAccount with successful switch calls reInitialize`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns true

        viewModel.handleIntent(MultiAccountLandingIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify { navigationService.reInitialize() }
    }

    @Test
    fun `SelectAccount when switchAccount throws does not crash`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } throws RuntimeException(ERROR_FAIL)

        viewModel.handleIntent(MultiAccountLandingIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.reInitialize() }
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Test
    fun `Login with account navigates to Login with email`() = runTest(mainDispatcherRule.scheduler) {
        val account = TestFixtures.secondaryAccount
        viewModel.handleIntent(MultiAccountLandingIntent.Login(account))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(account.email)) }
    }

    @Test
    fun `Login with null account and limit not reached navigates to Login with null email`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MultiAccountLandingIntent.Login(null))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(null)) }
    }

    @Test
    fun `Login with null account when max reached shows max limit alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MultiAccountLandingIntent.SetAccounts(emptyList(), true))
        viewModel.handleIntent(MultiAccountLandingIntent.Login(null))
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true, onDismiss = any()) }
        coVerify(exactly = 0) { navigationService.navigateTo(any()) }
    }

    @Test
    fun `Login with account when max reached still navigates to Login`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MultiAccountLandingIntent.SetAccounts(emptyList(), true))
        val account = TestFixtures.secondaryAccount
        viewModel.handleIntent(MultiAccountLandingIntent.Login(account))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(account.email)) }
    }

    // -------------------------------------------------------------------------
    // CreateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `CreateAccount navigates to Signup when limit not reached`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MultiAccountLandingIntent.CreateAccount)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Signup) }
    }

    @Test
    fun `CreateAccount when max reached shows max limit alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MultiAccountLandingIntent.SetAccounts(emptyList(), true))
        viewModel.handleIntent(MultiAccountLandingIntent.CreateAccount)
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true, onDismiss = any()) }
        coVerify(exactly = 0) { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // ShowMaxLimitReachedAlert
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxLimitReachedAlert calls dialogUtility showMaxAccountAlert`() {
        viewModel.handleIntent(MultiAccountLandingIntent.ShowMaxLimitReachedAlert)

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true, onDismiss = any()) }
    }

    // -------------------------------------------------------------------------
    // RequestRemoveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `RequestRemoveAccount sets accountToRemove and shows dialog`() {
        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))

        assertThat(viewModel.state.value.accountToRemove).isEqualTo(TestFixtures.secondaryAccount)
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `RequestRemoveAccount confirm callback removes account from device`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify {
            accountService.removeAccountFromDevice(
                TestFixtures.secondaryAccount.id,
                TestFixtures.secondaryAccount.fcmToken,
            )
        }
        coVerify(exactly = 0) { accountService.logout(any(), any()) }
    }

    @Test
    fun `RequestRemoveAccount confirm shows and dismisses loader`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `RequestRemoveAccount confirm dismisses loader on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.removeAccountFromDevice(any(), any()) } throws RuntimeException(ERROR_FAIL)
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `RequestRemoveAccount cancel callback dismisses dialog`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `RequestRemoveAccount dismiss callback dismisses dialog`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onDismiss?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `RequestRemoveAccount confirm also dismisses current dialog`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MultiAccountLandingIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
    }
}
