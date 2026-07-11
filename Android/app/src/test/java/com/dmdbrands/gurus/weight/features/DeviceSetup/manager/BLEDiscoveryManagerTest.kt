package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.BluetoothPreferencesService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGScanResponseType
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [BLEDiscoveryManager] (MOB-966).
 *
 * The manager wraps the bleWrapper [GGDeviceService] (mocked) and drives pairing discovery via a
 * set of callbacks. Tests use a [TestScope] so the internal timeout coroutine can be advanced
 * deterministically. Callbacks are captured into locals so state transitions are asserted without
 * a real ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BLEDiscoveryManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val ggDeviceService: GGDeviceService = mockk(relaxed = true)
    private val deviceService: IDeviceService = mockk(relaxed = true)
    private val accountService: IAccountService = mockk(relaxed = true)
    private val bluetoothPreferencesService: BluetoothPreferencesService = mockk(relaxed = true)

    private lateinit var scope: TestScope

    // Captured callback effects
    private val intents = mutableListOf<BtWifiScaleSetupIntent>()
    private var state = BtWifiScaleSetupState()
    private var discoveredScale: Device? = null
    private var isScaleConnected: Boolean? = null
    private var nextCalled = 0
    private var exitCalled: Boolean? = null
    private var startObserveCalled = 0
    private var stopObserveCalled = 0
    private val dialogs = mutableListOf<DialogModel>()
    private var dismissDialogCalled = 0

    private val OPERATION_TIMEOUT = 5_000L
    private val CONNECTION_DELAY = 100L
    private val TEST_SKU = "0375"

    @BeforeEach
    fun setUp() {
        scope = TestScope(testDispatcher)
        intents.clear()
        dialogs.clear()
        state = BtWifiScaleSetupState()
        discoveredScale = null
        isScaleConnected = null
        nextCalled = 0
        exitCalled = null
        startObserveCalled = 0
        stopObserveCalled = 0
        dismissDialogCalled = 0
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
        every { ggDeviceService.localSkipDevices } returns MutableStateFlow(emptyList<String>())
    }

    private fun manager() = BLEDiscoveryManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        accountService = accountService,
        bluetoothPreferencesService = bluetoothPreferencesService,
        sku = TEST_SKU,
        scope = scope,
        operationTimeout = OPERATION_TIMEOUT,
        connectionDelay = CONNECTION_DELAY,
        getState = { state },
        onIntent = { intents.add(it) },
        getDiscoveredScale = { discoveredScale },
        setDiscoveredScale = { discoveredScale = it },
        setIsScaleConnected = { isScaleConnected = it },
        onNext = { nextCalled++ },
        onExitSetup = { exitCalled = it },
        startObservingDevices = { startObserveCalled++ },
        stopObservingDevices = { stopObserveCalled++ },
        showDialog = { dialogs.add(it) },
        dismissCurrentDialog = { dismissDialogCalled++ },
        setModePreference = { },
    )

    private fun connectionStatesFor(step: BtWifiSetupStep) =
        intents.filterIsInstance<BtWifiScaleSetupIntent.SetStepConnectionState>()
            .filter { it.step == step }
            .map { it.connectionState }

    // -------------------------------------------------------------------------
    // startPairing
    // -------------------------------------------------------------------------

    @Test
    fun `startPairing emits Loading for WAKEUP and starts scan + observing`() {
        manager().startPairing()
        scope.testScheduler.runCurrent()

        assertThat(connectionStatesFor(BtWifiSetupStep.WAKEUP)).contains(ConnectionState.Loading)
        verify { ggDeviceService.scanForPairing() }
        assertThat(startObserveCalled).isEqualTo(1)
    }

    @Test
    fun `startPairing timeout while still on WAKEUP marks failed and stops observing`() {
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.WAKEUP)
        manager().startPairing()
        scope.testScheduler.advanceTimeBy(OPERATION_TIMEOUT + 1)
        scope.testScheduler.runCurrent()

        assertThat(connectionStatesFor(BtWifiSetupStep.WAKEUP)).contains(ConnectionState.Failed.Error)
        assertThat(stopObserveCalled).isAtLeast(1)
    }

    @Test
    fun `startPairing timeout does nothing when step already advanced`() {
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_BLUETOOTH)
        manager().startPairing()
        scope.testScheduler.advanceTimeBy(OPERATION_TIMEOUT + 1)
        scope.testScheduler.runCurrent()

        // No failure emitted for WAKEUP because the user already moved past it.
        assertThat(connectionStatesFor(BtWifiSetupStep.WAKEUP)).doesNotContain(ConnectionState.Failed.Error)
    }

    @Test
    fun `startPairing marks failed when scanForPairing throws`() {
        every { ggDeviceService.scanForPairing() } throws RuntimeException("ble down")
        manager().startPairing()
        scope.testScheduler.runCurrent()

        assertThat(connectionStatesFor(BtWifiSetupStep.WAKEUP)).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `cancelPairing after start prevents the timeout failure`() {
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.WAKEUP)
        val m = manager()
        m.startPairing()
        scope.testScheduler.runCurrent()
        m.cancelPairing()
        scope.testScheduler.advanceTimeBy(OPERATION_TIMEOUT + 1)
        scope.testScheduler.runCurrent()

        assertThat(connectionStatesFor(BtWifiSetupStep.WAKEUP)).doesNotContain(ConnectionState.Failed.Error)
    }

    // -------------------------------------------------------------------------
    // handleScanResponse — connection status branches
    // -------------------------------------------------------------------------

    @Test
    fun `handleScanResponse DEVICE_CONNECTED sets connected and updates status`() {
        discoveredScale = Device(connectionStatus = BLEStatus.DISCONNECTED)
        val response = mockk<GGScanResponse.DeviceDetail>(relaxed = true)
        every { response.type } returns GGScanResponseType.DEVICE_CONNECTED

        manager().handleScanResponse(response)

        assertThat(isScaleConnected).isTrue()
        assertThat(discoveredScale?.connectionStatus).isEqualTo(BLEStatus.CONNECTED)
    }

    @Test
    fun `handleScanResponse DEVICE_DISCONNECTED sets disconnected and updates status`() {
        discoveredScale = Device(connectionStatus = BLEStatus.CONNECTED)
        val response = mockk<GGScanResponse.DeviceDetail>(relaxed = true)
        every { response.type } returns GGScanResponseType.DEVICE_DISCONNECTED

        manager().handleScanResponse(response)

        assertThat(isScaleConnected).isFalse()
        assertThat(discoveredScale?.connectionStatus).isEqualTo(BLEStatus.DISCONNECTED)
    }
}
