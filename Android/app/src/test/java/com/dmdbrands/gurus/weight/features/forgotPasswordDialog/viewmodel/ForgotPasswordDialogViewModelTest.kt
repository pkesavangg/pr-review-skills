package com.dmdbrands.gurus.weight.features.forgotPasswordDialog.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.forgotPasswordDialog.model.ForgotPasswordDialogIntent
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordDialogViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: ForgotPasswordDialogViewModel

    companion object {
        private const val TEST_EMAIL = "test@example.com"
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = ForgotPasswordDialogViewModel(
            accountService = accountService,
        ).initTestDependencies(
            dialogQueueService = dialogQueueService,
        )
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty form and no loading or error`() {
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isSuccess).isFalse()
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with valid email calls resetPassword and sets success`() = runTest {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)
        coEvery { accountService.resetPassword(any()) } returns Unit

        viewModel.handleIntent(ForgotPasswordDialogIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.resetPassword(TEST_EMAIL) }
        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `Submit with exception sets error`() = runTest {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)
        coEvery { accountService.resetPassword(any()) } throws RuntimeException("Network error")

        viewModel.handleIntent(ForgotPasswordDialogIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNotNull()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit dismisses current dialog`() = runTest {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)
        coEvery { accountService.resetPassword(any()) } returns Unit

        viewModel.handleIntent(ForgotPasswordDialogIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    @Test
    fun `Close resets the form`() {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)

        viewModel.handleIntent(ForgotPasswordDialogIntent.Close)

        // Form should be reset — email cleared
        assertThat(viewModel.state.value.form.controls.email.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // SetEmail
    // -------------------------------------------------------------------------

    @Test
    fun `SetEmail updates email form control`() {
        viewModel.handleIntent(ForgotPasswordDialogIntent.SetEmail(TEST_EMAIL))

        assertThat(viewModel.state.value.form.controls.email.value).isEqualTo(TEST_EMAIL)
    }

    @Test
    fun `SetEmail with blank is handled by reducer updating form`() {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)

        viewModel.handleIntent(ForgotPasswordDialogIntent.SetEmail(""))

        // Reducer calls onValueChange("") on the control; private setEmail
        // ignores blank but reducer still processes the intent
        assertThat(viewModel.state.value.form.controls.email.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // setInitialEmail
    // -------------------------------------------------------------------------

    @Test
    fun `setInitialEmail with non-blank sets email`() {
        viewModel.setInitialEmail(TEST_EMAIL)

        assertThat(viewModel.state.value.form.controls.email.value).isEqualTo(TEST_EMAIL)
    }

    @Test
    fun `setInitialEmail with blank does not set email`() {
        viewModel.setInitialEmail("")

        assertThat(viewModel.state.value.form.controls.email.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // isSubmitEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `isSubmitEnabled is true with valid email and not loading`() {
        viewModel.state.value.form.controls.email.onValueChange(TEST_EMAIL)
        viewModel.state.value.form.controls.email.onBlur()

        assertThat(viewModel.isSubmitEnabled).isTrue()
    }

    @Test
    fun `isSubmitEnabled is false with empty email`() {
        assertThat(viewModel.isSubmitEnabled).isFalse()
    }
}
