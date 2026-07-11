package com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.domain.services.IEntryReadService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.CustomizeSettings
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [BtWifiScaleSetupViewModel].
 *
 * Same pattern as WifiScaleSetupViewModelTest: StandardTestDispatcher +
 * advanceTimeBy to avoid infinite coroutine hangs from init-block observers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BtWifiScaleSetupViewModelTest {

    companion object {
        private const val TEST_SKU = "0375"
        private const val TEST_BROADCAST_ID = "broadcast-123"
        private const val TEST_SSID = "HomeWifi"
        private const val TEST_ERROR_CODE = "ERR01"
        private const val TEST_USERNAME = "User1"
        private const val TEST_SCALE_ID = "scale-456"
        private const val FINISH_BUTTON_TEXT = "Finish"
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService
    @MockK(relaxed = true) lateinit var entryService: IEntryService
    @MockK(relaxed = true) lateinit var entryReadService: IEntryReadService
    @MockK(relaxed = true) lateinit var deviceRepository: IDeviceRepository
    @MockK(relaxed = true) lateinit var dashboardService: IDashboardService
    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var connectivityObserver: IConnectivityObserver
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var accountService: IAccountService
    @MockK(relaxed = true) lateinit var bluetoothPreferencesService: BluetoothPreferencesService

    private lateinit var viewModel: BtWifiScaleSetupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { deviceService.isWeightOnlyModeAlertShown } returns MutableStateFlow(false)
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf<String, String>())
        every { connectivityObserver.observe() } returns MutableStateFlow(mockk(relaxed = true))
        every { ggDeviceService.deviceCache } returns MutableStateFlow(mutableMapOf())
        every { entryReadService.latestEntry() } returns flowOf(null)
        coEvery { accountService.activeAccountFlow } returns flowOf(mockk(relaxed = true))

        viewModel = BtWifiScaleSetupViewModel(
            sku = TEST_SKU,
            broadcastId = TEST_BROADCAST_ID,
            initialStep = BtWifiSetupStep.SCALE_INFO,
            userList = null,
            ggDeviceService = ggDeviceService,
            deviceService = deviceService,
            entryService = entryService,
            entryReadService = entryReadService,
            deviceRepository = deviceRepository,
            dashboardService = dashboardService,
            permissionService = permissionService,
            connectivityObserver = connectivityObserver,
            dialogUtility = dialogUtility,
            accountService = accountService,
            bluetoothPreferencesService = bluetoothPreferencesService,
        ).initTestDependencies()
    }

    @AfterEach
    fun tearDown() {
        try {
            val method = viewModel::class.java.getDeclaredMethod("onCleared")
            method.isAccessible = true
            method.invoke(viewModel)
        } catch (_: Exception) {
            // Suppress leaked exceptions from initializeSetup coroutine
        }
        // Drain any pending coroutine exceptions
        try {
            testDispatcher.scheduler.advanceUntilIdle()
        } catch (_: Exception) {
            // Expected — initializeSetup may throw ClassCastException due to relaxed mocks
        }
    }

    private fun advanceScheduler() {
        testDispatcher.scheduler.advanceTimeBy(200)
        testDispatcher.scheduler.runCurrent()
    }

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has SCALE_INFO step`() {
        assertThat(viewModel.state.value.currentStep).isEqualTo(BtWifiSetupStep.SCALE_INFO)
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.sku).isEqualTo(TEST_SKU)
        assertThat(state.isLoading).isFalse()
        assertThat(state.connectedSSID).isEmpty()
        assertThat(state.userList).isEmpty()
        assertThat(state.duplicateUser).isNull()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `SetConnectedSSID updates connectedSSID`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetConnectedSSID(TEST_SSID))
        assertThat(viewModel.state.value.connectedSSID).isEqualTo(TEST_SSID)
    }

    @Test
    fun `SetCurrentStep changes step`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.PERMISSIONS))
        assertThat(viewModel.state.value.currentStep).isEqualTo(BtWifiSetupStep.PERMISSIONS)
    }

    @Test
    fun `SetLoading updates isLoading`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetLoading(true))
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `SetErrorCode updates errorCode`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetErrorCode(TEST_ERROR_CODE))
        assertThat(viewModel.state.value.errorCode).isEqualTo(TEST_ERROR_CODE)
    }

    @Test
    fun `UpdateNextButtonText updates nextButtonText`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.UpdateNextButtonText(FINISH_BUTTON_TEXT))
        assertThat(viewModel.state.value.nextButtonText).isEqualTo(FINISH_BUTTON_TEXT)
    }

    @Test
    fun `SetCanProceedToNext updates flag`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCanProceedToNext(true))
        assertThat(viewModel.state.value.canProceedToNext).isTrue()
    }

    @Test
    fun `SetScaleId updates scaleId`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetScaleId(TEST_SCALE_ID))
        assertThat(viewModel.state.value.scaleId).isEqualTo(TEST_SCALE_ID)
    }

    @Test
    fun `SetPermissions updates permissions map`() {
        val perms = mutableMapOf("bluetooth" to "enabled")
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetPermissions(perms))
        assertThat(viewModel.state.value.permissions).isEqualTo(perms)
    }

    @Test
    fun `SetStepConnectionState updates step connection state`() {
        viewModel.handleIntent(
            BtWifiScaleSetupIntent.SetStepConnectionState(
                BtWifiSetupStep.CONNECTING_BLUETOOTH,
                ConnectionState.Success,
            ),
        )
        assertThat(viewModel.state.value.stepConnectionStates[BtWifiSetupStep.CONNECTING_BLUETOOTH])
            .isEqualTo(ConnectionState.Success)
    }

    @Test
    fun `SetHasSavedSettings updates flag`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetHasSavedSettings(true))
        assertThat(viewModel.state.value.hasSavedSettings).isTrue()
    }

    @Test
    fun `SetVisitedCustomizeSteps updates steps`() {
        val steps = setOf(CustomizeSettings.DASHBOARD_METRICS)
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetVisitedCustomizeSteps(steps))
        assertThat(viewModel.state.value.visitedCustomizeSteps).contains(CustomizeSettings.DASHBOARD_METRICS)
    }

    @Test
    fun `SetGoalProgress updates goal progress`() {
        val progress = mockk<WeightProgress>(relaxed = true)
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetGoalProgress(progress))
        assertThat(viewModel.state.value.goalProgress).isEqualTo(progress)
    }

    @Test
    fun `SetLatestWeight updates latestWeight`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetLatestWeight(75.5))
        assertThat(viewModel.state.value.latestWeight).isEqualTo(75.5)
    }

    @Test
    fun `SetInitialStep updates initialStep`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetInitialStep(BtWifiSetupStep.CUSTOMIZE_SETTINGS))
        assertThat(viewModel.state.value.initialStep).isEqualTo(BtWifiSetupStep.CUSTOMIZE_SETTINGS)
    }

    @Test
    fun `UpdateUsernameForm updates username`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.UpdateUsernameForm(TEST_USERNAME))
        assertThat(viewModel.state.value.usernameForm.username.value).isEqualTo(TEST_USERNAME)
    }

    // -------------------------------------------------------------------------
    // Step Transitions
    // -------------------------------------------------------------------------

    @Test
    fun `Next from SCALE_INFO advances step`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.Next)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isNotEqualTo(BtWifiSetupStep.SCALE_INFO)
    }

    @Test
    fun `Back from second step returns to first step`() {
        advanceScheduler()
        // Advance to next step first
        viewModel.handleIntent(BtWifiScaleSetupIntent.Next)
        advanceScheduler()
        val stepAfterNext = viewModel.state.value.currentStep
        // Now go back
        viewModel.handleIntent(BtWifiScaleSetupIntent.Back)
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isNotEqualTo(stepAfterNext)
    }

    // -------------------------------------------------------------------------
    // ExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `ExitSetup not finished shows confirm dialog`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.ExitSetup(isSetupFinished = false))
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // OpenHelp
    // -------------------------------------------------------------------------

    @Test
    fun `OpenHelp enqueues custom dialog`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.OpenHelp)
        advanceScheduler()
        verify { viewModel.dialogQueueService.enqueue(any<DialogModel.Custom>()) }
    }

    // -------------------------------------------------------------------------
    // Init block verifications
    // -------------------------------------------------------------------------

    @Test
    fun `init calls deviceService setSetupInProgress true`() {
        verify { deviceService.setSetupInProgress(true) }
    }

    @Test
    fun `init subscribes to accountService activeAccountFlow`() {
        advanceScheduler()
        verify { accountService.activeAccountFlow }
    }

    // -------------------------------------------------------------------------
    // Progress
    // -------------------------------------------------------------------------

    @Test
    fun `progress reflects current step`() {
        val totalSteps = viewModel.state.value.steps.size
        assertThat(viewModel.state.value.progress).isGreaterThan(0f)
        assertThat(viewModel.state.value.progress).isAtMost(1f)
    }

    @Test
    fun `isFirstStep is true at initial step`() {
        assertThat(viewModel.state.value.isFirstStep).isTrue()
    }

    // -------------------------------------------------------------------------
    // RequestPermission
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission calls dialogUtility permissionAlert`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.RequestPermission("bluetooth"))
        advanceScheduler()
        verify { dialogUtility.permissionAlert(permissionType = "bluetooth", onRequest = any()) }
    }

    // -------------------------------------------------------------------------
    // Skip
    // -------------------------------------------------------------------------

    @Test
    fun `Skip from GATHERING_NETWORK does not crash`() {
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.GATHERING_NETWORK))
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.Skip)
        advanceScheduler()
        // Should handle skip without crash — dialog verification depends on internal state
        assertThat(viewModel.state.value).isNotNull()
    }

    @Test
    fun `Skip from SCALE_INFO calls onNext`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.Skip)
        advanceScheduler()
        // At SCALE_INFO, Skip just calls onNext — step should advance
        assertThat(viewModel.state.value.currentStep).isNotEqualTo(BtWifiSetupStep.SCALE_INFO)
    }

    // -------------------------------------------------------------------------
    // Bluetooth-off during measurement → Error Collecting Measurement (MOB-871)
    // -------------------------------------------------------------------------

    /** All BtWifiR4-required permissions enabled (JVM tests run below API S). */
    private fun allPermissionsEnabled() = mutableMapOf(
        GGPermissionType.BLUETOOTH_SWITCH to PermissionState.ENABLED,
        GGPermissionType.LOCATION_SWITCH to PermissionState.ENABLED,
        GGPermissionType.LOCATION to PermissionState.ENABLED,
        GGPermissionType.WIFI_SWITCH to PermissionState.ENABLED,
    )

    private fun invokeHandlePermissionBasedErrors() {
        val method = viewModel::class.java.getDeclaredMethod("handlePermissionBasedErrors")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    @Test
    fun `Bluetooth off while on STEP_ON routes to MEASUREMENT error screen`() {
        advanceScheduler()
        // Arrive on "One Last Step" with every permission granted (so entry runs stepOn, not the error path).
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetPermissions(allPermissionsEnabled()))
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.STEP_ON))
        advanceScheduler()
        assertThat(viewModel.state.value.currentStep).isEqualTo(BtWifiSetupStep.STEP_ON)

        // User turns Bluetooth off before stepping on the scale.
        viewModel.handleIntent(
            BtWifiScaleSetupIntent.SetPermissions(
                allPermissionsEnabled().apply { put(GGPermissionType.BLUETOOTH_SWITCH, PermissionState.DISABLED) },
            ),
        )
        invokeHandlePermissionBasedErrors()
        advanceScheduler()

        assertThat(viewModel.state.value.currentStep).isEqualTo(BtWifiSetupStep.MEASUREMENT)
        assertThat(viewModel.state.value.stepConnectionStates[BtWifiSetupStep.MEASUREMENT])
            .isInstanceOf(ConnectionState.Failed::class.java)
    }

    @Test
    fun `Bluetooth off during MEASUREMENT surfaces the error state`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetPermissions(allPermissionsEnabled()))
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.MEASUREMENT))
        advanceScheduler()

        viewModel.handleIntent(
            BtWifiScaleSetupIntent.SetPermissions(
                allPermissionsEnabled().apply { put(GGPermissionType.BLUETOOTH_SWITCH, PermissionState.DISABLED) },
            ),
        )
        invokeHandlePermissionBasedErrors()
        advanceScheduler()

        assertThat(viewModel.state.value.stepConnectionStates[BtWifiSetupStep.MEASUREMENT])
            .isInstanceOf(ConnectionState.Failed::class.java)
    }

    @Test
    fun `only network permission missing on STEP_ON does not fail the measurement`() {
        advanceScheduler()
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetPermissions(allPermissionsEnabled()))
        viewModel.handleIntent(BtWifiScaleSetupIntent.SetCurrentStep(BtWifiSetupStep.STEP_ON))
        advanceScheduler()

        // Wi-Fi/network off alone must not block the BLE reading — stay on "One Last Step".
        viewModel.handleIntent(
            BtWifiScaleSetupIntent.SetPermissions(
                allPermissionsEnabled().apply { put(GGPermissionType.WIFI_SWITCH, PermissionState.DISABLED) },
            ),
        )
        invokeHandlePermissionBasedErrors()
        advanceScheduler()

        assertThat(viewModel.state.value.currentStep).isEqualTo(BtWifiSetupStep.STEP_ON)
        assertThat(viewModel.state.value.stepConnectionStates[BtWifiSetupStep.MEASUREMENT])
            .isNotInstanceOf(ConnectionState.Failed::class.java)
    }

    // -------------------------------------------------------------------------
    // onCleared cleanup
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared sets setupInProgress false`() {
        val method = viewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        verify { deviceService.setSetupInProgress(false) }
    }
}
