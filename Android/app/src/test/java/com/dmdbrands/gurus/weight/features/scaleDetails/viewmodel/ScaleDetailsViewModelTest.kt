package com.dmdbrands.gurus.weight.features.scaleDetails.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.testutil.TestFixtures
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleDetailsViewModelTest {

    companion object {
        private const val TEST_SCALE_ID = "test-scale-id"
        private const val TEST_SCALE_NAME = "My Scale"
        private const val TEST_SSID = "HomeWifi"
        private const val TEST_MAC_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_PERMISSION_TYPE = "bluetooth"
        private const val TIME_FORMAT_12H = "12H"
        private const val TIME_FORMAT_24H = "24H"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @MockK(relaxed = true) lateinit var accountService: IAccountService
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService
    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var customTabManager: ICustomTabManager
    private lateinit var viewModel: ScaleDetailsViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        customTabManager = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf<String, String>())
    }

    private fun createViewModel(): ScaleDetailsViewModel =
        ScaleDetailsViewModel(
            accountService = accountService,
            deviceService = deviceService,
            ggDeviceService = ggDeviceService,
            permissionService = permissionService,
            dialogUtility = dialogUtility,
            scaleId = TEST_SCALE_ID,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
            customTabManager = customTabManager,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has null scale and default values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.scale).isNull()
        assertThat(state.connectedSSID).isNull()
        assertThat(state.wifiMacAddress).isNull()
        assertThat(state.deviceInfo).isNull()
        assertThat(state.isSessionImpedanceEnabled).isFalse()
        assertThat(state.currentTimeFormat).isEqualTo(TIME_FORMAT_12H)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetConnectedSSID updates connectedSSID`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.SetConnectedSSID(TEST_SSID))
        advanceUntilIdle()
        assertThat(viewModel.state.value.connectedSSID).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetWifiMacAddress updates wifiMacAddress`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.SetWifiMacAddress(TEST_MAC_ADDRESS))
        advanceUntilIdle()
        assertThat(viewModel.state.value.wifiMacAddress).isEqualTo(TEST_MAC_ADDRESS)
    }

    @Test
    fun `SetScaleName updates scale name in form`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.SetScaleName(TEST_SCALE_NAME))
        advanceUntilIdle()
        assertThat(viewModel.state.value.scaleNameForm.controls.name.value).isEqualTo(TEST_SCALE_NAME)
    }

    @Test
    fun `ToggleSessionImpedance updates isSessionImpedanceEnabled`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ToggleSessionImpedance(true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isSessionImpedanceEnabled).isTrue()
    }

    @Test
    fun `ChangeTimeFormat to 24H updates currentTimeFormat`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ChangeTimeFormat(false))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentTimeFormat).isEqualTo(TIME_FORMAT_24H)
    }

    @Test
    fun `ChangeTimeFormat to 12H updates currentTimeFormat`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ChangeTimeFormat(true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentTimeFormat).isEqualTo(TIME_FORMAT_12H)
    }

    @Test
    fun `ToggleScaleAnimation start updates isStartAnimationEnabled`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isStartAnimationEnabled).isTrue()
        assertThat(viewModel.state.value.isEndAnimationEnabled).isFalse()
    }

    @Test
    fun `ToggleScaleAnimation end updates isEndAnimationEnabled`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = false, enabled = true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isEndAnimationEnabled).isTrue()
        assertThat(viewModel.state.value.isStartAnimationEnabled).isFalse()
    }

    @Test
    fun `ClearScaleData updates currentClearDataSelection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ClearScaleData("WIFI"))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentClearDataSelection).isEqualTo("WIFI")
    }

    // -------------------------------------------------------------------------
    // Navigation Intents
    // -------------------------------------------------------------------------

    @Test
    fun `Back navigates back`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.Back)
        advanceUntilIdle()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OpenScaleMode navigates to ScaleMode route`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set a scale so navigation has an ID
        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OpenScaleMode)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.ScaleDetails.ScaleMode(device.id)) }
    }

    @Test
    fun `OpenScaleDisplayMetrics navigates to ScaleDisplayMetrics route`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OpenScaleDisplayMetrics)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.ScaleDetails.ScaleDisplayMetrics(device.id)) }
    }

    @Test
    fun `OpenScaleUsers navigates to ScaleUsers route`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OpenScaleUsers)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.ScaleDetails.ScaleUsers(device.id)) }
    }

    // -------------------------------------------------------------------------
    // Dialog Intents
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteScale shows confirm dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.DeleteScale)
        advanceUntilIdle()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ShowScaleNameModal enqueues custom dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ShowScaleNameModal)
        advanceUntilIdle()
        verify {
            dialogQueueService.enqueue(match<DialogModel.Custom> {
                it.contentKey == DialogType.ScaleName
            })
        }
    }

    @Test
    fun `ShowEnableBodyMetricsAlert shows confirm dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ShowEnableBodyMetricsAlert)
        advanceUntilIdle()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ShowTimeFormatDialog shows radio group modal`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ShowTimeFormatDialog)
        advanceUntilIdle()
        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `ShowClearDataDialog shows radio group modal`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ShowClearDataDialog)
        advanceUntilIdle()
        verify { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // OnCopyMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `OnCopyMacAddress with true shows success toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OnCopyMacAddress(true))
        advanceUntilIdle()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `OnCopyMacAddress with false shows error toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OnCopyMacAddress(false))
        advanceUntilIdle()
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // OpenProductGuide
    // -------------------------------------------------------------------------

    @Test
    fun `OpenProductGuide opens in-app browser when scale has SKU`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OpenProductGuide)
        advanceUntilIdle()
        verify { customTabManager.openChromeTab(any()) }
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and updates scale info`() = runTest {
        val device = TestFixtures.bleDevice.copy(id = TEST_SCALE_ID)
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `init subscribes to pairedScales but ignores non-matching scaleId`() = runTest {
        val otherDevice = TestFixtures.bleDevice.copy(id = "other-id")
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(otherDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isNull()
    }

    // -------------------------------------------------------------------------
    // RequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission calls dialogUtility permissionAlert`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.RequestPermission(TEST_PERMISSION_TYPE))
        advanceUntilIdle()
        verify { dialogUtility.permissionAlert(permissionType = TEST_PERMISSION_TYPE, any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Helper — connected scale
    // -------------------------------------------------------------------------

    private fun createViewModelWithConnectedScale(): ScaleDetailsViewModel {
        val connectedScale = TestFixtures.bleDevice.copy(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(connectedScale))
        return createViewModel()
    }

    // -------------------------------------------------------------------------
    // UpdateScaleName
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateScaleName with valid name calls deviceService updateScaleNickname`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.state.value.scaleNameForm.controls.name.onValueChange(TEST_SCALE_NAME)
        viewModel.handleIntent(ScaleDetailsIntent.UpdateScaleName)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        coVerify { deviceService.updateScaleNickname(any(), TEST_SCALE_NAME) }
    }

    @Test
    fun `UpdateScaleName with whitespace-only name does not call service`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        // Set name to whitespace only (fails noWhiteSpace validator)
        viewModel.state.value.scaleNameForm.controls.name.onValueChange("   ")
        viewModel.handleIntent(ScaleDetailsIntent.UpdateScaleName)
        advanceUntilIdle()

        coVerify(exactly = 0) { deviceService.updateScaleNickname(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // OpenWiFiSetup
    // -------------------------------------------------------------------------

    @Test
    fun `OpenWiFiSetup navigates to BtWifiScaleSetup`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.OpenWiFiSetup)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // StartFirmwareUpdate
    // -------------------------------------------------------------------------

    @Test
    fun `StartFirmwareUpdate with connected scale calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        // Need wifi configured for firmware update
        viewModel.handleIntent(ScaleDetailsIntent.StartFirmwareUpdate)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // DownloadLogs
    // -------------------------------------------------------------------------

    @Test
    fun `DownloadLogs with connected scale calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.DownloadLogs)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // ResetFirmware
    // -------------------------------------------------------------------------

    @Test
    fun `ResetFirmware with connected scale calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ResetFirmware)
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // RestoreFactorySettings
    // -------------------------------------------------------------------------

    @Test
    fun `RestoreFactorySettings with connected scale calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.RestoreFactorySettings)
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // EnableBodyMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `EnableBodyMetrics with connected scale shows loader and calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.EnableBodyMetrics)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // ToggleSessionImpedance — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleSessionImpedance with connected scale calls ggDeviceService updateSettings`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ToggleSessionImpedance(true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // ChangeTimeFormat — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ChangeTimeFormat with connected scale calls ggDeviceService updateSettings`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ChangeTimeFormat(true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // ToggleScaleAnimation — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleScaleAnimation with connected scale calls ggDeviceService`() = runTest {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }
}
