package com.dmdbrands.gurus.weight.features.addScale.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.addScale.reducer.AddScaleIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class AddScaleViewModelTest {

    companion object {
        private const val TEST_SCALE_ID = "test-scale-id"
        private const val TEST_SKU = "0375"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: AddScaleViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
    }

    private fun createViewModel(): AddScaleViewModel =
        AddScaleViewModel(
            dialogUtility = dialogUtility,
            deviceService = deviceService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty form and no saved scales`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.form.controls.modelNumber.value).isEmpty()
        assertThat(state.isSubmitting).isFalse()
        assertThat(state.selectedSku).isNull()
        assertThat(state.savedScales).isEmpty()
        assertThat(state.scaleId).isNull()
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and updates savedScales`() = runTest(mainDispatcherRule.scheduler) {
        val devices = listOf(
            TestFixtures.aDevice(id = "device-1", deviceType = "bluetooth"),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(devices)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.savedScales).isNotEmpty()
    }

    @Test
    fun `init with empty paired scales keeps savedScales empty`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList<Device>())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.savedScales).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetSavedScales updates savedScales in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val devices = listOf(TestFixtures.bleDevice)
        viewModel.handleIntent(AddScaleIntent.SetSavedScales(devices))
        advanceUntilIdle()

        assertThat(viewModel.state.value.savedScales).isNotEmpty()
    }

    @Test
    fun `OpenSelectedScaleSetup updates selectedSku`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.OpenSelectedScaleSetup(TEST_SKU))
        advanceUntilIdle()

        assertThat(viewModel.state.value.selectedSku).isEqualTo(TEST_SKU)
    }

    @Test
    fun `OpenScaleSettings updates scaleId`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.OpenScaleSettings(TEST_SCALE_ID))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scaleId).isEqualTo(TEST_SCALE_ID)
    }

    @Test
    fun `Submit sets isSubmitting to true`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isSubmitting).isTrue()
    }

    // -------------------------------------------------------------------------
    // Navigation Intents
    // -------------------------------------------------------------------------

    @Test
    fun `OpenScaleChooser navigates to ChooseScale route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.OpenScaleChooser)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(any()) }
    }

    @Test
    fun `OpenScaleSettings navigates to ScaleDetails route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.OpenScaleSettings(TEST_SCALE_ID))
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // ShowHelp
    // -------------------------------------------------------------------------

    @Test
    fun `ShowHelp calls dialogUtility showModelNumberHelpDialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.ShowHelp)
        advanceUntilIdle()

        verify { dialogUtility.showModelNumberHelpDialog() }
    }

    // -------------------------------------------------------------------------
    // Submit — form validation
    // -------------------------------------------------------------------------

    @Test
    fun `Submit with empty model number does not navigate`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.Submit)
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.replaceLastAndNavigate(any()) }
    }

    @Test
    fun `Submit with valid model number navigates to scale setup`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set a valid SKU in the form
        viewModel.state.value.form.controls.modelNumber.onValueChange("0375")

        viewModel.handleIntent(AddScaleIntent.Submit)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // ResetForm
    // -------------------------------------------------------------------------

    @Test
    fun `ResetForm resets model number form value`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.form.controls.modelNumber.onValueChange("1234")
        assertThat(viewModel.state.value.form.controls.modelNumber.value).isEqualTo("1234")

        viewModel.handleIntent(AddScaleIntent.ResetForm)
        advanceUntilIdle()

        assertThat(viewModel.state.value.form.controls.modelNumber.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // OpenSelectedScaleSetup — already paired dialog
    // -------------------------------------------------------------------------

    @Test
    fun `OpenSelectedScaleSetup with unknown SKU does not navigate`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AddScaleIntent.OpenSelectedScaleSetup("9999"))
        advanceUntilIdle()

        coVerify(exactly = 0) { navigationService.replaceLastAndNavigate(any()) }
    }
}
