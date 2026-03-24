package com.dmdbrands.gurus.weight.features.ScaleSetup.viewmodel

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.dmdbrands.library.ggbluetooth.model.GGScanResponse
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [ScaleSetupViewmodel] via a concrete test subclass.
 *
 * Focuses on basic state management, initial state, and subscribePermissions behavior.
 * Hardware-dependent scanning/pairing methods are not tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSetupViewModelTest {

    // Minimal test state/intent for the abstract ScaleSetupViewmodel
    data class TestState(val value: String = "initial") : IReducer.State
    sealed interface TestIntent : IReducer.Intent {
        data class SetValue(val value: String) : TestIntent
    }

    class TestReducer : IReducer<TestState, TestIntent> {
        override fun reduce(state: TestState, intent: TestIntent): TestState? = when (intent) {
            is TestIntent.SetValue -> state.copy(value = intent.value)
        }
    }

    /** Concrete test subclass of ScaleSetupViewmodel to expose protected members. */
    class TestScaleSetupViewModel(
        ggDeviceService: GGDeviceService,
        connectivityObserver: IConnectivityObserver,
        permissionService: GGPermissionService,
    ) : ScaleSetupViewmodel<TestState, TestIntent>(
        ggDeviceService = ggDeviceService,
        connectivityObserver = connectivityObserver,
        permissionService = permissionService,
        reducer = TestReducer(),
    ) {
        override fun provideInitialState(): TestState = TestState()

        override fun onScanResponse(response: GGScanResponse.DeviceDetail) {
            // No-op for tests
        }

        // Expose protected methods for testing
        fun exposedSubscribePermissions(isSkipNetworkCheck: Boolean = false) =
            subscribePermissions(isSkipNetworkCheck)
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var connectivityObserver: IConnectivityObserver
    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService

    private lateinit var viewModel: TestScaleSetupViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(mutableMapOf())
        every { connectivityObserver.observe() } returns MutableStateFlow(
            NetworkState(available = true, unAvailable = false),
        )

        viewModel = TestScaleSetupViewModel(
            ggDeviceService = ggDeviceService,
            connectivityObserver = connectivityObserver,
            permissionService = permissionService,
        ).initTestDependencies()
    }

    // -------------------------------------------------------------------------
    // provideInitialState
    // -------------------------------------------------------------------------

    @Test
    fun `provideInitialState returns default TestState`() {
        assertThat(viewModel.state.value.value).isEqualTo("initial")
    }

    // -------------------------------------------------------------------------
    // handleIntent — reducer routing
    // -------------------------------------------------------------------------

    @Test
    fun `handleIntent SetValue updates state`() {
        viewModel.handleIntent(TestIntent.SetValue("updated"))
        assertThat(viewModel.state.value.value).isEqualTo("updated")
    }

    @Test
    fun `handleIntent multiple SetValue calls produce correct final state`() {
        viewModel.handleIntent(TestIntent.SetValue("first"))
        viewModel.handleIntent(TestIntent.SetValue("second"))
        assertThat(viewModel.state.value.value).isEqualTo("second")
    }

    // -------------------------------------------------------------------------
    // subscribePermissions — skip network check
    // -------------------------------------------------------------------------

    @Test
    fun `subscribePermissions with skip returns permissions from flow`() = runTest {
        val perms: GGPermissionStatusMap = mutableMapOf(
            GGPermissionType.BLUETOOTH_SWITCH to GGPermissionState.ENABLED,
            GGPermissionType.WIFI_SWITCH to GGPermissionState.DISABLED,
        )
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(perms)

        val result = viewModel.exposedSubscribePermissions(isSkipNetworkCheck = true).first()

        assertThat(result[GGPermissionType.BLUETOOTH_SWITCH]).isEqualTo(GGPermissionState.ENABLED)
        assertThat(result[GGPermissionType.WIFI_SWITCH]).isEqualTo(GGPermissionState.DISABLED)
    }

    @Test
    fun `subscribePermissions with skip defaults WIFI_SWITCH to DISABLED when absent`() = runTest {
        val perms: GGPermissionStatusMap = mutableMapOf(
            GGPermissionType.BLUETOOTH_SWITCH to GGPermissionState.ENABLED,
        )
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(perms)

        val result = viewModel.exposedSubscribePermissions(isSkipNetworkCheck = true).first()

        assertThat(result[GGPermissionType.WIFI_SWITCH]).isEqualTo(GGPermissionState.DISABLED)
    }

    // -------------------------------------------------------------------------
    // subscribePermissions — combined with network
    // -------------------------------------------------------------------------

    @Test
    fun `subscribePermissions without skip enables WIFI_SWITCH when network available`() = runTest {
        val perms: GGPermissionStatusMap = mutableMapOf(
            GGPermissionType.WIFI_SWITCH to GGPermissionState.DISABLED,
        )
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(perms)
        every { connectivityObserver.observe() } returns MutableStateFlow(
            NetworkState(available = true, unAvailable = false),
        )

        val result = viewModel.exposedSubscribePermissions(isSkipNetworkCheck = false).first()

        assertThat(result[GGPermissionType.WIFI_SWITCH]).isEqualTo(GGPermissionState.ENABLED)
    }

    @Test
    fun `subscribePermissions without skip keeps WIFI_SWITCH disabled when both unavailable`() {
        // Use non-coroutine test to avoid leaking exceptions from BtWifiScaleSetupViewModelTest
        val perms: GGPermissionStatusMap = mutableMapOf(
            GGPermissionType.WIFI_SWITCH to GGPermissionState.DISABLED,
        )
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(perms)
        every { connectivityObserver.observe() } returns MutableStateFlow(
            NetworkState(available = false, unAvailable = true),
        )

        val flow = viewModel.exposedSubscribePermissions(isSkipNetworkCheck = false)
        val result = kotlinx.coroutines.runBlocking { flow.first() }

        assertThat(result[GGPermissionType.WIFI_SWITCH]).isEqualTo(GGPermissionState.DISABLED)
    }

    @Test
    fun `subscribePermissions without skip enables WIFI_SWITCH when permission enabled but no network`() = runTest {
        val perms: GGPermissionStatusMap = mutableMapOf(
            GGPermissionType.WIFI_SWITCH to GGPermissionState.ENABLED,
        )
        every { permissionService.permissionCallBackFlow } returns MutableStateFlow(perms)
        every { connectivityObserver.observe() } returns MutableStateFlow(
            NetworkState(available = false, unAvailable = true),
        )

        val result = viewModel.exposedSubscribePermissions(isSkipNetworkCheck = false).first()

        assertThat(result[GGPermissionType.WIFI_SWITCH]).isEqualTo(GGPermissionState.ENABLED)
    }

    // -------------------------------------------------------------------------
    // discoveredScale default
    // -------------------------------------------------------------------------

    @Test
    fun `discoveredScale is null by default`() {
        // Access via reflection since it is protected
        val field = ScaleSetupViewmodel::class.java.getDeclaredField("discoveredScale")
        field.isAccessible = true
        val value = field.get(viewModel)
        assertThat(value).isNull()
    }
}
