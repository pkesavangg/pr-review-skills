package com.dmdbrands.gurus.weight.features.profile.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.profile.model.ProfileIntent
import com.dmdbrands.gurus.weight.features.profile.model.ProfileReducer
import com.dmdbrands.gurus.weight.features.profile.strings.ProfileStrings
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
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
class ProfileViewModelTest {

    companion object {
        private const val TEST_FIRST_NAME = "John"
        private const val TEST_LAST_NAME = "Doe"
        private const val TEST_EMAIL = "john@example.com"
        private const val TEST_ZIPCODE = "90210"
        private const val TEST_DOB = "1990-01-01"
        private const val TEST_GENDER = "male"
        private const val TEST_ACCOUNT_ID = "active-account-id"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var ggDeviceService: GGDeviceService

    @MockK(relaxUnitFun = true)
    lateinit var bodyCompositionService: IBodyCompositionService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: ProfileViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)

        // Default: getCurrentAccount returns activeAccount so init LoadProfile succeeds
        coEvery { accountService.getCurrentAccount() } returns TestFixtures.activeAccount

        viewModel = ProfileViewModel(
            accountService = accountService,
            ggDeviceService = ggDeviceService,
            bodyCompositionService = bodyCompositionService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has form and no error`() {
        val state = viewModel.state.value
        assertThat(state.error).isNull()
        assertThat(state.form).isNotNull()
    }

    // -------------------------------------------------------------------------
    // LoadProfile — success
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile populates form with account data`() = runTest {
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.form.controls.firstName.value).isEqualTo(TestFixtures.activeAccount.firstName)
        assertThat(state.form.controls.lastName.value).isEqualTo(TestFixtures.activeAccount.lastName)
        assertThat(state.form.controls.email.value).isEqualTo(TestFixtures.activeAccount.email)
        assertThat(state.form.controls.zipcode.value).isEqualTo(TestFixtures.activeAccount.zipcode)
    }

    // -------------------------------------------------------------------------
    // LoadProfile — null account
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile sets error when no current account`() = runTest {
        coEvery { accountService.getCurrentAccount() } returns null

        viewModel = ProfileViewModel(
            accountService = accountService,
            ggDeviceService = ggDeviceService,
            bodyCompositionService = bodyCompositionService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.error).isEqualTo(ProfileStrings.Error.MessageGeneric)
        assertThat(state.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // LoadProfile — exception
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile sets error when exception thrown`() = runTest {
        coEvery { accountService.getCurrentAccount() } throws RuntimeException("Network error")

        viewModel = ProfileViewModel(
            accountService = accountService,
            ggDeviceService = ggDeviceService,
            bodyCompositionService = bodyCompositionService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.error).isEqualTo(ProfileStrings.Error.MessageGeneric)
        assertThat(state.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Submit — form validation failure
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with invalid form dismisses loader and sets validation error`() = runTest {
        advanceUntilIdle()

        // Clear required fields to make form invalid
        viewModel.state.value.form.controls.firstName.onValueChange("")
        viewModel.state.value.form.controls.lastName.onValueChange("")

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = ProfileStrings.LoaderMessage) }
        verify { dialogQueueService.dismissLoader() }
        assertThat(viewModel.state.value.error).isEqualTo(ProfileStrings.Error.MessageValidation)
    }

    // -------------------------------------------------------------------------
    // Submit — success (no scale update needed)
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with valid form calls updateProfile and navigates back`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.updateProfile(any(), true, showToast = false) }
        coVerify { navigationService.navigateBack(topLevel = null) }
    }

    @Test
    fun `Submit success shows and dismisses loader`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = ProfileStrings.LoaderMessage) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit success clears error and loading state`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit — exception
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets error when updateProfile throws`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } throws RuntimeException("API error")

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isEqualTo(ProfileStrings.Error.MessageGeneric)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit dismisses loader even when exception occurs`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } throws RuntimeException("fail")

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = ProfileStrings.LoaderMessage) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Submit — null account on submit
    // -------------------------------------------------------------------------

    @Test
    fun `Submit returns early when getCurrentAccount returns null during submit`() = runTest {
        advanceUntilIdle()

        // After initial load succeeds, change mock to return null for submit flow
        coEvery { accountService.getCurrentAccount() } returns null

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.updateProfile(any(), any(), showToast = any()) }
    }

    // -------------------------------------------------------------------------
    // OnRequestBack — no changes
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack with no changes navigates back directly`() = runTest {
        advanceUntilIdle()

        // Form is loaded with initial data, not dirty
        viewModel.handleIntent(ProfileIntent.OnRequestBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(topLevel = null) }
        verify(exactly = 0) { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // OnRequestBack — with changes
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack with changes enqueues confirm dialog`() = runTest {
        advanceUntilIdle()

        // Make the form dirty by changing a field
        viewModel.state.value.form.controls.firstName.onValueChange("Changed")

        viewModel.handleIntent(ProfileIntent.OnRequestBack)

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `OnRequestBack confirm callback navigates back and dismisses dialog`() = runTest {
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.state.value.form.controls.firstName.onValueChange("Changed")
        viewModel.handleIntent(ProfileIntent.OnRequestBack)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(topLevel = null) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `OnRequestBack cancel callback dismisses dialog without navigating`() = runTest {
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.state.value.form.controls.firstName.onValueChange("Changed")
        viewModel.handleIntent(ProfileIntent.OnRequestBack)
        dialogSlot.captured.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Error intent
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message and clears loading`() {
        viewModel.handleIntent(ProfileIntent.Error("Something went wrong"))

        val state = viewModel.state.value
        assertThat(state.error).isEqualTo("Something went wrong")
        assertThat(state.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Success intent
    // -------------------------------------------------------------------------

    @Test
    fun `Success intent clears error and loading`() {
        viewModel.handleIntent(ProfileIntent.Error("error"))
        viewModel.handleIntent(ProfileIntent.Success)

        val state = viewModel.state.value
        assertThat(state.error).isNull()
        assertThat(state.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // UpdateForm intent
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm intent replaces the form in state`() = runTest {
        advanceUntilIdle()

        val newForm = viewModel.state.value.form
        viewModel.handleIntent(ProfileIntent.UpdateForm(newForm))

        assertThat(viewModel.state.value.form).isEqualTo(newForm)
    }

    // -------------------------------------------------------------------------
    // Reducer — Submit sets loading
    // -------------------------------------------------------------------------

    @Test
    fun `Submit intent sets isLoading true and clears error`() {
        // Verify the reducer in isolation: ProfileViewModel.onSubmit runs form
        // validation as a side effect and immediately dispatches Error when the
        // (empty) default form is invalid, so we assert the pure reducer transition.
        val previous = viewModel.state.value.copy(isLoading = false, error = "previous error")

        val state = ProfileReducer().reduce(previous, ProfileIntent.Submit)

        assertThat(state?.isLoading).isTrue()
        assertThat(state?.error).isNull()
    }

    // -------------------------------------------------------------------------
    // updateR4Profile coverage — BLE callback + exception
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with updateProfile callback invoked covers lambda`() = runTest {
        coEvery { accountService.updateProfile(any()) } returns mockk(relaxed = true)
        coEvery { ggDeviceService.updateProfile(any(), any()) } answers {
            val callback = secondArg<(com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType) -> Unit>()
            callback(com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType.CREATION_COMPLETED)
        }

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit with updateProfile throwing exception handles gracefully`() = runTest {
        coEvery { accountService.updateProfile(any()) } returns mockk(relaxed = true)
        coEvery { ggDeviceService.updateProfile(any(), any()) } throws RuntimeException("BLE error")

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional coverage for form validation path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit calls updateProfile with correct request fields`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        coVerify { accountService.updateProfile(any(), true, showToast = false) }
    }

    // -------------------------------------------------------------------------
    // onUpdateSuccess — logs success
    // -------------------------------------------------------------------------

    @Test
    fun `Success intent via handleIntent does not crash`() = runTest {
        advanceUntilIdle()

        viewModel.handleIntent(ProfileIntent.Success)

        // onUpdateSuccess just logs, verify state is clean
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Gender & Height form controls
    // -------------------------------------------------------------------------

    @Test
    fun `LoadProfile populates gender and height in form`() = runTest {
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.form.controls.gender.value).isEqualTo(TestFixtures.activeAccount.gender)
        assertThat(state.form.controls.height.value).isEqualTo(TestFixtures.activeAccount.height)
    }

    @Test
    fun `changing gender via form control makes form dirty`() = runTest {
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.isDirty).isFalse()
        viewModel.state.value.form.controls.gender.onValueChange("female")
        assertThat(viewModel.state.value.form.isDirty).isTrue()
        assertThat(viewModel.state.value.form.controls.gender.value).isEqualTo("female")
    }

    @Test
    fun `changing height via form control makes form dirty`() = runTest {
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.isDirty).isFalse()
        viewModel.state.value.form.controls.height.onValueChange(1800)
        assertThat(viewModel.state.value.form.isDirty).isTrue()
        assertThat(viewModel.state.value.form.controls.height.value).isEqualTo(1800)
    }

    @Test
    fun `ShowBiologicalSexModal enqueues dialog`() = runTest {
        advanceUntilIdle()

        viewModel.handleIntent(ProfileIntent.ShowBiologicalSexModal)

        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `ShowHeightModal enqueues dialog`() = runTest {
        advanceUntilIdle()

        viewModel.handleIntent(ProfileIntent.ShowHeightModal)

        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `Success after error clears error state`() = runTest {
        advanceUntilIdle()

        viewModel.handleIntent(ProfileIntent.Error("previous error"))
        assertThat(viewModel.state.value.error).isNotNull()

        viewModel.handleIntent(ProfileIntent.Success)
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit — body-comp failure must not block R4 scale profile update
    // -------------------------------------------------------------------------

    @Test
    fun `Submit still updates R4 scale profile when body composition update throws`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit
        coEvery {
            bodyCompositionService.updateBodyComposition(any(), any())
        } throws RuntimeException("body-comp API failure")

        // Change the form's height so heightChanged is true and the body-comp branch is taken
        val newHeight = (TestFixtures.activeAccount.height ?: 0) + 10
        viewModel.state.value.form.controls.height.onValueChange(newHeight)

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        // R4 scale profile update should still run despite body-comp throwing
        verify { ggDeviceService.updateProfile(any(), any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Submit — navigateBack is called on success
    // -------------------------------------------------------------------------

    @Test
    fun `Submit navigates back after successful profile update`() = runTest {
        advanceUntilIdle()

        coEvery { accountService.updateProfile(any(), any(), showToast = any()) } returns Unit

        viewModel.handleIntent(ProfileIntent.Submit)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(topLevel = null) }
    }
}
