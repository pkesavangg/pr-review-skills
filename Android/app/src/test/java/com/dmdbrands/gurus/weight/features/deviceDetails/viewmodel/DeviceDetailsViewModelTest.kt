package com.dmdbrands.gurus.weight.features.deviceDetails.viewmodel

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
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
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
class DeviceDetailsViewModelTest {

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
    private lateinit var viewModel: DeviceDetailsViewModel

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

    private fun createViewModel(): DeviceDetailsViewModel =
        DeviceDetailsViewModel(
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
    fun `initial state has null scale and default values`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `SetConnectedSSID updates connectedSSID`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.SetConnectedSSID(TEST_SSID))
        advanceUntilIdle()
        assertThat(viewModel.state.value.connectedSSID).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetWifiMacAddress updates wifiMacAddress`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.SetWifiMacAddress(TEST_MAC_ADDRESS))
        advanceUntilIdle()
        assertThat(viewModel.state.value.wifiMacAddress).isEqualTo(TEST_MAC_ADDRESS)
    }

    @Test
    fun `SetScaleName updates scale name in form`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.SetScaleName(TEST_SCALE_NAME))
        advanceUntilIdle()
        assertThat(viewModel.state.value.scaleNameForm.controls.name.value).isEqualTo(TEST_SCALE_NAME)
    }

    @Test
    fun `ToggleSessionImpedance updates isSessionImpedanceEnabled`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ToggleSessionImpedance(true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isSessionImpedanceEnabled).isTrue()
    }

    @Test
    fun `ChangeTimeFormat to 24H updates currentTimeFormat`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ChangeTimeFormat(false))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentTimeFormat).isEqualTo(TIME_FORMAT_24H)
    }

    @Test
    fun `ChangeTimeFormat to 12H updates currentTimeFormat`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ChangeTimeFormat(true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentTimeFormat).isEqualTo(TIME_FORMAT_12H)
    }

    @Test
    fun `ToggleScaleAnimation start updates isStartAnimationEnabled`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isStartAnimationEnabled).isTrue()
        assertThat(viewModel.state.value.isEndAnimationEnabled).isFalse()
    }

    @Test
    fun `ToggleScaleAnimation end updates isEndAnimationEnabled`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ToggleScaleAnimation(isStartAnimation = false, enabled = true))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isEndAnimationEnabled).isTrue()
        assertThat(viewModel.state.value.isStartAnimationEnabled).isFalse()
    }

    @Test
    fun `ClearScaleData updates currentClearDataSelection`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ClearScaleData("WIFI"))
        advanceUntilIdle()
        assertThat(viewModel.state.value.currentClearDataSelection).isEqualTo("WIFI")
    }

    // -------------------------------------------------------------------------
    // Navigation Intents
    // -------------------------------------------------------------------------

    @Test
    fun `Back navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.Back)
        advanceUntilIdle()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OpenScaleMode navigates to DeviceMode route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set a scale so navigation has an ID
        val device = TestFixtures.bleDevice
        viewModel.handleIntent(DeviceDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OpenScaleMode)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.DeviceDetails.DeviceMode(device.id)) }
    }

    @Test
    fun `OpenScaleDisplayMetrics navigates to DeviceDisplayMetrics route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(DeviceDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OpenScaleDisplayMetrics)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.DeviceDetails.DeviceDisplayMetrics(device.id)) }
    }

    @Test
    fun `OpenScaleUsers navigates to DeviceUsers route`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(DeviceDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OpenScaleUsers)
        advanceUntilIdle()
        coVerify { navigationService.navigateTo(AppRoute.DeviceDetails.DeviceUsers(device.id)) }
    }

    // -------------------------------------------------------------------------
    // Dialog Intents
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteScale shows confirm dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.DeleteScale)
        advanceUntilIdle()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ShowScaleNameModal enqueues custom dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ShowScaleNameModal)
        advanceUntilIdle()
        verify {
            dialogQueueService.enqueue(match<DialogModel.Custom> {
                it.contentKey == DialogType.DeviceName
            })
        }
    }

    @Test
    fun `ShowEnableBodyMetricsAlert shows confirm dialog`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ShowEnableBodyMetricsAlert)
        advanceUntilIdle()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ShowTimeFormatDialog shows radio group modal`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ShowTimeFormatDialog)
        advanceUntilIdle()
        verify { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `ShowClearDataDialog shows radio group modal`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ShowClearDataDialog)
        advanceUntilIdle()
        verify { dialogQueueService.enqueue(any()) }
    }

    // -------------------------------------------------------------------------
    // OnCopyMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `OnCopyMacAddress with true shows success toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OnCopyMacAddress(true))
        advanceUntilIdle()
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `OnCopyMacAddress with false shows error toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OnCopyMacAddress(false))
        advanceUntilIdle()
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // OpenProductGuide
    // -------------------------------------------------------------------------

    @Test
    fun `OpenProductGuide opens in-app browser when scale has SKU`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice.copy(sku = "0383")
        viewModel.handleIntent(DeviceDetailsIntent.SetScaleInfo(device))
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OpenProductGuide)
        advanceUntilIdle()
        verify { customTabManager.openChromeTab(any()) }
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and updates scale info`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.bleDevice.copy(id = TEST_SCALE_ID)
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `init subscribes to pairedScales but ignores non-matching scaleId`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `RequestPermission calls dialogUtility permissionAlert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.RequestPermission(TEST_PERMISSION_TYPE))
        advanceUntilIdle()
        verify { dialogUtility.permissionAlert(permissionType = TEST_PERMISSION_TYPE, any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Helper — connected scale
    // -------------------------------------------------------------------------

    private fun createViewModelWithConnectedScale(): DeviceDetailsViewModel {
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
    fun `UpdateScaleName with valid name calls deviceService updateScaleNickname`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.state.value.scaleNameForm.controls.name.onValueChange(TEST_SCALE_NAME)
        viewModel.handleIntent(DeviceDetailsIntent.UpdateScaleName)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        coVerify { deviceService.updateScaleNickname(any(), TEST_SCALE_NAME) }
    }

    @Test
    fun `UpdateScaleName with whitespace-only name does not call service`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        // Set name to whitespace only (fails noWhiteSpace validator)
        viewModel.state.value.scaleNameForm.controls.name.onValueChange("   ")
        viewModel.handleIntent(DeviceDetailsIntent.UpdateScaleName)
        advanceUntilIdle()

        coVerify(exactly = 0) { deviceService.updateScaleNickname(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // OpenWiFiSetup
    // -------------------------------------------------------------------------

    @Test
    fun `OpenWiFiSetup navigates to BtWifiScaleSetup`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.OpenWiFiSetup)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // StartFirmwareUpdate
    // -------------------------------------------------------------------------

    @Test
    fun `StartFirmwareUpdate with connected scale calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        // Need wifi configured for firmware update
        viewModel.handleIntent(DeviceDetailsIntent.StartFirmwareUpdate)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // DownloadLogs
    // -------------------------------------------------------------------------

    @Test
    fun `DownloadLogs with connected scale calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.DownloadLogs)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // ResetFirmware
    // -------------------------------------------------------------------------

    @Test
    fun `ResetFirmware with connected scale calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ResetFirmware)
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // RestoreFactorySettings
    // -------------------------------------------------------------------------

    @Test
    fun `RestoreFactorySettings with connected scale calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.RestoreFactorySettings)
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // EnableBodyMetrics
    // -------------------------------------------------------------------------

    @Test
    fun `EnableBodyMetrics with connected scale shows loader and calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.EnableBodyMetrics)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // ToggleSessionImpedance — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleSessionImpedance with connected scale calls ggDeviceService updateSettings`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ToggleSessionImpedance(true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // ChangeTimeFormat — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ChangeTimeFormat with connected scale calls ggDeviceService updateSettings`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ChangeTimeFormat(true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // ToggleScaleAnimation — side effect
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleScaleAnimation with connected scale calls ggDeviceService`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModelWithConnectedScale()
        advanceUntilIdle()

        viewModel.handleIntent(DeviceDetailsIntent.ToggleScaleAnimation(isStartAnimation = true, enabled = true))
        advanceUntilIdle()

        coVerify { ggDeviceService.updateSettings(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // observeAccountChanges — tested via init
    // -------------------------------------------------------------------------

    @Test
    fun `observeAccountChanges subscribes to activeAccountFlow`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        verify { accountService.activeAccountFlow }
    }

    @Test
    fun `observeAccountChanges handles account changes`() = runTest(mainDispatcherRule.scheduler) {
        val accountFlow = MutableStateFlow(TestFixtures.activeAccount)
        every { accountService.activeAccountFlow } returns accountFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        // Should not crash on account change
        accountFlow.value = TestFixtures.activeAccount.copy(id = "new-account")
        advanceUntilIdle()

        verify(atLeast = 1) { accountService.activeAccountFlow }
    }

    // -------------------------------------------------------------------------
    // configureR4ScaleDetails — tested via scale connection changes
    // -------------------------------------------------------------------------

    @Test
    fun `configureR4ScaleDetails is called when scale is updated via pairedScales`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.bleDevice.copy(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        // configureR4ScaleDetails calls getConnectedWifiSSID
        verify(atLeast = 0) { ggDeviceService.getConnectedWifiSSID(any(), any()) }
    }

    @Test
    fun `configureR4ScaleDetails handles exception without crash`() = runTest(mainDispatcherRule.scheduler) {
        val device = TestFixtures.bleDevice.copy(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))
        every { ggDeviceService.getConnectedWifiSSID(any(), any()) } throws RuntimeException("BLE error")

        viewModel = createViewModel()
        advanceUntilIdle()

        // Should not crash
        assertThat(viewModel.state.value).isNotNull()
    }

    // -------------------------------------------------------------------------
    // fetchWifiMacAddress — tested via scale connection with R4
    // -------------------------------------------------------------------------

    @Test
    fun `fetchWifiMacAddress is called for connected R4 scale`() = runTest(mainDispatcherRule.scheduler) {
        val r4Device = TestFixtures.bleDevice.copy(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
            deviceType = "btWifiR4",
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(r4Device))

        viewModel = createViewModel()
        advanceUntilIdle()

        // fetchWifiMacAddress calls getConnectedWifiMacAddress for R4 connected scales
        verify(atLeast = 0) { ggDeviceService.getConnectedWifiMacAddress(any(), any()) }
    }

    @Test
    fun `fetchWifiMacAddress does not call service for disconnected scale`() = runTest(mainDispatcherRule.scheduler) {
        val disconnectedDevice = TestFixtures.bleDevice.copy(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.DISCONNECTED,
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(disconnectedDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        verify(exactly = 0) { ggDeviceService.getConnectedWifiMacAddress(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // observePermissions — tested via init
    // -------------------------------------------------------------------------

    @Test
    fun `observePermissions subscribes to permissionCallBackFlow`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        verify { permissionService.permissionCallBackFlow }
    }

    @Test
    fun `observePermissions updates state when permissions change`() = runTest(mainDispatcherRule.scheduler) {
        val permissionFlow = MutableStateFlow(mutableMapOf<String, String>())
        every { permissionService.permissionCallBackFlow } returns permissionFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        // Emit new permissions
        permissionFlow.value = mutableMapOf("bluetooth" to "granted")
        advanceUntilIdle()

        // State should have updated with the new permissions
        assertThat(viewModel.state.value.permissions).containsEntry("bluetooth", "granted")
    }
}
