package com.dmdbrands.gurus.weight.features.deviceMode.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.deviceMode.reducer.DeviceModeIntent
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
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
class DeviceModeViewModelTest {

    companion object {
        private const val TEST_SCALE_ID = "test-scale-id"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @MockK(relaxed = true) lateinit var deviceService: IDeviceService
    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: DeviceModeViewModel

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

    private fun createViewModel(): DeviceModeViewModel =
        DeviceModeViewModel(
            deviceService = deviceService,
            ggDeviceService = ggDeviceService,
            scaleId = TEST_SCALE_ID,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has null scale and default mode values`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.scale).isNull()
        assertThat(state.isAllBodyMetrics).isTrue()
        assertThat(state.isHeartRateOn).isFalse()
        assertThat(state.hasModeChanged).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and sets scale for matching id`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = true,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `init sets body metrics mode from scale preferences`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isAllBodyMetrics).isTrue()
        assertThat(viewModel.state.value.isHeartRateOn).isFalse()
    }

    @Test
    fun `init sets weight only mode when impedance is off`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(
                shouldMeasureImpedance = false,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isAllBodyMetrics).isFalse()
    }

    @Test
    fun `init ignores devices with non-matching scaleId`() = runTest(mainDispatcherRule.scheduler) {
        val otherDevice = TestFixtures.aDevice(id = "other-id")
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(otherDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isNull()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale updates scale in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(DeviceModeIntent.SetScale(device))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `SetMode updates isAllBodyMetrics and hasModeChanged`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true))
        advanceUntilIdle()

        assertThat(viewModel.state.value.isAllBodyMetrics).isFalse()
        assertThat(viewModel.state.value.hasModeChanged).isTrue()
    }

    @Test
    fun `SetHeartRate updates isHeartRateOn and hasModeChanged`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.SetHeartRate(isHeartRateOn = true, hasModeChanged = true))
        advanceUntilIdle()

        assertThat(viewModel.state.value.isHeartRateOn).isTrue()
        assertThat(viewModel.state.value.hasModeChanged).isTrue()
    }

    // -------------------------------------------------------------------------
    // Back — navigation
    // -------------------------------------------------------------------------

    @Test
    fun `Back with no mode changes navigates back directly`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Back)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `Back with unsaved mode changes shows confirm dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.SetMode(isAllBodyMetrics = false, hasModeChanged = true))
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Back)
        advanceUntilIdle()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // OpenBiaModal
    // -------------------------------------------------------------------------

    @Test
    fun `OpenBiaModal enqueues custom BIA modal dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.OpenBiaModal)
        advanceUntilIdle()

        verify {
            dialogQueueService.enqueue(match<DialogModel.Custom> {
                it.contentKey == DialogType.BiaModal
            })
        }
    }

    // -------------------------------------------------------------------------
    // Save — saveModeSettings
    // -------------------------------------------------------------------------

    @Test
    fun `Save with null scale shows error toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save shows loader`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
    }

    @Test
    fun `Save with valid scale calls deviceService updateScalePreferences`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        coVerify { deviceService.updateScalePreferences(TEST_SCALE_ID, any()) }
    }

    @Test
    fun `Save with connected scale calls ggDeviceService updateAccount`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.CONNECTED,
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify { ggDeviceService.updateAccount(any(), any()) }
    }

    @Test
    fun `Save with disconnected scale skips ggDeviceService updateAccount`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify(exactly = 0) { ggDeviceService.updateAccount(any(), any()) }
    }

    @Test
    fun `Save with successful API update dismisses loader and navigates back`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
        coVerify { navigationService.navigateBack() }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save with failed API update shows error toast`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(
                shouldMeasureImpedance = true,
                shouldMeasurePulse = false,
            ),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceModeIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
    }
}
