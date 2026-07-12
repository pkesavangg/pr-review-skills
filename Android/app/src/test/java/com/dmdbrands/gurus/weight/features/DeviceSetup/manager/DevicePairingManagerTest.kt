package com.dmdbrands.gurus.weight.features.DeviceSetup.manager

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IDashboardService
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.BtWifiSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupReducer
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.BtWifiScaleSetupState
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGScaleUserResponse
import com.greatergoods.blewrapper.GGDeviceService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [DevicePairingManager] (MOB-966).
 *
 * onIntent is wired through the real [BtWifiScaleSetupReducer] so getState() reflects emitted
 * intents (the manager's success-path guards read stepConnectionStates back out of state).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicePairingManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val ggDeviceService: GGDeviceService = mockk(relaxed = true)
    private val deviceService: IDeviceService = mockk(relaxed = true)
    private val accountService: IAccountService = mockk(relaxed = true)
    private val dashboardService: IDashboardService = mockk(relaxed = true)

    private lateinit var scope: TestScope
    private val reducer = BtWifiScaleSetupReducer()

    private val intents = mutableListOf<BtWifiScaleSetupIntent>()
    private var state = BtWifiScaleSetupState()
    private var discoveredScale: Device? = null
    private var isScaleSaved: Boolean? = null
    private var nextCalled = 0
    private val dialogs = mutableListOf<DialogModel>()

    private val OPERATION_TIMEOUT = 5_000L
    private val TEST_SKU = "0375"
    private val TEST_ACCOUNT_ID = "acc-1"

    @BeforeEach
    fun setUp() {
        scope = TestScope(testDispatcher)
        intents.clear()
        dialogs.clear()
        state = BtWifiScaleSetupState()
        discoveredScale = null
        isScaleSaved = null
        nextCalled = 0
        coEvery { accountService.activeAccountFlow } returns flowOf(mockk(relaxed = true))
    }

    private fun manager() = DevicePairingManager(
        ggDeviceService = ggDeviceService,
        deviceService = deviceService,
        accountService = accountService,
        dashboardService = dashboardService,
        sku = TEST_SKU,
        scope = scope,
        operationTimeout = OPERATION_TIMEOUT,
        getState = { state },
        onIntent = { intents.add(it); state = reducer.reduce(state, it) ?: state },
        getDiscoveredScale = { discoveredScale },
        setDiscoveredScale = { discoveredScale = it },
        setIsScaleSaved = { isScaleSaved = it },
        getAccountId = { TEST_ACCOUNT_ID },
        onNext = { nextCalled++ },
        enqueueDialog = { dialogs.add(it) },
    )

    private fun btStates() =
        intents.filterIsInstance<BtWifiScaleSetupIntent.SetStepConnectionState>()
            .filter { it.step == BtWifiSetupStep.CONNECTING_BLUETOOTH }
            .map { it.connectionState }

    private fun currentSteps() =
        intents.filterIsInstance<BtWifiScaleSetupIntent.SetCurrentStep>().map { it.step }

    /** Stub pairDevice so its callback fires synchronously with [response]. */
    private fun stubPairResponse(response: GGUserActionResponseType) {
        every { ggDeviceService.pairDevice(any(), any(), any(), any()) } answers {
            lastArg<(GGUserActionResponseType) -> Unit>().invoke(response)
        }
        every { ggDeviceService.getUsers(any(), any()) } answers {
            secondArg<(GGScaleUserResponse) -> Unit>().invoke(mockk(relaxed = true))
        }
    }

    // -------------------------------------------------------------------------
    // connectToBluetooth — guard / timeout / error
    // -------------------------------------------------------------------------

    @Test
    fun `connectToBluetooth emits Loading then fails when no scale discovered`() {
        discoveredScale = null
        manager().connectToBluetooth()
        scope.testScheduler.runCurrent()

        assertThat(btStates()).contains(ConnectionState.Loading)
        assertThat(btStates()).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `connectToBluetooth timeout while connecting marks failed`() {
        discoveredScale = null
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_BLUETOOTH)
        manager().connectToBluetooth()
        scope.testScheduler.advanceTimeBy(OPERATION_TIMEOUT + 1)
        scope.testScheduler.runCurrent()

        assertThat(btStates()).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `connectToBluetooth marks failed when pairDevice throws`() {
        discoveredScale = Device()
        every { ggDeviceService.pairDevice(any(), any(), any(), any()) } throws RuntimeException("boom")
        manager().connectToBluetooth()
        scope.testScheduler.runCurrent()

        assertThat(btStates()).contains(ConnectionState.Failed.Error)
    }

    @Test
    fun `connectToBluetooth CREATION_COMPLETED saves scale and advances`() {
        discoveredScale = Device()
        coEvery { deviceService.saveScale(any()) } answers { firstArg() }
        stubPairResponse(GGUserActionResponseType.CREATION_COMPLETED)

        manager().connectToBluetooth()
        scope.testScheduler.advanceUntilIdle()

        assertThat(btStates()).contains(ConnectionState.Success)
        assertThat(isScaleSaved).isTrue()
        assertThat(nextCalled).isEqualTo(1)
    }

    @Test
    fun `CREATION_COMPLETED but getUsers never returns surfaces retryable error and does not advance`() {
        // MOB-248: pairing succeeds (green "Connected") but Bluetooth is switched off in that
        // instant, so the getUsers callback never fires. Stub only pairDevice — leaving getUsers
        // a relaxed no-op reproduces the dead BLE link. Without the withTimeoutOrNull guard the
        // manager would suspend forever here; it must instead fall back to a retryable error.
        discoveredScale = Device()
        state = BtWifiScaleSetupState(currentStep = BtWifiSetupStep.CONNECTING_BLUETOOTH)
        coEvery { deviceService.saveScale(any()) } answers { firstArg() }
        every { ggDeviceService.pairDevice(any(), any(), any(), any()) } answers {
            lastArg<(GGUserActionResponseType) -> Unit>().invoke(GGUserActionResponseType.CREATION_COMPLETED)
        }

        manager().connectToBluetooth()
        scope.testScheduler.advanceUntilIdle()

        assertThat(btStates()).contains(ConnectionState.Success)       // green check was shown
        assertThat(btStates()).contains(ConnectionState.Failed.Error)  // then recovered to a retryable error
        assertThat(nextCalled).isEqualTo(0)                            // never auto-advanced past the dead link
    }

    @Test
    fun `connectToBluetooth DUPLICATE_USER_ERROR routes to DUPLICATES_FOUND`() {
        discoveredScale = Device()
        stubPairResponse(GGUserActionResponseType.DUPLICATE_USER_ERROR)

        manager().connectToBluetooth()
        scope.testScheduler.advanceUntilIdle()

        assertThat(currentSteps()).contains(BtWifiSetupStep.DUPLICATES_FOUND)
    }

    @Test
    fun `connectToBluetooth MEMORY_FULL routes to USER_LIMIT_REACHED`() {
        discoveredScale = Device()
        stubPairResponse(GGUserActionResponseType.MEMORY_FULL)

        manager().connectToBluetooth()
        scope.testScheduler.advanceUntilIdle()

        assertThat(currentSteps()).contains(BtWifiSetupStep.USER_LIMIT_REACHED)
    }

    // -------------------------------------------------------------------------
    // replaceAccount / dialogs / cancelTimeout
    // -------------------------------------------------------------------------

    @Test
    fun `replaceAccount sets username and routes back to CONNECTING_BLUETOOTH`() {
        discoveredScale = Device()
        manager().replaceAccount("Alice")
        scope.testScheduler.advanceUntilIdle()

        assertThat(currentSteps()).contains(BtWifiSetupStep.CONNECTING_BLUETOOTH)
    }

    @Test
    fun `deleteUser enqueues a confirm dialog`() {
        discoveredScale = Device()
        val user = mockk<GGBTUser>(relaxed = true)
        manager().deleteUser(user)

        assertThat(dialogs.filterIsInstance<DialogModel.Confirm>()).isNotEmpty()
    }

    @Test
    fun `showRestoreAccountAlert enqueues a confirm dialog`() {
        manager().showRestoreAccountAlert()
        assertThat(dialogs.filterIsInstance<DialogModel.Confirm>()).isNotEmpty()
    }

    @Test
    fun `cancelTimeout after connect prevents the timeout failure`() {
        discoveredScale = Device()
        stubPairResponse(GGUserActionResponseType.CREATION_COMPLETED)
        coEvery { deviceService.saveScale(any()) } answers { firstArg() }
        val m = manager()
        m.connectToBluetooth()
        scope.testScheduler.runCurrent()
        m.cancelTimeout()
        scope.testScheduler.advanceUntilIdle()

        // Success emitted from the callback; no extra Failed from a fired timeout.
        assertThat(btStates()).doesNotContain(ConnectionState.Failed.Error)
    }
}
