package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.BtScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.ScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupReducer
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.BtScaleSetupState
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.ScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
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
 * Unit tests for the abstract [BLESetupViewmodel] via the concrete [BtScaleSetupViewModel].
 *
 * Focus: provideInitialState, basic intents, handleIntent routing, and common
 * behaviors inherited from BLESetupViewmodel. BLE-dependent scanning/pairing
 * methods are not tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BLESetupViewModelTest {

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
    // provideInitialState — BLESetupViewmodel abstract method
    // -------------------------------------------------------------------------

    @Test
    fun `provideInitialState returns correct initial step`() {
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `provideInitialState sets isFirstStep true`() {
        assertThat(viewModel.state.value.isFirstStep).isTrue()
    }

    @Test
    fun `provideInitialState sets isLastStep false`() {
        assertThat(viewModel.state.value.isLastStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // handleIntent routing — inherited from BLESetupViewmodel
    // -------------------------------------------------------------------------

    @Test
    fun `handleIntent Next is routed through BLESetupViewmodel`() {
        advanceScheduler()
        val initialStep = viewModel.state.value.step
        viewModel.handleIntent(ScaleSetupIntent.Next)
        advanceScheduler()
        // Step should have changed (Next was handled)
        assertThat(viewModel.state.value.step).isNotEqualTo(initialStep)
    }

    @Test
    fun `handleIntent Skip delegates to onNext`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.Skip)
        advanceScheduler()
        // Should have moved from SCALE_INFO
        assertThat(viewModel.state.value.step).isNotEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `handleIntent SetNewStep changes step`() {
        viewModel.handleIntent(ScaleSetupIntent.SetNewStep(BtScaleSetupStep.PERMISSIONS))
        assertThat(viewModel.state.value.step).isEqualTo(BtScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `handleIntent BackEnabled updates backEnabled flag`() {
        viewModel.handleIntent(ScaleSetupIntent.BackEnabled(true))
        assertThat(viewModel.state.value.backEnabled).isTrue()
    }

    @Test
    fun `handleIntent NextEnabled updates nextEnabled flag`() {
        viewModel.handleIntent(ScaleSetupIntent.NextEnabled(false))
        assertThat(viewModel.state.value.nextEnabled).isFalse()
    }

    @Test
    fun `handleIntent SetPermissions updates permissions`() {
        val perms = mutableMapOf("bluetooth_switch" to "enabled")
        viewModel.handleIntent(ScaleSetupIntent.SetPermissions(perms))
        assertThat(viewModel.state.value.permissions).isEqualTo(perms)
    }

    @Test
    fun `handleIntent AlterConnectionState updates connection state`() {
        viewModel.handleIntent(ScaleSetupIntent.AlterConnectionState(ConnectionState.Success))
        assertThat(viewModel.state.value.scaleSetupState.setupState.connectionState)
            .isEqualTo(ConnectionState.Success)
    }

    // -------------------------------------------------------------------------
    // ExitSetup — via BLESetupViewmodel
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup with finished true exits without dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()
        verify(exactly = 0) { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ExitSetup with finished false shows confirm dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // OpenHelp — via BLESetupViewmodel
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        advanceScheduler()
        viewModel.handleIntent(ScaleSetupIntent.OpenHelp)
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // init — BLESetupViewmodel calls setSetupInProgress
    // -------------------------------------------------------------------------

    @Test
    fun `init calls deviceService setSetupInProgress true`() {
        verify { deviceService.setSetupInProgress(true) }
    }

    // -------------------------------------------------------------------------
    // onCleared — BLESetupViewmodel cleanup
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
    // currentSetupState tracks step
    // -------------------------------------------------------------------------

    @Test
    fun `currentSetupState initial step matches provideInitialState`() {
        assertThat(viewModel.currentSetupState.step).isEqualTo(BtScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // isPermissionGranted default
    // -------------------------------------------------------------------------

    @Test
    fun `isPermissionGranted defaults to false`() {
        assertThat(viewModel.isPermissionGranted).isFalse()
    }

    @Test
    fun `isPermissionGranted can be set to true`() {
        viewModel.isPermissionGranted = true
        assertThat(viewModel.isPermissionGranted).isTrue()
    }
}
