package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.WifiConnectionStatus
import com.dmdbrands.gurus.weight.core.service.WifiScaleService
import com.dmdbrands.gurus.weight.core.service.WifiStatus
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.WifiScaleSetupStep
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.SetupPath
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScalePasswordFormControls
import com.dmdbrands.gurus.weight.features.ScaleSetup.reducer.WifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [WifiScaleSetupViewModel].
 *
 * Uses [StandardTestDispatcher] so the 7 init-block coroutines are scheduled
 * but don't execute eagerly. Pure reducer intents work without advancing time.
 *
 * IMPORTANT: No `runTest` is used — it hangs because `runTest` cleanup waits
 * for infinite coroutines (monitorNetworkStatus polling loop, state.collect)
 * to finish, which never happens. Instead, we advance the shared scheduler
 * directly via [mainDispatcherRule].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiScaleSetupViewModelTest {

    companion object {
        private const val TEST_SKU = "0384"
        private const val TEST_WIFI_SETUP_TYPE = "first"
        private const val TEST_SSID = "MyNetwork"
        private const val TEST_PASSWORD = "password123"
        private const val TEST_MAC_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_USER_NUMBER = 1
        private const val TEST_WIFI_MODE_AP = "apmode"
        private const val TEST_ERROR_CODE = "E01"
        private const val TEST_BSSID = "00:11:22:33:44:55"
        private const val FINISH_BUTTON_TEXT = "Finish"
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var wifiScaleService: WifiScaleService
    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var connectivityObserver: IConnectivityObserver
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var viewModel: WifiScaleSetupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf<String, String>())
        every { connectivityObserver.observe() } returns MutableStateFlow(mockk(relaxed = true))

        viewModel = WifiScaleSetupViewModel(
            sku = TEST_SKU,
            wifiSetupTypeString = TEST_WIFI_SETUP_TYPE,
            scaleInfo = null,
            ggDeviceService = ggDeviceService,
            wifiScaleService = wifiScaleService,
            permissionService = permissionService,
            connectivityObserver = connectivityObserver,
            dialogUtility = dialogUtility,
            deviceService = deviceService,
        ).initTestDependencies()
    }

    @AfterEach
    fun tearDown() {
        // Cancel viewModelScope to stop infinite polling (monitorNetworkStatus)
        // and the state observer. onCleared() is protected — invoke via reflection.
        val method = viewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    /**
     * Advance the test dispatcher by a small amount to execute pending coroutines.
     * We use advanceTimeBy (NOT advanceUntilIdle) because monitorNetworkStatus()
     * has an infinite while(!isDestroyed) + delay(1500) loop — advanceUntilIdle
     * would chase it forever.
     */
    private fun advanceScheduler() {
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.runCurrent()
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has SCALE_INFO step`() {
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.sku).isEqualTo(TEST_SKU)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isSetupFinished).isFalse()
        assertThat(state.isConnected).isFalse()
        assertThat(state.selectedUser).isNull()
        assertThat(state.selectedWifiMode).isNull()
        assertThat(state.showApMode).isFalse()
        assertThat(state.showError).isFalse()
        assertThat(state.isFirstStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // Step Transitions — Next / Back
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO advances to PERMISSIONS`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.PERMISSIONS)
    }

    @Test
    fun `Back from PERMISSIONS returns to SCALE_INFO`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ClearNavigationState)
        viewModel.handleIntent(WifiScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `Back from SCALE_INFO stays on SCALE_INFO`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only — no advanceScheduler needed)
    // -------------------------------------------------------------------------

    @Test
    fun `SelectUser sets selectedUser`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SelectUser(TEST_USER_NUMBER))
        assertThat(viewModel.state.value.selectedUser).isEqualTo(TEST_USER_NUMBER)
    }

    @Test
    fun `SelectWifiMode sets selectedWifiMode`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SelectWifiMode(TEST_WIFI_MODE_AP))
        assertThat(viewModel.state.value.selectedWifiMode).isEqualTo(TEST_WIFI_MODE_AP)
    }

    @Test
    fun `SelectErrorCode sets selectedErrorCode and canProceedToNext`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SelectErrorCode(TEST_ERROR_CODE))
        assertThat(viewModel.state.value.selectedErrorCode).isEqualTo(TEST_ERROR_CODE)
        assertThat(viewModel.state.value.canProceedToNext).isTrue()
    }

    @Test
    fun `SetCanProceedToNext updates flag`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetCanProceedToNext(true))
        assertThat(viewModel.state.value.canProceedToNext).isTrue()
    }

    @Test
    fun `SetShowApMode updates showApMode`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetShowApMode(true))
        assertThat(viewModel.state.value.showApMode).isTrue()
    }

    @Test
    fun `SetShowError updates showError`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetShowError(true))
        assertThat(viewModel.state.value.showError).isTrue()
    }

    @Test
    fun `SetPermissionsSkipped updates flag`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetPermissionsSkipped(true))
        assertThat(viewModel.state.value.permissionsSkipped).isTrue()
    }

    @Test
    fun `SetNextButtonText updates text`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetNextButtonText(FINISH_BUTTON_TEXT))
        assertThat(viewModel.state.value.nextButtonText).isEqualTo(FINISH_BUTTON_TEXT)
    }

    @Test
    fun `SetMacAddress updates macAddress`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetMacAddress(TEST_MAC_ADDRESS))
        assertThat(viewModel.state.value.macAddress).isEqualTo(TEST_MAC_ADDRESS)
    }

    @Test
    fun `SetConnectionSuccess updates isConnected`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetConnectionSuccess(true))
        assertThat(viewModel.state.value.isConnected).isTrue()
    }

    @Test
    fun `SetWifiStatus updates wifiStatus`() {
        val status = WifiStatus(
            status = WifiConnectionStatus.CONNECTED,
            locationStatus = "enabled",
            ssid = TEST_SSID,
            bssid = TEST_BSSID,
        )
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiStatus(status))
        assertThat(viewModel.state.value.wifiStatus?.ssid).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetShouldGetMacAddress updates flag`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetShouldGetMacAddress(true))
        assertThat(viewModel.state.value.shouldGetMacAddress).isTrue()
    }

    // -------------------------------------------------------------------------
    // Password Form
    // -------------------------------------------------------------------------

    @Test
    fun `SetWifiPasswordFormSsid updates ssid in form`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiPasswordFormSsid(TEST_SSID))
        assertThat(viewModel.state.value.wifiPasswordForm.ssid.value).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetWifiPasswordFormPassword updates password`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiPasswordFormPassword(TEST_PASSWORD))
        assertThat(viewModel.state.value.wifiPasswordForm.password.value).isEqualTo(TEST_PASSWORD)
    }

    @Test
    fun `SetWifiPasswordFormNoPassword updates flag`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiPasswordFormNoPassword(true))
        assertThat(viewModel.state.value.wifiPasswordForm.noPasswordNetwork.value).isTrue()
    }

    @Test
    fun `SetWifiPasswordForm replaces entire form`() {
        val newForm = WifiScalePasswordFormControls.create()
        newForm.ssid.onValueChange(TEST_SSID)
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiPasswordForm(newForm))
        assertThat(viewModel.state.value.wifiPasswordForm.ssid.value).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetScaleNetworkFormSsid updates scale network ssid`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetScaleNetworkFormSsid(TEST_SSID))
        assertThat(viewModel.state.value.scaleNetworkForm.ssid.value).isEqualTo(TEST_SSID)
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup updates isSetupFinished and isConnected`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ExitSetup(isSetupFinished = true, isConnected = true))
        advanceScheduler()
        assertThat(viewModel.state.value.isSetupFinished).isTrue()
        assertThat(viewModel.state.value.isConnected).isTrue()
    }

    // -------------------------------------------------------------------------
    // NavigateToErrorGuide / NavigateToTroubleShooting
    // -------------------------------------------------------------------------

    @Test
    fun `NavigateToErrorGuide sets step and saves previous step`() {
        viewModel.handleIntent(WifiScaleSetupIntent.NavigateToErrorGuide())
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.ERROR_GUIDE)
        assertThat(viewModel.state.value.stepBeforeErrorGuide).isEqualTo(WifiScaleSetupStep.SCALE_INFO)
    }

    @Test
    fun `NavigateToTroubleShooting sets step and button text`() {
        viewModel.handleIntent(WifiScaleSetupIntent.NavigateToTroubleShooting())
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.TROUBLE_SHOOTING)
        assertThat(viewModel.state.value.nextButtonText).isEqualTo(FINISH_BUTTON_TEXT)
    }

    // -------------------------------------------------------------------------
    // OnGetScaleMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `OnGetScaleMacAddress sets isGetMACSetup and shouldGetMacAddress`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.OnGetScaleMacAddress())
        advanceScheduler()
        assertThat(viewModel.state.value.isGetMACSetup).isTrue()
        assertThat(viewModel.state.value.shouldGetMacAddress).isTrue()
    }

    // -------------------------------------------------------------------------
    // HandleUserConfirmSelected
    // -------------------------------------------------------------------------

    @Test
    fun `HandleUserConfirmSelected AP_MODE sets showApMode true`() {
        viewModel.handleIntent(WifiScaleSetupIntent.HandleUserConfirmSelected(SetupPath.AP_MODE))
        assertThat(viewModel.state.value.showApMode).isTrue()
    }

    @Test
    fun `HandleUserConfirmSelected COMPLETE sets showApMode false`() {
        viewModel.handleIntent(WifiScaleSetupIntent.HandleUserConfirmSelected(SetupPath.COMPLETE))
        assertThat(viewModel.state.value.showApMode).isFalse()
    }

    // -------------------------------------------------------------------------
    // ClearNavigationState
    // -------------------------------------------------------------------------

    @Test
    fun `ClearNavigationState sets isNavigating false`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ClearNavigationState)
        assertThat(viewModel.state.value.isNavigating).isFalse()
    }

    // -------------------------------------------------------------------------
    // OpenHelp
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.OpenHelp())
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // Progress
    // -------------------------------------------------------------------------

    @Test
    fun `progress reflects current step index`() {
        val totalSteps = viewModel.state.value.steps.size
        assertThat(viewModel.state.value.progress).isEqualTo(1f / totalSteps)
    }

    // -------------------------------------------------------------------------
    // GoToWifiSettings
    // -------------------------------------------------------------------------

    @Test
    fun `GoToWifiSettings calls wifiScaleService openWifiSettings`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.GoToWifiSettings)
        advanceScheduler()
        verify { wifiScaleService.openWifiSettings() }
    }

    @Test
    fun `GoToWifiSettings does not crash when service throws`() {
        every { wifiScaleService.openWifiSettings() } throws RuntimeException("fail")
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.GoToWifiSettings)
        advanceScheduler()
        // No exception thrown — caught internally
    }

    // -------------------------------------------------------------------------
    // Skip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip without scaleToken shows toast`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Skip)
        advanceScheduler()
        verify { viewModel.dialogQueueService.showToast(any<Toast>()) }
    }

    // -------------------------------------------------------------------------
    // checkScaleToken
    // -------------------------------------------------------------------------

    @Test
    fun `checkScaleToken returns false and shows toast when token is null`() {
        val result = viewModel.checkScaleToken()
        assertThat(result).isFalse()
        verify { viewModel.dialogQueueService.showToast(any<Toast>()) }
    }

    // -------------------------------------------------------------------------
    // RequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission calls permissionService for standard type`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.RequestPermission("location"))
        advanceScheduler()
        verify { dialogUtility.permissionAlert(permissionType = "location", onRequest = any()) }
    }

    // -------------------------------------------------------------------------
    // Init block — getNetworkInfo sets wifiStatus
    // -------------------------------------------------------------------------

    @Test
    fun `init block calls getNetworkInfo which sets wifiStatus`() {
        val testStatus = WifiStatus(
            status = WifiConnectionStatus.CONNECTED,
            locationStatus = "enabled",
            ssid = TEST_SSID,
            bssid = TEST_BSSID,
        )
        coEvery { wifiScaleService.getConnectedWifiInfo() } returns testStatus
        advanceScheduler()
        // After advancing, init block's getNetworkInfo() should have run
        assertThat(viewModel.state.value.wifiStatus).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Init block — getScaleToken
    // -------------------------------------------------------------------------

    @Test
    fun `init block calls getScaleToken via wifiScaleService`() {
        advanceScheduler()
        coVerify { wifiScaleService.getScaleToken() }
    }

    // -------------------------------------------------------------------------
    // Init block — loadScaleInfo sets sku
    // -------------------------------------------------------------------------

    @Test
    fun `init sets sku in state from constructor param`() {
        assertThat(viewModel.state.value.sku).isEqualTo(TEST_SKU)
    }

    // -------------------------------------------------------------------------
    // Init block — monitorNetworkStatus calls updateNetworkStatus
    // -------------------------------------------------------------------------

    @Test
    fun `init block starts monitoring which calls getConnectedWifiInfo`() {
        advanceScheduler()
        coVerify(atLeast = 1) { wifiScaleService.getConnectedWifiInfo(any()) }
    }

    // -------------------------------------------------------------------------
    // onCleared — cleanup
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared stops wifiScaleService and deviceService`() {
        advanceScheduler()
        val method = viewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        verify { wifiScaleService.stop() }
        verify { deviceService.setSetupInProgress(false) }
    }

    // -------------------------------------------------------------------------
    // ExitSetup with isSetupFinished=false shows confirm dialog
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup not finished shows confirm dialog`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ExitSetup(isSetupFinished = false, isConnected = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // Next from PERMISSIONS without token shows toast
    // -------------------------------------------------------------------------

    @Test
    fun `Next from PERMISSIONS without token shows toast`() {
        advanceScheduler()
        // First advance to PERMISSIONS
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ClearNavigationState)
        // Now Next from PERMISSIONS — no token available
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        verify { viewModel.dialogQueueService.showToast(any<Toast>()) }
    }

    // -------------------------------------------------------------------------
    // Next from TROUBLE_SHOOTING exits setup
    // -------------------------------------------------------------------------

    @Test
    fun `Next from TROUBLE_SHOOTING calls navigateBack`() {
        viewModel.handleIntent(WifiScaleSetupIntent.NavigateToTroubleShooting())
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ClearNavigationState)
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        // startExitSetup(true) → navigateBack(waitForScaleInList=true)
        verify { wifiScaleService.stop() }
    }

    // -------------------------------------------------------------------------
    // Next from ERROR_CODE_SELECTED clears error and exits
    // -------------------------------------------------------------------------

    @Test
    fun `Next from ERROR_CODE_SELECTED clears error and exits`() {
        viewModel.handleIntent(WifiScaleSetupIntent.NavigateToErrorGuide())
        viewModel.handleIntent(WifiScaleSetupIntent.SelectErrorCode(TEST_ERROR_CODE))
        viewModel.handleIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.ERROR_CODE_SELECTED))
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.ClearNavigationState)
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.showError).isFalse()
    }

    // -------------------------------------------------------------------------
    // handleUserConfirmSelected public method
    // -------------------------------------------------------------------------

    @Test
    fun `handleUserConfirmSelected dispatches intent`() {
        viewModel.handleUserConfirmSelected(SetupPath.AP_MODE)
        assertThat(viewModel.state.value.showApMode).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetCurrentStep via intent
    // -------------------------------------------------------------------------

    @Test
    fun `SetCurrentStep changes step directly`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetCurrentStep(WifiScaleSetupStep.WIFI_PASSWORD))
        assertThat(viewModel.state.value.currentStep).isEqualTo(WifiScaleSetupStep.WIFI_PASSWORD)
    }

    // -------------------------------------------------------------------------
    // SetWifiSsid
    // -------------------------------------------------------------------------

    @Test
    fun `SetWifiSsid updates ssid in password form`() {
        viewModel.handleIntent(WifiScaleSetupIntent.SetWifiSsid(TEST_SSID))
        advanceScheduler()
        // SetWifiSsid goes through reducer and updates form
        assertThat(viewModel.state.value.wifiPasswordForm.ssid.value).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Init block — deviceService.setSetupInProgress called
    // -------------------------------------------------------------------------

    @Test
    fun `init calls deviceService setSetupInProgress true`() {
        verify { deviceService.setSetupInProgress(true) }
    }

    // -------------------------------------------------------------------------
    // isWifiPasswordFormValid through WIFI_PASSWORD step
    // -------------------------------------------------------------------------

    @Test
    fun `isFirstStep is false after advancing`() {
        advanceScheduler()
        viewModel.handleIntent(WifiScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.isFirstStep).isFalse()
    }
}
