package com.dmdbrands.gurus.weight.features.monitorSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.BLESetupDependencies
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.MonitorSetupViewModel
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.DeviceSearchInfo
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.MonitorSetupViewModel].
 *
 * Hardware-centric ViewModel — targets ~50% coverage focusing on
 * state management, reducer intents, step navigation, and button logic.
 * Bluetooth scanning and pairing are hardware-bound and not tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorSetupViewModelTest {

  companion object {
    private const val TEST_SKU = "0603"
  }

  private val testDispatcher = StandardTestDispatcher()

  @JvmField
  @RegisterExtension
  val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
  @MockK(relaxed = true) lateinit var deviceService: IDeviceService
  @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
  @MockK(relaxed = true) lateinit var connectivityObserver: IConnectivityObserver
  @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility

  private lateinit var viewModel: MonitorSetupViewModel

  @BeforeEach
  fun setUp() {
    MockKAnnotations.init(this)
    every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
    every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf())

    val dependencies = BLESetupDependencies(
      ggDeviceService = ggDeviceService,
      connectivityObserver = connectivityObserver,
      deviceService = deviceService,
      permissionService = permissionService,
      dialogUtility = dialogUtility,
    )

    val monitorInit = SetupInitData(
      sku = TEST_SKU,
      initialStep = MonitorSetupStep.MONITOR_DETAIL,
    )

    viewModel = MonitorSetupViewModel(
        monitorInit = monitorInit,
        dependencies = dependencies,
    ).initTestDependencies()
  }

  @AfterEach
  fun tearDown() {
    var clazz: Class<*>? = viewModel::class.java
    while (clazz != null) {
      try {
        val method = clazz.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        return
      } catch (_: NoSuchMethodException) {
        clazz = clazz.superclass
      }
    }
  }

  private fun advanceScheduler() {
    testDispatcher.scheduler.advanceTimeBy(200)
    testDispatcher.scheduler.runCurrent()
  }

  // -------------------------------------------------------------------------
  // Default State
  // -------------------------------------------------------------------------

  @Test
  fun `initial state starts at MONITOR_DETAIL step`() {
    assertThat(viewModel.state.value.step).isEqualTo(MonitorSetupStep.MONITOR_DETAIL)
  }

  @Test
  fun `initial state has default selectedUser for numeric SKU`() {
    assertThat(viewModel.state.value.selectedUser).isEqualTo("1")
  }

  @Test
  fun `initial state has default monitorNickname from SKU`() {
    assertThat(viewModel.state.value.monitorNickname).isNotEmpty()
  }

  @Test
  fun `initial state has correct step list for SKU 0603`() {
    val expectedSteps = MonitorSetupStepHelper.stepsForSku(TEST_SKU)
    assertThat(viewModel.state.value.steps.toList()).isEqualTo(expectedSteps)
  }

  @Test
  fun `initial state isFirstStep is true`() {
    assertThat(viewModel.state.value.isFirstStep).isTrue()
  }

  @Test
  fun `initial state isLastStep is false`() {
    assertThat(viewModel.state.value.isLastStep).isFalse()
  }

  @Test
  fun `initial state hasNumericUsers is true for SKU 0603`() {
    assertThat(viewModel.state.value.hasNumericUsers).isTrue()
  }

  // -------------------------------------------------------------------------
  // Pure State Intents (reducer-only)
  // -------------------------------------------------------------------------

  @Test
  fun `SetSelectedUser updates selectedUser in state`() {
    viewModel.handleIntent(MonitorSetupIntent.SetSelectedUser("A"))
    assertThat(viewModel.state.value.selectedUser).isEqualTo("A")
  }

  @Test
  fun `SetMonitorNickname updates monitorNickname in state`() {
    viewModel.handleIntent(MonitorSetupIntent.SetMonitorNickname("My BPM"))
    assertThat(viewModel.state.value.monitorNickname).isEqualTo("My BPM")
  }

  @Test
  fun `SetNewStep changes current step`() {
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
    assertThat(viewModel.state.value.step).isEqualTo(MonitorSetupStep.PERMISSIONS)
  }

  @Test
  fun `AlterConnectionState updates connection state`() {
    viewModel.handleIntent(DeviceSetupIntent.AlterConnectionState(ConnectionState.Success))
    assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
      .isEqualTo(ConnectionState.Success)
  }

  @Test
  fun `BackEnabled updates backEnabled flag`() {
    viewModel.handleIntent(DeviceSetupIntent.BackEnabled(true))
    assertThat(viewModel.state.value.backEnabled).isTrue()
  }

  @Test
  fun `NextEnabled updates nextEnabled flag`() {
    viewModel.handleIntent(DeviceSetupIntent.NextEnabled(false))
    assertThat(viewModel.state.value.nextEnabled).isFalse()
  }

  @Test
  fun `SetPermissions updates permissions map`() {
    val perms = mutableMapOf("bluetooth_switch" to "enabled")
    viewModel.handleIntent(DeviceSetupIntent.SetPermissions(perms))
    assertThat(viewModel.state.value.permissions).isEqualTo(perms)
  }

  // -------------------------------------------------------------------------
  // Button Changes
  // -------------------------------------------------------------------------

  @Test
  fun `handleButtonChanges disables back for MONITOR_DETAIL step`() {
    advanceScheduler()
    assertThat(viewModel.state.value.backEnabled).isFalse()
  }

  @Test
  fun `handleButtonChanges disables back for MONITOR_NICKNAME step`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_NICKNAME))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.backEnabled).isFalse()
  }

  @Test
  fun `handleButtonChanges enables back for PERMISSIONS step`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.backEnabled).isTrue()
  }

  @Test
  fun `handleButtonChanges disables next for MONITOR_PAIRING step`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_PAIRING))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.nextEnabled).isFalse()
  }

  @Test
  fun `handleButtonChanges enables next for USER_SELECTION when user has default value`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.USER_SELECTION))
    advanceScheduler()
    advanceScheduler()
    // Init pre-sets selectedUser to "1" for numeric SKU, so next is enabled
    assertThat(viewModel.state.value.nextEnabled).isTrue()
  }

  @Test
  fun `handleButtonChanges enables next for USER_SELECTION when user is set`() {
    advanceScheduler()
    viewModel.handleIntent(MonitorSetupIntent.SetSelectedUser("1"))
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.USER_SELECTION))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.nextEnabled).isTrue()
  }

  @Test
  fun `handleButtonChanges disables next for MONITOR_NICKNAME when nickname is blank`() {
    advanceScheduler()
    viewModel.handleIntent(MonitorSetupIntent.SetMonitorNickname(""))
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_NICKNAME))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.nextEnabled).isFalse()
  }

  @Test
  fun `handleButtonChanges enables next for MONITOR_NICKNAME when nickname is set`() {
    advanceScheduler()
    viewModel.handleIntent(MonitorSetupIntent.SetMonitorNickname("My BPM"))
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_NICKNAME))
    advanceScheduler()
    advanceScheduler()
    assertThat(viewModel.state.value.nextEnabled).isTrue()
  }

  // -------------------------------------------------------------------------
  // Step Navigation
  // -------------------------------------------------------------------------

  @Test
  fun `onBack from first step navigates away`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.Back)
    advanceScheduler()
    coVerify { viewModel.navigationService.navigateTo(any()) }
  }

  @Test
  fun `onBack from PERMISSIONS goes to previous step`() {
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.Back)
    advanceScheduler()
    assertThat(viewModel.state.value.step).isEqualTo(MonitorSetupStep.MONITOR_DETAIL)
  }

  @Test
  fun `onBack from USER_SELECTION with permission goes to MONITOR_DETAIL`() {
    viewModel.isPermissionGranted = true
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.USER_SELECTION))
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.Back)
    advanceScheduler()
    assertThat(viewModel.state.value.step).isEqualTo(MonitorSetupStep.MONITOR_DETAIL)
  }

  @Test
  fun `onSkip delegates to onNext`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.Skip)
    advanceScheduler()
    assertThat(viewModel.state.value.step).isNotEqualTo(MonitorSetupStep.MONITOR_DETAIL)
  }

  // -------------------------------------------------------------------------
  // ExitSetup
  // -------------------------------------------------------------------------

  @Test
  fun `ExitSetup with finished true does not show confirmation dialog`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.ExitSetup(isSetupFinished = true))
    advanceScheduler()
    verify(exactly = 0) { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
  }

  @Test
  fun `ExitSetup with finished false shows confirmation dialog`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.ExitSetup(isSetupFinished = false))
    advanceScheduler()
    verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
  }

  // -------------------------------------------------------------------------
  // OpenHelp
  // -------------------------------------------------------------------------

  @Test
  fun `OpenHelp enqueues custom dialog`() {
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.OpenHelp)
    advanceScheduler()
    verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
  }

  // -------------------------------------------------------------------------
  // Init / Cleanup
  // -------------------------------------------------------------------------

  @Test
  fun `init calls deviceService setSetupInProgress true`() {
    verify { deviceService.setSetupInProgress(true) }
  }

  @Test
  fun `init subscribes to pairedScales`() {
    advanceScheduler()
    verify { deviceService.pairedScales }
  }

  @Test
  fun `onCleared sets setupInProgress false`() {
    var clazz: Class<*>? = viewModel::class.java
    while (clazz != null) {
      try {
        val method = clazz.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        break
      } catch (_: NoSuchMethodException) {
        clazz = clazz.superclass
      }
    }
    verify { deviceService.setSetupInProgress(false) }
  }

  // -------------------------------------------------------------------------
  // TryAgain
  // -------------------------------------------------------------------------

  @Test
  fun `TryAgain on non-retry step does not crash`() {
    viewModel.handleIntent(DeviceSetupIntent.SetNewStep(MonitorSetupStep.PERMISSIONS))
    advanceScheduler()
    viewModel.handleIntent(DeviceSetupIntent.TryAgain)
    advanceScheduler()
    assertThat(viewModel.state.value.step).isEqualTo(MonitorSetupStep.PERMISSIONS)
  }

  // -------------------------------------------------------------------------
  // A6 companion scale is paired separately (not in the wizard)
  // -------------------------------------------------------------------------
  //
  // Companion scale BLE pairing is intentionally NOT performed inside the BPM
  // wizard — users pair the companion scale via the standard Add-Device flow,
  // so the A6 flow carries no in-wizard scale step. This contract test ensures
  // the post-pairing SUCCESS_SCREEN never triggers any BLE scan / pair calls
  // for the A6 SKUs (0661, 0663). (MOB-596)

  @Test
  fun `SUCCESS_SCREEN on A6 0661 never triggers BLE`() {
    verifySuccessStepDoesNotTriggerBle("0661")
  }

  @Test
  fun `SUCCESS_SCREEN on A6 0663 never triggers BLE`() {
    verifySuccessStepDoesNotTriggerBle("0663")
  }

  private fun createA6ViewModel(sku: String): MonitorSetupViewModel {
    val a6Init = SetupInitData(
      sku = sku,
      initialStep = MonitorSetupStep.MONITOR_DETAIL,
    )
    val a6Dependencies = BLESetupDependencies(
      ggDeviceService = ggDeviceService,
      connectivityObserver = connectivityObserver,
      deviceService = deviceService,
      permissionService = permissionService,
      dialogUtility = dialogUtility,
    )
    return MonitorSetupViewModel(
      monitorInit = a6Init,
      dependencies = a6Dependencies,
    ).initTestDependencies()
  }

  private fun verifySuccessStepDoesNotTriggerBle(sku: String) {
    val a6ViewModel = createA6ViewModel(sku)

    // Positive contrast: MONITOR_PAIRING MUST trigger a BLE scan. If this
    // never fires, the test infra is broken and the negative assertions
    // below would pass vacuously.
    a6ViewModel.handleIntent(
      DeviceSetupIntent.SetNewStep(MonitorSetupStep.MONITOR_PAIRING),
    )
    advanceScheduler()
    verify(atLeast = 1) { ggDeviceService.scanForPairing() }

    // Reset call counts and assert the post-pairing SUCCESS_SCREEN + TryAgain
    // produce zero BLE scan / pair calls.
    clearMocks(ggDeviceService, answers = false)
    a6ViewModel.handleIntent(
      DeviceSetupIntent.SetNewStep(MonitorSetupStep.SUCCESS_SCREEN),
    )
    advanceScheduler()
    a6ViewModel.handleIntent(DeviceSetupIntent.TryAgain)
    advanceScheduler()

    verify(exactly = 0) { ggDeviceService.scanForPairing() }
    verify(exactly = 0) { ggDeviceService.pairDevice(any(), any(), any(), any()) }
  }

  // -------------------------------------------------------------------------
  // A6 — step list shape and per-step state
  // -------------------------------------------------------------------------

  @Test
  fun `A6 0661 step list has no MONITOR_OFF and goes SUCCESS_SCREEN to INSTRUCTION_CUFF`() {
    val a6ViewModel = createA6ViewModel("0661")
    val steps = a6ViewModel.state.value.steps.toList()
    assertThat(steps).doesNotContain(MonitorSetupStep.MONITOR_OFF)
    val successIndex = steps.indexOf(MonitorSetupStep.SUCCESS_SCREEN)
    assertThat(successIndex).isAtLeast(0)
    assertThat(steps[successIndex + 1]).isEqualTo(MonitorSetupStep.INSTRUCTION_CUFF)
  }

  @Test
  fun `A6 0663 step list contains MONITOR_OFF and goes SUCCESS_SCREEN to INSTRUCTION_CUFF`() {
    val a6ViewModel = createA6ViewModel("0663")
    val steps = a6ViewModel.state.value.steps.toList()
    assertThat(steps).contains(MonitorSetupStep.MONITOR_OFF)
    val successIndex = steps.indexOf(MonitorSetupStep.SUCCESS_SCREEN)
    assertThat(successIndex).isAtLeast(0)
    assertThat(steps[successIndex + 1]).isEqualTo(MonitorSetupStep.INSTRUCTION_CUFF)
  }

  @Test
  fun `A6 default selectedUser is letter A not numeric`() {
    val a6ViewModel = createA6ViewModel("0661")
    assertThat(a6ViewModel.state.value.selectedUser).isEqualTo("A")
    assertThat(a6ViewModel.state.value.hasNumericUsers).isFalse()
  }

  // -------------------------------------------------------------------------
  // checkIfDeviceExists — already-paired detection by peripheralIdentifier + userNumber
  // -------------------------------------------------------------------------

  private fun pairedMonitor(identifier: String, userNumber: Int): Device {
    val detail: GGDeviceDetail = mockk(relaxed = true)
    every { detail.identifier } returns identifier
    every { detail.macAddress } returns "AA:BB:CC:DD:EE:FF"
    return Device(id = "id-$identifier-$userNumber", device = detail, sku = TEST_SKU, userNumber = userNumber)
  }

  private fun viewModelWithPaired(devices: List<Device>): MonitorSetupViewModel {
    every { deviceService.pairedScales } returns MutableStateFlow(devices)
    val dependencies = BLESetupDependencies(
      ggDeviceService = ggDeviceService,
      connectivityObserver = connectivityObserver,
      deviceService = deviceService,
      permissionService = permissionService,
      dialogUtility = dialogUtility,
    )
    val vm = MonitorSetupViewModel(
      monitorInit = SetupInitData(sku = TEST_SKU, initialStep = MonitorSetupStep.MONITOR_DETAIL),
      dependencies = dependencies,
    ).initTestDependencies()
    advanceScheduler()
    return vm
  }

  private fun invokeCheckIfDeviceExists(vm: MonitorSetupViewModel, key: String, userNumber: Int?): DeviceSearchInfo {
    val method = MonitorSetupViewModel::class.java
      .getDeclaredMethod("checkIfDeviceExists", String::class.java, Integer::class.java)
    method.isAccessible = true
    return method.invoke(vm, key, userNumber) as DeviceSearchInfo
  }

  @Test
  fun `checkIfDeviceExists flags same-user when peripheralIdentifier and userNumber match`() {
    val vm = viewModelWithPaired(listOf(pairedMonitor("periph-1", userNumber = 1)))
    val result = invokeCheckIfDeviceExists(vm, key = "periph-1", userNumber = 1)
    assertThat(result.isMonitorExistsWithSameUser).isTrue()
    assertThat(result.isMonitorExistsWithDifferentUser).isFalse()
  }

  @Test
  fun `checkIfDeviceExists flags different-user when same monitor but other user slot`() {
    val vm = viewModelWithPaired(listOf(pairedMonitor("periph-1", userNumber = 1)))
    val result = invokeCheckIfDeviceExists(vm, key = "periph-1", userNumber = 2)
    assertThat(result.isMonitorExistsWithDifferentUser).isTrue()
    assertThat(result.isMonitorExistsWithSameUser).isFalse()
  }

  @Test
  fun `checkIfDeviceExists matches peripheralIdentifier case-insensitively`() {
    val vm = viewModelWithPaired(listOf(pairedMonitor("PERIPH-1", userNumber = 1)))
    // peripheralKey lowercases both sides, so an upper-cased stored id still matches.
    val result = invokeCheckIfDeviceExists(vm, key = "periph-1", userNumber = 1)
    assertThat(result.isMonitorExistsWithSameUser).isTrue()
  }

  @Test
  fun `checkIfDeviceExists reports no match for an unknown monitor`() {
    val vm = viewModelWithPaired(listOf(pairedMonitor("periph-1", userNumber = 1)))
    val result = invokeCheckIfDeviceExists(vm, key = "periph-unknown", userNumber = 1)
    assertThat(result.isMonitorExists).isFalse()
  }
}
