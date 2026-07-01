package com.dmdbrands.gurus.weight.features.settings.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUserProfile
import com.greatergoods.blewrapper.GGDeviceService
import com.google.common.truth.Truth.assertThat
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Covers DeviceSettingsManager: the scale-update result branching, the R4 profile
 * update round-trip (success / exception / timeout), MAC address settings loading,
 * and the MAC address filter modal flow (testing-features gating, selection
 * persistence, and error handling). Private methods are driven the way the UI
 * drives them: onMacAddressFilterClick enqueues a Custom dialog whose captured
 * onConfirm callback is invoked to reach onMacAddressSelectionChange.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSettingsManagerTest {

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule()

  private val bluetoothPreferencesService: BluetoothPreferencesService = mockk(relaxed = true)
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val ggDeviceService: GGDeviceService = mockk(relaxed = true)

  private val manager = DeviceSettingsManager(
    bluetoothPreferencesService = bluetoothPreferencesService,
    dialogQueueService = dialogQueueService,
    ggDeviceService = ggDeviceService,
  )

  // region handleScaleUpdateResult

  @Test
  fun `handleScaleUpdateResult with USER_SELECTION_IN_PROGRESS enqueues alert and dismiss works`() {
    val dialogSlot = slot<DialogModel>()

    manager.handleScaleUpdateResult(GGUserActionResponseType.USER_SELECTION_IN_PROGRESS)

    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val alert = dialogSlot.captured as DialogModel.Alert
    alert.onDismiss?.invoke()
    verify { dialogQueueService.dismissCurrent() }
  }

  @Test
  fun `handleScaleUpdateResult with CREATION_COMPLETED dismisses loader and shows success toast`() {
    manager.handleScaleUpdateResult(GGUserActionResponseType.CREATION_COMPLETED)

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  @Test
  fun `handleScaleUpdateResult with UPDATE_COMPLETED dismisses loader and shows success toast`() {
    manager.handleScaleUpdateResult(GGUserActionResponseType.UPDATE_COMPLETED)

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  @Test
  fun `handleScaleUpdateResult with CREATION_FAILED dismisses loader and shows success toast`() {
    manager.handleScaleUpdateResult(GGUserActionResponseType.CREATION_FAILED)

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  @Test
  fun `handleScaleUpdateResult with an unhandled value falls through else branch`() {
    manager.handleScaleUpdateResult(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)

    verify { dialogQueueService.dismissLoader() }
    verify { dialogQueueService.showToast(any<Toast.Simple>()) }
  }

  // endregion

  // region updateR4Profile

  @Test
  fun `updateR4Profile returns callback result when callback completes`() = runTest {
    val profile = mockk<GGBTUserProfile>(relaxed = true)
    val callback = slot<(GGUserActionResponseType) -> Unit>()
    every { ggDeviceService.updateProfile(any(), capture(callback)) } answers {
      callback.captured.invoke(GGUserActionResponseType.UPDATE_COMPLETED)
    }

    val result = manager.updateR4Profile(profile)

    assertThat(result).isEqualTo(GGUserActionResponseType.UPDATE_COMPLETED)
  }

  @Test
  fun `updateR4Profile returns EXCEPTION_ENCOUNTERED when updateProfile throws`() = runTest {
    val profile = mockk<GGBTUserProfile>(relaxed = true)
    every { ggDeviceService.updateProfile(any(), any()) } throws RuntimeException("boom")

    val result = manager.updateR4Profile(profile)

    assertThat(result).isEqualTo(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
  }

  @Test
  fun `updateR4Profile returns EXCEPTION_ENCOUNTERED when callback never fires (timeout)`() = runTest {
    val profile = mockk<GGBTUserProfile>(relaxed = true)
    every { ggDeviceService.updateProfile(any(), any()) } answers { }

    val result = manager.updateR4Profile(profile)
    advanceUntilIdle()

    assertThat(result).isEqualTo(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)
  }

  // endregion

  // region loadMacAddressSettings

  @Test
  fun `loadMacAddressSettings dispatches selected mac and testing features`() = runTest {
    every { bluetoothPreferencesService.selectedMacAddress } returns flowOf("AA:BB")
    every { bluetoothPreferencesService.enableTestingFeatures } returns true
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)

    manager.loadMacAddressSettings(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify { dispatch(SettingsIntent.UpdateSelectedMacAddress("AA:BB")) }
    verify { dispatch(SettingsIntent.UpdateTestingFeatures(true)) }
  }

  @Test
  fun `loadMacAddressSettings swallows flow errors`() = runTest {
    every { bluetoothPreferencesService.selectedMacAddress } returns
      flow { throw RuntimeException("flow boom") }
    every { bluetoothPreferencesService.enableTestingFeatures } returns false
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)

    manager.loadMacAddressSettings(scope = this, dispatch = dispatch)
    advanceUntilIdle()

    verify(exactly = 0) { dispatch(any<SettingsIntent.UpdateSelectedMacAddress>()) }
    verify { dispatch(SettingsIntent.UpdateTestingFeatures(false)) }
  }

  // endregion

  // region onMacAddressFilterClick

  @Test
  fun `onMacAddressFilterClick is a no-op when testing features are disabled`() = runTest {
    val state = SettingsState(enableTestingFeatures = false)
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)

    manager.onMacAddressFilterClick(scope = this, stateProvider = { state }, dispatch = dispatch)

    coVerify(exactly = 0) { dialogQueueService.enqueue(any()) }
  }

  @Test
  fun `onMacAddressFilterClick selection persists mac and dispatches when testing enabled`() = runTest {
    every { bluetoothPreferencesService.knownMacAddresses } returns listOf("AA:BB", "CC:DD")
    val state = SettingsState(enableTestingFeatures = true)
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)

    val onConfirm = openFilterModal(state, dispatch)
    onConfirm("AA:BB")
    advanceUntilIdle()

    verify { dialogQueueService.showLoader(any()) }
    coVerify { bluetoothPreferencesService.setSelectedMacAddressLocally("AA:BB") }
    verify { dispatch(SettingsIntent.UpdateSelectedMacAddress("AA:BB")) }
    verify { dialogQueueService.dismissLoader() }
  }

  @Test
  fun `onMacAddressSelectionChange returns early when testing disabled at selection time`() = runTest {
    every { bluetoothPreferencesService.knownMacAddresses } returns listOf("AA:BB", "CC:DD")
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)
    var testingEnabled = true
    val stateProvider = { SettingsState(enableTestingFeatures = testingEnabled) }

    val onConfirm = openFilterModal(stateProvider(), dispatch, stateProvider)
    testingEnabled = false
    onConfirm("AA:BB")
    advanceUntilIdle()

    coVerify(exactly = 0) { bluetoothPreferencesService.setSelectedMacAddressLocally(any()) }
  }

  @Test
  fun `onMacAddressSelectionChange dismisses loader even when persistence throws`() = runTest {
    every { bluetoothPreferencesService.knownMacAddresses } returns listOf("AA:BB", "CC:DD")
    coEvery { bluetoothPreferencesService.setSelectedMacAddressLocally(any()) } throws
      RuntimeException("persist boom")
    val state = SettingsState(enableTestingFeatures = true)
    val dispatch: (SettingsIntent) -> Unit = mockk(relaxed = true)

    val onConfirm = openFilterModal(state, dispatch)
    onConfirm("AA:BB")
    advanceUntilIdle()

    verify { dialogQueueService.dismissLoader() }
    verify(exactly = 0) { dispatch(any<SettingsIntent.UpdateSelectedMacAddress>()) }
  }

  // endregion

  /** Opens the MAC filter modal and returns its captured onConfirm callback. */
  @Suppress("UNCHECKED_CAST")
  private fun kotlinx.coroutines.test.TestScope.openFilterModal(
    state: SettingsState,
    dispatch: (SettingsIntent) -> Unit,
    stateProvider: () -> SettingsState = { state },
  ): (String?) -> Unit {
    val dialogSlot = slot<DialogModel>()
    manager.onMacAddressFilterClick(scope = this, stateProvider = stateProvider, dispatch = dispatch)
    verify { dialogQueueService.enqueue(capture(dialogSlot)) }
    val custom = dialogSlot.captured as DialogModel.Custom
    return custom.params["onConfirm"] as (String?) -> Unit
  }
}
