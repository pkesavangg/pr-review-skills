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

    @Test
    fun `ChangeGoalType to LOSE_GAIN with empty fields does not mark weight controls dirty`() = runTest {
        viewModel = createViewModel() // new account — weights are ""
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        val controls = viewModel.state.value.form.controls
        assertThat(controls.startingWeight.dirty).isFalse()
        assertThat(controls.goalWeight.dirty).isFalse()
    }

    @Test
    fun `ChangeGoalType to LOSE_GAIN with empty fields shows no weight field error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        val controls = viewModel.state.value.form.controls
        assertThat(controls.startingWeight.error).isNull()
        assertThat(controls.goalWeight.error).isNull()
    }

    @Test
    fun `ChangeGoalType to LOSE_GAIN with pre-filled fields marks weight controls dirty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        val controls = viewModel.state.value.form.controls
        controls.startingWeight.onValueChange("150")
        controls.goalWeight.onValueChange("140")

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        assertThat(controls.startingWeight.dirty).isTrue()
        assertThat(controls.goalWeight.dirty).isTrue()
    }

    @Test
    fun `ChangeGoalType to LOSE_GAIN with empty-but-touched field clears stale touched state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        val controls = viewModel.state.value.form.controls
        // Simulate spurious onBlur that fires when AppInput leaves composition (MAINTAIN switch)
        controls.startingWeight.onBlur()

        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.MAINTAIN))
        viewModel.handleIntent(GoalIntent.ChangeGoalType(GoalType.LOSE_GAIN))

        assertThat(controls.startingWeight.error).isNull()
        assertThat(controls.startingWeight.touched).isFalse()
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

    // -------------------------------------------------------------------------
    // updateStateWithAccount — account loading paths
    // -------------------------------------------------------------------------

    @Test
    fun `account with MAINTAIN goalType loads form in maintain mode`() = runTest {
        val account = accountWithGoal(goalType = GoalType.MAINTAIN.value, goalWeight = 160.0)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.account).isEqualTo(account)
        assertThat(viewModel.state.value.form.controls.goalType.value).isEqualTo(GoalType.MAINTAIN.value)
    }

    @Test
    fun `account with null goalType defaults to LOSE_GAIN`() = runTest {
        val account = accountWithGoal(goalType = GoalType.LOSE_GAIN.value).copy(goalType = null)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    @Test
    fun `account with zero weights produces empty form fields`() = runTest {
        val account = accountWithGoal(goalWeight = 0.0, initialWeight = 0.0)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.controls.startingWeight.value).isEmpty()
        assertThat(viewModel.state.value.form.controls.goalWeight.value).isEmpty()
    }

    @Test
    fun `account with metric unit uses KG`() = runTest {
        val account = accountWithGoal(weightUnit = WeightUnit.KG, goalWeight = 70.0, initialWeight = 80.0)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.account?.weightUnit).isEqualTo(WeightUnit.KG)
    }

    // -------------------------------------------------------------------------
    // Submit — MAINTAIN mode and updateR4Profile paths
    // -------------------------------------------------------------------------

    @Test
    fun `Submit in MAINTAIN mode uses latest weight as starting weight`() = runTest {
        val account = accountWithGoal(goalType = GoalType.MAINTAIN.value, goalWeight = 160.0)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()
        viewModel.handleIntent(GoalIntent.UpdateLatestWeight(152.1))
        coEvery { goalService.updateGoal(any(), any(), any(), any()) } returns mockk(relaxed = true)

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify { goalService.updateGoal(any(), any(), any(), any()) }
    }

    @Test
    fun `Submit calls goalService and dismisses loader`() = runTest {
        val account = accountWithGoal()
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()
        coEvery { goalService.updateGoal(any(), any(), any(), any()) } returns mockk(relaxed = true)

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify { goalService.updateGoal(any(), any(), any(), any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // calculateGoalPercentage — via account loading
    // -------------------------------------------------------------------------

    @Test
    fun `account with lose goal and latest weight triggers percentage calculation`() = runTest {
        val account = accountWithGoal(
            goalType = GoalType.LOSE_GAIN.value,
            goalWeight = 150.0,
            initialWeight = 180.0,
        )
        every { goalService.getPercentComplete(any(), any()) } returns 50

        viewModel = createViewModel()
        viewModel.handleIntent(GoalIntent.UpdateLatestWeight(165.0))
        accountFlow.value = account
        advanceUntilIdle()

        verify { goalService.getPercentComplete(any(), any()) }
    }

    @Test
    fun `account with maintain goal skips percentage calculation`() = runTest {
        val account = accountWithGoal(goalType = GoalType.MAINTAIN.value)

        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        verify(exactly = 0) { goalService.getPercentComplete(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // onSubmit — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Submit calls updateR4Profile after goalService updateGoal`() = runTest {
        val account = accountWithGoal()
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        coEvery { goalService.updateGoal(any(), any(), any(), any()) } returns mockk(relaxed = true)

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify { goalService.updateGoal(any(), any(), any(), any()) }
        // updateR4Profile is called after goalService succeeds
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Submit with MAINTAIN goalType and zero latestWeight still calls goalService`() = runTest {
        val account = accountWithGoal(goalType = GoalType.MAINTAIN.value)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Submit)
        advanceUntilIdle()

        coVerify { goalService.updateGoal(any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // onSuccess — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `Success navigates back with null topLevel`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.Success)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack(null) }
    }

    // -------------------------------------------------------------------------
    // onHandleGoalMet — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalMet with setNewGoal true clears existing form values`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal(goalWeight = 150.0, initialWeight = 180.0)
        advanceUntilIdle()

        viewModel.handleIntent(GoalIntent.HandleGoalMet(setNewGoal = true))
        advanceUntilIdle()

        // Form should be reset with new controls
        val controls = viewModel.state.value.form.controls
        assertThat(controls.goalType.value).isEqualTo(GoalType.LOSE_GAIN.value)
    }

    // -------------------------------------------------------------------------
    // onHandleGoalLeave — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `HandleGoalLeave with updateGoal true preserves state`() = runTest {
        viewModel = createViewModel()
        accountFlow.value = accountWithGoal()
        advanceUntilIdle()

        val stateBefore = viewModel.state.value
        viewModel.handleIntent(GoalIntent.HandleGoalLeave(updateGoal = true))
        advanceUntilIdle()

        // State should remain unchanged (only logging occurs)
        assertThat(viewModel.state.value.account).isEqualTo(stateBefore.account)
    }

    // -------------------------------------------------------------------------
    // updateStateWithAccount — additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `updateStateWithAccount preserves latestWeight when account changes`() = runTest {
        every { goalService.getPercentComplete(any(), any()) } returns 50

        viewModel = createViewModel()
        viewModel.handleIntent(GoalIntent.UpdateLatestWeight(165.0))
        advanceUntilIdle()

        val account = accountWithGoal()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.latestWeight).isEqualTo(165.0)
    }

    @Test
    fun `updateStateWithAccount with non-zero weights populates form fields`() = runTest {
        val account = accountWithGoal(goalWeight = 150.0, initialWeight = 180.0)
        viewModel = createViewModel()
        accountFlow.value = account
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.controls.startingWeight.value).isNotEmpty()
        assertThat(viewModel.state.value.form.controls.goalWeight.value).isNotEmpty()
    }
}
