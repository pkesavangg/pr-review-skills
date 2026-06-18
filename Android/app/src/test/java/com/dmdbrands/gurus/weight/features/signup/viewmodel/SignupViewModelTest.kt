package com.dmdbrands.gurus.weight.features.signup.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.api.auth.SignupRequest
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.signup.strings.SignupErrorStrings
import com.dmdbrands.gurus.weight.features.signup.model.BabyFormControls
import com.dmdbrands.gurus.weight.features.signup.model.BabyWeightUnit
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

    @MockK(relaxUnitFun = true)
    lateinit var analyticsService: IAnalyticsService

    @MockK(relaxUnitFun = true)
    lateinit var babyProfileService: IBabyProfileService

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
            babyProfileService = babyProfileService,
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
        // The Phase 2 flow starts on the common step list (NAME, EMAIL,
        // BIRTHDAY, PICK_DEVICE, PASSWORD) — progress is 1 / steps.size.
        val totalSteps = viewModel.state.value.steps.size
        assertThat(viewModel.state.value.progress).isEqualTo(1f / totalSteps)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — Next
    // -------------------------------------------------------------------------

    @Test
    fun `Next advances from NAME to EMAIL`() {
        // COMMON_STEPS order: NAME → EMAIL → BIRTHDAY → PICK_DEVICE → PASSWORD
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.EMAIL)
    }

    @Test
    fun `Next advances through common steps sequentially`() {
        // Before a device is picked the flow walks the common step list only.
        val expectedSteps = listOf(
            SignupStep.EMAIL, SignupStep.BIRTHDAY, SignupStep.PICK_DEVICE,
            SignupStep.PASSWORD,
        )
        for (expected in expectedSteps) {
            viewModel.handleIntent(SignupIntent.Next)
            assertThat(viewModel.state.value.currentStep).isEqualTo(expected)
        }
    }

    @Test
    fun `Next on last step does not advance`() {
        // Walk to PASSWORD (last step of COMMON_STEPS).
        repeat(viewModel.state.value.steps.size - 1) {
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
    fun `Back from EMAIL returns to NAME`() {
        viewModel.handleIntent(SignupIntent.Next) // NAME → EMAIL
        viewModel.handleIntent(SignupIntent.Back)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.NAME)
    }

    @Test
    fun `Back from NAME stays on NAME`() {
        viewModel.handleIntent(SignupIntent.Back)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.NAME)
    }

    @Test
    fun `isFirstStep is true at NAME and false at EMAIL`() {
        assertThat(viewModel.state.value.isFirstStep).isTrue()
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.isFirstStep).isFalse()
    }

    @Test
    fun `isLastStep is true at PASSWORD`() {
        repeat(viewModel.state.value.steps.size - 1) {
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
        val totalSteps = viewModel.state.value.steps.size
        assertThat(viewModel.state.value.progress).isEqualTo(1f / totalSteps)
        viewModel.handleIntent(SignupIntent.Next) // step 2
        assertThat(viewModel.state.value.progress).isEqualTo(2f / totalSteps)
    }

    // -------------------------------------------------------------------------
    // Skip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip on GOAL step sets goalSkipped and advances to PASSWORD`() {
        navigateToGoalStep()
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.GOAL)

        viewModel.handleIntent(SignupIntent.Skip)
        assertThat(viewModel.state.value.goalSkipped).isTrue()
        // First-pass Skip on GOAL advances to PASSWORD (next in the Weight
        // Scale step list).
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.PASSWORD)
    }

    @Test
    fun `showSkipButton is true only on GOAL step`() {
        assertThat(viewModel.state.value.showSkipButton).isFalse() // NAME
        navigateToGoalStep()
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.GOAL)
        assertThat(viewModel.state.value.showSkipButton).isTrue() // GOAL
        viewModel.handleIntent(SignupIntent.Next)
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.PASSWORD)
        assertThat(viewModel.state.value.showSkipButton).isFalse() // PASSWORD
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
    fun `Submit success advances to a Ready terminal step without navigating`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next) // triggers submit → RegisterDevice
        advanceUntilIdle()

        // Submit no longer navigates; it records the device and advances to the
        // terminal Ready step. Navigation happens later via FinishSignup.
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.DEVICE_READY)
        assertThat(viewModel.state.value.registeredDevices).contains(ProductType.MY_WEIGHT)
        coVerify(exactly = 0) { navigationService.replaceStack(AppRoute.Init.Loading) }
    }

    @Test
    fun `FinishSignup navigates to Loading screen`() = runTest {
        viewModel.handleIntent(SignupIntent.FinishSignup)
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
    fun `Submit does not show generic toast when signup returns null`() = runTest {
        // AccountService.signup already surfaces the specific failure toast (e.g.
        // "Email address is already in use"). The VM must not mask it with the
        // generic "We couldn't create your account" toast. (MOB-592)
        coEvery { accountService.signup(any()) } returns null

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        coVerify { accountService.signup(any()) }
        verify(exactly = 0) {
            dialogQueueService.showToast(
                match<Toast.Simple> { it.message == SignupErrorStrings.accountFailedToast },
            )
        }
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
        // PASSWORD is the final data step (next is DEVICE_READY) — onNext submits.
        assertThat(viewModel.state.value.isFinalDataStep).isTrue()

        viewModel.onNext() // should trigger onSubmit since isFinalDataStep
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        coVerify { accountService.signup(any()) }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional error path coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with invalid form sets error without calling signup`() = runTest {
        // Reach the final data step (PASSWORD, next is DEVICE_READY) with a
        // device selected so submit runs first-pass validation.
        navigateToLastStepWithValidForm()
        assertThat(viewModel.state.value.isFinalDataStep).isTrue()

        // Corrupt fields so first-pass validation fails.
        val controls = viewModel.state.value.form.controls
        controls.email.onValueChange("not-an-email")
        controls.password.onValueChange("ab") // too short (min 6)

        viewModel.handleIntent(SignupIntent.Next) // triggers submit with invalid form
        advanceUntilIdle()

        // Validation fails before the API call and before the loader is shown,
        // so neither signup nor the loader is ever reached.
        coVerify(exactly = 0) { accountService.signup(any()) }
        verify(exactly = 0) { dialogQueueService.showLoader(message = any()) }
        // No device gets registered and the flow stays on the final data step.
        assertThat(viewModel.state.value.registeredDevices).isEmpty()
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.PASSWORD)
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
     * Walks the common head and selects the Weight Scale device so the step
     * list expands to include GENDER / HEIGHT / GOAL, landing on the GOAL step.
     *
     * Resulting Weight Scale step list (first pass):
     * NAME → EMAIL → BIRTHDAY → PICK_DEVICE → GENDER → HEIGHT → GOAL →
     * PASSWORD → DEVICE_READY.
     */
    private fun navigateToGoalStep() {
        val controls = viewModel.state.value.form.controls
        // NAME
        controls.firstName.onValueChange(TEST_FIRST_NAME)
        controls.lastName.onValueChange(TEST_LAST_NAME)
        viewModel.handleIntent(SignupIntent.Next) // → EMAIL

        // EMAIL
        controls.email.onValueChange(TEST_EMAIL)
        viewModel.handleIntent(SignupIntent.Next) // → BIRTHDAY

        // BIRTHDAY (default value valid)
        viewModel.handleIntent(SignupIntent.Next) // → PICK_DEVICE

        // PICK_DEVICE — pick the Weight Scale, expanding the step list.
        viewModel.handleIntent(SignupIntent.SelectDevice(ProductType.MY_WEIGHT.id))
        viewModel.handleIntent(SignupIntent.Next) // → GENDER

        // GENDER
        controls.sex.onValueChange(TEST_SEX)
        viewModel.handleIntent(SignupIntent.Next) // → HEIGHT

        // HEIGHT — always valid
        viewModel.handleIntent(SignupIntent.Next) // → GOAL
    }

    /**
     * Navigates through the Weight Scale flow filling in valid form data,
     * ending on PASSWORD — the final data step before DEVICE_READY — so the
     * next Next triggers submit.
     */
    private fun navigateToLastStepWithValidForm(skipGoal: Boolean = false) {
        val controls = viewModel.state.value.form.controls
        navigateToGoalStep()

        if (skipGoal) {
            viewModel.handleIntent(SignupIntent.Skip) // → PASSWORD, sets goalSkipped
        } else {
            // GOAL step
            controls.goalType.onValueChange(TEST_GOAL_TYPE)
            controls.currentWeight.onValueChange(TEST_CURRENT_WEIGHT) // 180.0 lbs
            controls.goalWeight.onValueChange(TEST_GOAL_WEIGHT) // 160.0 lbs
            viewModel.handleIntent(SignupIntent.Next) // → PASSWORD
        }

        // PASSWORD step (final data step — next is DEVICE_READY)
        controls.password.onValueChange(TEST_PASSWORD)
        controls.confirmPassword.onValueChange(TEST_PASSWORD)
        controls.zipcode.onValueChange(TEST_ZIPCODE)
        // Now on PASSWORD — next Next triggers submit
    }

    // -------------------------------------------------------------------------
    // Baby signup — persist babies to the server (POST /v3/baby/)
    // -------------------------------------------------------------------------

    /**
     * Walks the Baby Scale flow: fills the common head, selects the baby scale, configures the
     * baby form via [configureBaby] and saves it, then fills PASSWORD — landing on the final
     * data step so the next Next triggers submit.
     */
    private fun navigateToBabyPasswordStep(configureBaby: (BabyFormControls) -> Unit) {
        val controls = viewModel.state.value.form.controls
        controls.firstName.onValueChange(TEST_FIRST_NAME)
        controls.lastName.onValueChange(TEST_LAST_NAME)
        viewModel.handleIntent(SignupIntent.Next) // → EMAIL
        controls.email.onValueChange(TEST_EMAIL)
        viewModel.handleIntent(SignupIntent.Next) // → BIRTHDAY
        viewModel.handleIntent(SignupIntent.Next) // → PICK_DEVICE
        viewModel.handleIntent(SignupIntent.SelectDevice(ProductType.BABY.id))
        viewModel.handleIntent(SignupIntent.Next) // → ADD_BABY

        val babyForm = requireNotNull(viewModel.state.value.babyState).babyForm
        configureBaby(babyForm)
        viewModel.handleIntent(SignupIntent.Next) // ADD_BABY → BABY_ADDED (baby saved to list)
        viewModel.handleIntent(SignupIntent.Next) // BABY_ADDED → PASSWORD

        controls.password.onValueChange(TEST_PASSWORD)
        controls.confirmPassword.onValueChange(TEST_PASSWORD)
        controls.zipcode.onValueChange(TEST_ZIPCODE)
    }

    @Test
    fun `Submit baby scale persists baby with lbs-oz weight and inch length converted`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount

        navigateToBabyPasswordStep { form ->
            form.name.onValueChange("Tammy")
            form.biologicalSex.onValueChange("male")
            form.weightUnit.onValueChange(BabyWeightUnit.LBS_OZ)
            form.birthWeight.onValueChange("7")
            form.birthWeightOz.onValueChange("4")
            form.birthLength.onValueChange("20")
        }
        viewModel.handleIntent(SignupIntent.Next) // triggers submit
        advanceUntilIdle()

        val expectedDg = ConversionTools.convertLbOzToDecigrams(7, 4.0)
        val expectedMm = ConversionTools.convertInchesToMm(20.0)
        coVerify {
            babyProfileService.save(
                match {
                    it.name == "Tammy" &&
                        it.sex == "male" &&
                        it.accountId == TestFixtures.activeAccount.id &&
                        it.birthWeightDecigrams == expectedDg &&
                        it.birthLengthMillimeters == expectedMm &&
                        it.birthdate != null
                },
            )
        }
    }

    @Test
    fun `Submit baby scale persists baby with kg weight and cm length converted`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount

        navigateToBabyPasswordStep { form ->
            form.name.onValueChange("Katey")
            form.biologicalSex.onValueChange("female")
            form.weightUnit.onValueChange(BabyWeightUnit.KG)
            form.birthWeight.onValueChange("3.5")
            form.birthLength.onValueChange("50")
        }
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        val expectedDg = ConversionTools.convertKgToDecigrams(3.5)
        val expectedMm = ConversionTools.convertCmToMm(50.0)
        coVerify {
            babyProfileService.save(
                match {
                    it.name == "Katey" &&
                        it.sex == "female" &&
                        it.birthWeightDecigrams == expectedDg &&
                        it.birthLengthMillimeters == expectedMm
                },
            )
        }
    }

    @Test
    fun `Submit baby scale persists baby with decimal-lbs weight converted`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount

        navigateToBabyPasswordStep { form ->
            form.name.onValueChange("Sam")
            form.biologicalSex.onValueChange("other")
            form.weightUnit.onValueChange(BabyWeightUnit.LBS)
            form.birthWeight.onValueChange("7.5")
            form.birthLength.onValueChange("19")
        }
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        val expectedDg = ConversionTools.convertLbToDecigrams(7.5)
        coVerify {
            babyProfileService.save(
                match {
                    it.name == "Sam" &&
                        // Gender.OTHER maps to BabySex "private".
                        it.sex == "private" &&
                        it.birthWeightDecigrams == expectedDg
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Signup request payload — conditional gender/dob/height (MOB-592 / MOB-377)
    // -------------------------------------------------------------------------

    @Test
    fun `Submit baby scale omits gender dob and height so the server does not 400`() = runTest {
        // The baby path skips the GENDER/HEIGHT steps, leaving sex = "". Sending an
        // empty gender made the server reject the request as a missing required value.
        // For a baby-only account these fields must be omitted (null), not "".
        val requestSlot = slot<SignupRequest>()
        coEvery { accountService.signup(capture(requestSlot)) } returns TestFixtures.activeAccount

        navigateToBabyPasswordStep { form ->
            form.name.onValueChange("Tammy")
            form.biologicalSex.onValueChange("male")
            form.weightUnit.onValueChange(BabyWeightUnit.LBS)
            form.birthWeight.onValueChange("7")
        }
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        val request = requestSlot.captured
        assertThat(request.productTypes).containsExactly(ProductType.BABY.apiValue)
        assertThat(request.gender).isNull()
        assertThat(request.dob).isNull()
        assertThat(request.height).isNull()
    }

    @Test
    fun `Submit weight scale sends gender dob and height`() = runTest {
        val requestSlot = slot<SignupRequest>()
        coEvery { accountService.signup(capture(requestSlot)) } returns TestFixtures.activeAccount
        coEvery { goalService.createGoalForSignup(any(), any(), any(), any()) } returns TestFixtures.activeAccount

        navigateToLastStepWithValidForm()
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        val request = requestSlot.captured
        assertThat(request.productTypes).containsExactly(ProductType.MY_WEIGHT.apiValue)
        assertThat(request.gender).isEqualTo(TEST_SEX)
        assertThat(request.dob).isNotNull()
        assertThat(request.height).isNotNull()
    }

    @Test
    fun `Submit baby scale swallows a save failure and still reaches the Ready terminal`() = runTest {
        coEvery { accountService.signup(any()) } returns TestFixtures.activeAccount
        coEvery { babyProfileService.save(any()) } throws RuntimeException("network down")

        navigateToBabyPasswordStep { form ->
            form.name.onValueChange("Tammy")
            form.biologicalSex.onValueChange("male")
            form.weightUnit.onValueChange(BabyWeightUnit.LBS)
            form.birthWeight.onValueChange("7")
        }
        viewModel.handleIntent(SignupIntent.Next)
        advanceUntilIdle()

        // The failure is best-effort/swallowed: save was attempted and the flow still advances.
        coVerify { babyProfileService.save(any()) }
        assertThat(viewModel.state.value.currentStep).isEqualTo(SignupStep.DEVICE_READY)
    }
}
