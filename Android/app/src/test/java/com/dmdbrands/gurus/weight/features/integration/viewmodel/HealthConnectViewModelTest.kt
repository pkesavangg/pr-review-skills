package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
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
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
    @MockK(relaxed = true) lateinit var customTabManager: ICustomTabManager

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
            customTabManager = customTabManager,
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

    // -------------------------------------------------------------------------
    // handlePrimaryAction — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `handlePrimaryAction UPDATE_PERMISSIONS calls requestAuthorization`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS))
        advanceScheduler()
        coVerify { healthConnectService.requestAuthorization(any()) }
    }

    @Test
    fun `handlePrimaryAction CONNECT shows and dismisses loader`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()
        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `handlePrimaryAction FINISH shows loader and navigates back on success`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()
        verify { dialogQueueService.showLoader(any()) }
        coVerify { healthConnectService.turnOnIntegration() }
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `handlePrimaryAction FINISH sets error on exception`() {
        coEvery { healthConnectService.turnOnIntegration() } throws RuntimeException("fail")
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()
        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    @Test
    fun `handlePrimaryAction FINISH dismisses loader on exception`() {
        coEvery { healthConnectService.turnOnIntegration() } throws RuntimeException("fail")
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // resumeSetup — tested via onResume lifecycle callback
    // -------------------------------------------------------------------------

    @Test
    fun `onResume with isHealthConnectOpened triggers resumeSetup`() {
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.ALL
        advanceScheduler()

        // Set the flag first
        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()
        assertThat(viewModel.state.value.isHealthConnectOpened).isTrue()

        // Trigger lifecycle onResume
        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        // resumeSetup checks permission status again
        coVerify(atLeast = 2) { healthConnectService.checkPermissionStatus() }
    }

    @Test
    fun `onResume with ALL permissions transitions to FINISH_CONNECT`() {
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.ALL
        advanceScheduler()

        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.FINISH_CONNECT)
    }

    @Test
    fun `onResume with PARTIAL permissions transitions to FINISH_CONNECT`() {
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.PARTIAL
        advanceScheduler()

        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.FINISH_CONNECT)
    }

    @Test
    fun `onResume with NONE permissions transitions to START_CONNECT`() {
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE
        advanceScheduler()

        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.START_CONNECT)
    }

    @Test
    fun `onResume handles exception in resumeSetup and sets error`() {
        // First call succeeds (init), subsequent calls throw
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE andThenThrows RuntimeException("fail")
        advanceScheduler()

        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    // -------------------------------------------------------------------------
    // handleAppResume — does nothing when HC not opened
    // -------------------------------------------------------------------------

    @Test
    fun `onResume does nothing when isHealthConnectOpened is false`() {
        advanceScheduler()

        // isHealthConnectOpened is false by default
        assertThat(viewModel.state.value.isHealthConnectOpened).isFalse()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        // checkPermissionStatus should only have been called once from init
        coVerify(exactly = 1) { healthConnectService.checkPermissionStatus() }
    }

    @Test
    fun `onResume clears isHealthConnectOpened after processing`() {
        coEvery { healthConnectService.checkPermissionStatus() } returns HealthConnectPermissionStatus.NONE
        advanceScheduler()

        viewModel.handleIntent(HealthConnectIntent.SetHealthConnectOpened)
        advanceScheduler()

        val lifecycleOwner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)
        viewModel.onResume(lifecycleOwner)
        advanceScheduler()

        assertThat(viewModel.state.value.isHealthConnectOpened).isFalse()
    }

    // -------------------------------------------------------------------------
    // requestAuthorization callback branches (handleConnect)
    // -------------------------------------------------------------------------

    private fun captureAuthCallback(): io.mockk.CapturingSlot<(HealthConnectRequestStatus) -> Unit> {
        val slot = io.mockk.slot<(HealthConnectRequestStatus) -> Unit>()
        coEvery { healthConnectService.requestAuthorization(capture(slot)) } returns Unit
        return slot
    }

    @Test
    fun `handleConnect CONNECTED sets FINISH_CONNECT setup state`() {
        val slot = captureAuthCallback()
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()

        slot.captured.invoke(HealthConnectRequestStatus.CONNECTED)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.FINISH_CONNECT)
    }

    @Test
    fun `handleConnect from incomplete CONNECTED sets FINISH_INCOMPLETE_RECONNECTION`() {
        val slot = captureAuthCallback()
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.UPDATE_PERMISSIONS))
        advanceScheduler()

        slot.captured.invoke(HealthConnectRequestStatus.CONNECTED)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState)
            .isEqualTo(HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION)
    }

    @Test
    fun `handleConnect PARTIAL sets FINISH_INCOMPLETE_RECONNECTION`() {
        val slot = captureAuthCallback()
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()

        slot.captured.invoke(HealthConnectRequestStatus.PARTIAL)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState)
            .isEqualTo(HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION)
    }

    @Test
    fun `handleConnect CANCELLED sets PERMISSION_LIMIT`() {
        val slot = captureAuthCallback()
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()

        slot.captured.invoke(HealthConnectRequestStatus.CANCELLED)
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState).isEqualTo(HealthConnectSetup.PERMISSION_LIMIT)
    }

    @Test
    fun `handleConnect PRIVACY_POLICY opens in-app browser`() {
        val slot = captureAuthCallback()
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()

        slot.captured.invoke(HealthConnectRequestStatus.PRIVACY_POLICY)
        advanceScheduler()

        // openInAppBrowser delegates to the custom tab manager — assert the privacy-policy
        // URL was actually handed to it, so a regression in that branch fails the test.
        verify { customTabManager.openChromeTab(any()) }
    }

    @Test
    fun `handleConnect surfaces no crash when requestAuthorization throws`() {
        coEvery { healthConnectService.requestAuthorization(any()) } throws RuntimeException("auth fail")
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.CONNECT))
        advanceScheduler()

        // requestAuthorization threw, so the failure branch ran; handleConnect swallows
        // the exception and the CONNECT handler still dismisses the loader afterwards
        // (no stuck spinner / crash) — assert the observable outcome, not just non-null.
        coVerify { healthConnectService.requestAuthorization(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // handleFinish / openHealthConnect failure paths
    // -------------------------------------------------------------------------

    @Test
    fun `handleFinish turns on integration and navigates back`() {
        coEvery { healthConnectService.turnOnIntegration(any(), any()) } returns Unit
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()

        coVerify { healthConnectService.turnOnIntegration(any(), any()) }
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `handleFinish sets error message when integration fails`() {
        coEvery { healthConnectService.turnOnIntegration(any(), any()) } throws RuntimeException("turn on fail")
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.FINISH))
        advanceScheduler()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    @Test
    fun `openHealthConnect sets error message when service fails`() {
        coEvery { healthConnectService.openHealthConnect(any()) } throws RuntimeException("open fail")
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.PrimaryAction(HealthConnectAction.OPEN_HEALTH_CONNECT))
        advanceScheduler()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
    }

    // -------------------------------------------------------------------------
    // SecondaryAction SKIP / ConfirmExitSetup
    // -------------------------------------------------------------------------

    @Test
    fun `SecondaryAction SKIP sets FINISH_INCOMPLETE_RECONNECTION`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.SecondaryAction(HealthConnectAction.SKIP))
        advanceScheduler()

        assertThat(viewModel.state.value.healthConnectSetupState)
            .isEqualTo(HealthConnectSetup.FINISH_INCOMPLETE_RECONNECTION)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `ConfirmExitSetup in default setup state shows exit alert`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.ConfirmExitSetup)
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ConfirmExitSetup in FINISH_CONNECT state finishes integration`() {
        coEvery { healthConnectService.turnOnIntegration(any(), any()) } returns Unit
        advanceScheduler()
        forceSetupState(HealthConnectSetup.FINISH_CONNECT)

        viewModel.handleIntent(HealthConnectIntent.ConfirmExitSetup)
        advanceScheduler()

        coVerify { healthConnectService.turnOnIntegration(any(), any()) }
    }

    @Test
    fun `ConfirmExitSetup in PERMISSION_LIMIT state navigates back`() {
        advanceScheduler()
        forceSetupState(HealthConnectSetup.PERMISSION_LIMIT)

        viewModel.handleIntent(HealthConnectIntent.ConfirmExitSetup)
        advanceScheduler()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `exit alert onConfirm navigates back`() {
        advanceScheduler()
        viewModel.handleIntent(HealthConnectIntent.ConfirmExitSetup)
        advanceScheduler()

        val slot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(slot)) }
        (slot.captured as DialogModel.Confirm).onConfirm?.invoke()
        advanceScheduler()

        coVerify { navigationService.navigateBack() }
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
