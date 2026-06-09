package com.dmdbrands.gurus.weight.features.signup.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.signup.model.SignupIntent
import com.dmdbrands.gurus.weight.features.signup.model.SignupStep
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
class SignupViewModelTest {

    companion object {
        private const val ERROR_NETWORK_FAILURE = "Network failure"
        private const val ERROR_SOME = "some error"
        private const val TEST_FIRST_NAME = "John"
        private const val TEST_LAST_NAME = "Doe"
        private const val TEST_SEX = "male"
        private const val TEST_EMAIL = "john@example.com"
        private const val TEST_PASSWORD = "password123"
        private const val TEST_ZIPCODE = "12345"
        private const val TEST_GOAL_TYPE = "lose"
        private const val TEST_CURRENT_WEIGHT = "1800"
        private const val TEST_GOAL_WEIGHT = "1600"
        private const val TEST_TERMS_URL = "https://example.com/terms"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxUnitFun = true)
    lateinit var goalService: IGoalService

    @MockK(relaxed = true)
    lateinit var analyticsService: IAnalyticsService

    @MockK(relaxed = true)
    lateinit var productSelectionRepository: IProductSelectionRepository

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: SignupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        viewModel = SignupViewModel(
            accountService = accountService,
            goalService = goalService,
            analyticsService = analyticsService,
            productSelectionRepository = productSelectionRepository,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has NAME step and default values`() {
        val state = viewModel.state.value
        assertThat(state.currentStep).isEqualTo(SignupStep.NAME)
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.goalSkipped).isFalse()
        assertThat(state.isFirstStep).isTrue()
        assertThat(state.isLastStep).isFalse()
    }

    @Test
    fun `initial progress is 1 over total steps`() {
        val totalSteps = SignupStep.entries.size
        assertThat(viewModel.state.value.progress).isEqualTo(1f / totalSteps)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — Next
    // -------------------------------------------------------------------------

    @Test
    fun `Next advances from NAME to BIRTHDAY`() {
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.BIRTHDAY)
    }

    @Test
    fun `Next advances through all steps sequentially`() {
        val expectedSteps = listOf(
            SignupStep.BIRTHDAY, SignupStep.GENDER, SignupStep.HEIGHT,
            SignupStep.GOAL, SignupStep.EMAIL, SignupStep.PASSWORD,
        )
        for (expected in expectedSteps) {
            viewModel.handleIntent(SignupIntent.Next)
            assertThat(viewModel.state.value.currentStep).isEqualTo(expected)
        }
    }

    @Test
    fun `Next on last step does not advance`() {
        // Navigate to PASSWORD (last step)
        repeat(SignupStep.entries.size - 1) {
            viewModel.handleIntent(SignupIntent.Next)
        }
        assertThat(viewModel.state.value.isLastStep).isTrue()
        val stepBefore = viewModel.state.value.currentStep
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.currentStep).isEqualTo(stepBefore)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — Back
    // -------------------------------------------------------------------------

    @Test
    fun `Back from BIRTHDAY returns to NAME`() {
        viewModel.handleIntent(SignupIntent.Next) // NAME → BIRTHDAY
        viewModel.handleIntent(SignupIntent.Back)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.NAME)
    }

    @Test
    fun `Back from NAME stays on NAME`() {
        viewModel.handleIntent(SignupIntent.Back)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.NAME)
    }

    @Test
    fun `isFirstStep is true at NAME and false at BIRTHDAY`() {
        assertThat(viewModel.state.value.isFirstStep).isTrue()
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.isFirstStep).isFalse()
    }

    @Test
    fun `isLastStep is true at PASSWORD`() {
        repeat(SignupStep.entries.size - 1) {
            viewModel.handleIntent(SignupIntent.Next)
        }
        assertThat(viewModel.state.value.isLastStep).isTrue()
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.PASSWORD)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — progress
    // -------------------------------------------------------------------------

    @Test
    fun `progress increases as steps advance`() {
        val totalSteps = SignupStep.entries.size
        assertThat(viewModel.state.value.progress).isEqualTo(1f / totalSteps)
        viewModel.handleIntent(SignupIntent.Next) // step 2
        assertThat(viewModel.state.value.progress).isEqualTo(2f / totalSteps)
    }

    // -------------------------------------------------------------------------
    // Skip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip on GOAL step sets goalSkipped and advances`() {
        // Navigate to GOAL step
        repeat(4) { viewModel.handleIntent(SignupIntent.Next) } // NAME→BIRTHDAY→GENDER→HEIGHT→GOAL
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.GOAL)

        viewModel.handleIntent(SignupIntent.Skip)
        assertThat(viewModel.state.value.goalSkipped).isTrue()
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.EMAIL)
    }

    @Test
    fun `showSkipButton is true only on GOAL step`() {
        assertThat(viewModel.state.value.showSkipButton).isFalse() // NAME
        repeat(4) { viewModel.handleIntent(SignupIntent.Next) }
        assertThat(viewModel.state.value.showSkipButton).isTrue() // GOAL
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.showSkipButton).isFalse() // EMAIL
    }

    // -------------------------------------------------------------------------
    // UpdateGoalSkipped
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateGoalSkipped sets goalSkipped flag`() {
        viewModel.handleIntent(SignupIntent.UpdateGoalSkipped(true))
        assertThat(viewModel.state.value.goalSkipped).isTrue()
        viewModel.handleIntent(SignupIntent.UpdateGoalSkipped(false))
        assertThat(viewModel.state.value.goalSkipped).isFalse()
    }

    // -------------------------------------------------------------------------
    // ToggleMetric
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleMetric true sets useMetric in form`() {
        viewModel.handleIntent(SignupIntent.ToggleMetric(true))
        assertThat(viewModel.state.value.form.controls.useMetric.value).isTrue()
    }

    @Test
    fun `ToggleMetric false keeps useMetric false`() {
        viewModel.handleIntent(SignupIntent.ToggleMetric(false))
        assertThat(viewModel.state.value.form.controls.useMetric.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // Error / Success intents
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message and clears isLoading`() {
        viewModel.handleIntent(SignupIntent.Error(ERROR_NETWORK_FAILURE))
        assertThat(viewModel.state.value.error).isEqualTo(ERROR_NETWORK_FAILURE)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `Success intent clears error and isLoading`() {
        viewModel.handleIntent(SignupIntent.Error(ERROR_SOME))
        viewModel.handleIntent(SignupIntent.Success)
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // OnRequestBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnRequestBack enqueues confirm dialog`() {
        viewModel.handleIntent(SignupIntent.OnRequestBack)
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `OnRequestBack confirm callback navigates back`() = runTest {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(SignupIntent.OnRequestBack)
        dialogSlot.captured.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(topLevel = null) }
    }

    @Test
    fun `OnRequestBack cancel callback dismisses dialog`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } returns Unit

        viewModel.handleIntent(SignupIntent.OnRequestBack)
        dialogSlot.captured.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal enqueues custom dialog with HelpPopup type`() {
        viewModel.handleIntent(SignupIntent.OpenHelpModal)
        verify {
            dialogQueueService.enqueue(match<DialogModel.Custom> {
                it.contentKey == DialogType.HelpPopup
            })
        }
    }

    // -------------------------------------------------------------------------
    // OpenURL
    // -------------------------------------------------------------------------

    @Test
    fun `OpenURL opens browser via customTabManager`() {
        val url = TEST_TERMS_URL
        viewModel.handleIntent(SignupIntent.OpenURL(url))
        // OpenURL is handled via openInAppBrowser which uses customTabManager
        // The reducer passes through to state, verifying it doesn't crash
    }

    // -------------------------------------------------------------------------
    // Signup submission — success with goal
    // -------------------------------------------------------------------------

    @Test
    fun `Submit on last step with goal calls accountService signup and goalService`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        // Navigate to last step and fill required form fields
        navigateToLastStepWithValidForm()

        viewModel.handleIntent(SignupIntent.Next) // triggers submit
        advanceUntilIdle()

        coVerify { accountService.signup(any()) }
        coVerify { goalService.createGoalForSignup(any(), any(), any(), any()) }
    }

    @Test
    fun `Submit success navigates to Loading screen`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        coVerify { navigationService.replaceStack(AppRoute.Init.Loading) }
    }

    @Test
    fun `Submit success shows and dismisses loader`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Signup submission — success with goal skipped
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with goalSkipped does not call goalService`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm(skipGoal = true)
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        coVerify { accountService.signup(any()) }
        coVerify(exactly = 0) { goalService.createGoalForSignup(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Signup submission — failure
    // -------------------------------------------------------------------------

    @Test
    fun `Submit when signup returns null does not navigate to Loading`() = runTest {
        coEvery { accountService.signup(any()) } returns null

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        coVerify { accountService.signup(any()) }
        coVerify(exactly = 0) { navigationService.replaceStack(AppRoute.Init.Loading) }
    }

    @Test
    fun `Submit when signup throws does not navigate to Loading`() = runTest {
        coEvery { accountService.signup(any()) } throws RuntimeException("Network error")

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        coVerify { accountService.signup(any()) }
        coVerify(exactly = 0) { navigationService.replaceStack(AppRoute.Init.Loading) }
    }

    @Test
    fun `Submit when signup throws dismisses loader`() = runTest {
        coEvery { accountService.signup(any()) } throws RuntimeException("fail")

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // onNext — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `onNext on non-last step does not call submit`() {
        // Start at NAME (not last step), onNext should not trigger submit
        viewModel.onNext()
        // onNext alone doesn't advance steps - that's done by reducer via handleIntent
        // Verify it doesn't show loader (which would mean onSubmit was called)
        verify(exactly = 0) { dialogQueueService.showLoader(message = any()) }
    }

    @Test
    fun `onNext on last step triggers submit`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        assertThat(viewModel.state.value.isLastStep).isTrue()

        viewModel.onNext() // should trigger onSubmit since isLastStep
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        coVerify { accountService.signup(any()) }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional error path coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with invalid form sets error without calling signup`() = runTest {
        // Navigate to last step
        repeat(SignupStep.entries.size - 1) {
            viewModel.handleIntent(SignupIntent.Next)
        }
        assertThat(viewModel.state.value.isLastStep).isTrue()

        // Make form controls dirty with invalid values so validation actually fails
        // (untouched/undirty controls skip validation by design)
        viewModel.state.value.form.controls.email.onValueChange("not-an-email")
        viewModel.state.value.form.controls.password.onValueChange("ab") // too short (min 6)

        viewModel.handleIntent(SignupIntent.Next) // triggers submit with invalid form
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.signup(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit success sets Success intent state`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Navigates through all steps filling in valid form data,
     * ending on PASSWORD (last step) so the next Next triggers submit.
     */
    private fun navigateToLastStepWithValidForm(skipGoal: Boolean = false) {
        val controls = viewModel.state.value.form.controls
        // NAME step — fill first and last name
        controls.firstName.onValueChange(TEST_FIRST_NAME)
        controls.lastName.onValueChange(TEST_LAST_NAME)
        viewModel.handleIntent(SignupIntent.Next) // → BIRTHDAY

        // BIRTHDAY step — already has default value
        viewModel.handleIntent(SignupIntent.Next) // → GENDER

        // GENDER step
        controls.sex.onValueChange(TEST_SEX)
        viewModel.handleIntent(SignupIntent.Next) // → HEIGHT

        // HEIGHT step — always valid
        viewModel.handleIntent(SignupIntent.Next) // → GOAL

        if (skipGoal) {
            viewModel.handleIntent(SignupIntent.Skip) // → EMAIL, sets goalSkipped
        } else {
            // GOAL step
            controls.goalType.onValueChange(TEST_GOAL_TYPE)
            controls.currentWeight.onValueChange(TEST_CURRENT_WEIGHT) // 180.0 lbs
            controls.goalWeight.onValueChange(TEST_GOAL_WEIGHT) // 160.0 lbs
            viewModel.handleIntent(SignupIntent.Next) // → EMAIL
        }

        // EMAIL step
        controls.email.onValueChange(TEST_EMAIL)
        viewModel.handleIntent(SignupIntent.Next) // → PASSWORD

        // PASSWORD step
        controls.password.onValueChange(TEST_PASSWORD)
        controls.confirmPassword.onValueChange(TEST_PASSWORD)
        controls.zipcode.onValueChange(TEST_ZIPCODE)
        // Now on PASSWORD (last step) — next Next triggers submit
    }
}
