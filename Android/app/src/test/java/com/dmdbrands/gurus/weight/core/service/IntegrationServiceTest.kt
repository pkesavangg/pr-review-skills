package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.integration.IntegrationProvider
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationServiceTest {

    companion object {
        private const val TEST_ACCOUNT_ID = "test-account-id"
        private const val TEST_OAUTH_URL = "https://fitbit.com/oauth/authorize?id=123"
    }

    private val connectivityObserver: IConnectivityObserver = mockk(relaxed = true)
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val accountService: IAccountService = mockk(relaxed = true)
    private val integrationRepository: IIntegrationRepository = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var service: IntegrationService

    private val fakeAccount = Account(
        id = TEST_ACCOUNT_ID,
        firstName = "Test",
        lastName = "User",
        dob = "1990-01-01",
        email = "test@test.com",
        gender = "male",
        zipcode = "12345",
        height = 170,
        activityLevel = "moderate",
        weightUnit = com.dmdbrands.gurus.weight.domain.model.common.WeightUnit.LB,
    )

    @Before
    fun setUp() {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(
            available = true,
            unAvailable = false,
        )
        // Return a flow that never emits true to avoid init block check
        every { accountService.checkIntegrations } returns MutableStateFlow(false)
        coEvery { accountService.getCurrentAccount() } returns fakeAccount

        service = IntegrationService(
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            accountService = accountService,
            integrationRepository = integrationRepository,
            healthConnectRepository = healthConnectRepository,
            appScope = testScope,
        )
    }

    // -------------------------------------------------------------------------
    // integrationState — initial value
    // -------------------------------------------------------------------------

    @Test
    fun `integrationState initial value is Fitbit not connected`() = runTest {
        val item = service.integrationState.first()
        assertThat(item.provider).isEqualTo(IntegrationProvider.Fitbit)
        assertThat(item.isConnected).isFalse()
        assertThat(item.isValid).isFalse()
    }

    // -------------------------------------------------------------------------
    // getIntegrationsWithStatus
    // -------------------------------------------------------------------------

    @Test
    fun `getIntegrationsWithStatus returns three integrations when account exists`() = runTest {
        coEvery { healthConnectRepository.getAccountByID(TEST_ACCOUNT_ID) } returns null

        val result = service.getIntegrationsWithStatus().toList()

        assertThat(result).hasSize(1)
        val integrations = result[0]
        assertThat(integrations).hasSize(3)
        assertThat(integrations[0].provider).isEqualTo(IntegrationProvider.Fitbit)
        assertThat(integrations[1].provider).isEqualTo(IntegrationProvider.MyFitnessPal)
        assertThat(integrations[2].provider).isEqualTo(IntegrationProvider.HealthConnect)
    }

    @Test
    fun `getIntegrationsWithStatus returns empty when no account`() = runTest {
        coEvery { accountService.getCurrentAccount() } returns null
        coEvery { healthConnectRepository.getAccountByID(any()) } returns null

        val result = service.getIntegrationsWithStatus().toList()

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEmpty()
    }

    @Test
    fun `getIntegrationsWithStatus reflects Fitbit connection status`() = runTest {
        val connectedAccount = fakeAccount.copy(isFitbitOn = true, isFitbitValid = true)
        coEvery { accountService.getCurrentAccount() } returns connectedAccount
        coEvery { healthConnectRepository.getAccountByID(TEST_ACCOUNT_ID) } returns null

        val result = service.getIntegrationsWithStatus().toList()

        val fitbit = result[0].first { it.provider == IntegrationProvider.Fitbit }
        assertThat(fitbit.isConnected).isTrue()
        assertThat(fitbit.isValid).isTrue()
    }

    // -------------------------------------------------------------------------
    // connectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `connectIntegration returns null for non-OAuth provider`() = runTest {
        val result = service.connectIntegration(IntegrationProvider.HealthConnect, TEST_ACCOUNT_ID)
        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // disconnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `disconnectIntegration shows loader and dismisses it`() = runTest {
        service.disconnectIntegration(IntegrationProvider.Fitbit)

        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `disconnectIntegration calls removeIntegration on repository`() = runTest {
        service.disconnectIntegration(IntegrationProvider.Fitbit)

        coVerify { integrationRepository.removeIntegration("fitbit", any()) }
    }

    @Test
    fun `disconnectIntegration refreshes account after removal`() = runTest {
        service.disconnectIntegration(IntegrationProvider.MyFitnessPal)

        coVerify { accountService.refreshAccount() }
    }

    @Test
    fun `disconnectIntegration shows success toast`() = runTest {
        val toastSlot = slot<Toast>()
        every { dialogQueueService.showToast(capture(toastSlot)) } returns Unit

        service.disconnectIntegration(IntegrationProvider.Fitbit)

        assertThat(toastSlot.captured.message).isNotEmpty()
    }

    @Test
    fun `disconnectIntegration dismisses loader on exception`() = runTest {
        coEvery { integrationRepository.removeIntegration(any(), any()) } throws RuntimeException("API error")

        try {
            service.disconnectIntegration(IntegrationProvider.Fitbit)
        } catch (_: Exception) {
            // expected
        }

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // getIntegrationStatus
    // -------------------------------------------------------------------------

    @Test
    fun `getIntegrationStatus returns Fitbit status from account`() = runTest {
        val account = fakeAccount.copy(isFitbitOn = true, isFitbitValid = false)
        coEvery { accountService.getCurrentAccount() } returns account

        val (isConnected, isValid) = service.getIntegrationStatus(IntegrationProvider.Fitbit)

        assertThat(isConnected).isTrue()
        assertThat(isValid).isFalse()
    }

    @Test
    fun `getIntegrationStatus returns MFP status from account`() = runTest {
        val account = fakeAccount.copy(isMFPOn = true, isMFPValid = true)
        coEvery { accountService.getCurrentAccount() } returns account

        val (isConnected, isValid) = service.getIntegrationStatus(IntegrationProvider.MyFitnessPal)

        assertThat(isConnected).isTrue()
        assertThat(isValid).isTrue()
    }

    @Test
    fun `getIntegrationStatus returns HealthConnect status from account`() = runTest {
        val account = fakeAccount.copy(isHealthConnectOn = true)
        coEvery { accountService.getCurrentAccount() } returns account

        val (isConnected, isValid) = service.getIntegrationStatus(IntegrationProvider.HealthConnect)

        assertThat(isConnected).isTrue()
        assertThat(isValid).isTrue()
    }

    @Test
    fun `getIntegrationStatus returns false pair when no account`() = runTest {
        coEvery { accountService.getCurrentAccount() } returns null

        val (isConnected, isValid) = service.getIntegrationStatus(IntegrationProvider.Fitbit)

        assertThat(isConnected).isFalse()
        assertThat(isValid).isFalse()
    }

    @Test
    fun `getIntegrationStatus returns false pair on exception`() = runTest {
        coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("fail")

        val (isConnected, isValid) = service.getIntegrationStatus(IntegrationProvider.Fitbit)

        assertThat(isConnected).isFalse()
        assertThat(isValid).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkForInactiveIntegrations
    // -------------------------------------------------------------------------

    @Test
    fun `checkForInactiveIntegrations returns empty when no account`() = runTest {
        coEvery { accountService.getCurrentAccount() } returns null

        val result = service.checkForInactiveIntegrations()

        assertThat(result).isEmpty()
    }

    @Test
    fun `checkForInactiveIntegrations finds inactive Fitbit`() = runTest {
        val account = fakeAccount.copy(isFitbitOn = true, isFitbitValid = false)
        coEvery { accountService.getCurrentAccount() } returns account

        val result = service.checkForInactiveIntegrations()

        assertThat(result).containsExactly(IntegrationProvider.Fitbit)
    }

    @Test
    fun `checkForInactiveIntegrations finds inactive MFP`() = runTest {
        val account = fakeAccount.copy(isMFPOn = true, isMFPValid = false)
        coEvery { accountService.getCurrentAccount() } returns account

        val result = service.checkForInactiveIntegrations()

        assertThat(result).containsExactly(IntegrationProvider.MyFitnessPal)
    }

    @Test
    fun `checkForInactiveIntegrations finds both inactive when both are invalid`() = runTest {
        val account = fakeAccount.copy(
            isFitbitOn = true, isFitbitValid = false,
            isMFPOn = true, isMFPValid = false,
        )
        coEvery { accountService.getCurrentAccount() } returns account

        val result = service.checkForInactiveIntegrations()

        assertThat(result).containsExactly(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal)
    }

    @Test
    fun `checkForInactiveIntegrations returns empty when all valid`() = runTest {
        val account = fakeAccount.copy(
            isFitbitOn = true, isFitbitValid = true,
            isMFPOn = true, isMFPValid = true,
        )
        coEvery { accountService.getCurrentAccount() } returns account

        val result = service.checkForInactiveIntegrations()

        assertThat(result).isEmpty()
    }

    @Test
    fun `checkForInactiveIntegrations returns empty when integrations off`() = runTest {
        val account = fakeAccount.copy(
            isFitbitOn = false, isFitbitValid = false,
            isMFPOn = false, isMFPValid = false,
        )
        coEvery { accountService.getCurrentAccount() } returns account

        val result = service.checkForInactiveIntegrations()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getInvalidIntegrationNames
    // -------------------------------------------------------------------------

    @Test
    fun `getInvalidIntegrationNames returns empty string for empty list`() {
        val result = service.getInvalidIntegrationNames(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `getInvalidIntegrationNames returns single provider name`() {
        val result = service.getInvalidIntegrationNames(listOf(IntegrationProvider.Fitbit))
        assertThat(result).isEqualTo("Fitbit")
    }

    @Test
    fun `getInvalidIntegrationNames joins two providers with and`() {
        val result = service.getInvalidIntegrationNames(
            listOf(IntegrationProvider.Fitbit, IntegrationProvider.MyFitnessPal),
        )
        assertThat(result).isEqualTo("Fitbit and My Fitness Pal")
    }

    @Test
    fun `getInvalidIntegrationNames formats HealthConnect correctly`() {
        val result = service.getInvalidIntegrationNames(listOf(IntegrationProvider.HealthConnect))
        assertThat(result).isEqualTo("Health Connect")
    }

    // -------------------------------------------------------------------------
    // init block — checkIntegrations flow
    // -------------------------------------------------------------------------

    @Test
    fun `init block processes checkIntegrations signal when true`() = runTest {
        val checkFlow = MutableStateFlow(false)
        every { accountService.checkIntegrations } returns checkFlow

        // Create new service to trigger init
        val svc = IntegrationService(
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            accountService = accountService,
            integrationRepository = integrationRepository,
            healthConnectRepository = healthConnectRepository,
            appScope = testScope,
        )

        checkFlow.value = true
        testScope.testScheduler.advanceUntilIdle()

        coVerify { integrationRepository.updateLocalAccount() }
    }
}
