package com.dmdbrands.gurus.weight.features.goal.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.enums.GoalType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.goal.model.GoalIntent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {

    companion object {
        private const val SUCCESS_TITLE = "Success!"
        private const val SUCCESS_MESSAGE = "Goal Saved."
        private const val SAVE_ERROR_MESSAGE = "Failed to save goal"
        private const val LOADER_MESSAGE = "Saving..."
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
    lateinit var goalService: IGoalService

    @MockK(relaxUnitFun = true)
    lateinit var entryService: IEntryService

    @MockK(relaxed = true)
    lateinit var ggDeviceService: GGDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: GoalViewModel

    private val accountFlow = MutableStateFlow<Account?>(null)
    private val latestEntryFlow = MutableStateFlow<ScaleEntry?>(null)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns accountFlow
        every { entryService.latestEntry } returns latestEntryFlow
    }

    private fun createViewModel(): GoalViewModel =
        GoalViewModel(
            dialogUtility = dialogUtility,
            accountService = accountService,
            goalService = goalService,
            entryService = entryService,
            ggDeviceService = ggDeviceService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    private fun accountWithGoal(
        goalType: String = GoalType.LOSE_GAIN.value,
        goalWeight: Double = 150.0,
        initialWeight: Double = 180.0,
        weightUnit: WeightUnit = WeightUnit.LB,
    ): Account = TestFixtures.anAccount(
        isActiveAccount = true,
        isLoggedIn = true,
        weightUnit = weightUnit,
    ).copy(
        goalType = goalType,
        goalWeight = goalWeight,
        initialWeight = initialWeight,
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
        assertThat(state.account).isNull()
    }

    @Test
    fun `initial state form has LOSE_GAIN goal type`() {
        viewModel = createViewModel()
        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    // -------------------------------------------------------------------------
    // Init — subscribes to account flow
    // -------------------------------------------------------------------------

    @Test
    fun `subscribes to activeAccountFlow and updates account`() = runTest {
        viewModel = createViewModel()
        val account = accountWithGoal()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.account).isEqualTo(account)
    }

    // -------------------------------------------------------------------------
    // Init — subscribes to latest entry flow
    // -------------------------------------------------------------------------

    @Test
    fun `subscribes to latestEntry and updates latestWeight`() = runTest {
        viewModel = createViewModel()
        val entry = TestFixtures.weightEntry
        latestEntryFlow.value = entry
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isEqualTo(entry.scale.scaleEntry.weight)
    }

    // -------------------------------------------------------------------------
    // Submit — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit shows loader`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = LOADER_MESSAGE) }
    }

    @Test
    fun `Submit calls goalService updateGoal`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify { goalService.updateGoal(any(), any(), any(), any()) }
    }

    @Test
    fun `Submit dismisses loader after success`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Submit — error path
    // -------------------------------------------------------------------------

    @Test
    fun `Submit sets error when goalService throws`() = runTest {
        coEvery { goalService.updateGoal(any(), any(), any(), any()) } throws RuntimeException("API error")
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isEqualTo(SAVE_ERROR_MESSAGE)
    }

    @Test
    fun `Submit dismisses loader when goalService throws`() = runTest {
        coEvery { goalService.updateGoal(any(), any(), any(), any()) } throws RuntimeException("API error")
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit returns early when account is null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { goalService.updateGoal(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // OnBack — no changes
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack navigates back when no changes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.OnBack)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // OnBack — with unsaved changes
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack shows unsaved changes dialog when form is dirty`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        // Make the form dirty by changing a value
        viewModel.state.value.form.controls.goalWeight.onValueChange("200")
        viewModel.handleIntent(GoalIntent.OnBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.title).isEqualTo(UNSAVED_CHANGES_TITLE)
    }

    @Test
    fun `OnBack dialog onConfirm navigates back and dismisses`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.state.value.form.controls.goalWeight.onValueChange("200")
        viewModel.handleIntent(GoalIntent.OnBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `OnBack dialog onCancel dismisses dialog`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.state.value.form.controls.goalWeight.onValueChange("200")
        viewModel.handleIntent(GoalIntent.OnBack)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success shows toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Success)
        advanceUntilIdle()

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.title == SUCCESS_TITLE && it.message == SUCCESS_MESSAGE
            })
        }
    }

    @Test
    fun `Success navigates back`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // HandleGoalMet
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalMet with setNewGoal true resets form to LOSE_GAIN`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal(goalType = GoalType.MAINTAIN.value)
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.HandleGoalMet(setNewGoal = true))
        advanceUntilIdle()

        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    @Test
    fun `HandleGoalMet with setNewGoal false switches to maintain mode`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.HandleGoalMet(setNewGoal = false))
        advanceUntilIdle()

        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.MAINTAIN.value)
    }

    // -------------------------------------------------------------------------
    // HandleGoalLeave
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalLeave with updateGoal true does not crash`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.HandleGoalLeave(updateGoal = true))
        advanceUntilIdle()

        // No exception expected; just logging
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `HandleGoalLeave with updateGoal false does not crash`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.HandleGoalLeave(updateGoal = false))
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — ChangeGoalType
    // -------------------------------------------------------------------------

    @Test
    fun `ChangeGoalType to MAINTAIN updates goalType control`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.MAINTAIN))

        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.MAINTAIN.value)
    }

    @Test
    fun `ChangeGoalType to LOSE_GAIN updates goalType control`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.MAINTAIN))
        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    // -------------------------------------------------------------------------
    // Reducer — Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error intent sets error message`() {
        viewModel = createViewModel()

        viewModel.handleIntent(GoalIntent.Error("Something failed"))

        assertThat(viewModel.state.value.error).isEqualTo("Something failed")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // Reducer — UpdateAccount
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateAccount sets account in state`() {
        viewModel = createViewModel()
        val account = accountWithGoal()

        viewModel.handleIntent(GoalIntent.UpdateAccount(account))

        assertThat(viewModel.state.value.account).isEqualTo(account)
    }

    @Test
    fun `UpdateAccount with null clears account`() {
        viewModel = createViewModel()

        viewModel.handleIntent(GoalIntent.UpdateAccount(null))

        assertThat(viewModel.state.value.account).isNull()
    }

    // -------------------------------------------------------------------------
    // Reducer — UpdateLatestWeight
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateLatestWeight sets latestWeight`() {
        viewModel = createViewModel()

        viewModel.handleIntent(GoalIntent.UpdateLatestWeight(175.5))

        assertThat(viewModel.state.value.latestWeight).isEqualTo(175.5)
    }

    @Test
    fun `UpdateLatestWeight with null sets null`() {
        viewModel = createViewModel()

        viewModel.handleIntent(GoalIntent.UpdateLatestWeight(null))

        assertThat(viewModel.state.value.latestWeight).isNull()
    }
}
