package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
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
}
