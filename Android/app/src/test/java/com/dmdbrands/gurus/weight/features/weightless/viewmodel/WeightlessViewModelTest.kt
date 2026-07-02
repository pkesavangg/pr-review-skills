package com.dmdbrands.gurus.weight.features.weightless.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.weightless.model.WeightlessIntent
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class WeightlessViewModelTest {

    companion object {
        private const val SUCCESS_TITLE = "Success!"
        private const val SUCCESS_MESSAGE = "Weightless Updated."
        private const val LOADER_MESSAGE = "Saving weightless settings..."
        private const val HELP_TITLE = "About Weightless Mode"
        private const val UNSAVED_CHANGES_TITLE = "Confirm"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var dialogUtility: IDialogUtility

    @MockK(relaxUnitFun = true)
    lateinit var accountService: IAccountService

    @MockK(relaxUnitFun = true)
    lateinit var userSettingsService: IUserSettingsService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: WeightlessViewModel

    private val accountFlow = MutableStateFlow<Account?>(null)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns accountFlow
    }

    private fun createViewModel(): WeightlessViewModel =
        WeightlessViewModel(
            dialogUtility = dialogUtility,
            accountService = accountService,
            userSettingsService = userSettingsService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    private fun accountWithWeightless(
        isWeightlessOn: Boolean = false,
        weightlessWeight: Float? = null,
        weightUnit: WeightUnit = WeightUnit.LB,
    ): Account = TestFixtures.anAccount(
        isActiveAccount = true,
        isLoggedIn = true,
        weightUnit = weightUnit,
    ).copy(
        isWeightlessOn = isWeightlessOn,
        weightlessWeight = weightlessWeight,
    )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        viewModel = createViewModel()
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.isWeightlessOn).isFalse()
        assertThat(state.hasToggleChanged).isFalse()
        assertThat(state.weightUnit).isEqualTo(WeightUnit.LB)
        assertThat(state.isMetric).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — subscribes to account flow
    // -------------------------------------------------------------------------

    @Test
    fun `subscribes to activeAccountFlow and updates state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(isWeightlessOn = true, weightlessWeight = 150.0f)
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.account).isEqualTo(account)
        assertThat(viewModel.state.value.isWeightlessOn).isTrue()
    }

    @Test
    fun `account with KG unit sets isMetric to true`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(weightUnit = WeightUnit.KG)
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isTrue()
        assertThat(viewModel.state.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `null account does not update state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = null
        advanceUntilIdle()

        assertThat(viewModel.state.value.account).isNull()
    }

    // -------------------------------------------------------------------------
    // Submit — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit shows loader`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = LOADER_MESSAGE) }
    }

    @Test
    fun `Submit calls userSettingsService toggleWeightlessSetting`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        coVerify { userSettingsService.toggleWeightlessSetting(any(), any()) }
    }

    @Test
    fun `Submit dismisses loader after success`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit shows success toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        verify {
            dialogQueueService.showToast(match<Toast.Simple> {
                it.title == SUCCESS_TITLE && it.message == SUCCESS_MESSAGE
            })
        }
    }

    @Test
    fun `Submit navigates back on success`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // Submit — error path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit dismisses loader when toggleWeightlessSetting throws`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { userSettingsService.toggleWeightlessSetting(any(), any()) } throws RuntimeException("API error")
        viewModel = createViewModel()
        accountFlow.value = accountWithWeightless()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit returns early when account is null`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { userSettingsService.toggleWeightlessSetting(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // OpenHelpModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelpModal enqueues alert dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.OpenHelpModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured
        assertThat(dialog).isInstanceOf(DialogModel.Alert::class.java)
    }

    @Test
    fun `OpenHelpModal dialog has correct title`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.OpenHelpModal)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Alert
        assertThat(dialog.title).isEqualTo(HELP_TITLE)
    }

    // -------------------------------------------------------------------------
    // OnBack — no changes
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack navigates back when no changes`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.OnBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // OnBack — with unsaved changes (toggle changed)
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack shows unsaved changes dialog when toggle changed`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Toggle weightless to make hasToggleChanged = true
        viewModel.handleIntent(WeightlessIntent.ToggleWeightless)
        viewModel.handleIntent(WeightlessIntent.OnBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.title).isEqualTo(UNSAVED_CHANGES_TITLE)
    }

    @Test
    fun `OnBack dialog onConfirm navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.ToggleWeightless)
        viewModel.handleIntent(WeightlessIntent.OnBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success shows toast and navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Success)
        advanceUntilIdle()

        verify {
            dialogQueueService.showToast(match<Toast.Simple> {
                it.title == SUCCESS_TITLE && it.message == SUCCESS_MESSAGE
            })
        }
        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // Reducer — ToggleWeightless
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleWeightless flips isWeightlessOn from false to true`() {
        viewModel = createViewModel()

        viewModel.handleIntent(WeightlessIntent.ToggleWeightless)

        assertThat(viewModel.state.value.isWeightlessOn).isTrue()
        assertThat(viewModel.state.value.hasToggleChanged).isTrue()
    }

    @Test
    fun `ToggleWeightless flips isWeightlessOn from true to false`() {
        viewModel = createViewModel()

        viewModel.handleIntent(WeightlessIntent.ToggleWeightless)
        viewModel.handleIntent(WeightlessIntent.ToggleWeightless)

        assertThat(viewModel.state.value.isWeightlessOn).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message`() {
        viewModel = createViewModel()

        viewModel.handleIntent(WeightlessIntent.Error("Something failed"))

        assertThat(viewModel.state.value.error).isEqualTo("Something failed")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — UpdateForm
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateForm replaces form in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val newForm = viewModel.state.value.form
        viewModel.handleIntent(WeightlessIntent.UpdateForm(newForm))

        assertThat(viewModel.state.value.form).isEqualTo(newForm)
    }

    // -------------------------------------------------------------------------
    // Reducer — Submit sets loading
    // -------------------------------------------------------------------------

    @Test
    fun `Submit reducer sets isLoading to true and clears error`() {
        viewModel = createViewModel()
        viewModel.handleIntent(WeightlessIntent.Error("Previous error"))

        viewModel.handleIntent(WeightlessIntent.Submit)

        assertThat(viewModel.state.value.isLoading).isTrue()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Reducer — Success clears loading
    // -------------------------------------------------------------------------

    @Test
    fun `Success reducer sets isLoading to false`() {
        viewModel = createViewModel()
        viewModel.handleIntent(WeightlessIntent.Submit)

        viewModel.handleIntent(WeightlessIntent.Success)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    // -------------------------------------------------------------------------
    // updateStateWithAccount — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `updateStateWithAccount with weightless on and weight populates form`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(
            isWeightlessOn = true,
            weightlessWeight = 150.0f,
            weightUnit = WeightUnit.LB,
        )
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.isWeightlessOn).isTrue()
        assertThat(viewModel.state.value.form.controls.weightlessWeight.value).isNotEmpty()
    }

    @Test
    fun `updateStateWithAccount with weightless off uses default weight`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(
            isWeightlessOn = false,
            weightlessWeight = null,
        )
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.isWeightlessOn).isFalse()
        assertThat(viewModel.state.value.hasToggleChanged).isFalse()
    }

    @Test
    fun `updateStateWithAccount with KG unit sets isMetric true`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(weightUnit = WeightUnit.KG)
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.isMetric).isTrue()
        assertThat(viewModel.state.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun `updateStateWithAccount resets hasToggleChanged to false`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        viewModel.handleIntent(WeightlessIntent.ToggleWeightless) // hasToggleChanged = true
        assertThat(viewModel.state.value.hasToggleChanged).isTrue()

        val account = accountWithWeightless()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasToggleChanged).isFalse()
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with weightless on calls toggleWeightlessSetting with correct params`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        val account = accountWithWeightless(isWeightlessOn = true, weightlessWeight = 150.0f)
        accountFlow.value = account
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Submit)
        advanceUntilIdle()

        coVerify {
            userSettingsService.toggleWeightlessSetting(
                isWeightlessOn = true,
                weightlessWeight = any(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // onSuccess — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Success shows toast with correct title and message`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Success)
        advanceUntilIdle()

        verify {
            dialogQueueService.showToast(match<Toast.Simple> {
                it.title == SUCCESS_TITLE && it.message == SUCCESS_MESSAGE && it.action == null
            })
        }
    }

    @Test
    fun `Success navigates back with null topLevel`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(WeightlessIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }
}
