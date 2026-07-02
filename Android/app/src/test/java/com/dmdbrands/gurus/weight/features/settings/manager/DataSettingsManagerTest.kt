package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.proto.ThemeMode
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import retrofit2.HttpException
import retrofit2.Response

/**
 * Covers DataSettingsManager — export observation, theme loading, and the
 * dialog-driven flows (export, delete account, logout / logout-all, appearance).
 * Private branches (performExport, logout, onLogoutAllAccounts, onAppearanceUpdate)
 * are driven the way the UI does: by capturing the enqueued DialogModel and
 * invoking its callbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataSettingsManagerTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  private val entryReadService: IEntryReadService = mockk(relaxed = true)
  private val accountService: IAccountService = mockk(relaxed = true)
  private val exportService: IExportService = mockk(relaxed = true)
  private val healthConnectService: IHealthConnectService = mockk(relaxed = true)
  private val userDataStore: UserDataStore = mockk(relaxed = true)
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val navigationService: IAppNavigationService = mockk(relaxed = true)

  private val manager = DataSettingsManager(
    entryReadService = entryReadService,
    accountService = accountService,
    exportService = exportService,
    healthConnectService = healthConnectService,
    userDataStore = userDataStore,
    dialogQueueService = dialogQueueService,
    navigationService = navigationService,
  )

  private val account = TestFixtures.anAccount(
    id = "acct-1",
    isActiveAccount = true,
  ).copy(fcmToken = "tok")

  private fun httpException(): HttpException {
    val body = "".toResponseBody("application/json".toMediaTypeOrNull())
    return HttpException(Response.error<Any>(500, body))
  }

  /** Captures the most recently enqueued Confirm dialog. */
  private fun captureConfirm(): DialogModel.Confirm {
    val slot = slot<DialogModel>()
    verify { dialogQueueService.enqueue(capture(slot)) }
    return slot.captured as DialogModel.Confirm
  }

  /** Captures the most recently enqueued Custom dialog's onConfirm callback. */
  @Suppress("UNCHECKED_CAST")
  private fun captureCustomOnConfirm(): (String?) -> Unit {
    val slot = slot<DialogModel>()
    verify { dialogQueueService.enqueue(capture(slot)) }
    val custom = slot.captured as DialogModel.Custom
    return custom.params["onConfirm"] as (String?) -> Unit
  }

  // ── observeExportEnabled ──────────────────────────────────────────────────

  @Test
  fun `observeExportEnabled with non-null latest entry sets export enabled`() = runTest {
    every { entryReadService.latestEntry() } returns flowOf(TestFixtures.weightEntry)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeExportEnabled(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetExportEnabled(true)) }
  }

  @Test
  fun `observeExportEnabled with null latest entry sets export disabled`() = runTest {
    every { entryReadService.latestEntry() } returns flowOf(null)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeExportEnabled(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetExportEnabled(false)) }
  }

  @Test
  fun `observeExportEnabled catches flow error and sets export disabled`() = runTest {
    every { entryReadService.latestEntry() } returns flow { throw RuntimeException("boom") }
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeExportEnabled(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetExportEnabled(false)) }
  }

  // ── loadCurrentThemeMode ──────────────────────────────────────────────────

  @Test
  fun `loadCurrentThemeMode maps LIGHT to light display string`() = runTest {
    every { userDataStore.currentThemeModeFlow } returns flowOf(ThemeMode.LIGHT)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.loadCurrentThemeMode(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify {
      dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.Light))
    }
  }

  @Test
  fun `loadCurrentThemeMode maps DARK to dark display string`() = runTest {
    every { userDataStore.currentThemeModeFlow } returns flowOf(ThemeMode.DARK)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.loadCurrentThemeMode(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify {
      dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.Dark))
    }
  }

  @Test
  fun `loadCurrentThemeMode maps SYSTEM to system display string`() = runTest {
    every { userDataStore.currentThemeModeFlow } returns flowOf(ThemeMode.SYSTEM)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.loadCurrentThemeMode(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify {
      dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.System))
    }
  }

  // ── onExportDataClick / performExport ─────────────────────────────────────

  @Test
  fun `onExportDataClick confirm runs export with loader then dismisses`() = runTest {
    manager.onExportDataClick(scope = this)
    val confirm = captureConfirm()

    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify { exportService.exportCsvWithPrompt() }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `onExportDataClick cancel dismisses current dialog`() = runTest {
    manager.onExportDataClick(scope = this)
    val confirm = captureConfirm()

    confirm.onCancel?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `performExport dismisses loader when export throws HttpException`() = runTest {
    coEvery { exportService.exportCsvWithPrompt() } throws httpException()

    manager.onExportDataClick(scope = this)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
  }

  // ── onConfirmDeleteAccount ────────────────────────────────────────────────

  @Test
  fun `onConfirmDeleteAccount confirm dispatches DeleteAccount`() = runTest {
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onConfirmDeleteAccount(dispatch = dispatch)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()

    verify { dispatch(SettingsIntent.DeleteAccount) }
  }

  // ── onDeleteAccount ───────────────────────────────────────────────────────

  @Test
  fun `onDeleteAccount with active account deletes and emits auth event`() = runTest {
    val state = SettingsState(account = account)

    manager.onDeleteAccount(scope = this, stateProvider = { state })
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify { accountService.deleteAccount(account.id, true) }
    coVerify { healthConnectService.clearHealthConnect() }
    coVerify { navigationService.emitAuthEvent(AuthState.AccountDeleted(true)) }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `onDeleteAccount with null account skips delete but still dismisses and clears`() = runTest {
    val state = SettingsState(account = null)

    manager.onDeleteAccount(scope = this, stateProvider = { state })
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { accountService.deleteAccount(any(), any()) }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `onDeleteAccount dismisses and clears when delete throws`() = runTest {
    coEvery { accountService.deleteAccount(any(), any()) } throws RuntimeException("fail")
    val state = SettingsState(account = account)

    manager.onDeleteAccount(scope = this, stateProvider = { state })
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  // ── onLogOutClick / logout / onLogoutAllAccounts ──────────────────────────

  @Test
  fun `onLogOutClick single logout logs out account and reinitializes`() = runTest {
    val state = SettingsState(account = account)

    manager.onLogOutClick(scope = this, stateProvider = { state }, isLogoutAll = false)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify { accountService.logout(account.id, account.fcmToken) }
    coVerify { navigationService.reInitialize() }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `onLogOutClick logout-all logs out all accounts`() = runTest {
    val state = SettingsState(account = account)

    manager.onLogOutClick(scope = this, stateProvider = { state }, isLogoutAll = true)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify { accountService.logoutAll() }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `logout with null account skips logout but still dismisses and clears`() = runTest {
    val state = SettingsState(account = null)

    manager.onLogOutClick(scope = this, stateProvider = { state }, isLogoutAll = false)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    coVerify(exactly = 0) { accountService.logout(any(), any()) }
    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `logout dismisses and clears when logout throws`() = runTest {
    coEvery { accountService.logout(any(), any()) } throws RuntimeException("fail")
    val state = SettingsState(account = account)

    manager.onLogOutClick(scope = this, stateProvider = { state }, isLogoutAll = false)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  @Test
  fun `onLogoutAllAccounts dismisses and clears when logoutAll throws`() = runTest {
    coEvery { accountService.logoutAll() } throws RuntimeException("fail")
    val state = SettingsState(account = account)

    manager.onLogOutClick(scope = this, stateProvider = { state }, isLogoutAll = true)
    val confirm = captureConfirm()
    confirm.onConfirm?.invoke()
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.clear() }
  }

  // ── onAppearanceClick / onAppearanceUpdate ────────────────────────────────

  @Test
  fun `onAppearanceClick light selection persists LIGHT theme`() = runTest {
    val state = SettingsState(
      account = account,
      currentThemeMode = RadioGroupModalStrings.Appearance.Light,
    )
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onAppearanceClick(scope = this, stateProvider = { state }, dispatch = dispatch)
    val onConfirm = captureCustomOnConfirm()
    onConfirm("LIGHT")
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify { userDataStore.setThemeMode(account.id, ThemeMode.LIGHT) }
    verify { dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.Light)) }
    verify { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `onAppearanceClick dark selection persists DARK theme`() = runTest {
    val state = SettingsState(
      account = account,
      currentThemeMode = RadioGroupModalStrings.Appearance.Dark,
    )
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onAppearanceClick(scope = this, stateProvider = { state }, dispatch = dispatch)
    val onConfirm = captureCustomOnConfirm()
    onConfirm("DARK")
    advanceUntilIdle()

    coVerify { userDataStore.setThemeMode(account.id, ThemeMode.DARK) }
    verify { dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.Dark)) }
  }

  @Test
  fun `onAppearanceClick system selection persists SYSTEM theme`() = runTest {
    val state = SettingsState(
      account = account,
      currentThemeMode = RadioGroupModalStrings.Appearance.System,
    )
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onAppearanceClick(scope = this, stateProvider = { state }, dispatch = dispatch)
    val onConfirm = captureCustomOnConfirm()
    onConfirm("SYSTEM")
    advanceUntilIdle()

    coVerify { userDataStore.setThemeMode(account.id, ThemeMode.SYSTEM) }
    verify { dispatch(SettingsIntent.UpdateThemeMode(RadioGroupModalStrings.Appearance.System)) }
  }

  @Test
  fun `onAppearanceClick with null account does not persist theme`() = runTest {
    val state = SettingsState(account = null)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onAppearanceClick(scope = this, stateProvider = { state }, dispatch = dispatch)
    val onConfirm = captureCustomOnConfirm()
    onConfirm("LIGHT")
    advanceUntilIdle()

    coVerify(exactly = 0) { userDataStore.setThemeMode(any(), any()) }
  }

  @Test
  fun `onAppearanceClick dismisses loader when setThemeMode throws`() = runTest {
    coEvery { userDataStore.setThemeMode(any(), any()) } throws RuntimeException("fail")
    val state = SettingsState(account = account)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.onAppearanceClick(scope = this, stateProvider = { state }, dispatch = dispatch)
    val onConfirm = captureCustomOnConfirm()
    onConfirm("LIGHT")
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
  }
}
