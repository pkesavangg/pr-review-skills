package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LcbtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [LcbtBLESetupViewModel].
 *
 * Uses [StandardTestDispatcher] so init-block coroutines are scheduled but don't
 * execute eagerly. Pure reducer intents work without advancing time.
 *
 * No `runTest` — it hangs because `runTest` cleanup waits for infinite coroutines
 * (step observation, permission observation) to finish, which never happens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LcbtBLESetupViewModelTest {

    companion object {
        private const val TEST_SKU = "0375"
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var connectivityObserver: IConnectivityObserver
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var dependencies: BLESetupDependencies
    private lateinit var viewModel: LcbtBLESetupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf())
        every { connectivityObserver.observe() } returns MutableStateFlow(mockk(relaxed = true))
        every { ggDeviceService.deviceCallbackFlow } returns MutableStateFlow(mockk(relaxed = true))
        every { ggDeviceService.localSkipDevices } returns MutableStateFlow(emptyList())
        every { ggDeviceService.deviceCache } returns MutableStateFlow(emptyMap())

        dependencies = BLESetupDependencies(
            ggDeviceService = ggDeviceService,
            connectivityObserver = connectivityObserver,
            deviceService = deviceService,
            permissionService = permissionService,
            dialogUtility = dialogUtility,
        )
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            // onCleared is declared in BLESetupViewmodel (superclass), not LcbtBLESetupViewModel
            val bleViewModelClass = viewModel::class.java.superclass // BLESetupViewmodel
            val method = bleViewModelClass.getDeclaredMethod("onCleared")
            method.isAccessible = true
            method.invoke(viewModel)
        }
    }

    private fun advanceScheduler() {
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.runCurrent()
    }

    private fun createViewModel(
        sku: String = TEST_SKU,
        initialStep: LcbtScaleSetupStep = LcbtScaleSetupStep.SCALE_INFO,
    ): LcbtBLESetupViewModel {
        val setupInit = SetupInitData(
            sku = sku,
            initialStep = initialStep,
            scaleInfo = null,
            broadcastId = null,
        )
        viewModel = LcbtBLESetupViewModel(
            setupInit = setupInit,
            dependencies = dependencies,
        ).initTestDependencies()
        return viewModel
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @Test
    fun `init sets initial step from SetupInitData`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `init calls deviceService setSetupInProgress true`() {
        createViewModel()
        verify { deviceService.setSetupInProgress(true) }
    }

    @Test
    fun `initial state has correct steps list`() {
        createViewModel()
        val steps = viewModel.state.value.steps
        assertThat(steps).containsExactly(
            LcbtScaleSetupStep.SCALE_INFO,
            LcbtScaleSetupStep.PERMISSIONS,
            LcbtScaleSetupStep.WAKEUP,
            LcbtScaleSetupStep.CONNECTING_BLUETOOTH,
            LcbtScaleSetupStep.SETUP_FINISHED,
        ).inOrder()
    }

    @Test
    fun `initial state isFirstStep is true`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.isFirstStep).isTrue()
    }

    @Test
    fun `initial state isLastStep is false`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.isLastStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has loading connection state`() {
        createViewModel()
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Loading)
    }

    @Test
    fun `initial state nextEnabled is true`() {
        createViewModel()
        assertThat(viewModel.state.value.nextEnabled).isTrue()
    }

    @Test
    fun `initial state backEnabled is false`() {
        createViewModel()
        assertThat(viewModel.state.value.backEnabled).isFalse()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `SetSku updates sku in state`() {
        createViewModel()
        viewModel.handleIntent(ScaleSetupIntent.SetSku("1234"))
        assertThat(viewModel.state.value.scaleSetupState.sku).isEqualTo("1234")
    }

    @Test
    fun `AlterConnectionState updates connection state to Success`() {
        createViewModel()
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `AlterConnectionState updates connection state to Failed Error`() {
        createViewModel()
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isInstanceOf(ConnectionState.Failed.Error::class.java)
    }

    @Test
    fun `AlterConnectionState updates to Failed ErrorWithMessage`() {
        createViewModel()
        viewModel.handleIntent(
            ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.ErrorWithMessage("BT_001"))
        )
        val connState = viewModel.state.value.scaleSetupState.setupState.connectionState
        assertThat(connState).isInstanceOf(ConnectionState.Failed.ErrorWithMessage::class.java)
        assertThat((connState as ConnectionState.Failed.ErrorWithMessage).message).isEqualTo("BT_001")
    }

    @Test
    fun `NextEnabled updates nextEnabled`() {
        createViewModel()
        viewModel.handleIntent(ScaleSetupIntent.NextEnabled(false))
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `BackEnabled updates backEnabled`() {
        createViewModel()
        viewModel.handleIntent(ScaleSetupIntent.BackEnabled(true))
        assertThat(viewModel.state.value.backEnabled).isTrue()
    }

    @Test
    fun `SetPermissions updates permissions in state`() {
        createViewModel()
        val permissions = mutableMapOf("BLUETOOTH_SWITCH" to "ENABLED")
        viewModel.handleIntent(ScaleSetupIntent.SetPermissions(permissions))
        assertThat(viewModel.state.value.permissions).isEqualTo(permissions)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — SetNewStep
    // -------------------------------------------------------------------------

    @Test
    fun `SetNewStep changes current step`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `SetNewStep to SETUP_FINISHED sets isLastStep true`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.SETUP_FINISHED))
        assertThat(viewModel.state.value.isLastStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // onNext
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO with permissions granted goes to WAKEUP`() {
        createViewModel()
        advanceScheduler()
        // Simulate permissions granted
        viewModel.isPermissionGranted = true
        viewModel.handleIntent(ScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.WAKEUP)
    }

    @Test
    fun `Next from SCALE_INFO without permissions goes to PERMISSIONS`() {
        createViewModel()
        advanceScheduler()
        viewModel.isPermissionGranted = false
        viewModel.handleIntent(ScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `Next at last step triggers ExitSetup`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.SETUP_FINISHED))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Next)
        advanceScheduler()
        // ExitSetup(true) shows exit flow — should call setSetupInProgress(false)
        verify { deviceService.setSetupInProgress(false) }
    }

    @Test
    fun `Next from intermediate step advances to next step`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.CONNECTING_BLUETOOTH)
    }

    // -------------------------------------------------------------------------
    // onBack
    // -------------------------------------------------------------------------

    @Test
    fun `Back from first step navigates to MyDevices`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Back)
        advanceScheduler()
      // Should navigate to MyDevices route
        coVerify { viewModel.navigationService.navigateTo(any()) }
    }

    @Test
    fun `Back from PERMISSIONS returns to SCALE_INFO`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // onSkip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip delegates to onNext`() {
        createViewModel()
        advanceScheduler()
        viewModel.isPermissionGranted = true
        viewModel.handleIntent(ScaleSetupIntent.Skip)
        advanceScheduler()
        // Skip from SCALE_INFO with permissions granted should go to WAKEUP
        assertThat(viewModel.state.value.step).isEqualTo(LcbtScaleSetupStep.WAKEUP)
    }

    // -------------------------------------------------------------------------
    // onTryAgain
    // -------------------------------------------------------------------------

    @Test
    fun `TryAgain on WAKEUP sets connection state to Loading`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
        advanceScheduler()
        // Set failed state first
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.TryAgain)
        advanceScheduler()
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Loading)
    }

    @Test
    fun `TryAgain on CONNECTING_BLUETOOTH sets connection state to Loading`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.CONNECTING_BLUETOOTH))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Failed.Error))
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.TryAgain)
        advanceScheduler()
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Loading)
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup with isSetupFinished false shows confirm dialog`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ExitSetup with isSetupFinished true clears setup in progress`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()
        verify { deviceService.setSetupInProgress(false) }
    }

    // -------------------------------------------------------------------------
    // OpenHelp
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.OpenHelp)
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // onCleared
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared sets setup in progress false`() {
        createViewModel()
        advanceScheduler()
        val bleViewModelClass = viewModel::class.java.superclass
        val method = bleViewModelClass.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        verify { deviceService.setSetupInProgress(false) }
    }

    // -------------------------------------------------------------------------
    // Permission observation
    // -------------------------------------------------------------------------

    @Test
    fun `permission observation updates state when permissions change`() {
        val permissionsFlow = MutableStateFlow(mutableMapOf<String, String>())
        every { permissionService.permissionCallBackFlow } returns permissionsFlow

        createViewModel()
        advanceScheduler()

        val newPermissions = mutableMapOf("BLUETOOTH_SWITCH" to "ENABLED")
        permissionsFlow.value = newPermissions
        advanceScheduler()

        assertThat(viewModel.state.value.permissions).isEqualTo(newPermissions)
    }

    // -------------------------------------------------------------------------
    // Step-related properties
    // -------------------------------------------------------------------------

    @Test
    fun `nextStep returns correct next step`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.nextStep).isEqualTo(LcbtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `previousStep returns null at first step`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.previousStep).isNull()
    }

    @Test
    fun `previousStep returns correct step from PERMISSIONS`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.PERMISSIONS))
        advanceScheduler()
        assertThat(viewModel.state.value.previousStep).isEqualTo(LcbtScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // WAKEUP step — scan for pairing
    // -------------------------------------------------------------------------

    @Test
    fun `onStepChange to WAKEUP calls scanForPairing`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.WAKEUP))
        advanceScheduler()
        verify { ggDeviceService.scanForPairing() }
    }

    @Test
    fun `onStepChange to CONNECTING_BLUETOOTH sets loading connection state`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(LcbtScaleSetupStep.CONNECTING_BLUETOOTH))
        advanceScheduler()
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Loading)
    }
}
