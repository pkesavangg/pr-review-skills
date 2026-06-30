package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IBodyCompositionService
import com.dmdbrands.gurus.weight.features.common.components.RadioGroupSection
import com.dmdbrands.gurus.weight.features.common.components.SectionedRadioGroupModalConfig
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Covers UnitSettingsManager.handleSave — the per-product branching that the
 * sectioned Unit Type dialog commits: adult-only, baby-only, both, and
 * no-change. handleSave is private, so each test drives it the way the UI does:
 * onUnitTypeClick enqueues a sectioned dialog, and we invoke the captured
 * onConfirm callback with the section selections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnitSettingsManagerTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  private val bodyCompositionService: IBodyCompositionService = mockk(relaxed = true)
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val scaleSettingsManager: IScaleSettingsManager = mockk(relaxed = true)
  private val userDataStore: UserDataStore = mockk(relaxed = true)
  private val accountService: IAccountService = mockk(relaxed = true)

  private val manager = UnitSettingsManager(
    bodyCompositionService = bodyCompositionService,
    dialogQueueService = dialogQueueService,
    scaleSettingsManager = scaleSettingsManager,
    userDataStore = userDataStore,
    accountService = accountService,
  )

  private val account = TestFixtures.anAccount(weightUnit = WeightUnit.LB)

  /** Opens the dialog and returns its captured onConfirm callback. */
  @Suppress("UNCHECKED_CAST")
  private fun kotlinx.coroutines.test.TestScope.openDialog(
    state: SettingsState,
  ): (Map<String, String?>) -> Unit {
    val dialogSlot = slot<DialogModel>()
    manager.onUnitTypeClick(scope = this, stateProvider = { state })
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val custom = dialogSlot.captured as DialogModel.Custom
    return custom.params["onConfirm"] as (Map<String, String?>) -> Unit
  }

  private fun stateWithBaby(
    adultUnit: WeightUnit = WeightUnit.LB,
    babyUnit: WeightUnit = WeightUnit.LB_OZ,
  ) = SettingsState(
    account = account.copy(weightUnit = adultUnit),
    isBabyProduct = true,
    babyWeightUnit = babyUnit,
  )

  @Test
  fun `baby-only change persists baby unit without loader or body comp update`() = runTest(mainDispatcherRule.scheduler) {
    val onConfirm = openDialog(stateWithBaby())

    // Adult unchanged (lb -> lb), baby changes (lb_oz -> kg).
    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.LB.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.KG.value,
      ),
    )
    advanceUntilIdle()

    coVerify(exactly = 1) { userDataStore.setBabyWeightUnit(account.id, WeightUnit.KG) }
    // Baby unit must reach the server via PATCH /v3/account/measurement-units (spec §2.1).
    // KG -> metric.
    coVerify(exactly = 1) { accountService.updateMeasurementUnits(MeasurementUnits.METRIC) }
    // Baby change hits the network now, so the loader is shown and dismissed.
    verify(exactly = 1) { dialogQueueService.showLoader(any()) }
    verify(exactly = 1) { dialogQueueService.dismissLoader() }
    // Adult body-comp must NOT be touched for a baby-only change.
    coVerify(exactly = 0) { bodyCompositionService.updateBodyComposition(any(), any(), any()) }
  }

  @Test
  fun `baby change does not persist locally when the measurement-units sync fails`() = runTest(mainDispatcherRule.scheduler) {
    // The baby unit is synced server-first: updateMeasurementUnits runs BEFORE the
    // device-local setBabyWeightUnit, and throws when offline / on HTTP failure. When
    // it throws, the local write must be skipped so the device never gets ahead of the
    // server (the local/server divergence PR #2109 set out to fix).
    coEvery { accountService.updateMeasurementUnits(any()) } throws RuntimeException("sync failed")
    val onConfirm = openDialog(stateWithBaby())

    // Adult unchanged (lb -> lb), baby changes (lb_oz -> kg).
    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.LB.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.KG.value,
      ),
    )
    advanceUntilIdle()

    // Server PATCH was attempted but failed...
    coVerify(exactly = 1) { accountService.updateMeasurementUnits(MeasurementUnits.METRIC) }
    // ...so the device-local unit must NOT be written.
    coVerify(exactly = 0) { userDataStore.setBabyWeightUnit(any(), any()) }
    // User is told via the generic error toast, and the loader is dismissed.
    verify(exactly = 1) { dialogQueueService.showToast(any()) }
    verify(exactly = 1) { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `adult-only change updates body comp and shows then dismisses loader`() = runTest(mainDispatcherRule.scheduler) {
    val onConfirm = openDialog(stateWithBaby())

    // Adult changes (lb -> kg), baby unchanged (lb_oz -> lb_oz).
    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.KG.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.LB_OZ.value,
      ),
    )
    advanceUntilIdle()

    verify(exactly = 1) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 1) {
      bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, any(), any())
    }
    verify(exactly = 1) { dialogQueueService.dismissLoader() }
    coVerify(exactly = 0) { userDataStore.setBabyWeightUnit(any(), any()) }
    // Adult-only change must NOT touch the account measurement system.
    coVerify(exactly = 0) { accountService.updateMeasurementUnits(any()) }
  }

  @Test
  fun `changing both persists adult and baby units`() = runTest(mainDispatcherRule.scheduler) {
    val onConfirm = openDialog(stateWithBaby())

    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.KG.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.KG.value,
      ),
    )
    advanceUntilIdle()

    coVerify(exactly = 1) {
      bodyCompositionService.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, any(), any())
    }
    coVerify(exactly = 1) { userDataStore.setBabyWeightUnit(account.id, WeightUnit.KG) }
    // Baby half of the change also syncs the measurement system (KG -> metric).
    coVerify(exactly = 1) { accountService.updateMeasurementUnits(MeasurementUnits.METRIC) }
  }

  @Test
  fun `baby lbs and oz selection maps to imperialLbOz measurement units`() = runTest {
    // Start metric, switch baby to lbs & oz — the baby-specific format that
    // a plain lb_oz weight unit must map to imperialLbOz on the server.
    val onConfirm = openDialog(stateWithBaby(babyUnit = WeightUnit.KG))

    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.LB.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.LB_OZ.value,
      ),
    )
    advanceUntilIdle()

    coVerify(exactly = 1) { accountService.updateMeasurementUnits(MeasurementUnits.IMPERIAL_LB_OZ) }
  }

  @Test
  fun `identical selections persist nothing`() = runTest(mainDispatcherRule.scheduler) {
    val onConfirm = openDialog(stateWithBaby())

    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.LB.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.LB_OZ.value,
      ),
    )
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { bodyCompositionService.updateBodyComposition(any(), any(), any()) }
    coVerify(exactly = 0) { userDataStore.setBabyWeightUnit(any(), any()) }
    coVerify(exactly = 0) { accountService.updateMeasurementUnits(any()) }
  }

  @Test
  fun `legacy lb_oz adult unit falls back to LB selection in My Weight section`() = runTest(mainDispatcherRule.scheduler) {
    val dialogSlot = slot<DialogModel>()
    manager.onUnitTypeClick(
      scope = this,
      stateProvider = { stateWithBaby(adultUnit = WeightUnit.LB_OZ) },
    )
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }

    val config = (dialogSlot.captured as DialogModel.Custom)
      .params["config"] as SectionedRadioGroupModalConfig<*>
    val myWeight = config.sections.first { it.key == UnitSettingsManager.SECTION_MY_WEIGHT }

    // lb_oz is no longer a My Weight option, so the section falls back to LB
    // rather than rendering with nothing selected.
    assertThat(myWeight.selectedItem).isEqualTo(WeightUnit.LB.value)
  }

  @Test
  fun `non-baby product shows only the My Weight section`() = runTest(mainDispatcherRule.scheduler) {
    val dialogSlot = slot<DialogModel>()
    manager.onUnitTypeClick(
      scope = this,
      stateProvider = { SettingsState(account = account, isBabyProduct = false) },
    )
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }

    val config = (dialogSlot.captured as DialogModel.Custom)
      .params["config"] as SectionedRadioGroupModalConfig<*>
    @Suppress("UNCHECKED_CAST")
    val keys = (config.sections as List<RadioGroupSection<String>>).map { it.key }

    assertThat(keys).containsExactly(UnitSettingsManager.SECTION_MY_WEIGHT)
  }

  @Test
  fun `observeBabyWeightUnit dispatches the persisted baby unit`() = runTest {
    every { userDataStore.babyWeightUnitForCurrentAccountFlow } returns flowOf(WeightUnit.KG)
    val dispatch = mockk<(SettingsIntent) -> Unit>(relaxed = true)

    manager.observeBabyWeightUnit(scope = this, dispatchIntent = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.SetBabyWeightUnit(WeightUnit.KG)) }
  }

  @Test
  fun `handleSave with no active account persists nothing`() = runTest {
    val onConfirm = openDialog(SettingsState(account = null, isBabyProduct = true))

    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.KG.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.KG.value,
      ),
    )
    advanceUntilIdle()

    verify(exactly = 0) { dialogQueueService.showLoader(any()) }
    coVerify(exactly = 0) { userDataStore.setBabyWeightUnit(any(), any()) }
  }

  @Test
  fun `handleSave swallows persistence exception and still dismisses loader`() = runTest {
    coEvery { bodyCompositionService.updateBodyComposition(any(), any(), any()) } throws
      RuntimeException("update failed")
    val onConfirm = openDialog(stateWithBaby())

    // Adult changes (lb -> kg) -> loader shown; the failing body-comp update must still
    // dismiss the loader in the finally block.
    onConfirm(
      mapOf(
        UnitSettingsManager.SECTION_MY_WEIGHT to WeightUnit.KG.value,
        UnitSettingsManager.SECTION_MY_KIDS to WeightUnit.LB_OZ.value,
      ),
    )
    advanceUntilIdle()

    verify(exactly = 1) { dialogQueueService.dismissLoader() }
  }
}
