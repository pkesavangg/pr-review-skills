package com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BabyScaleSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for the SKIP flow in [BabyScaleBLESetupViewModel] (MOB-440):
 * skipping on the baby-profile FORM confirms via the "Skip Baby Profile?" dialog and FINISH SETUP
 * exits; all other steps (including PAIRED_SUCCESS, which no longer shows a SKIP button per design)
 * fall through to onNext.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BabyScaleBLESetupSkipFlowTest {

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
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: BabyScaleBLESetupViewModel

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
        dialogQueueService = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
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

    /**
     * Creates the VM on SCALE_INFO and lets the initial (empty) permission emission settle —
     * the permission observer only force-navigates to PERMISSIONS when the step is past
     * SCALE_INFO, so settling here keeps a later [stepTo] stable.
     */
    private fun createViewModel(): BabyScaleBLESetupViewModel {
        val setupInit = SetupInitData(
            sku = TEST_SKU,
            initialStep = BabyScaleSetupStep.SCALE_INFO,
            scaleInfo = null,
            broadcastId = null,
        )
        viewModel = BabyScaleBLESetupViewModel(
            setupInit = setupInit,
            dependencies = dependencies,
            babyProfileService = mockk(relaxed = true),
            accountRepository = mockk(relaxed = true),
        ).initTestDependencies(dialogQueueService = dialogQueueService)
        advanceScheduler()
        return viewModel
    }

    private fun stepTo(step: BabyScaleSetupStep) {
        viewModel.handleIntent(DeviceSetupIntent.SetNewStep(step))
        advanceScheduler()
        assertThat(viewModel.state.value.step).isEqualTo(step)
    }

    @Test
    fun `Skip on PAIRED_SUCCESS does not show the confirm dialog`() {
        createViewModel()
        stepTo(BabyScaleSetupStep.PAIRED_SUCCESS)

        viewModel.handleIntent(DeviceSetupIntent.Skip)
        advanceScheduler()

        // PAIRED_SUCCESS no longer has a SKIP button (design); skip falls through to onNext.
        verify(exactly = 0) { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `Skip on BABY_PROFILE_FORM enqueues the skip-baby-profile confirm dialog`() {
        createViewModel()
        stepTo(BabyScaleSetupStep.BABY_PROFILE_FORM)

        viewModel.handleIntent(DeviceSetupIntent.Skip)
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `Skip on a non baby-profile step does not show the confirm dialog`() {
        createViewModel()
        stepTo(BabyScaleSetupStep.SCALE_NAME)

        viewModel.handleIntent(DeviceSetupIntent.Skip)
        advanceScheduler()

        // SCALE_NAME falls through to onNext — no skip-confirmation dialog.
        verify(exactly = 0) { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `confirming the skip dialog finishes setup`() {
        createViewModel()
        stepTo(BabyScaleSetupStep.BABY_PROFILE_FORM)

        val dialogSlot = slot<DialogModel.Confirm>()
        viewModel.handleIntent(DeviceSetupIntent.Skip)
        advanceScheduler()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }

        // Tap "FINISH SETUP" → ExitSetup(true) → onExit clears setup-in-progress.
        dialogSlot.captured.onConfirm?.invoke()
        advanceScheduler()

        verify { deviceService.setSetupInProgress(false) }
    }

    @Test
    fun `finishing setup shows then dismisses the saving loader`() {
        createViewModel()
        stepTo(BabyScaleSetupStep.SETUP_FINISHED)

        // FINISH on the "You're Done!" screen → ExitSetup(true) → onSetupFinished wraps the
        // save in a loader so the screen isn't interactive before navigating back.
        viewModel.handleIntent(DeviceSetupIntent.ExitSetup(true))
        advanceScheduler()

        verify { dialogQueueService.showLoader(any(), any()) }
        verify { dialogQueueService.dismissLoader() }
    }
}
