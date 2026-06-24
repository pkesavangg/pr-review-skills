package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupIntent
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.enums.ScaleSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [BtScaleSetupViewModel].
 *
 * Hardware-centric ViewModel — targets ~50% coverage focusing on
 * state management, reducer intents, step navigation, and button logic.
 * Bluetooth scanning and pairing are hardware-bound and not tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BtScaleSetupViewModelTest {

    companion object {
        private const val TEST_SKU = "0391"
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

    private lateinit var viewModel: BtScaleSetupViewModel

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

        val scaleInit = SetupInitData(
            sku = TEST_SKU,
            initialStep = BtScaleSetupStep.SCALE_INFO,
        )

        viewModel = BtScaleSetupViewModel(
            scaleInit = scaleInit,
            dependencies = dependencies,
        ).initTestDependencies()
    }

    @AfterEach
    fun tearDown() {
        // Walk the class hierarchy to find onCleared (declared in BLESetupViewmodel)
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
    fun `initial state starts at SCALE_INFO step`() {
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `initial state has null user`() {
        assertThat(viewModel.state.value.user).isNull()
    }

    @Test
    fun `initial state has correct step list`() {
        val steps = viewModel.state.value.steps
        assertThat(steps).containsExactly(
            BtScaleSetupStep.SCALE_INFO,
            BtScaleSetupStep.PERMISSIONS,
            BtScaleSetupStep.SELECT_USER,
            BtScaleSetupStep.PAIRING_MODE,
            BtScaleSetupStep.SET_DEVICE_USER,
            BtScaleSetupStep.STEP_ON,
            BtScaleSetupStep.SETUP_FINISHED,
        ).inOrder()
    }

    @Test
    fun `initial state isFirstStep is true`() {
        assertThat(viewModel.state.value.isFirstStep).isTrue()
    }

    @Test
    fun `initial state isLastStep is false`() {
        assertThat(viewModel.state.value.isLastStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `SetUser updates user in state`() {
        viewModel.handleIntent(BtScaleSetupIntent.SetUser(3))
        assertThat(viewModel.state.value.user).isEqualTo(3)
    }

    @Test
    fun `userString returns formatted user string`() {
        viewModel.handleIntent(BtScaleSetupIntent.SetUser(2))
        assertThat(viewModel.state.value.userString).isEqualTo("U2")
    }

    @Test
    fun `SetNewStep changes current step`() {
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `AlterConnectionState updates connection state`() {
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `BackEnabled updates backEnabled flag`() {
        viewModel.handleIntent(ScaleSetupIntent.BackEnabled(true))
        assertThat(viewModel.state.value.backEnabled).isTrue()
    }

    @Test
    fun `NextEnabled updates nextEnabled flag`() {
        viewModel.handleIntent(ScaleSetupIntent.NextEnabled(false))
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms = mutableMapOf("bluetooth_switch" to "enabled")
        viewModel.handleIntent(ScaleSetupIntent.SetPermissions(perms))
        assertThat(viewModel.state.value.permissions).isEqualTo(perms)
    }

    @Test
    fun `SetSku updates sku in state`() {
        viewModel.handleIntent(ScaleSetupIntent.SetSku("0999"))
        assertThat(viewModel.state.value.scaleSetupState.sku).isEqualTo("0999")
    }

    // -------------------------------------------------------------------------
    // Button Changes
    // -------------------------------------------------------------------------

    @Test
    fun `handleButtonChanges disables back for SCALE_INFO step`() {
        advanceScheduler()
        // SCALE_INFO is the first step, back should be disabled by handleButtonChanges
        assertThat(viewModel.state.value.backEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges disables back for SETUP_FINISHED step`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SETUP_FINISHED))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.backEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges disables back for SET_DEVICE_USER step`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SET_DEVICE_USER))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.backEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges enables back for PERMISSIONS step`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.backEnabled).isTrue()
    }

    @Test
    fun `handleButtonChanges disables next for PAIRING_MODE step`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PAIRING_MODE))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges disables next for STEP_ON step`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.STEP_ON))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges disables next for SELECT_USER when user is null`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SELECT_USER))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `handleButtonChanges enables next for SELECT_USER when user is set`() {
        advanceScheduler()
        viewModel.handleIntent(BtScaleSetupIntent.SetUser(1))
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SELECT_USER))
        advanceScheduler()
        advanceScheduler()
        assertThat(viewModel.state.value.nextEnabled).isTrue()
    }

    // -------------------------------------------------------------------------
    // Step Navigation
    // -------------------------------------------------------------------------

    @Test
    fun `onBack from first step navigates to MyDevices`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Back)
        advanceScheduler()
        coVerify { viewModel.navigationService.navigateTo(any()) }
    }

    @Test
    fun `onBack from PERMISSIONS goes to previous step`() {
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `onBack from SELECT_USER with permission goes to SCALE_INFO`() {
        viewModel.isPermissionGranted = true
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.SELECT_USER))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `onSkip delegates to onNext`() {
        advanceScheduler()
        // Skip from first step should advance like Next
        viewModel.handleIntent(ScaleSetupIntent.Skip)
        advanceScheduler()
        // Should have moved from SCALE_INFO
        assertThat(viewModel.state.value.step).isNotEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup with finished true does not show confirmation dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()
        // When finished, it should exit directly - no Confirm dialog
        verify(exactly = 0) { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ExitSetup with finished false shows confirmation dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // OpenHelp
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.OpenHelp)
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // Init block verifications
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

    // -------------------------------------------------------------------------
    // onCleared cleanup
    // -------------------------------------------------------------------------

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
    // RequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission calls dialogUtility permissionAlert`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.RequestPermission("bluetooth"))
        advanceScheduler()
        verify {
            dialogUtility.permissionAlert(
                permissionType = "bluetooth",
                isScaleSetupRequest = any(),
                onRequest = any(),
                onDismiss = any(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // TryAgain
    // -------------------------------------------------------------------------

    @Test
    fun `TryAgain on non-retry step does not crash`() {
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.TryAgain)
        advanceScheduler()
        // Should not crash, just log a warning
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.PERMISSIONS)
    }
}
