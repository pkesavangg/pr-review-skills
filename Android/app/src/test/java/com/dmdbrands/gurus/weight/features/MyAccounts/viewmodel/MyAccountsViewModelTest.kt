package com.dmdbrands.gurus.weight.features.MyAccounts.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.MyAccounts.reducer.MyAccountsIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MyAccountsViewModelTest {

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
    private lateinit var viewModel: MyAccountsViewModel

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
    }

    private fun createViewModel(): MyAccountsViewModel =
        MyAccountsViewModel(
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
        assertThat(state.showMaxAccountsDialog).isFalse()
        assertThat(state.accountToRemove).isNull()
        assertThat(state.hasReachedMaxAccounts).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Test
    fun `init calls emitNavigateToMyAccounts`() = runTest(mainDispatcherRule.scheduler) {
        advanceUntilIdle()
        coVerify { accountService.emitNavigateToMyAccounts() }
    }

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

    // -------------------------------------------------------------------------
    // SetAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `SetAccounts updates accounts and hasReachedMaxAccounts`() {
        val accounts = listOf(TestFixtures.activeAccount)
        viewModel.handleIntent(MyAccountsIntent.SetAccounts(accounts, true))
        assertThat(viewModel.state.value.accounts).isEqualTo(accounts)
        assertThat(viewModel.state.value.hasReachedMaxAccounts).isTrue()
    }

    // -------------------------------------------------------------------------
    // SelectAccount — switching
    // -------------------------------------------------------------------------

    @Test
    fun `SelectAccount with inactive account calls switchAccount`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns true

        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify { accountService.switchAccount(TestFixtures.secondaryAccount, true) }
    }

    @Test
    fun `SelectAccount with successful switch calls reInitialize`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns true

        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify { navigationService.reInitialize() }
    }

    @Test
    fun `SelectAccount with failed switch does not call reInitialize`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns false

        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.reInitialize() }
    }

    @Test
    fun `SelectAccount with active account does not call switchAccount`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.activeAccount))
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.switchAccount(any(), any()) }
    }

    @Test
    fun `SelectAccount when switchAccount throws does not crash`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } throws RuntimeException(ERROR_FAIL)

        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.reInitialize() }
    }

    // -------------------------------------------------------------------------
    // LoginToAccount
    // -------------------------------------------------------------------------

    @Test
    fun `LoginToAccount navigates to Login when limit not reached`() = runTest(mainDispatcherRule.scheduler) {
        val account = TestFixtures.secondaryAccount
        viewModel.handleIntent(MyAccountsIntent.LoginToAccount(account))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(account.email)) }
    }

    @Test
    fun `LoginToAccount with null account navigates to Login with null email`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.LoginToAccount(null))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(null)) }
    }

    @Test
    fun `LoginToAccount when max reached shows max account alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.SetAccounts(emptyList(), true))
        viewModel.handleIntent(MyAccountsIntent.LoginToAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = false, onDismiss = any()) }
        coVerify(exactly = 0) { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // CreateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `CreateAccount navigates to Signup when limit not reached`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.CreateAccount)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Signup) }
    }

    @Test
    fun `CreateAccount when max reached shows max account alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.SetAccounts(emptyList(), true))
        viewModel.handleIntent(MyAccountsIntent.CreateAccount)
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = false, onDismiss = any()) }
        coVerify(exactly = 0) { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // ShowMaxAccountsAlert
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxAccountsAlert sets showMaxAccountsDialog and calls dialogUtility`() {
        viewModel.handleIntent(MyAccountsIntent.ShowMaxAccountsAlert)
        assertThat(viewModel.state.value.showMaxAccountsDialog).isTrue()
        verify { dialogUtility.showMaxAccountAlert(isFromLanding = false, onDismiss = any()) }
    }

    // -------------------------------------------------------------------------
    // RequestRemoveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `RequestRemoveAccount sets accountToRemove and shows dialog`() {
        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        assertThat(viewModel.state.value.accountToRemove).isEqualTo(TestFixtures.secondaryAccount)
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `RequestRemoveAccount confirm callback calls removeAccountFromDevice`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify {
            accountService.removeAccountFromDevice(
                TestFixtures.secondaryAccount.id,
                TestFixtures.secondaryAccount.fcmToken,
            )
        }
    }

    @Test
    fun `RequestRemoveAccount confirm shows and dismisses loader`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
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

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `RequestRemoveAccount cancel callback dismisses dialog`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `RequestRemoveAccount dismiss callback dismisses dialog`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onDismiss?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `RequestRemoveAccount confirm shows loader`() = runTest(mainDispatcherRule.scheduler) {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(any()) }
    }

    // -------------------------------------------------------------------------
    // Remove — post-removal navigation (MOB-1474)
    // -------------------------------------------------------------------------

    @Test
    fun `removing the active account with no other accounts navigates to Landing`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.removeAccountFromDevice(any(), any()) } returns true
        coEvery { accountService.getLoggedInAccounts() } returns emptyList()
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.activeAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify {
            accountService.removeAccountFromDevice(
                TestFixtures.activeAccount.id,
                TestFixtures.activeAccount.fcmToken,
            )
        }
        coVerify { navigationService.replaceStack(AppRoute.Auth.Landing) }
    }

    @Test
    fun `removing the active account with other accounts navigates to MultiAccountLanding`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.removeAccountFromDevice(any(), any()) } returns true
        coEvery { accountService.getLoggedInAccounts() } returns listOf(TestFixtures.secondaryAccount)
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.activeAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(AppRoute.Auth.MultiAccountLanding) }
    }

    @Test
    fun `removing a non-active account does not navigate`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.removeAccountFromDevice(any(), any()) } returns true
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify {
            accountService.removeAccountFromDevice(
                TestFixtures.secondaryAccount.id,
                TestFixtures.secondaryAccount.fcmToken,
            )
        }
        coVerify(exactly = 0) { navigationService.replaceStack(AppRoute.Auth.Landing) }
        coVerify(exactly = 0) { navigationService.replaceStack(AppRoute.Auth.MultiAccountLanding) }
    }

    // -------------------------------------------------------------------------
    // onNavigateBack
    // -------------------------------------------------------------------------

    @Test
    fun `onNavigateBack calls emitNavigateBackFromMyAccounts`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.onNavigateBack()
        advanceUntilIdle()
        coVerify { accountService.emitNavigateBackFromMyAccounts() }
    }

    // -------------------------------------------------------------------------
    // goToLogin — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `LoginToAccount with non-null account passes email to Login route`() = runTest(mainDispatcherRule.scheduler) {
        val account = TestFixtures.secondaryAccount
        viewModel.handleIntent(MyAccountsIntent.LoginToAccount(account))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Login(account.email)) }
    }

    @Test
    fun `LoginToAccount checks max accounts before navigating`() = runTest(mainDispatcherRule.scheduler) {
        // Set max accounts reached
        viewModel.handleIntent(MyAccountsIntent.SetAccounts(emptyList(), true))

        viewModel.handleIntent(MyAccountsIntent.LoginToAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        // Should show alert instead of navigating
        verify { dialogUtility.showMaxAccountAlert(isFromLanding = false, onDismiss = any()) }
    }

    // -------------------------------------------------------------------------
    // goToSignUp — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `CreateAccount navigates to Signup route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.CreateAccount)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.Auth.Signup) }
    }

    @Test
    fun `CreateAccount checks max accounts before navigating`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.SetAccounts(emptyList(), true))

        viewModel.handleIntent(MyAccountsIntent.CreateAccount)
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = false, onDismiss = any()) }
        coVerify(exactly = 0) { navigationService.navigateTo(AppRoute.Auth.Signup) }
    }

    // -------------------------------------------------------------------------
    // onAccountSelect — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `SelectAccount with active account is no-op`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.activeAccount))
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.switchAccount(any(), any()) }
        coVerify(exactly = 0) { navigationService.reInitialize() }
    }

    @Test
    fun `SelectAccount with inactive account calls switchAccount with showLoader true`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.switchAccount(any(), any()) } returns true

        viewModel.handleIntent(MyAccountsIntent.SelectAccount(TestFixtures.secondaryAccount))
        advanceUntilIdle()

        coVerify { accountService.switchAccount(TestFixtures.secondaryAccount, true) }
    }

    // -------------------------------------------------------------------------
    // showRemoveAccountDialog — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `RequestRemoveAccount shows dialog with account name in title`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))

        val dialog = dialogSlot.captured
        assertThat(dialog.title).contains(TestFixtures.secondaryAccount.firstName)
    }

    @Test
    fun `RequestRemoveAccount sets accountToRemove in state via reducer`() {
        viewModel.handleIntent(MyAccountsIntent.RequestRemoveAccount(TestFixtures.secondaryAccount))
        assertThat(viewModel.state.value.accountToRemove).isEqualTo(TestFixtures.secondaryAccount)
    }
}
