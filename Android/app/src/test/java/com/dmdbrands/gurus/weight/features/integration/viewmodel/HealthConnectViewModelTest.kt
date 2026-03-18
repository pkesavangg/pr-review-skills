package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectAction
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectIntent
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectSetup
import com.dmdbrands.gurus.weight.features.integration.model.HealthConnectUiState
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [HealthConnectViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var healthConnectService: IHealthConnectService
    @MockK(relaxed = true) lateinit var navigationService: IAppNavigationService
    @MockK(relaxed = true) lateinit var dialogQueueService: IDialogQueueService

    private lateinit var viewModel: HealthConnectViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        coEvery { healthConnectService.checkAvailability() } returns true
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE
        coEvery { healthConnectService.checkIfAlreadyUsed() } returns true

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    @AfterEach
    fun tearDown() {
        // Walk the class hierarchy to find onCleared (declared in ViewModel)
        var clazz: Class<*>? = viewModel::class.java
        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod("onCleared")
                method.isAccessible = true
                method.invoke(viewModel)
                return
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            }
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
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.currentSlide).isEqualTo(0)
        assertThat(state.isOutOfSync).isFalse()
        assertThat(state.alertPresented).isFalse()
        assertThat(state.isHealthConnectOpened).isFalse()
    }

    @Test
    fun `initial state has NONE permission status`() {
        assertThat(viewModel.state.value.permissionStatus).isEqualTo(HealthConnectPermissionStatus.NONE)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `ConnectSuccess clears loading and error`() {
        viewModel.handleIntent(HealthConnectIntent.ConnectSuccess)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `ConnectError sets error message and resets to START_CONNECT`() {
        viewModel.handleIntent(HealthConnectIntent.ConnectError)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.healthConnectSetupState).isEqualTo(HealthConnectSetup.START_CONNECT)
        assertThat(state.errorMessage).isNotNull()
    }

    @Test
    fun `AppResumed clears isHealthConnectOpened`() {
        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        assertThat(viewModel.state.value.isHealthConnectOpened).isTrue()
        viewModel.handleIntent(HealthConnectIntent.AppResumed)
        assertThat(viewModel.state.value.isHealthConnectOpened).isFalse()
    }

    @Test
    fun `SetAlertPresented sets alertPresented to true`() {
        viewModel.handleIntent(HealthConnectIntent.SetAlertPresented)
        assertThat(viewModel.state.value.alertPresented).isTrue()
    }

    @Test
    fun `ClearAlertPresented sets alertPresented to false`() {
        viewModel.handleIntent(HealthConnectIntent.SetAlertPresented)
        viewModel.handleIntent(HealthConnectIntent.ClearAlertPresented)
        assertThat(viewModel.state.value.alertPresented).isFalse()
    }

    @Test
    fun `SetHealthConnectOpened sets isHealthConnectOpened to true`() {
        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        assertThat(viewModel.state.value.isHealthConnectOpened).isTrue()
    }

    @Test
    fun `ClearHealthConnectOpened sets isHealthConnectOpened to false`() {
        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        viewModel.handleIntent(HealthConnectIntent.ClearHealthConnectOpened)
        assertThat(viewModel.state.value.isHealthConnectOpened).isFalse()
    }

    @Test
    fun `UpdateSlide updates currentSlide`() {
        viewModel.handleIntent(HealthConnectIntent.UpdateSlide(3))
        assertThat(viewModel.state.value.currentSlide).isEqualTo(3)
    }

    @Test
    fun `ConfirmExitSetup clears loading and error`() {
        viewModel.handleIntent(HealthConnectIntent.ConfirmExitSetup)
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction reducer effects
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction CONNECT sets loading true via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `PrimaryAction OPEN_HEALTH_CONNECT sets isHealthConnectOpened via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
        assertThat(viewModel.state.value.isHealthConnectOpened).isTrue()
    }

    @Test
    fun `PrimaryAction UPDATE_PERMISSIONS sets loading true via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS))
        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    @Test
    fun `PrimaryAction EXIT sets alertPresented via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.EXIT))
        assertThat(viewModel.state.value.alertPresented).isTrue()
    }

    // -------------------------------------------------------------------------
    // SecondaryAction reducer effects
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction SKIP transitions to FINISH_INCOMPLETE_RECONNECTION`() {
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.SKIP))
        assertThat(viewModel.state.value.healthConnectSetupState)
            .isEqualTo(HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION)
    }

    @Test
    fun `SecondaryAction EXIT sets alertPresented via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.EXIT))
        assertThat(viewModel.state.value.alertPresented).isTrue()
    }

    @Test
    fun `SecondaryAction OPEN_HEALTH_CONNECT sets isHealthConnectOpened via reducer`() {
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
        assertThat(viewModel.state.value.isHealthConnectOpened).isTrue()
    }

    // -------------------------------------------------------------------------
    // PrimaryAction side effects
    // -------------------------------------------------------------------------

    @Test
    fun `PrimaryAction CONNECT calls requestAuthorization`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()
        coVerify { healthConnectService.requestAuthorization(any()) }
    }

    @Test
    fun `PrimaryAction FINISH calls turnOnIntegration`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()
        coVerify { healthConnectService.turnOnIntegration() }
    }

    @Test
    fun `PrimaryAction OPEN_HEALTH_CONNECT calls openHealthConnect`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
        advanceScheduler()
        coVerify { healthConnectService.openHealthConnect(true) }
    }

    @Test
    fun `PrimaryAction EXIT navigates back`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.EXIT))
        advanceScheduler()
        coVerify { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // SecondaryAction side effects
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction EXIT navigates back`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.EXIT))
        advanceScheduler()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `SecondaryAction OPEN_HEALTH_CONNECT calls openHealthConnect`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
        advanceScheduler()
        coVerify { healthConnectService.openHealthConnect(true) }
    }

    // -------------------------------------------------------------------------
    // exitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `exitSetup on FINISH_CONNECT calls handleFinish`() {
        advanceScheduler()
        forceSetupState(HealthConnectSetup.FINISH_CONNECT)
        viewModel.exitSetup()
        advanceScheduler()
        advanceScheduler()
        coVerify { healthConnectService.turnOnIntegration() }
    }

    @Test
    fun `exitSetup on COMPLETE_RECONNECTION calls handleFinish`() {
        advanceScheduler()
        forceSetupState(HealthConnectSetup.COMPLETE_RECONNECTION)
        viewModel.exitSetup()
        advanceScheduler()
        advanceScheduler()
        coVerify { healthConnectService.turnOnIntegration() }
    }

    @Test
    fun `exitSetup on PERMISSION_LIMIT navigates back`() {
        advanceScheduler()
        forceSetupState(HealthConnectSetup.PERMISSION_LIMIT)
        viewModel.exitSetup()
        advanceScheduler()
        advanceScheduler()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `exitSetup on USER_CONFLICT navigates back`() {
        advanceScheduler()
        forceSetupState(HealthConnectSetup.USER_CONFLICT)
        viewModel.exitSetup()
        advanceScheduler()
        advanceScheduler()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `exitSetup on START_CONNECT shows exit alert dialog`() {
        forceSetupState(HealthConnectSetup.START_CONNECT)
        advanceScheduler()
        viewModel.exitSetup()
        advanceScheduler()
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // openHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `openHealthConnect shows loader and calls service`() {
        advanceScheduler()
        viewModel.openHealthConnect()
        advanceScheduler()
        verify { dialogQueueService.showLoader(any()) }
        coVerify { healthConnectService.openHealthConnect(true) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // Init - determineSetupState
    // -------------------------------------------------------------------------

    @Test
    fun `init with INSTALLED and NONE permissions sets START_CONNECT`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE
        coEvery { healthConnectService.checkIfAlreadyUsed() } returns true

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.START_CONNECT)
    }

    @Test
    fun `init with INSTALLED and ALL permissions sets COMPLETE_RECONNECTION`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.ALL
        coEvery { healthConnectService.checkIfAlreadyUsed() } returns true

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.COMPLETE_RECONNECTION)
    }

    @Test
    fun `init with INSTALLED and PARTIAL permissions sets INCOMPLETE_RECONNECTION`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.PARTIAL
        coEvery { healthConnectService.checkIfAlreadyUsed() } returns true

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.INCOMPLETE_RECONNECTION)
    }

    @Test
    fun `init with user conflict sets USER_CONFLICT`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE
        coEvery { healthConnectService.checkIfAlreadyUsed() } returns false

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.USER_CONFLICT)
    }

    @Test
    fun `init with INSTALL_REQUIRED sets NONE`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.NONE)
    }

    @Test
    fun `init with UNAVAILABLE sets NONE`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.UNAVAILABLE
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.NONE)
    }

    @Test
    fun `init sets isHealthConnectAvailable`() {
        coEvery { healthConnectService.checkAvailability() } returns true
        advanceScheduler()
        assertThat(viewModel.state.value.isHealthConnectAvailable).isTrue()
    }

    @Test
    fun `init with exception sets error message`() {
        coEvery { healthConnectService.checkAvailability() } throws RuntimeException("test failure")

        viewModel = HealthConnectViewModel(
            healthConnectService = healthConnectService,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        advanceScheduler()
        assertThat(viewModel.state.value.errorMessage).contains("test failure")
    }

    // -------------------------------------------------------------------------
    // navigationEvent
    // -------------------------------------------------------------------------

    @Test
    fun `navigationEvent initial value is null`() {
        assertThat(viewModel.navigationEvent.value).isNull()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun forceSetupState(setup: HealthConnectSetup) {
        // _state is declared in BaseIntentViewModel — walk hierarchy to find it
        var clazz: Class<*>? = viewModel::class.java
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField("_state")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<HealthConnectUiState>
                stateFlow.value = stateFlow.value.copy(healthConnectSetupState = setup)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw IllegalStateException("Could not find _state field in class hierarchy")
    }
}
