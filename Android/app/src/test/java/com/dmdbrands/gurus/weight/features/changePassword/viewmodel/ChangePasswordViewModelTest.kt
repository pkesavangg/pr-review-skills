package com.dmdbrands.gurus.weight.features.changePassword.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.changePassword.model.ChangePasswordIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var navigationService: IAppNavigationService

    @MockK(relaxed = true)
    lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: ChangePasswordViewModel

    companion object {
        private const val CURRENT_PASSWORD = "oldPass123"
        private const val NEW_PASSWORD = "newPass456"
        private const val TEST_EMAIL = "user@example.com"
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = ChangePasswordViewModel(
            accountService = accountService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    private fun fillValidForm() {
        viewModel.state.value.form.controls.currentPassword.onValueChange(CURRENT_PASSWORD)
        viewModel.state.value.form.controls.newPassword.onValueChange(NEW_PASSWORD)
        viewModel.state.value.form.controls.confirmPassword.onValueChange(NEW_PASSWORD)
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty form and no loading or error`() {
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with valid form calls changePassword and navigates back on success`() = runTest {
        fillValidForm()
        coEvery { accountService.changePassword(any(), any()) } returns true

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.changePassword(CURRENT_PASSWORD, NEW_PASSWORD) }
        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
        coVerify { navigationService.navigateBack(null) }
    }

    @Test
    fun `Submit with changePassword returning false sets error`() = runTest {
        fillValidForm()
        coEvery { accountService.changePassword(any(), any()) } returns false

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNotNull()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit with exception sets error`() = runTest {
        fillValidForm()
        coEvery { accountService.changePassword(any(), any()) } throws RuntimeException("Network error")

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNotNull()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit shows loader`() = runTest {
        fillValidForm()
        coEvery { accountService.changePassword(any(), any()) } returns true

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(any()) }
    }

    // -------------------------------------------------------------------------
    // OpenForgotPasswordModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenForgotPasswordModal shows confirm dialog with email`() = runTest {
        val mockAccount: Account = io.mockk.mockk(relaxed = true) {
            io.mockk.every { email } returns TEST_EMAIL
        }
        coEvery { accountService.getCurrentAccount() } returns mockAccount

        viewModel.handleIntent(ChangePasswordIntent.OpenForgotPasswordModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Confirm::class.java)
    }

    @Test
    fun `OpenForgotPasswordModal confirm callback calls resetPassword`() = runTest {
        val mockAccount: Account = io.mockk.mockk(relaxed = true) {
            io.mockk.every { email } returns TEST_EMAIL
        }
        coEvery { accountService.getCurrentAccount() } returns mockAccount
        coEvery { accountService.resetPassword(any()) } returns Unit

        viewModel.handleIntent(ChangePasswordIntent.OpenForgotPasswordModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { accountService.resetPassword(TEST_EMAIL) }
        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal enqueues Custom dialog`() {
        viewModel.handleIntent(ChangePasswordIntent.OpenHelpModal)

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Custom::class.java)
    }

    // -------------------------------------------------------------------------
    // OnRequestBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack with no changes navigates back directly`() = runTest {
        viewModel.handleIntent(ChangePasswordIntent.OnRequestBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnRequestBack with dirty form shows confirm dialog`() = runTest {
        fillValidForm()
        viewModel.state.value.form.controls.currentPassword.onBlur()

        viewModel.handleIntent(ChangePasswordIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Confirm::class.java)
    }

    @Test
    fun `OnRequestBack confirm callback navigates back and dismisses dialog`() = runTest {
        fillValidForm()
        viewModel.state.value.form.controls.currentPassword.onBlur()

        viewModel.handleIntent(ChangePasswordIntent.OnRequestBack)
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
    fun `OnRequestBack cancel callback dismisses dialog without navigation`() = runTest {
        fillValidForm()
        viewModel.state.value.form.controls.currentPassword.onBlur()

        viewModel.handleIntent(ChangePasswordIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `OnRequestBack confirm resets form fields before navigating back`() = runTest {
        fillValidForm()
        viewModel.state.value.form.controls.currentPassword.onBlur()

        viewModel.handleIntent(ChangePasswordIntent.OnRequestBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onConfirm?.invoke()
        advanceUntilIdle()

        val controls = viewModel.state.value.form.controls
        assertThat(controls.currentPassword.value).isEmpty()
        assertThat(controls.newPassword.value).isEmpty()
        assertThat(controls.confirmPassword.value).isEmpty()
        assertThat(viewModel.state.value.form.isDirty).isFalse()
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success navigates back`() = runTest {
        viewModel.handleIntent(ChangePasswordIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with invalid form dismisses loader without calling changePassword`() = runTest {
        // Touch and dirty the controls with invalid values so validation actually fails
        // (untouched/undirty controls pass validation by design)
        viewModel.state.value.form.controls.currentPassword.onValueChange("ab")  // too short
        viewModel.state.value.form.controls.newPassword.onValueChange("ab")      // too short
        viewModel.state.value.form.controls.confirmPassword.onValueChange("ab")  // too short

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.changePassword(any(), any()) }
        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit calls changePassword with correct passwords from form`() = runTest {
        fillValidForm()
        coEvery { accountService.changePassword(CURRENT_PASSWORD, NEW_PASSWORD) } returns true

        viewModel.handleIntent(ChangePasswordIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.changePassword(CURRENT_PASSWORD, NEW_PASSWORD) }
    }

    // -------------------------------------------------------------------------
    // onSuccess — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Success navigates back with null argument`() = runTest {
        viewModel.handleIntent(ChangePasswordIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // resetPasswordForCurrentUser — triggered via OpenForgotPasswordModal confirm
    // -------------------------------------------------------------------------

    @Test
    fun `resetPasswordForCurrentUser shows and dismisses loader`() = runTest {
        val mockAccount: Account = io.mockk.mockk(relaxed = true) {
            io.mockk.every { email } returns TEST_EMAIL
        }
        coEvery { accountService.getCurrentAccount() } returns mockAccount
        coEvery { accountService.resetPassword(any()) } returns Unit

        viewModel.handleIntent(ChangePasswordIntent.OpenForgotPasswordModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `resetPasswordForCurrentUser dismisses loader when resetPassword throws`() = runTest {
        val mockAccount: Account = io.mockk.mockk(relaxed = true) {
            io.mockk.every { email } returns TEST_EMAIL
        }
        coEvery { accountService.getCurrentAccount() } returns mockAccount
        coEvery { accountService.resetPassword(any()) } throws RuntimeException("Network error")

        viewModel.handleIntent(ChangePasswordIntent.OpenForgotPasswordModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val confirm = dialogSlot.captured as DialogModel.Confirm
        confirm.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }
}
