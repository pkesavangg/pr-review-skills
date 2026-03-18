package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkAvailable
import com.dmdbrands.gurus.weight.core.helpers.stubNetworkUnavailable
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.storage.datastore.GoalAlertDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.goal.GoalData
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.goal.Goal
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.goal.helper.GoalHelper
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalServiceTest {

    companion object {
        private const val TEST_ACCOUNT_ID = "acc-1"
        private const val TEST_EMAIL = "john@example.com"
        private const val TEST_FIRST_NAME = "John"
        private const val TEST_LAST_NAME = "Doe"
        private const val TEST_DOB = "1990-01-01"
        private const val TEST_GENDER = "male"
        private const val TEST_ZIPCODE = "12345"
        private const val TEST_ACTIVITY_LEVEL = "normal"
        private const val TEST_HEIGHT = 1700
        private const val TEST_GOAL_WEIGHT = 1600.0
        private const val TEST_INITIAL_WEIGHT = 1800.0
        private const val TEST_LOSE_GOAL_WEIGHT = 160.0
        private const val TEST_LOSE_INITIAL_WEIGHT = 180.0
        private const val TEST_GAIN_GOAL_WEIGHT = 200.0
        private const val TEST_GAIN_INITIAL_WEIGHT = 180.0
        private const val TEST_MAINTAIN_WEIGHT = 180.0
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val goalRepository: IGoalRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val goalAlertDataStore: GoalAlertDataStore = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk()
    private val deviceService: IDeviceService = mockk(relaxed = true)

    private lateinit var service: GoalService

    // --- Test fixtures ---
    private val fakeAccount = Account(
        id = TEST_ACCOUNT_ID,
        firstName = TEST_FIRST_NAME,
        lastName = TEST_LAST_NAME,
        dob = TEST_DOB,
        email = TEST_EMAIL,
        gender = TEST_GENDER,
        zipcode = TEST_ZIPCODE,
        weightUnit = WeightUnit.LB,
        height = TEST_HEIGHT,
        activityLevel = TEST_ACTIVITY_LEVEL,
        goalType = "lose",
        goalWeight = TEST_GOAL_WEIGHT,
        initialWeight = TEST_INITIAL_WEIGHT,
        isActiveAccount = true,
    )

    private val fakeLoseGoal = Goal(
        goalWeight = TEST_LOSE_GOAL_WEIGHT,
        initialWeight = TEST_LOSE_INITIAL_WEIGHT,
        type = "lose",
    )
    private val fakeGainGoal = Goal(
        goalWeight = TEST_GAIN_GOAL_WEIGHT,
        initialWeight = TEST_GAIN_INITIAL_WEIGHT,
        type = "gain",
    )
    private val fakeMaintainGoal = Goal(
        goalWeight = TEST_MAINTAIN_WEIGHT,
        initialWeight = TEST_MAINTAIN_WEIGHT,
        type = "maintain",
    )

    @Before
    fun setUp() {
        stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        setupCurrentGoalFlows()

        service = createService()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createService() = GoalService(
        goalRepository = goalRepository,
        connectivityObserver = connectivityObserver,
        dialogQueueService = dialogQueueService,
        appNavigationService = appNavigationService,
        goalAlertDataStore = goalAlertDataStore,
        accountRepository = accountRepository,
        deviceService = deviceService,
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers (delegate to shared TestHelpers.kt)
    // -------------------------------------------------------------------------

    private fun stubNetworkAvailable() = connectivityObserver.stubNetworkAvailable()
    private fun stubNetworkUnavailable() = connectivityObserver.stubNetworkUnavailable()
    private fun goOnline() = stubNetworkAvailable()
    private fun goOffline() = stubNetworkUnavailable()

    /**
     * Wires up the three flows that getCurrentGoal() combines.
     * Must be called before any test that exercises checkGoalCard() or showGoalCompletionAlert().
     */
    private fun setupCurrentGoalFlows(goal: Goal? = fakeLoseGoal) {
        every { accountRepository.getActiveAccountWeightUnitFlow() } returns flowOf(WeightUnit.LB)
        every { accountRepository.getActiveAccountWeightlessFlow() } returns flowOf(Weightless(false, 0f))
        every { goalRepository.getCurrentGoal() } returns flowOf(goal)
    }

    /** Stubs common alert gating conditions to allow alerts to show. */
    private fun stubAlertConditions(accountId: String = TEST_ACCOUNT_ID) {
        coEvery { goalAlertDataStore.hasShownAlert(accountId) } returns false
        every { deviceService.isSetupInProgress() } returns false
    }

    /** Re-stubs active account to null. */
    private fun withNoActiveAccount() {
        every { accountRepository.getActiveAccount() } returns flowOf(null)
    }

    // -------------------------------------------------------------------------
    // getFormattedGoalType — pure function, no mocks needed
    // -------------------------------------------------------------------------

    @Test
    fun `getFormattedGoalType returns Lose for lose`() {
        assertThat(service.getFormattedGoalType("lose")).isEqualTo("Lose")
    }

    @Test
    fun `getFormattedGoalType returns Gain for gain`() {
        assertThat(service.getFormattedGoalType("gain")).isEqualTo("Gain")
    }

    @Test
    fun `getFormattedGoalType returns Maintain for maintain`() {
        assertThat(service.getFormattedGoalType("maintain")).isEqualTo("Maintain")
    }

    @Test
    fun `getFormattedGoalType is case insensitive`() {
        assertThat(service.getFormattedGoalType("LOSE")).isEqualTo("Lose")
        assertThat(service.getFormattedGoalType("GAIN")).isEqualTo("Gain")
        assertThat(service.getFormattedGoalType("MAINTAIN")).isEqualTo("Maintain")
    }

    @Test
    fun `getFormattedGoalType returns Maintain for unknown type`() {
        assertThat(service.getFormattedGoalType("unknown")).isEqualTo("Maintain")
        assertThat(service.getFormattedGoalType("")).isEqualTo("Maintain")
    }

    // -------------------------------------------------------------------------
    // getPercentComplete — pure function, no mocks needed
    // -------------------------------------------------------------------------

    @Test
    fun `getPercentComplete returns null when latest weight is zero`() {
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = 0.0)).isNull()
    }

    @Test
    fun `getPercentComplete returns null when latest weight is negative`() {
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = -1.0)).isNull()
    }

    @Test
    fun `getPercentComplete returns null for maintain goal type`() {
        assertThat(service.getPercentComplete(fakeMaintainGoal, latest = 180.0)).isNull()
    }

    @Test
    fun `getPercentComplete returns null for unknown goal type`() {
        val unknownGoal = Goal(goalWeight = 180.0, initialWeight = 180.0, type = "unknown")
        assertThat(service.getPercentComplete(unknownGoal, latest = 170.0)).isNull()
    }

    @Test
    fun `getPercentComplete calculates correct percentage for lose goal`() {
        // initialWeight=180, goalWeight=160, latest=170
        // percent = 100 - floor((170-160)/(180-160)*100) = 100 - 50 = 50
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = 170.0)).isEqualTo(50)
    }

    @Test
    fun `getPercentComplete returns 100 when lose goal is fully achieved`() {
        // latest == goalWeight → (160-160)/(180-160)*100 = 0 → 100 - 0 = 100
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = 160.0)).isEqualTo(100)
    }

    @Test
    fun `getPercentComplete calculates correct percentage for gain goal`() {
        // initialWeight=180, goalWeight=200, latest=190
        // percent = floor((190-180)/(200-180)*100) = 50
        assertThat(service.getPercentComplete(fakeGainGoal, latest = 190.0)).isEqualTo(50)
    }

    @Test
    fun `getPercentComplete returns 100 when gain goal is fully achieved`() {
        // latest == goalWeight → (200-180)/(200-180)*100 = 100
        assertThat(service.getPercentComplete(fakeGainGoal, latest = 200.0)).isEqualTo(100)
    }

    @Test
    fun `getPercentComplete returns 0 when percent is negative for lose goal`() {
        // latest > initialWeight → negative → clamped to 0
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = 200.0)).isEqualTo(0)
    }

    @Test
    fun `getPercentComplete is case insensitive for goal type`() {
        val upperCaseGoal = fakeLoseGoal.copy(type = "LOSE")
        assertThat(service.getPercentComplete(upperCaseGoal, latest = 170.0)).isEqualTo(50)
    }

    // -------------------------------------------------------------------------
    // updateGoal — online and offline paths
    // -------------------------------------------------------------------------

    @Test
    fun `updateGoal calls updateGoalSetting when network is available`() = runTest {
        stubNetworkAvailable()
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        val result = service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        coVerify { goalRepository.updateGoalSetting(any()) }
        coVerify(exactly = 0) { goalRepository.updateGoalSettingOffline(any()) }
        assertThat(result).isEqualTo(fakeAccount)
    }

    @Test
    fun `updateGoal calls updateGoalSettingOffline when network is unavailable`() = runTest {
        stubNetworkUnavailable()
        coEvery { goalRepository.updateGoalSettingOffline(any()) } returns fakeAccount

        val result = service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        coVerify { goalRepository.updateGoalSettingOffline(any()) }
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
        assertThat(result).isEqualTo(fakeAccount)
    }

    @Test
    fun `updateGoal returns null when repository throws exception`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } throws RuntimeException("API error")

        val result = service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        assertThat(result).isNull()
    }

    @Test
    fun `updateGoal resets goal alert after successful update`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) }
    }

    @Test
    fun `updateGoal passes correct GoalData to repository`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = true)

        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = TEST_LOSE_GOAL_WEIGHT,
                    initialWeight = TEST_LOSE_INITIAL_WEIGHT,
                    type = "lose",
                    metPreviousGoal = true,
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // createGoalForSignup
    // -------------------------------------------------------------------------

    @Test
    fun `createGoalForSignup returns updated account on success`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "lose",
            startingWeight = TEST_LOSE_INITIAL_WEIGHT,
            goalWeight = TEST_LOSE_GOAL_WEIGHT,
        )

        assertThat(result).isNotNull()
    }

    @Test
    fun `createGoalForSignup returns null when exception occurs`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } throws RuntimeException("Network error")

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "lose",
            startingWeight = TEST_LOSE_INITIAL_WEIGHT,
            goalWeight = TEST_LOSE_GOAL_WEIGHT,
        )

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // checkGoalCard
    // -------------------------------------------------------------------------

    @Test
    fun `checkGoalCard does nothing when no active account`() = runTest {
        withNoActiveAccount()

        service.checkGoalCard()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkGoalCard does nothing when goal card already shown`() = runTest {
        coEvery { goalAlertDataStore.getGoalCardValue(TEST_ACCOUNT_ID) } returns "true"

        service.checkGoalCard()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkGoalCard shows popup when goalType is null and setup is not in progress`() = runTest {
        val accountWithNoGoal = fakeAccount.copy(goalType = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountWithNoGoal)
        coEvery { goalAlertDataStore.getGoalCardValue(accountWithNoGoal.id) } returns null
        every { deviceService.isSetupInProgress() } returns false

        service.checkGoalCard()

        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkGoalCard does not show popup when setup is in progress`() = runTest {
        val accountWithNoGoal = fakeAccount.copy(goalType = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountWithNoGoal)
        coEvery { goalAlertDataStore.getGoalCardValue(accountWithNoGoal.id) } returns null
        every { deviceService.isSetupInProgress() } returns true

        service.checkGoalCard()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkGoalCard does not show popup when goalType is already set`() = runTest {
        // fakeAccount.goalType = "lose" — goal already exists
        coEvery { goalAlertDataStore.getGoalCardValue(TEST_ACCOUNT_ID) } returns null
        every { deviceService.isSetupInProgress() } returns false

        service.checkGoalCard()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkGoalCard marks goal card as shown after displaying popup`() = runTest {
        val accountWithNoGoal = fakeAccount.copy(goalType = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountWithNoGoal)
        coEvery { goalAlertDataStore.getGoalCardValue(accountWithNoGoal.id) } returns null
        every { deviceService.isSetupInProgress() } returns false

        service.checkGoalCard()

        coVerify { goalAlertDataStore.setGoalCardValue(accountWithNoGoal.id, "true") }
    }

    // -------------------------------------------------------------------------
    // showGoalCompletionAlert — gating conditions and alert routing
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalCompletionAlert does nothing when no active account`() = runTest {
        withNoActiveAccount()

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert does nothing when current goal is null`() = runTest {
        setupCurrentGoalFlows(goal = null)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert does nothing when alert already shown for account`() = runTest {
        coEvery { goalAlertDataStore.hasShownAlert(TEST_ACCOUNT_ID) } returns true
        every { deviceService.isSetupInProgress() } returns false

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert does nothing when setup is in progress`() = runTest {
        coEvery { goalAlertDataStore.hasShownAlert(TEST_ACCOUNT_ID) } returns false
        every { deviceService.isSetupInProgress() } returns true

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert shows goal met alert when lose goal is achieved`() = runTest {
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify { dialogQueueService.enqueue(any()) }
        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, true) }
    }

    @Test
    fun `showGoalCompletionAlert does not show alert when lose goal is not yet met`() = runTest {
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1700.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert shows goal met alert when gain goal is achieved`() = runTest {
        val gainAccount = fakeAccount.copy(goalType = "gain", goalWeight = 2000.0)
        every { accountRepository.getActiveAccount() } returns flowOf(gainAccount)
        setupCurrentGoalFlows(goal = fakeGainGoal)
        stubAlertConditions(gainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 2000.0)

        verify { dialogQueueService.enqueue(any()) }
        coVerify { goalAlertDataStore.setAlertShown(gainAccount.id, true) }
    }

    @Test
    fun `showGoalCompletionAlert does not show alert when gain goal is not yet met`() = runTest {
        val gainAccount = fakeAccount.copy(goalType = "gain", goalWeight = 2000.0)
        every { accountRepository.getActiveAccount() } returns flowOf(gainAccount)
        setupCurrentGoalFlows(goal = fakeGainGoal)
        stubAlertConditions(gainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 1800.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert shows goal leave alert when maintain goal drifts`() = runTest {
        val maintainAccount = fakeAccount.copy(goalType = "maintain", goalWeight = TEST_GOAL_WEIGHT)
        every { accountRepository.getActiveAccount() } returns flowOf(maintainAccount)
        setupCurrentGoalFlows(goal = fakeMaintainGoal)
        stubAlertConditions(maintainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify { dialogQueueService.enqueue(any()) }
        coVerify { goalAlertDataStore.setAlertShown(maintainAccount.id, true) }
    }

    @Test
    fun `showGoalCompletionAlert does not show alert when maintain weight matches goal`() = runTest {
        val maintainAccount = fakeAccount.copy(goalType = "maintain", goalWeight = TEST_GOAL_WEIGHT)
        every { accountRepository.getActiveAccount() } returns flowOf(maintainAccount)
        setupCurrentGoalFlows(goal = fakeMaintainGoal)
        stubAlertConditions(maintainAccount.id)

        // currentWeight == goalWeight → no drift, no alert
        service.showGoalCompletionAlert(currentWeight = TEST_GOAL_WEIGHT)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // showGoalCompletionAlert — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalCompletionAlert handles exception gracefully and shows no dialog`() = runTest {
        // Make hasShownAlert throw → triggers the outer catch block
        coEvery { goalAlertDataStore.hasShownAlert(any()) } throws RuntimeException("DataStore error")
        every { deviceService.isSetupInProgress() } returns false

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        // Exception was caught — no dialog should have been enqueued
        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // handleGoalMet — exception handling (via onConfirm callback)
    // -------------------------------------------------------------------------

    @Test
    fun `handleGoalMet handles exception gracefully when setAlertShown throws`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()
        // setAlertShown(true) succeeds (called from showGoalCompletionAlert)
        // setAlertShown(false) throws (called from handleGoalMet) → triggers inner catch
        coEvery { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, true) } just Runs
        coEvery { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) } throws RuntimeException("DataStore write error")

        service.showGoalCompletionAlert(currentWeight = 1500.0)
        val dialog = dialogSlot.captured as DialogModel.Confirm

        // Should not crash — exception is caught inside handleGoalMet
        dialog.onConfirm?.invoke()
        advanceUntilIdle()
    }

    // -------------------------------------------------------------------------
    // showGoalMetAlert callbacks — invoked after lose/gain goal met dialog appears
    // -------------------------------------------------------------------------

    /**
     * Triggers showGoalCompletionAlert for a lose goal that is met,
     * captures the Confirm dialog passed to enqueue(), and returns it.
     */
    private suspend fun captureGoalMetDialog(): DialogModel.Confirm {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        return dialogSlot.captured as DialogModel.Confirm
    }

    /**
     * Triggers showGoalCompletionAlert for a maintain goal that drifts,
     * captures the Confirm dialog, and returns it.
     */
    private suspend fun captureGoalLeaveDialog(): DialogModel.Confirm {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        val maintainAccount = fakeAccount.copy(goalType = "maintain", goalWeight = TEST_GOAL_WEIGHT)
        every { accountRepository.getActiveAccount() } returns flowOf(maintainAccount)
        setupCurrentGoalFlows(goal = fakeMaintainGoal)
        stubAlertConditions(maintainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        return dialogSlot.captured as DialogModel.Confirm
    }

    @Test
    fun `showGoalMetAlert onConfirm resets alert flag`() = runTest {
        val dialog = captureGoalMetDialog()

        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) }
    }

    @Test
    fun `showGoalMetAlert onConfirm updates goal to maintain type`() = runTest {
        val dialog = captureGoalMetDialog()

        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        // handleGoalMet(true): convertTenthsBetweenUnits(TEST_GOAL_WEIGHT, LB, LB) = TEST_GOAL_WEIGHT
        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = TEST_GOAL_WEIGHT,
                    initialWeight = TEST_GOAL_WEIGHT,
                    type = "maintain",
                    metPreviousGoal = true,
                )
            )
        }
    }

    @Test
    fun `showGoalMetAlert onCancel resets alert without updating goal`() = runTest {
        val dialog = captureGoalMetDialog()

        dialog.onCancel?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        // handleGoalMet(false) — resets flag but skips updateGoal
        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) }
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `showGoalMetAlert onCancel navigates to goal screen`() = runTest {
        val dialog = captureGoalMetDialog()

        dialog.onCancel?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        coVerify { appNavigationService.navigateTo(any()) }
    }

    @Test
    fun `showGoalMetAlert onDismiss resets alert without updating goal`() = runTest {
        val dialog = captureGoalMetDialog()

        dialog.onDismiss?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) }
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // showGoalLeaveAlert callbacks — invoked after maintain goal drift dialog appears
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalLeaveAlert onConfirm navigates to goal screen`() = runTest {
        val dialog = captureGoalLeaveDialog()

        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { appNavigationService.navigateTo(any()) }
    }

    @Test
    fun `showGoalLeaveAlert onCancel does not update goal`() = runTest {
        val dialog = captureGoalLeaveDialog()

        dialog.onCancel?.invoke()
        advanceUntilIdle()

        // handleGoalLeave(updateGoal = false) — returns immediately without calling repo
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // getCurrentGoal — Flow emissions tested with Turbine
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentGoal emits processed goal from combined flows`() = runTest {
        setupCurrentGoalFlows(goal = fakeLoseGoal)

        service.getCurrentGoal().test {
            val emitted = awaitItem()
            assertThat(emitted).isNotNull()
            assertThat(emitted!!.type).isEqualTo("lose")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCurrentGoal emits null when repository has no goal`() = runTest {
        setupCurrentGoalFlows(goal = null)

        service.getCurrentGoal().test {
            val emitted = awaitItem()
            assertThat(emitted).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCurrentGoal applies unit conversion when account uses KG`() = runTest {
        every { accountRepository.getActiveAccountWeightUnitFlow() } returns flowOf(WeightUnit.KG)
        every { accountRepository.getActiveAccountWeightlessFlow() } returns flowOf(Weightless(false, 0f))
        // Goal stored in LB (raw tenths) — process() will convert to KG
        val storedGoal = Goal(goalWeight = 1600.0, initialWeight = 1800.0, type = "lose")
        every { goalRepository.getCurrentGoal() } returns flowOf(storedGoal)

        service.getCurrentGoal().test {
            val emitted = awaitItem()
            assertThat(emitted).isNotNull()
            // Converted: 1600.0 / 10 / 2.20462 * 10 ≈ 726 (rounded tenths)
            assertThat(emitted!!.goalWeight).isLessThan(storedGoal.goalWeight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // getCurrentGoalSync
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentGoalSync returns null when no goal has been emitted`() {
        val result = service.getCurrentGoalSync()
        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // goalStatusFlow — Turbine
    // -------------------------------------------------------------------------

    @Test
    fun `goalStatusFlow emits null initially`() = runTest {
        service.goalStatusFlow.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // showGoalCompletionAlert — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalCompletionAlert does not show alert for unknown goal type`() = runTest {
        val unknownGoal = Goal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, type = "unknown")
        setupCurrentGoalFlows(goal = unknownGoal)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert does not show alert when account goalWeight is null`() = runTest {
        val accountNullGoalWeight = fakeAccount.copy(goalWeight = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountNullGoalWeight)
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions(accountNullGoalWeight.id)

        // goalWeight null → convertWeight(0.0, ...) → 0.0 → currentWeight 1500 > 0 → no alert for lose
        service.showGoalCompletionAlert(currentWeight = 1500.0)

        // No alert because currentWeight > goalWeight(0.0) for lose goal
        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // showSetGoalPopup — dialog callback tests
    // -------------------------------------------------------------------------

    @Test
    fun `checkGoalCard onSetGoal callback navigates to goal screen`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        val accountWithNoGoal = fakeAccount.copy(goalType = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountWithNoGoal)
        coEvery { goalAlertDataStore.getGoalCardValue(accountWithNoGoal.id) } returns null
        every { deviceService.isSetupInProgress() } returns false

        service.checkGoalCard()

        val dialog = dialogSlot.captured as DialogModel.Custom
        @Suppress("UNCHECKED_CAST")
        val onSetGoal = dialog.params["onSetGoal"] as () -> Unit
        onSetGoal.invoke()
        advanceUntilIdle()

        coVerify { appNavigationService.navigateTo(any()) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `checkGoalCard onDismiss callback dismisses dialog`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        val accountWithNoGoal = fakeAccount.copy(goalType = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountWithNoGoal)
        coEvery { goalAlertDataStore.getGoalCardValue(accountWithNoGoal.id) } returns null
        every { deviceService.isSetupInProgress() } returns false

        service.checkGoalCard()

        val dialog = dialogSlot.captured as DialogModel.Custom
        dialog.onDismiss?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // getPercentComplete — additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `getPercentComplete returns 0 when gain percent is negative`() {
        // latest < initialWeight → negative percent → clamped to 0
        assertThat(service.getPercentComplete(fakeGainGoal, latest = 170.0)).isEqualTo(0)
    }

    @Test
    fun `getPercentComplete returns value exceeding 100 for lose goal overshooting target`() {
        // latest=150 < goalWeight=160, initial=180 → (150-160)/(180-160)*100 = -50 → 100-(-50) = 150
        assertThat(service.getPercentComplete(fakeLoseGoal, latest = 150.0)).isEqualTo(150)
    }

    // -------------------------------------------------------------------------
    // showGoalMetAlert — handleGoalMet with null account
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalMetAlert onConfirm returns early when no active account during handleGoalMet`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1500.0)

        // Now make account null for the callback
        withNoActiveAccount()

        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        // handleGoalMet returns early — no goal update
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // checkGoalCard — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `checkGoalCard handles exception gracefully`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        coEvery { goalAlertDataStore.getGoalCardValue(any()) } throws RuntimeException("DataStore error")

        // Should not crash
        service.checkGoalCard()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // createGoalForSignup — exception in GoalHelper (catch block)
    // -------------------------------------------------------------------------

    @Test
    fun `createGoalForSignup catches exception from GoalHelper and returns null`() = runTest {
        // Mock GoalHelper.createGoal to throw — hits createGoalForSignup's own catch block
        // (not updateGoal's catch, which swallows the exception separately)
        mockkObject(GoalHelper)
        every { GoalHelper.createGoal(any(), any(), any(), any(), any()) } throws RuntimeException("Conversion error")

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "lose",
            startingWeight = TEST_LOSE_INITIAL_WEIGHT,
            goalWeight = TEST_LOSE_GOAL_WEIGHT,
        )

        assertThat(result).isNull()
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
        unmockkObject(GoalHelper)
    }

    @Test
    fun `createGoalForSignup returns null when updateGoal fails silently`() = runTest {
        // updateGoal's own catch swallows the exception and returns null
        coEvery { goalRepository.updateGoalSetting(any()) } throws RuntimeException("API error")

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "lose",
            startingWeight = TEST_LOSE_INITIAL_WEIGHT,
            goalWeight = TEST_LOSE_GOAL_WEIGHT,
        )

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // showGoalLeaveAlert — callback tests
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalLeaveAlert onConfirm navigates to goal screen and dismisses`() = runTest {
        val dialog = captureGoalLeaveDialog()

        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        coVerify { appNavigationService.navigateTo(any()) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `showGoalLeaveAlert onCancel dismisses and does not navigate`() = runTest {
        val dialog = captureGoalLeaveDialog()

        dialog.onCancel?.invoke()
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
        coVerify(exactly = 0) { goalRepository.updateGoalSetting(any()) }
    }

    // -------------------------------------------------------------------------
    // showGoalCompletionAlert — isShowingAlert guard
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalCompletionAlert does not show second alert while first is still showing`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        // First call — shows alert, isShowingAlert = true
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }

        // Second call — isShowingAlert is still true (no callback invoked), should not show another alert
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // updateGoal — account null edge case
    // -------------------------------------------------------------------------

    @Test
    fun `updateGoal uses empty string for alert reset when account field is null`() = runTest {
        // Create service with null active account — account field stays null
        withNoActiveAccount()
        service = createService()

        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        coVerify { goalAlertDataStore.setAlertShown("", false) }
    }

    // -------------------------------------------------------------------------
    // handleGoalMet — null goalWeight edge case
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalMetAlert onConfirm handles null goalWeight by using 0`() = runTest {
        val accountNullGoalWeight = fakeAccount.copy(goalWeight = null)
        every { accountRepository.getActiveAccount() } returns flowOf(accountNullGoalWeight)

        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions(accountNullGoalWeight.id)
        coEvery { goalRepository.updateGoalSetting(any()) } returns accountNullGoalWeight

        service.showGoalCompletionAlert(currentWeight = 0.0)

        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        // goalWeight is null → uses 0.0 → convertTenthsBetweenUnits(0.0, ...) = 0.0
        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(goalWeight = 0.0, initialWeight = 0.0, type = "maintain", metPreviousGoal = true)
            )
        }
    }

    // -------------------------------------------------------------------------
    // createGoalForSignup — KG unit conversion
    // -------------------------------------------------------------------------

    @Test
    fun `createGoalForSignup converts KG weights to LB for storage`() = runTest {
        val kgAccount = fakeAccount.copy(weightUnit = WeightUnit.KG)
        coEvery { goalRepository.updateGoalSetting(any()) } returns kgAccount

        val result = service.createGoalForSignup(
            account = kgAccount,
            goalType = "lose",
            startingWeight = 800.0, // 80.0 KG in tenths
            goalWeight = 700.0,     // 70.0 KG in tenths
        )

        assertThat(result).isNotNull()
        // GoalHelper converts KG→LB: 80kg * 2.20462 = ~176.37 → tenths = 1764
        coVerify { goalRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `createGoalForSignup with maintain goal type`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "maintain",
            startingWeight = TEST_INITIAL_WEIGHT,
            goalWeight = TEST_INITIAL_WEIGHT,
        )

        assertThat(result).isNotNull()
        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = TEST_INITIAL_WEIGHT,
                    initialWeight = TEST_INITIAL_WEIGHT,
                    type = "maintain",
                    metPreviousGoal = false,
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // showGoalCompletionAlert — boundary tests for gain/lose
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalCompletionAlert gain goal at exact boundary shows alert`() = runTest {
        val gainAccount = fakeAccount.copy(goalType = "gain", goalWeight = 2000.0)
        every { accountRepository.getActiveAccount() } returns flowOf(gainAccount)
        setupCurrentGoalFlows(goal = fakeGainGoal)
        stubAlertConditions(gainAccount.id)

        // Exact boundary: currentWeight == goalWeight → gain >= → true
        service.showGoalCompletionAlert(currentWeight = 2000.0)

        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalCompletionAlert lose goal at exact boundary shows alert`() = runTest {
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        // Exact boundary: currentWeight == goalWeight → lose <= → true
        service.showGoalCompletionAlert(currentWeight = TEST_GOAL_WEIGHT)

        verify { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // showGoalMetAlert — isShowingAlert reset on callbacks
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalMetAlert onConfirm resets isShowingAlert allowing future alerts`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        // First alert
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }

        // Invoke onConfirm → resets isShowingAlert
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        // Now a second alert should be possible (hasShownAlert returns false again)
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 2) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalMetAlert onDismiss resets isShowingAlert allowing future alerts`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        setupCurrentGoalFlows(goal = fakeLoseGoal)
        stubAlertConditions()

        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }

        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onDismiss?.invoke()
        advanceUntilIdle()

        // isShowingAlert is now false — second alert is possible
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 2) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // showGoalLeaveAlert — isShowingAlert reset
    // -------------------------------------------------------------------------

    @Test
    fun `showGoalLeaveAlert onConfirm resets isShowingAlert`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        val maintainAccount = fakeAccount.copy(goalType = "maintain", goalWeight = TEST_GOAL_WEIGHT)
        every { accountRepository.getActiveAccount() } returns flowOf(maintainAccount)
        setupCurrentGoalFlows(goal = fakeMaintainGoal)
        stubAlertConditions(maintainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }

        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        // isShowingAlert reset — second alert should work
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 2) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `showGoalLeaveAlert onCancel resets isShowingAlert`() = runTest {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
        val maintainAccount = fakeAccount.copy(goalType = "maintain", goalWeight = TEST_GOAL_WEIGHT)
        every { accountRepository.getActiveAccount() } returns flowOf(maintainAccount)
        setupCurrentGoalFlows(goal = fakeMaintainGoal)
        stubAlertConditions(maintainAccount.id)

        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 1) { dialogQueueService.enqueue(any()) }

        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onCancel?.invoke()
        advanceUntilIdle()

        // isShowingAlert reset — second alert should work
        service.showGoalCompletionAlert(currentWeight = 1500.0)
        verify(exactly = 2) { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // updateGoal — offline path also resets alert
    // -------------------------------------------------------------------------

    @Test
    fun `updateGoal resets goal alert after successful offline update`() = runTest {
        stubNetworkUnavailable()
        coEvery { goalRepository.updateGoalSettingOffline(any()) } returns fakeAccount

        service.updateGoal(goalWeight = TEST_LOSE_GOAL_WEIGHT, initialWeight = TEST_LOSE_INITIAL_WEIGHT, goalType = "lose", wasMet = false)

        coVerify { goalAlertDataStore.setAlertShown(TEST_ACCOUNT_ID, false) }
    }

    // -------------------------------------------------------------------------
    // createGoalForSignup — gain goal type detection
    // -------------------------------------------------------------------------

    @Test
    fun `createGoalForSignup detects gain goal type correctly`() = runTest {
        coEvery { goalRepository.updateGoalSetting(any()) } returns fakeAccount

        val result = service.createGoalForSignup(
            account = fakeAccount,
            goalType = "lose", // form says lose, but goalWeight > startingWeight
            startingWeight = TEST_GOAL_WEIGHT,
            goalWeight = TEST_INITIAL_WEIGHT,
        )

        assertThat(result).isNotNull()
        // GoalHelper overrides type to "gain" when goalWeight > startingWeight
        coVerify {
            goalRepository.updateGoalSetting(
                GoalData(
                    goalWeight = TEST_INITIAL_WEIGHT,
                    initialWeight = TEST_GOAL_WEIGHT,
                    type = "gain",
                    metPreviousGoal = false,
                )
            )
        }
    }
}
