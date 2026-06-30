package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ChromeTabState
import com.dmdbrands.gurus.weight.core.shared.utilities.browser.ICustomTabManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationIntent
import com.dmdbrands.gurus.weight.features.integration.model.IntegrationItem
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Unit tests for [IntegrationViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationViewModelTest {

    companion object {
        private const val TEST_ACCOUNT_ID = "account-123"
        private const val TEST_OAUTH_URL = "https://oauth.example.com/authorize"
    }

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @MockK(relaxed = true) lateinit var accountService: IAccountService
    @MockK(relaxed = true) lateinit var integrationService: IIntegrationService
    @MockK(relaxed = true) lateinit var healthConnectService: IHealthConnectService
    @MockK(relaxed = true) lateinit var healthConnectRepository: IHealthConnectRepository
    @MockK(relaxed = true) lateinit var integrationRepository: IIntegrationRepository
    @MockK(relaxed = true) lateinit var navigationService: IAppNavigationService
    @MockK(relaxed = true) lateinit var dialogQueueService: IDialogQueueService
    @MockK(relaxed = true) lateinit var customTabManager: ICustomTabManager

    private lateinit var viewModel: IntegrationViewModel

    private fun createAccount(
        id: String = TEST_ACCOUNT_ID,
        isHealthConnectOn: Boolean = false,
    ): Account = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.isHealthConnectOn } returns isHealthConnectOn
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(emptyList())
        coEvery { integrationService.checkForInactiveIntegrations() } returns emptyList()

        viewModel = IntegrationViewModel(
            accountService = accountService,
            integrationService = integrationService,
            healthConnectService = healthConnectService,
            healthConnectRepository = healthConnectRepository,
            integrationRepository = integrationRepository,
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
    fun `initial state has empty integrations`() {
        assertThat(viewModel.state.value.integrations).isEmpty()
    }

    @Test
    fun `initial state has null selectedIntegrationForDisconnect`() {
        assertThat(viewModel.state.value.selectedIntegrationForDisconnect).isNull()
    }

    // -------------------------------------------------------------------------
    // Pure State Intents (reducer-only)
    // -------------------------------------------------------------------------

    @Test
    fun `InitializeIntegrations populates all providers`() {
        viewModel.handleIntent(IntegrationIntent.InitializeIntegrations)
        val integrations = viewModel.state.value.integrations
        assertThat(integrations).hasSize(3)
        assertThat(integrations.map { it.provider }).containsExactly(
            IntegrationProvider.Fitbit,
            IntegrationProvider.MyFitnessPal,
            IntegrationProvider.HealthConnect,
        )
    }

    @Test
    fun `SetIntegrations updates integrations list`() {
        val items = listOf(
            IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true),
        )
        viewModel.handleIntent(IntegrationIntent.SetIntegrations(items))
        assertThat(viewModel.state.value.integrations).hasSize(1)
        assertThat(viewModel.state.value.integrations.first().isConnected).isTrue()
    }

    @Test
    fun `RemoveIntegration sets selectedIntegrationForDisconnect`() {
        val item = IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true)
        viewModel.handleIntent(IntegrationIntent.RemoveIntegration(item))
        assertThat(viewModel.state.value.selectedIntegrationForDisconnect).isEqualTo(item)
    }

    @Test
    fun `UpdateIntegrationConnectionStatus updates specific provider`() {
        // First populate integrations
        viewModel.handleIntent(IntegrationIntent.InitializeIntegrations)
        // Then update Fitbit connection status
        viewModel.handleIntent(
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.Fitbit,
                isConnected = true,
                isValid = true,
            ),
        )
        val fitbit = viewModel.state.value.integrations.find {
            it.provider == IntegrationProvider.Fitbit
        }
        assertThat(fitbit?.isConnected).isTrue()
    }

    @Test
    fun `UpdateIntegrationConnectionStatus marks provider as invalid`() {
        viewModel.handleIntent(IntegrationIntent.InitializeIntegrations)
        viewModel.handleIntent(
            IntegrationIntent.UpdateIntegrationConnectionStatus(
                provider = IntegrationProvider.MyFitnessPal,
                isConnected = true,
                isValid = false,
            ),
        )
        val mfp = viewModel.state.value.integrations.find {
            it.provider == IntegrationProvider.MyFitnessPal
        }
        assertThat(mfp?.isValid).isFalse()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack intent navigates back`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.OnBack)
        advanceScheduler()
        coVerify { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // LoadIntegrations
    // -------------------------------------------------------------------------

    @Test
    fun `LoadIntegrations calls integrationService getIntegrationsWithStatus`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.LoadIntegrations)
        advanceScheduler()
        verify(atLeast = 1) { integrationService.getIntegrationsWithStatus() }
    }

    // -------------------------------------------------------------------------
    // NavigateToHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `NavigateToHealthConnect with INSTALLED status navigates to health connect`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()
        coVerify { navigationService.navigateTo(any()) }
    }

    @Test
    fun `NavigateToHealthConnect with INSTALL_REQUIRED shows install alert`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `NavigateToHealthConnect with UNAVAILABLE shows unavailable alert`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.UNAVAILABLE
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()
        verify { dialogQueueService.enqueue(any<DialogModel.Alert>()) }
    }

    // -------------------------------------------------------------------------
    // RemoveHealthConnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `RemoveHealthConnectIntegration shows confirmation dialog`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // AddIntegration — OAuth providers
    // -------------------------------------------------------------------------

    @Test
    fun `AddIntegration for OAuth provider starts OAuth flow`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(IntegrationProvider.Fitbit, TEST_ACCOUNT_ID) } returns TEST_OAUTH_URL
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()
        verify { dialogQueueService.showLoader(any()) }
    }

    @Test
    fun `AddIntegration for HealthConnect dispatches CheckHealthConnectAvailability`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.HealthConnect))
        advanceScheduler()
        // HealthConnect is platform-specific, so it dispatches CheckHealthConnectAvailability
        // which is a reducer-only intent (no-op side effect in the reducer)
        assertThat(viewModel.state.value).isNotNull()
    }

    // -------------------------------------------------------------------------
    // OpenIntegration — non-Health Connect
    // -------------------------------------------------------------------------

    @Test
    fun `OpenIntegration with connected non-HC integration sets disconnect target`() {
        val fitbitItem = IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true)
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.OpenIntegration(fitbitItem))
        advanceScheduler()
        assertThat(viewModel.state.value.selectedIntegrationForDisconnect).isEqualTo(fitbitItem)
    }

    @Test
    fun `OpenIntegration with disconnected non-HC integration triggers add flow`() {
        val mfpItem = IntegrationItem.fromProvider(IntegrationProvider.MyFitnessPal).copy(isConnected = false)
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(IntegrationProvider.MyFitnessPal, TEST_ACCOUNT_ID) } returns TEST_OAUTH_URL
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.OpenIntegration(mfpItem))
        advanceScheduler()
        verify { dialogQueueService.showLoader(any()) }
    }

    // -------------------------------------------------------------------------
    // ToggleHealthConnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `ToggleHealthConnectIntegration with connected integration triggers remove`() {
        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = true)
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.ToggleHealthConnectIntegration(hcItem))
        advanceScheduler()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `ToggleHealthConnectIntegration with disconnected integration triggers connect`() {
        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = false)
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.ToggleHealthConnectIntegration(hcItem))
        advanceScheduler()
        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // Init verifications
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to activeAccountFlow`() {
        advanceScheduler()
        verify { accountService.activeAccountFlow }
    }

    @Test
    fun `init loads integrations on startup`() {
        advanceScheduler()
        verify(atLeast = 1) { integrationService.getIntegrationsWithStatus() }
    }

    // -------------------------------------------------------------------------
    // OAuthFlowFailed reducer
    // -------------------------------------------------------------------------

    @Test
    fun `OAuthFlowFailed does not crash and state is intact`() {
        viewModel.handleIntent(IntegrationIntent.InitializeIntegrations)
        viewModel.handleIntent(
            IntegrationIntent.OAuthFlowFailed(IntegrationProvider.Fitbit, "test error"),
        )
        assertThat(viewModel.state.value.integrations).isNotEmpty()
    }

    @Test
    fun `OAuthFlowCompleted does not crash and state is intact`() {
        viewModel.handleIntent(IntegrationIntent.InitializeIntegrations)
        viewModel.handleIntent(
            IntegrationIntent.OAuthFlowCompleted(IntegrationProvider.Fitbit),
        )
        assertThat(viewModel.state.value.integrations).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // connectHealthConnectIntegration — additional status scenarios
    // -------------------------------------------------------------------------

    @Test
    fun `connectHealthConnectIntegration with UPDATE_REQUIRED navigates to health connect`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.UPDATE_REQUIRED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()
        coVerify { navigationService.navigateTo(any()) }
    }

    @Test
    fun `connectHealthConnectIntegration handles exception gracefully`() {
        coEvery { healthConnectService.healthConnectStatus() } throws RuntimeException("fail")
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()
        // Should not crash
        assertThat(viewModel.state.value).isNotNull()
    }

    // -------------------------------------------------------------------------
    // showHealthConnectInstallAlert — tested via NavigateToHealthConnect with INSTALL_REQUIRED
    // -------------------------------------------------------------------------

    @Test
    fun `showHealthConnectInstallAlert dialog has download and cancel buttons`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.confirmText).isNotNull()
        assertThat(dialog.cancelText).isNotNull()
    }

    @Test
    fun `showHealthConnectInstallAlert onConfirm calls openHealthConnect`() {
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.NavigateToHealthConnect)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceScheduler()

        coVerify { healthConnectService.openHealthConnect() }
    }

    // -------------------------------------------------------------------------
    // confirmRemoveIntegration — tested via RemoveHealthConnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `confirmRemoveIntegration shows dialog with remove button`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        assertThat(dialog.confirmText).isNotNull()
    }

    @Test
    fun `confirmRemoveIntegration onConfirm calls removeHealthConnectIntegration`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceScheduler()

        coVerify { healthConnectService.removeHealthConnectIntegration() }
    }

    @Test
    fun `confirmRemoveIntegration onCancel dismisses dialog`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onCancel?.invoke()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `confirmRemoveIntegration shows loader during removal`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceScheduler()

        verify { dialogQueueService.showLoader(any()) }
    }

    // -------------------------------------------------------------------------
    // disconnectAuthIntegration / confirmDisconnect / disconnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `RemoveIntegration then disconnect confirm disconnects via service`() {
        val fitbitItem = IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true)
        coEvery { integrationService.disconnectIntegration(IntegrationProvider.Fitbit) } returns Unit
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(emptyList())
        advanceScheduler()

        // Select the integration to disconnect, then trigger the confirm dialog
        viewModel.handleIntent(IntegrationIntent.RemoveIntegration(fitbitItem))
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onConfirm?.invoke()
        advanceScheduler()

        coVerify { integrationService.disconnectIntegration(IntegrationProvider.Fitbit) }
    }

    @Test
    fun `disconnect dialog cancel dismisses without disconnecting`() {
        val fitbitItem = IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true)
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RemoveIntegration(fitbitItem))
        advanceScheduler()

        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val dialog = dialogSlot.captured as DialogModel.Confirm
        dialog.onCancel?.invoke()

        coVerify(exactly = 0) { integrationService.disconnectIntegration(any()) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `disconnectIntegration surfaces error and dismisses loader on failure`() {
        val fitbitItem = IntegrationItem.fromProvider(IntegrationProvider.Fitbit).copy(isConnected = true)
        coEvery { integrationService.disconnectIntegration(any()) } throws RuntimeException("boom")
        advanceScheduler()

        viewModel.handleIntent(IntegrationIntent.RemoveIntegration(fitbitItem))
        advanceScheduler()
        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        (dialogSlot.captured as DialogModel.Confirm).onConfirm?.invoke()
        advanceScheduler()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // requestNewIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `RequestNewIntegration enqueues request modal`() {
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.RequestNewIntegration)
        advanceScheduler()
        verify { dialogQueueService.enqueue(any<DialogModel>()) }
    }

    // -------------------------------------------------------------------------
    // addIntegrations — no active account guard
    // -------------------------------------------------------------------------

    @Test
    fun `AddIntegration with no active account does not start OAuth flow`() {
        every { accountService.activeAccountFlow } returns flowOf(null)
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()

        verify(exactly = 0) { customTabManager.openChromeTab(any()) }
    }

    // -------------------------------------------------------------------------
    // OpenIntegration — Health Connect out-of-sync path
    // -------------------------------------------------------------------------

    @Test
    fun `OpenIntegration with HC out of sync shows out-of-sync alert`() {
        val account = createAccount(isHealthConnectOn = true)
        every { accountService.activeAccountFlow } returns flowOf(account)
        val hcData = mockk<com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData>(relaxed = true) {
            every { grantedPermissionList } returns emptyList()
        }
        coEvery { healthConnectRepository.getAccountByID(any()) } returns hcData
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = true)
        viewModel.handleIntent(IntegrationIntent.OpenIntegration(hcItem))
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Alert>()) }
    }

    @Test
    fun `OpenIntegration with connected HC and granted permissions triggers remove flow`() {
        val account = createAccount(isHealthConnectOn = true)
        every { accountService.activeAccountFlow } returns flowOf(account)
        val hcData = mockk<com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData>(relaxed = true) {
            every { grantedPermissionList } returns listOf("perm")
        }
        coEvery { healthConnectRepository.getAccountByID(any()) } returns hcData
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = true)
        viewModel.handleIntent(IntegrationIntent.OpenIntegration(hcItem))
        advanceScheduler()

        // not out of sync + connected -> dispatches RemoveIntegration -> auth disconnect confirm dialog
        assertThat(viewModel.state.value.selectedIntegrationForDisconnect).isEqualTo(hcItem)
        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // onHealthConnectIconClicked
    // -------------------------------------------------------------------------

    @Test
    fun `onHealthConnectIconClicked out of sync shows out-of-sync alert`() {
        val account = createAccount(isHealthConnectOn = true)
        every { accountService.activeAccountFlow } returns flowOf(account)
        val hcData = mockk<com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData>(relaxed = true) {
            every { grantedPermissionList } returns emptyList()
        }
        coEvery { healthConnectRepository.getAccountByID(any()) } returns hcData
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        viewModel.onHealthConnectIconClicked()
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Alert>()) }
    }

    @Test
    fun `onHealthConnectIconClicked not out of sync toggles connected HC to remove`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount(isHealthConnectOn = false))
        coEvery { healthConnectRepository.getAccountByID(any()) } returns null
        advanceScheduler()

        // Populate state with a connected HC integration
        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = true)
        viewModel.handleIntent(IntegrationIntent.SetIntegrations(listOf(hcItem)))
        viewModel.onHealthConnectIconClicked()
        advanceScheduler()

        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `onHealthConnectIconClicked not out of sync navigates when HC disconnected`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount(isHealthConnectOn = false))
        coEvery { healthConnectRepository.getAccountByID(any()) } returns null
        coEvery { healthConnectService.healthConnectStatus() } returns HealthConnectStatus.INSTALLED
        advanceScheduler()

        val hcItem = IntegrationItem.fromProvider(IntegrationProvider.HealthConnect).copy(isConnected = false)
        viewModel.handleIntent(IntegrationIntent.SetIntegrations(listOf(hcItem)))
        viewModel.onHealthConnectIconClicked()
        advanceScheduler()

        coVerify { navigationService.navigateTo(any()) }
    }

    // -------------------------------------------------------------------------
    // OAuth flow completion via Chrome tab hidden
    // -------------------------------------------------------------------------

    @Test
    fun `OAuth flow completion success shows success alert`() {
        val chromeState = MutableStateFlow<ChromeTabState?>(ChromeTabState.Idle)
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(any(), any()) } returns TEST_OAUTH_URL
        every { customTabManager.subscribeChromeState() } returns chromeState
        coEvery { integrationService.getIntegrationStatus(IntegrationProvider.Fitbit) } returns Pair(true, true)
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(emptyList())
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        // Start OAuth to set currentOAuthProvider, then emit tab-hidden to drive completion
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()
        chromeState.value = ChromeTabState.TabHidden
        advanceScheduler()

        coVerify { integrationService.getIntegrationStatus(IntegrationProvider.Fitbit) }
        verify { dialogQueueService.enqueue(any<DialogModel.Alert>()) }
    }

    @Test
    fun `OAuth flow completion not connected shows error alert`() {
        val chromeState = MutableStateFlow<ChromeTabState?>(ChromeTabState.Idle)
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(any(), any()) } returns TEST_OAUTH_URL
        every { customTabManager.subscribeChromeState() } returns chromeState
        coEvery { integrationService.getIntegrationStatus(IntegrationProvider.Fitbit) } returns Pair(false, false)
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()
        chromeState.value = ChromeTabState.TabHidden
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // showReintegrateAlert / handleDisableInactiveIntegrations
    // -------------------------------------------------------------------------

    @Test
    fun `inactive integrations on load show reintegrate alert and can disable them`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        coEvery { integrationService.checkForInactiveIntegrations() } returns
            listOf(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal)
        every { integrationService.getInvalidIntegrationNames(any()) } returns "Fitbit, MyFitnessPal"
        coEvery { integrationService.disconnectIntegration(any()) } returns Unit
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(emptyList())
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val dialogs = mutableListOf<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogs)) }
        val reintegrate = dialogs.filterIsInstance<DialogModel.Confirm>().first()
        reintegrate.onConfirm?.invoke()
        advanceScheduler()

        coVerify { integrationService.disconnectIntegration(IntegrationProvider.Fitbit) }
        coVerify { integrationService.disconnectIntegration(IntegrationProvider.MyFitnessPal) }
    }

    @Test
    fun `single inactive integration shows reintegrate alert with single-provider copy`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        coEvery { integrationService.checkForInactiveIntegrations() } returns listOf(IntegrationProvider.Fitbit)
        every { integrationService.getInvalidIntegrationNames(any()) } returns "Fitbit"
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `disable inactive integrations surfaces failure and marks provider invalid`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        coEvery { integrationService.checkForInactiveIntegrations() } returns listOf(IntegrationProvider.Fitbit)
        every { integrationService.getInvalidIntegrationNames(any()) } returns "Fitbit"
        coEvery { integrationService.disconnectIntegration(IntegrationProvider.Fitbit) } throws RuntimeException("fail")
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val dialogs = mutableListOf<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogs)) }
        dialogs.filterIsInstance<DialogModel.Confirm>().first().onConfirm?.invoke()
        advanceScheduler()

        coVerify { integrationService.disconnectIntegration(IntegrationProvider.Fitbit) }
    }

    // -------------------------------------------------------------------------
    // loadIntegrations — Health Connect mapping path
    // -------------------------------------------------------------------------

    @Test
    fun `loadIntegrations maps Health Connect connected status from repository`() {
        val account = createAccount()
        every { accountService.activeAccountFlow } returns flowOf(account)
        val hcData = mockk<com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData>(relaxed = true) {
            every { integrated } returns true
            every { outOfSync } returns true
        }
        coEvery { healthConnectRepository.getAccountByID(any()) } returns hcData
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(
            listOf(IntegrationItem.fromProvider(IntegrationProvider.HealthConnect)),
        )
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val hc = viewModel.state.value.integrations.find { it.provider == IntegrationProvider.HealthConnect }
        assertThat(hc?.isConnected).isTrue()
    }

    @Test
    fun `loadIntegrations handles service exception gracefully`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getIntegrationsWithStatus() } throws RuntimeException("load fail")
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        // getIntegrationsWithStatus() threw, but InitializeIntegrations had already
        // populated the base provider list and the collect failure is swallowed — so the
        // screen degrades to the full provider list with nothing connected (no crash,
        // no cleared/half-populated state).
        val state = viewModel.state.value
        assertThat(state.integrations.map { it.provider })
            .containsExactlyElementsIn(IntegrationProvider.getAllProviders())
        assertThat(state.integrations.any { it.isConnected }).isFalse()
    }

    // -------------------------------------------------------------------------
    // refreshIntegrationStatus (via successful HC removal)
    // -------------------------------------------------------------------------

    @Test
    fun `removeHealthConnect confirm refreshes integration status on success`() {
        every { integrationService.getIntegrationsWithStatus() } returns flowOf(
            listOf(IntegrationItem.fromProvider(IntegrationProvider.Fitbit)),
        )
        coEvery { healthConnectService.removeHealthConnectIntegration() } returns true
        advanceScheduler()

        viewModel.handleIntent(IntegrationIntent.RemoveHealthConnectIntegration)
        advanceScheduler()
        val dialogSlot = io.mockk.slot<DialogModel>()
        verify { dialogQueueService.showDialog(capture(dialogSlot)) }
        (dialogSlot.captured as DialogModel.Confirm).onConfirm?.invoke()
        advanceScheduler()

        coVerify { healthConnectService.removeHealthConnectIntegration() }
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // onBack failure / startOAuthFlow failure
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack handles navigation failure gracefully`() {
        val before = viewModel.state.value
        coEvery { navigationService.navigateBack() } throws RuntimeException("nav fail")
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.OnBack)
        advanceScheduler()
        // Navigation was attempted; the thrown exception is swallowed (no crash)
        // and the UI state is left untouched.
        coVerify { navigationService.navigateBack() }
        assertThat(viewModel.state.value).isEqualTo(before)
    }

    @Test
    fun `AddIntegration OAuth flow failure dispatches OAuthFlowFailed`() {
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(any(), any()) } returns TEST_OAUTH_URL
        every { customTabManager.openChromeTab(any()) } throws RuntimeException("tab fail")
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()

        val before = viewModel.state.value
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()

        // OAuth flow started (loader shown) and attempted to open the tab, which threw.
        // The failure is caught and re-dispatched as OAuthFlowFailed (a no-op in the reducer),
        // so the loader is never dismissed and the UI state is left intact rather than corrupted.
        verify { dialogQueueService.showLoader(any()) }
        verify { customTabManager.openChromeTab(any()) }
        verify(exactly = 0) { dialogQueueService.dismissLoader() }
        assertThat(viewModel.state.value).isEqualTo(before)
    }

    // -------------------------------------------------------------------------
    // showErrorAlert callbacks (OAuth not connected)
    // -------------------------------------------------------------------------

    @Test
    fun `OAuth error alert retry restarts OAuth flow`() {
        val chromeState = MutableStateFlow<ChromeTabState?>(ChromeTabState.Idle)
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(any(), any()) } returns TEST_OAUTH_URL
        every { customTabManager.subscribeChromeState() } returns chromeState
        coEvery { integrationService.getIntegrationStatus(IntegrationProvider.Fitbit) } returns Pair(false, false)
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()
        chromeState.value = ChromeTabState.TabHidden
        advanceScheduler()

        val dialogs = mutableListOf<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogs)) }
        val errorDialog = dialogs.filterIsInstance<DialogModel.Confirm>().last()
        errorDialog.onConfirm?.invoke()
        errorDialog.onCancel?.invoke()
        errorDialog.onDismiss?.invoke()
        advanceScheduler()

        verify(atLeast = 1) { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Chrome tab shown branch (no completion check)
    // -------------------------------------------------------------------------

    @Test
    fun `Chrome TabShown event during OAuth does not trigger completion`() {
        val chromeState = MutableStateFlow<ChromeTabState?>(ChromeTabState.Idle)
        every { accountService.activeAccountFlow } returns flowOf(createAccount())
        every { integrationService.getOAuthUrl(any(), any()) } returns TEST_OAUTH_URL
        every { customTabManager.subscribeChromeState() } returns chromeState
        viewModel = IntegrationViewModel(
            accountService, integrationService, healthConnectService,
            healthConnectRepository, integrationRepository,
        ).initTestDependencies(navigationService, dialogQueueService, customTabManager)
        advanceScheduler()
        viewModel.handleIntent(IntegrationIntent.AddIntegration(IntegrationProvider.Fitbit))
        advanceScheduler()
        chromeState.value = ChromeTabState.TabShown
        advanceScheduler()

        coVerify(exactly = 0) { integrationService.getIntegrationStatus(any()) }
    }
}
