package com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.scaleDisplayMetrics.reducer.ScaleDisplayMetricsIntent
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
class ScaleDisplayMetricsViewModelTest {

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
    private lateinit var viewModel: ScaleDisplayMetricsViewModel

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

    private fun createViewModel(): ScaleDisplayMetricsViewModel =
        ScaleDisplayMetricsViewModel(
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
    fun `initial state has null scale and empty metrics`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.scale).isNull()
        assertThat(state.enabledMetrics).isEmpty()
        assertThat(state.hasUpdated).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and sets scale for matching id`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight", "bodyFat")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
        assertThat(viewModel.state.value.enabledMetrics).containsExactly("weight", "bodyFat")
    }

    @Test
    fun `init ignores devices with non-matching scaleId`() = runTest {
        val otherDevice = TestFixtures.aDevice(id = "other-id")
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(otherDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isNull()
    }

    @Test
    fun `init with scale having null displayMetrics sets empty enabledMetrics`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = null),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.enabledMetrics).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale updates scale and enabledMetrics`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight", "bmi")),
        )
        viewModel.handleIntent(ScaleDisplayMetricsIntent.SetScale(device))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
        assertThat(viewModel.state.value.enabledMetrics).containsExactly("weight", "bmi")
        assertThat(viewModel.state.value.hasUpdated).isFalse()
    }

    @Test
    fun `UpdateMetrics updates enabledMetrics and sets hasUpdated when changed`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.UpdateMetrics(listOf("weight", "bodyFat")))
        advanceUntilIdle()

        assertThat(viewModel.state.value.enabledMetrics).containsExactly("weight", "bodyFat")
        assertThat(viewModel.state.value.hasUpdated).isTrue()
    }

    @Test
    fun `UpdateMetrics with same metrics as original sets hasUpdated false`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.UpdateMetrics(listOf("weight")))
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasUpdated).isFalse()
    }

    // -------------------------------------------------------------------------
    // Back — navigation
    // -------------------------------------------------------------------------

    @Test
    fun `Back with no updates navigates back directly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Back)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `Back with unsaved updates shows confirm dialog`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Make a change so hasUpdated becomes true
        viewModel.handleIntent(ScaleDisplayMetricsIntent.UpdateMetrics(listOf("weight", "bodyFat")))
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Back)
        advanceUntilIdle()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // Save — saveDisplayMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `Save with null scale shows error toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save with valid scale shows loader and calls deviceService`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        coVerify { deviceService.updateScalePreferences(TEST_SCALE_ID, any()) }
    }

    @Test
    fun `Save with connected scale calls ggDeviceService updateAccount`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.CONNECTED,
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify { ggDeviceService.updateAccount(any(), any()) }
    }

    @Test
    fun `Save with disconnected scale skips ggDeviceService updateAccount`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify(exactly = 0) { ggDeviceService.updateAccount(any(), any()) }
    }

    @Test
    fun `Save with successful API update dismisses loader and shows success toast`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save with failed API update shows error toast`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            connectionStatus = BLEStatus.DISCONNECTED,
            preferences = Preferences(displayMetrics = listOf("weight")),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // UpdateScaleMode — navigation
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateScaleMode navigates to ScaleMode route`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDisplayMetricsIntent.UpdateScaleMode)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(TEST_SCALE_ID)) }
    }
}
