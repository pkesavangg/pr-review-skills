package com.dmdbrands.gurus.weight.features.login.viewmodel

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.MaxAccountsReachedException
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.login.model.LoginFormControls
import com.dmdbrands.gurus.weight.features.login.model.LoginIntent
import com.dmdbrands.gurus.weight.features.login.model.LoginState
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var dialogUtility: IDialogUtility

    @MockK(relaxed = true)
    lateinit var navigationService: IAppNavigationService

    @MockK(relaxed = true)
    lateinit var dialogQueueService: IDialogQueueService

    @MockK(relaxed = true)
    lateinit var customTabManager: ICustomTabManager

    private lateinit var viewModel: LoginViewModel

    companion object {
        private const val TEST_EMAIL = "test@example.com"
        private const val TEST_PASSWORD = "password123"
        private const val PRE_FILL_EMAIL = "prefill@example.com"
        private const val ERROR_MESSAGE = "Login failed"
        private const val GENERIC_EXCEPTION_MESSAGE = "Network error"
        private const val TEST_URL = "https://example.com/help"
        private const val NAV_ERROR = "nav error"
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = LoginViewModel(
            email = null,
            accountService = accountService,
            dialogUtility = dialogUtility,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )
    }

    private fun createViewModelWithEmail(email: String): LoginViewModel =
        LoginViewModel(
            email = email,
            accountService = accountService,
            dialogUtility = dialogUtility,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )

    private fun fillValidForm() {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)
        viewModel.state.value.form.controls.password.onValueChange(TEST_PASSWORD)
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty form and no loading or error`() {
        val state = viewModel.state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.form.controls.email.value).isEmpty()
        assertThat(state.form.controls.password.value).isEmpty()
    }

    @Test
    fun `initial state with pre-filled email sets email control value`() {
        val vm = createViewModelWithEmail(PRE_FILL_EMAIL)

        assertThat(vm.state.value.form.controls.email.value).isEqualTo(PRE_FILL_EMAIL)
        assertThat(vm.state.value.form.controls.password.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // UpdateForm Intent — Reducer
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm intent updates form in state`() {
        val newForm = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(
            LoginFormControls.create(email = TEST_EMAIL)
        )

        viewModel.handleIntent(LoginIntent.UpdateForm(newForm))

        assertThat(viewModel.state.value.form).isEqualTo(newForm)
    }

    // -------------------------------------------------------------------------
    // Error Intent — Reducer
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message and clears isLoading`() {
        // Directly test the reducer transition: Error sets error and clears isLoading
        viewModel.handleIntent(LoginIntent.Error(ERROR_MESSAGE))

        assertThat(viewModel.state.value.error).isEqualTo(ERROR_MESSAGE)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Success Intent — Reducer + Side Effect
    // -------------------------------------------------------------------------

    @Test
    fun `Success intent clears isLoading and error`() {
        viewModel.handleIntent(LoginIntent.Submit)
        viewModel.handleIntent(LoginIntent.Error(ERROR_MESSAGE))

        viewModel.handleIntent(LoginIntent.Success)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `Success intent calls navigationService reInitialize`() = runTest {
        viewModel.handleIntent(LoginIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.reInitialize() }
    }

    // -------------------------------------------------------------------------
    // Submit Intent — Happy Path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets isLoading to true via reducer`() = runTest {
        // Stub login so the coroutine can proceed without error
        coEvery { accountService.login(any(), any()) } returns TestFixtures.activeAccount

        viewModel.state.test {
            skipItems(1) // initial state

            fillValidForm()
            viewModel.handleIntent(LoginIntent.Submit)

            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()
            assertThat(loading.error).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Submit with valid form calls accountService login`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns TestFixtures.activeAccount

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.login(TEST_EMAIL, TEST_PASSWORD) }
    }

    @Test
    fun `Submit with valid form and successful login triggers Success`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns TestFixtures.activeAccount

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
        coVerify { navigationService.reInitialize() }
    }

    @Test
    fun `Submit shows loader and dismisses it after success`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns TestFixtures.activeAccount

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = LoginStrings.LoaderMessage) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Submit Intent — Error Paths
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with invalid form dismisses loader without calling login`() = runTest {
        // Touch controls with invalid values so validation fails
        viewModel.state.value.form.controls.email.onValueChange("")
        viewModel.state.value.form.controls.password.onValueChange("")

        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.login(any(), any()) }
        verify { dialogQueueService.showLoader(message = LoginStrings.LoaderMessage) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit when login returns null sets error state`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns null

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isEqualTo(ERROR_MESSAGE)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `Submit when login throws generic exception sets error state`() = runTest {
        coEvery {
            accountService.login(TEST_EMAIL, TEST_PASSWORD)
        } throws RuntimeException(GENERIC_EXCEPTION_MESSAGE)

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit when login throws MaxAccountsReachedException shows max account alert`() = runTest {
        coEvery {
            accountService.login(TEST_EMAIL, TEST_PASSWORD)
        } throws MaxAccountsReachedException()

        fillValidForm()
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // OnBack Intent
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack intent calls navigationService navigateBack`() = runTest {
        viewModel.handleIntent(LoginIntent.OnBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnBack does not crash when navigateBack throws`() = runTest {
        coEvery { navigationService.navigateBack() } throws RuntimeException(NAV_ERROR)

        viewModel.handleIntent(LoginIntent.OnBack)
        advanceUntilIdle()

        // Should not propagate — exception is caught inside navigateBack()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnBack intent clears isLoading and error via reducer`() {
        viewModel.handleIntent(LoginIntent.Submit)
        viewModel.handleIntent(LoginIntent.OnBack)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OnRequestBack Intent
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack with clean form navigates back directly`() = runTest {
        viewModel.handleIntent(LoginIntent.OnRequestBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `OnRequestBack with dirty form shows confirmation dialog`() = runTest {
        // Make form dirty by changing a value
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)

        viewModel.handleIntent(LoginIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Confirm::class.java)
    }

    @Test
    fun `OnRequestBack confirm callback navigates back and dismisses dialog`() = runTest {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)

        viewModel.handleIntent(LoginIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `OnRequestBack cancel callback dismisses dialog without navigating`() = runTest {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)

        viewModel.handleIntent(LoginIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // OpenForgotPasswordModal Intent
    // -------------------------------------------------------------------------

    @Test
    fun `OpenForgotPasswordModal enqueues custom dialog with PasswordReset type`() {
        viewModel.handleIntent(LoginIntent.OpenForgotPasswordModal)

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Custom
        assertThat(dialog.contentKey).isEqualTo(DialogType.PasswordReset)
    }

    @Test
    fun `OpenForgotPasswordModal passes trimmed email in params`() {
        viewModel.state.value.form.controls.email.onValueChange("  $TEST_EMAIL  ")

        viewModel.handleIntent(LoginIntent.OpenForgotPasswordModal)

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Custom
        assertThat(dialog.params?.get("email")).isEqualTo(TEST_EMAIL)
    }

    @Test
    fun `OpenForgotPasswordModal dismiss callback dismisses dialog`() {
        viewModel.handleIntent(LoginIntent.OpenForgotPasswordModal)

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Custom
        dialog.onDismiss?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `OpenForgotPasswordModal clears isLoading and error via reducer`() {
        viewModel.handleIntent(LoginIntent.Submit)
        viewModel.handleIntent(LoginIntent.OpenForgotPasswordModal)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal Intent
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal enqueues custom dialog with HelpPopup type`() {
        viewModel.handleIntent(LoginIntent.OpenHelpModal)

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Custom
        assertThat(dialog.contentKey).isEqualTo(DialogType.HelpPopup)
    }

    @Test
    fun `OpenHelpModal clears isLoading and error via reducer`() {
        viewModel.handleIntent(LoginIntent.Submit)
        viewModel.handleIntent(LoginIntent.OpenHelpModal)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // ShowMaxAccountAlert Intent
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxAccountAlert calls dialogUtility showMaxAccountAlert`() {
        viewModel.handleIntent(LoginIntent.ShowMaxAccountAlert)

        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true) }
    }

    @Test
    fun `ShowMaxAccountAlert sets error to max accounts message via reducer`() {
        viewModel.handleIntent(LoginIntent.ShowMaxAccountAlert)

        assertThat(viewModel.state.value.error).isEqualTo(MaxAccountsReachedException().message)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // OpenInAppBrowser Intent
    // -------------------------------------------------------------------------

    @Test
    fun `OpenInAppBrowser calls customTabManager openChromeTab with url`() {
        viewModel.handleIntent(LoginIntent.OpenInAppBrowser(TEST_URL))

        verify { customTabManager.openChromeTab(TEST_URL) }
    }

    // -------------------------------------------------------------------------
    // State Transitions with Turbine
    // -------------------------------------------------------------------------

    @Test
    fun `Submit then Success produces correct state transitions`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns TestFixtures.activeAccount

        viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.isLoading).isFalse()

            fillValidForm()
            viewModel.handleIntent(LoginIntent.Submit)

            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()

            val success = awaitItem()
            assertThat(success.isLoading).isFalse()
            assertThat(success.error).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Submit then Error produces correct state transitions`() = runTest {
        coEvery {
            accountService.login(TEST_EMAIL, TEST_PASSWORD)
        } throws RuntimeException(GENERIC_EXCEPTION_MESSAGE)

        viewModel.state.test {
            val initial = awaitItem()
            assertThat(initial.isLoading).isFalse()

            fillValidForm()
            viewModel.handleIntent(LoginIntent.Submit)

            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()

            val error = awaitItem()
            assertThat(error.isLoading).isFalse()
            assertThat(error.error).isNotNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional coverage for edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `Submit trims email before calling login`() = runTest {
        coEvery { accountService.login(TEST_EMAIL, TEST_PASSWORD) } returns TestFixtures.activeAccount

        viewModel.state.value.form.controls.email.onValueChange("  $TEST_EMAIL  ")
        viewModel.state.value.form.controls.password.onValueChange(TEST_PASSWORD)
        viewModel.handleIntent(LoginIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.login(TEST_EMAIL, TEST_PASSWORD) }
    }

    // -------------------------------------------------------------------------
    // showMaxLimitReachedAlert — via ShowMaxAccountAlert intent
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxAccountAlert delegates to dialogUtility with isFromLanding true`() {
        viewModel.handleIntent(LoginIntent.ShowMaxAccountAlert)
        verify { dialogUtility.showMaxAccountAlert(isFromLanding = true) }
    }

    // -------------------------------------------------------------------------
    // navigateToDashboard — via Success intent
    // -------------------------------------------------------------------------

    @Test
    fun `navigateToDashboard calls reInitialize on navigation service`() = runTest {
        viewModel.handleIntent(LoginIntent.Success)
        advanceUntilIdle()
        coVerify { navigationService.reInitialize() }
    }

    @Test
    fun `navigateToDashboard clears loading and error state`() = runTest {
        viewModel.handleIntent(LoginIntent.Error(ERROR_MESSAGE))
        viewModel.handleIntent(LoginIntent.Success)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }
}
