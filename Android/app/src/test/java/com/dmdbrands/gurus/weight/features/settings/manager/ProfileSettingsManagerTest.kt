package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.domain.services.IUserSettingsService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Covers ProfileSettingsManager: the public profile-settings actions plus the
 * private branches reached through the dialog callbacks it enqueues
 * (activity-level update via the radio-group modal, and the account-switch
 * info modal's onAddAccount/onDismiss lambdas).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSettingsManagerTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  private val accountService: IAccountService = mockk(relaxed = true)
  private val bodyCompositionService: IBodyCompositionService = mockk(relaxed = true)
  private val userDataStore: UserDataStore = mockk(relaxed = true)
  private val userSettingsService: IUserSettingsService = mockk(relaxed = true)
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val navigationService: IAppNavigationService = mockk(relaxed = true)
  private val scaleSettingsManager: IScaleSettingsManager = mockk(relaxed = true)

  private val manager = ProfileSettingsManager(
    accountService = accountService,
    bodyCompositionService = bodyCompositionService,
    userDataStore = userDataStore,
    userSettingsService = userSettingsService,
    dialogQueueService = dialogQueueService,
    navigationService = navigationService,
    scaleSettingsManager = scaleSettingsManager,
  )

  private val account = TestFixtures.anAccount(weightUnit = WeightUnit.LB)

  /** Opens the activity-level radio dialog and returns its captured onConfirm callback. */
  @Suppress("UNCHECKED_CAST")
  private fun kotlinx.coroutines.test.TestScope.openActivityDialog(
    state: SettingsState,
  ): (String?) -> Unit {
    val dialogSlot = slot<DialogModel>()
    manager.onActivityLevelClick(scope = this, stateProvider = { state })
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val custom = dialogSlot.captured as DialogModel.Custom
    return custom.params["onConfirm"] as (String?) -> Unit
  }

  // ---------------------------------------------------------------------------
  // observeUserProfile
  // ---------------------------------------------------------------------------

  @Test
  fun `observeUserProfile dispatches UpdateAccount with hasMultipleAccounts true for two accounts`() = runTest {
    every { accountService.loggedInAccountsFlow } returns flowOf(listOf(account, account))
    coEvery { accountService.getCurrentAccount() } returns account
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeUserProfile(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.UpdateAccount(account, true)) }
  }

  @Test
  fun `observeUserProfile dispatches UpdateAccount with hasMultipleAccounts false for single account`() = runTest {
    every { accountService.loggedInAccountsFlow } returns flowOf(listOf(account))
    coEvery { accountService.getCurrentAccount() } returns account
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeUserProfile(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.UpdateAccount(account, false)) }
  }

  // ---------------------------------------------------------------------------
  // onGoalSettingClick / onWeightlessClick
  // ---------------------------------------------------------------------------

  @Test
  fun `onGoalSettingClick navigates to Goal`() = runTest {
    manager.onGoalSettingClick(scope = this)
    advanceUntilIdle()

    coVerify { navigationService.navigateTo(AppRoute.AccountSettings.Goal) }
  }

  @Test
  fun `onWeightlessClick navigates to Weightless`() = runTest {
    manager.onWeightlessClick(scope = this)
    advanceUntilIdle()

    coVerify { navigationService.navigateTo(AppRoute.AccountSettings.Weightless) }
  }

  // ---------------------------------------------------------------------------
  // onStreakUpdate
  // ---------------------------------------------------------------------------

  @Test
  fun `onStreakUpdate no-ops when streak already matches`() = runTest {
    val state = SettingsState(account = account.copy(isStreakOn = true))

    manager.onStreakUpdate(scope = this, stateProvider = { state }, isStreakOn = true)
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { userSettingsService.toggleStreakSetting(any()) }
  }

  @Test
  fun `onStreakUpdate toggles setting and shows then dismisses loader when changed`() = runTest {
    val state = SettingsState(account = account.copy(isStreakOn = false))

    manager.onStreakUpdate(scope = this, stateProvider = { state }, isStreakOn = true)
    advanceUntilIdle()

    verify(exactly = 1) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 1) { userSettingsService.toggleStreakSetting(isStreakOn = true) }
    verify(exactly = 1) { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `onStreakUpdate dismisses loader even when toggle throws`() = runTest {
    val state = SettingsState(account = account.copy(isStreakOn = false))
    coEvery { userSettingsService.toggleStreakSetting(any()) } throws RuntimeException("boom")

    manager.onStreakUpdate(scope = this, stateProvider = { state }, isStreakOn = true)
    advanceUntilIdle()

    verify(exactly = 1) { dialogQueueService.dismissLoader() }
  }

  // ---------------------------------------------------------------------------
  // onActivityLevelClick -> showRadioGroupModal -> onActivityLevelUpdate
  // ---------------------------------------------------------------------------

  @Test
  fun `activity level confirm with different level updates profile and body comp`() = runTest {
    val state = SettingsState(account = account.copy(activityLevel = "normal"))
    val onConfirm = openActivityDialog(state)

    onConfirm("athlete")
    advanceUntilIdle()

    verify(exactly = 1) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 1) { scaleSettingsManager.updateR4Profile(any()) }
    coVerify(exactly = 1) {
      bodyCompositionService.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, any(), any())
    }
    verify(exactly = 1) { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `activity level confirm with same level does nothing`() = runTest {
    val state = SettingsState(account = account.copy(activityLevel = "athlete"))
    val onConfirm = openActivityDialog(state)

    onConfirm("athlete")
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { scaleSettingsManager.updateR4Profile(any()) }
    coVerify(exactly = 0) { bodyCompositionService.updateBodyComposition(any(), any(), any()) }
  }

  @Test
  fun `activity level confirm with no active account does nothing`() = runTest {
    val onConfirm = openActivityDialog(SettingsState(account = null))

    onConfirm("athlete")
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { scaleSettingsManager.updateR4Profile(any()) }
  }

  @Test
  fun `activity level confirm with null selection is a no-op`() = runTest {
    val state = SettingsState(account = account.copy(activityLevel = "normal"))
    val onConfirm = openActivityDialog(state)

    onConfirm(null)
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { scaleSettingsManager.updateR4Profile(any()) }
  }

  // ---------------------------------------------------------------------------
  // showAccountSwitchInfoModal / dismissAccountSwitchInfoModal
  // ---------------------------------------------------------------------------

  @Test
  fun `showAccountSwitchInfoModal returns early when already shown`() = runTest {
    coEvery { userDataStore.hasShownAccountSwitchInfoModalForDevice() } returns true

    manager.showAccountSwitchInfoModal(scope = this)
    advanceUntilIdle()

    coVerify(exactly = 0) { dialogQueueService.enqueue(any()) }
    coVerify(exactly = 0) { userDataStore.setAccountSwitchInfoModalShownForDevice(any()) }
  }

  @Test
  fun `showAccountSwitchInfoModal enqueues modal and marks shown when not shown`() = runTest {
    coEvery { userDataStore.hasShownAccountSwitchInfoModalForDevice() } returns false
    coEvery { accountService.getCurrentAccount() } returns account

    manager.showAccountSwitchInfoModal(scope = this)
    advanceUntilIdle()

    coVerify(exactly = 1) { userDataStore.setAccountSwitchInfoModalShownForDevice(true) }
    verify(exactly = 1) { dialogQueueService.enqueue(any()) }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `account switch modal onAddAccount dismisses and navigates, onDismiss dismisses`() = runTest {
    coEvery { userDataStore.hasShownAccountSwitchInfoModalForDevice() } returns false
    coEvery { accountService.getCurrentAccount() } returns account
    val dialogSlot = slot<DialogModel>()

    manager.showAccountSwitchInfoModal(scope = this)
    advanceUntilIdle()
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }

    val custom = dialogSlot.captured as DialogModel.Custom
    val onAddAccount = custom.params["onAddAccount"] as () -> Unit
    onAddAccount()
    advanceUntilIdle()
    custom.onDismiss?.invoke()

    coVerify { navigationService.navigateTo(AppRoute.AccountSettings.MyAccounts) }
    verify(atLeast = 1) { dialogQueueService.dismissCurrent() }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun `account switch modal falls back to U initial when first name blank`() = runTest {
    coEvery { userDataStore.hasShownAccountSwitchInfoModalForDevice() } returns false
    coEvery { accountService.getCurrentAccount() } returns account.copy(firstName = "")
    val dialogSlot = slot<DialogModel>()

    manager.showAccountSwitchInfoModal(scope = this)
    advanceUntilIdle()
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }

    val custom = dialogSlot.captured as DialogModel.Custom
    assertThat(custom.params["userInitial"]).isEqualTo("U")
  }

  @Test
  fun `dismissAccountSwitchInfoModal dismisses current dialog`() {
    manager.dismissAccountSwitchInfoModal()

    verify { dialogQueueService.dismissCurrent() }
  }

  // ---------------------------------------------------------------------------
  // getWeightlessDisplayText
  // ---------------------------------------------------------------------------

  @Test
  fun `getWeightlessDisplayText returns On with value when weightless on with weight`() {
    val state = SettingsState(
      account = account.copy(isWeightlessOn = true, weightlessWeight = 750f),
    )

    val result = manager.getWeightlessDisplayText(state)

    assertThat(result).startsWith("On")
  }

  @Test
  fun `getWeightlessDisplayText returns On when weightless on without weight`() {
    val state = SettingsState(
      account = account.copy(isWeightlessOn = true, weightlessWeight = null),
    )

    val result = manager.getWeightlessDisplayText(state)

    assertThat(result).isEqualTo("On")
  }

  @Test
  fun `getWeightlessDisplayText returns Off when weightless off`() {
    val state = SettingsState(
      account = account.copy(isWeightlessOn = false),
    )

    val result = manager.getWeightlessDisplayText(state)

    assertThat(result).isEqualTo("Off")
  }
}
