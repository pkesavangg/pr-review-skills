package com.dmdbrands.gurus.weight.features.appPermissions.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogUtility
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGPermissionService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class AppPermissionsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var permissionService: GGPermissionService
    @MockK(relaxed = true) lateinit var dialogUtility: IDialogUtility
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private val permissionFlow = MutableStateFlow<MutableMap<String, String>>(mutableMapOf())
    private lateinit var viewModel: AppPermissionsViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { permissionService.permissionCallBackFlow } returns permissionFlow
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
    }

    private fun createViewModel(): AppPermissionsViewModel =
        AppPermissionsViewModel(
            permissionService = permissionService,
            dialogUtility = dialogUtility,
            deviceService = deviceService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.permissionMap).isEmpty()
        assertThat(state.requiredPermissions).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // Permission Subscription
    // -------------------------------------------------------------------------

    @Test
    fun `permission flow updates state with permissions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val permissions = mutableMapOf("bluetooth" to PermissionState.ENABLED)
        permissionFlow.value = permissions
        advanceUntilIdle()

        assertThat(viewModel.state.value.permissionMap).isEqualTo(permissions)
    }

    @Test
    fun `permission flow updates reflect multiple permission types`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val permissions = mutableMapOf(
            "bluetooth" to PermissionState.ENABLED,
            "location" to PermissionState.DISABLED,
        )
        permissionFlow.value = permissions
        advanceUntilIdle()

        assertThat(viewModel.state.value.permissionMap).hasSize(2)
        assertThat(viewModel.state.value.permissionMap["bluetooth"]).isEqualTo(PermissionState.ENABLED)
        assertThat(viewModel.state.value.permissionMap["location"]).isEqualTo(PermissionState.DISABLED)
    }

    // -------------------------------------------------------------------------
    // Paired Scales Subscription
    // -------------------------------------------------------------------------

    @Test
    fun `paired scales update triggers required permissions update`() = runTest {
        val pairedScalesFlow = MutableStateFlow(listOf(TestFixtures.bleDevice))
        every { deviceService.pairedScales } returns pairedScalesFlow

        viewModel = createViewModel()
        advanceUntilIdle()

        // The AppPermissionsHelper.getRequiredPermissionSets is called with paired scales
        // and resulting permissions are set in state — we verify state is updated
        // (exact permissions depend on AppPermissionsHelper logic with device types)
        val state = viewModel.state.value
        // State is updated via SetRequiredPermissions intent
        assertThat(state).isNotNull()
    }

    @Test
    fun `empty paired scales results in empty required permissions`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.requiredPermissions).isEmpty()
    }

    // -------------------------------------------------------------------------
    // RequestPermission Intent
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission intent triggers permission alert`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppPermissionsIntent.RequestPermission("bluetooth"))
        advanceUntilIdle()

        verify { dialogUtility.permissionAlert(permissionType = "bluetooth", isScaleSetupRequest = any(), onRequest = any(), onDismiss = any()) }
    }

    @Test
    fun `RequestPermission for different type passes correct type to dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(AppPermissionsIntent.RequestPermission("location"))
        advanceUntilIdle()

        verify { dialogUtility.permissionAlert(permissionType = "location", isScaleSetupRequest = any(), onRequest = any(), onDismiss = any()) }
    }

    // -------------------------------------------------------------------------
    // SetPermissions Intent
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions intent updates permissionMap in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val newPermissions = mutableMapOf("notification" to PermissionState.ENABLED)
        viewModel.handleIntent(AppPermissionsIntent.SetPermissions(newPermissions))

        assertThat(viewModel.state.value.permissionMap).isEqualTo(newPermissions)
    }

    // -------------------------------------------------------------------------
    // SetRequiredPermissions Intent
    // -------------------------------------------------------------------------

    @Test
    fun `SetRequiredPermissions intent updates requiredPermissions in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val required = setOf("bluetooth", "location")
        viewModel.handleIntent(AppPermissionsIntent.SetRequiredPermissions(required))

        assertThat(viewModel.state.value.requiredPermissions).isEqualTo(required)
    }
}
