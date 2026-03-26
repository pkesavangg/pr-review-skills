package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.AppsyncScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.AppsyncScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGPermissionService
import com.greatergoods.libs.appsync.model.AppSyncResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
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
 * Unit tests for [AppsyncScaleSetupViewModel].
 *
 * Uses [StandardTestDispatcher] so init-block coroutines are scheduled but don't
 * execute eagerly. Pure reducer intents work without advancing time.
 *
 * No `runTest` — it hangs because `runTest` cleanup waits for infinite coroutines
 * (state.collect, permission observation) to finish, which never happens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsyncScaleSetupViewModelTest {

    companion object {
        /** SKU "0341" is a body-comp AppSync scale in the SCALES list */
        private const val TEST_SKU_BODY_COMP = "0341"
        /** SKU "0342" is a non-body-comp AppSync scale in the SCALES list */
        private const val TEST_SKU_BASIC = "0342"
        /** SKU not present in the SCALES list */
        private const val TEST_SKU_UNKNOWN = "9999"
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var viewModel: AppsyncScaleSetupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf())
    }

    @AfterEach
    fun tearDown() {
        if (::viewModel.isInitialized) {
            val method = viewModel::class.java.getDeclaredMethod("onCleared")
            method.isAccessible = true
            method.invoke(viewModel)
        }
    }

    private fun advanceScheduler() {
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.runCurrent()
    }

    private fun createViewModel(sku: String = TEST_SKU_BODY_COMP): AppsyncScaleSetupViewModel {
        viewModel = AppsyncScaleSetupViewModel(
            permissionService = permissionService,
            dialogUtility = dialogUtility,
            deviceService = deviceService,
            sku = sku,
        ).initTestDependencies()
        return viewModel
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @Test
    fun `init sets SCALE_INFO as initial step`() {
        createViewModel()
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `init sets sku in state from constructor param`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.sku).isEqualTo(TEST_SKU_BODY_COMP)
    }

    @Test
    fun `init calls deviceService setSetupInProgress true`() {
        createViewModel()
        verify { deviceService.setSetupInProgress(true) }
    }

    @Test
    fun `init with body-comp sku sets bodyComp true and generates full steps`() {
        createViewModel(TEST_SKU_BODY_COMP)
        advanceScheduler()
        val state = viewModel.state.value
        assertThat(state.bodyComp).isTrue()
        assertThat(state.steps).contains(AppsyncScaleSetupStep.ADD_INFO)
        assertThat(state.steps).hasSize(7)
    }

    @Test
    fun `init with basic sku sets bodyComp false and generates steps without ADD_INFO`() {
        createViewModel(TEST_SKU_BASIC)
        advanceScheduler()
        val state = viewModel.state.value
        assertThat(state.bodyComp).isFalse()
        assertThat(state.steps).doesNotContain(AppsyncScaleSetupStep.ADD_INFO)
        assertThat(state.steps).hasSize(6)
    }

    @Test
    fun `init with unknown sku defaults bodyComp to true`() {
        createViewModel(TEST_SKU_UNKNOWN)
        advanceScheduler()
        assertThat(viewModel.state.value.bodyComp).isTrue()
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        createViewModel()
        val state = viewModel.state.value
        assertThat(state.isNextEnabled).isTrue()
        assertThat(state.error).isNull()
        assertThat(state.isSetupFinished).isFalse()
        assertThat(state.scanResult).isNull()
    }

    @Test
    fun `initial state isFirstStep is true`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.isFirstStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleSku updates sku`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetScaleSku("0999"))
        assertThat(viewModel.state.value.sku).isEqualTo("0999")
    }

    @Test
    fun `SetBodyComp updates bodyComp and regenerates steps`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetBodyComp(false))
        val state = viewModel.state.value
        assertThat(state.bodyComp).isFalse()
        assertThat(state.steps).doesNotContain(AppsyncScaleSetupStep.ADD_INFO)
    }

    @Test
    fun `SetCurrentStep updates current step`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.PERMISSIONS))
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `SetNextButtonState updates isNextEnabled`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetNextButtonState(false))
        assertThat(viewModel.state.value.isNextEnabled).isFalse()
    }

    @Test
    fun `SetError updates error`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetError("test error"))
        assertThat(viewModel.state.value.error).isEqualTo("test error")
    }

    @Test
    fun `SetError with null clears error`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetError("error"))
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetError(null))
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `SetPermissions updates permissions`() {
        createViewModel()
        val permissions = mutableMapOf("CAMERA" to "ENABLED")
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetPermissions(permissions))
        assertThat(viewModel.state.value.permissions).isEqualTo(permissions)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — Next
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO advances to PERMISSIONS`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `Next from non-PERMISSIONS step advances via reducer`() {
        createViewModel()
        advanceScheduler()
        // Move to ACTIVATE_SCALE
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.ACTIVATE_SCALE))
        advanceScheduler()
        // Next should advance
        viewModel.handleIntent(AppsyncScaleSetupIntent.Next)
        advanceScheduler()
        val step = viewModel.state.value.currentStep
        // After ACTIVATE_SCALE, body-comp should go to ADD_INFO
        assertThat(step).isEqualTo(AppsyncScaleSetupStep.ADD_INFO)
    }

    // -------------------------------------------------------------------------
    // Step Navigation — Back
    // -------------------------------------------------------------------------

    @Test
    fun `Back from SCALE_INFO stays on SCALE_INFO`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `Back from PERMISSIONS returns to SCALE_INFO`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.PERMISSIONS))
        viewModel.handleIntent(AppsyncScaleSetupIntent.Back)
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup with isSetupFinished false shows confirm dialog`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ExitSetup updates isSetupFinished in state`() {
        createViewModel()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = true))
        assertThat(viewModel.state.value.isSetupFinished).isTrue()
    }

    @Test
    fun `ExitSetup clears setup in progress`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        // It should call setSetupInProgress(false) — once in init (true) and once in exit (false)
        verify { deviceService.setSetupInProgress(false) }
    }

    @Test
    fun `ExitSetup when finished and at last step saves scale`() {
        createViewModel()
        advanceScheduler()
        // Move to last step
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.SETUP_FINISHED))
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()
        // Should show loader and attempt to save
        verify { viewModel.dialogQueueService.showLoader(any<String>()) }
    }

    // -------------------------------------------------------------------------
    // ExitSetup — blank SKU early return
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup at last step with blank sku does not show loader`() {
        createViewModel()
        advanceScheduler()
        // Set blank sku
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetScaleSku(""))
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.SETUP_FINISHED))
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()
        // checkAndSaveScale should early return — no loader shown
        verify(exactly = 0) { viewModel.dialogQueueService.showLoader(any<String>()) }
    }

    // -------------------------------------------------------------------------
    // OpenHelp
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.OpenHelp)
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // HandleAppSyncResult
    // -------------------------------------------------------------------------

    @Test
    fun `HandleAppSyncResult with canceled true advances step`() {
        createViewModel()
        advanceScheduler()
        val startStep = viewModel.state.value.currentStep
        val result = AppSyncResult(
            weight = null, fat = null, muscle = null, water = null, mode = null,
            canceled = true, manual = false,
        )
        viewModel.handleIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        advanceScheduler()
        // Should advance from current step because canceled=true triggers Next
        val endStep = viewModel.state.value.currentStep
        assertThat(endStep).isNotEqualTo(startStep)
    }

    @Test
    fun `HandleAppSyncResult with manual false and not canceled advances step`() {
        createViewModel()
        advanceScheduler()
        val result = AppSyncResult(
            weight = 75.0f, fat = 20.0f, muscle = null, water = null, mode = "kg",
            canceled = false, manual = false,
        )
        val startStep = viewModel.state.value.currentStep
        viewModel.handleIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isNotEqualTo(startStep)
    }

    @Test
    fun `HandleAppSyncResult with manual true does not advance step`() {
        createViewModel()
        advanceScheduler()
        // Move to OPEN_CAMERA step
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.OPEN_CAMERA))
        advanceScheduler()
        val result = AppSyncResult(
            weight = null, fat = null, muscle = null, water = null, mode = null,
            canceled = false, manual = true,
        )
        viewModel.handleIntent(AppsyncScaleSetupIntent.HandleAppSyncResult(result))
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(AppsyncScaleSetupStep.OPEN_CAMERA)
    }

    // -------------------------------------------------------------------------
    // Save Scale — already paired scale gets deleted first
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup at last step with already paired scale deletes old scale before saving`() {
        val pairedDevice = Device(sku = TEST_SKU_BODY_COMP)
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice))
        coEvery { deviceService.saveScale(any()) } returns Device(sku = TEST_SKU_BODY_COMP)

        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.SETUP_FINISHED))
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.ExitSetup(isSetupFinished = true))
        advanceScheduler()

        coVerify { deviceService.deleteScale(pairedDevice.id) }
    }

    // -------------------------------------------------------------------------
    // onCleared
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared sets setup in progress false`() {
        createViewModel()
        advanceScheduler()
        val method = viewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        verify { deviceService.setSetupInProgress(false) }
    }

    // -------------------------------------------------------------------------
    // isLastStep / isFirstStep
    // -------------------------------------------------------------------------

    @Test
    fun `isLastStep is true at SETUP_FINISHED`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.SetCurrentStep(AppsyncScaleSetupStep.SETUP_FINISHED))
        assertThat(viewModel.state.value.isLastStep).isTrue()
    }

    @Test
    fun `isLastStep is false at SCALE_INFO`() {
        createViewModel()
        advanceScheduler()
        assertThat(viewModel.state.value.isLastStep).isFalse()
    }

    @Test
    fun `isFirstStep is false after advancing`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.isFirstStep).isFalse()
    }

    // -------------------------------------------------------------------------
    // RequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission calls dialogUtility permissionAlert`() {
        createViewModel()
        advanceScheduler()
        viewModel.handleIntent(AppsyncScaleSetupIntent.RequestPermission("CAMERA"))
        advanceScheduler()
        verify { dialogUtility.permissionAlert(permissionType = "CAMERA", onRequest = any()) }
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

        val newPermissions = mutableMapOf("CAMERA" to "ENABLED")
        permissionsFlow.value = newPermissions
        advanceScheduler()

        assertThat(viewModel.state.value.permissions).isEqualTo(newPermissions)
    }
}
