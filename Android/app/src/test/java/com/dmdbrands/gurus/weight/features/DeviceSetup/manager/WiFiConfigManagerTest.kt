package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.library.ggbluetooth.model.GGWifiResponse
import com.dmdbrands.library.ggbluetooth.model.GGWifiSetupResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.ggbluetoothsdk.external.enums.GGWifiState
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [WiFiConfigManager] (MOB-966). onIntent is wired through the real reducer so
 * getState() reflects emitted intents (the manager reads currentStep/connection-state back out).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WiFiConfigManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val ggDeviceService: GGDeviceService = mockk(relaxed = true)

    private lateinit var scope: TestScope
    private val reducer = BtWifiScaleSetupReducer()

    private val intents = mutableListOf<BtWifiScaleSetupIntent>()
    private var state = BtWifiScaleSetupState()
    private var discoveredScale: Device? = null
    private var nextCalled = 0
    private var exitCalled: Boolean? = null
    private var notifPermissionRequested = 0

    private val OPERATION_TIMEOUT = 5_000L
    private val CONNECTION_DELAY = 100L

    @BeforeEach
    fun setUp() {
        scope = TestScope(testDispatcher)
        intents.clear()
        state = BtWifiScaleSetupState()
        discoveredScale = null
        nextCalled = 0
        exitCalled = null
        notifPermissionRequested = 0
    }

    private fun manager(initialStep: BtWifiSetupStep = BtWifiSetupStep.SCALE_INFO) = WiFiConfigManager(
        ggDeviceService = ggDeviceService,
        scope = scope,
        initialStep = initialStep,
        operationTimeout = OPERATION_TIMEOUT,
        connectionDelay = CONNECTION_DELAY,
        getState = { state },
        onIntent = { intents.add(it); state = reducer.reduce(state, it) ?: state },
        getDiscoveredScale = { discoveredScale },
        onNext = { nextCalled++ },
        onExitSetup = { exitCalled = it },
        requestNotificationPermission = { notifPermissionRequested++ },
    )

    private fun statesFor(step: BtWifiSetupStep) =
        intents.filterIsInstance<BtWifiScaleSetupIntent.SetStepConnectionState>()
            .filter { it.step == step }.map { it.connectionState }

    private fun stubWifiSetup(wifiState: String, errorCode: String? = null) {
        val response = mockk<GGWifiSetupResponse>(relaxed = true)
        every { response.wifiState } returns wifiState
        if (errorCode != null) every { response.errorCode } returns errorCode
        every { ggDeviceService.setupWifi(any(), any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[2] as (GGWifiSetupResponse) -> Unit).invoke(response)
        }
    }

    // -------------------------------------------------------------------------
    // gatherNetworks
    // -------------------------------------------------------------------------

    @Test
    fun `gatherNetworks with no scale marks GATHERING_NETWORK failed`() {
        discoveredScale = null
        manager().gatherNetworks()
        scope.testScheduler.runCurrent()

        assertThat(statesFor(BtWifiSetupStep.GATHERING_NETWORK)).contains(ConnectionState.Loading)
        assertThat(statesFor(BtWifiSetupStep.GATHERING_NETWORK)).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `gatherNetworks success sets wifi list, ssid and advances`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.GATHERING_NETWORK)
        val wifiResponse = mockk<GGWifiResponse>(relaxed = true)
        every { ggDeviceService.getWifiList(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as (GGWifiResponse) -> Unit).invoke(wifiResponse)
        }
        every { ggDeviceService.getConnectedWifiSSID(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as (String) -> Unit).invoke("HomeWifi")
        }
        every { ggDeviceService.getConnectedWifiMacAddress(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as (String) -> Unit).invoke("AA:BB:CC")
        }

        manager().gatherNetworks()
        scope.testScheduler.advanceUntilIdle()

        assertThat(intents.filterIsInstance<BtWifiScaleSetupIntent.SetConnectedSSID>()).isNotEmpty()
        assertThat(nextCalled).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // connectToWifi / setupWifi
    // -------------------------------------------------------------------------

    @Test
    fun `connectToWifi timeout marks CONNECTING_WIFI failed`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_WIFI)
        // setupWifi left as relaxed no-op → its callback never fires, so only the timeout path runs.
        manager().connectToWifi()
        scope.testScheduler.advanceTimeBy(OPERATION_TIMEOUT + 1)
        scope.testScheduler.runCurrent()

        assertThat(statesFor(BtWifiSetupStep.CONNECTING_WIFI)).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `connectToWifi success emits Success and advances`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_WIFI)
        stubWifiSetup(GGWifiState.GG_WIFI_STATE_CONNECTED.name)

        manager().connectToWifi()
        scope.testScheduler.advanceUntilIdle()

        assertThat(statesFor(BtWifiSetupStep.CONNECTING_WIFI)).contains(ConnectionState.Success)
        assertThat(nextCalled).isEqualTo(1)
    }

    @Test
    fun `connectToWifi success on GATHERING_NETWORK initial step exits setup`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_WIFI)
        stubWifiSetup(GGWifiState.GG_WIFI_STATE_CONNECTED.name)

        manager(initialStep = BtWifiSetupStep.GATHERING_NETWORK).connectToWifi()
        scope.testScheduler.advanceUntilIdle()

        assertThat(exitCalled).isTrue()
        assertThat(nextCalled).isEqualTo(0)
    }

    @Test
    fun `connectToWifi failure emits failed and sets error code`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_WIFI)
        stubWifiSetup("GG_WIFI_STATE_FAILED", errorCode = "E42")

        manager().connectToWifi()
        scope.testScheduler.advanceUntilIdle()

        assertThat(statesFor(BtWifiSetupStep.CONNECTING_WIFI)).contains(ConnectionState.Failed.Error)
        assertThat(intents.filterIsInstance<BtWifiScaleSetupIntent.SetErrorCode>().map { it.errorCode })
            .contains("E42")
    }

    @Test
    fun `cancelTimeout after connect prevents timeout failure`() {
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_WIFI)
        stubWifiSetup(GGWifiState.GG_WIFI_STATE_CONNECTED.name)
        val m = manager()
        m.connectToWifi()
        scope.testScheduler.runCurrent()
        m.cancelTimeout()
        scope.testScheduler.advanceUntilIdle()

        assertThat(statesFor(BtWifiSetupStep.CONNECTING_WIFI)).doesNotContain(ConnectionState.Failed.Error)
    }

    // -------------------------------------------------------------------------
    // password network status / form
    // -------------------------------------------------------------------------

    @Test
    fun `handlePasswordNetworkStatus no-password network clears password validator`() {
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.WIFI_PASSWORD)
        state.wifiPasswordForm.noPasswordNetwork.onValueChange(true)

        manager().handlePasswordNetworkStatus()

        assertThat(state.wifiPasswordForm.password.value).isEmpty()
    }

    @Test
    fun `handlePasswordNetworkStatus password network adds required validator`() {
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.WIFI_PASSWORD)
        state.wifiPasswordForm.noPasswordNetwork.onValueChange(false)

        manager().handlePasswordNetworkStatus()

        assertThat(state.wifiPasswordForm.password.value).isEmpty()
    }

    @Test
    fun `clearWifiPasswordForm resets password and no-password flag`() {
        state.wifiPasswordForm.password.onValueChange("secret")
        state.wifiPasswordForm.noPasswordNetwork.onValueChange(true)

        manager().clearWifiPasswordForm()

        assertThat(state.wifiPasswordForm.password.value).isEmpty()
        assertThat(state.wifiPasswordForm.noPasswordNetwork.value).isFalse()
    }
}
