package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.model.HealthConnectOptions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectServiceTest {

    companion object {
        private const val TEST_ACCOUNT_ID = "hc-account-id"
    }

    private val context: Context = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val connectivityObserver: IConnectivityObserver = mockk(relaxed = true)
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val integrationRepository: IIntegrationRepository = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var service: HealthConnectService

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
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)

        service = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
    }

    // -------------------------------------------------------------------------
    // requestingPermissions — write types
    // -------------------------------------------------------------------------

    @Test
    fun `requestingPermissions contains expected write types`() {
        val permissions = service.requestingPermissions
        assertThat(permissions.writeTypes).containsExactly(
            DataType.Weight,
            DataType.BodyFat,
            DataType.LeanBodyMass,
            DataType.BasalMetabolicRate,
            DataType.RestingHeartRate,
            DataType.BoneMass,
        )
    }

    // -------------------------------------------------------------------------
    // outOfSyncState — initial value
    // -------------------------------------------------------------------------

    @Test
    fun `outOfSyncState initial value is false`() = runTest {
        val result = service.outOfSyncState.first()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // init block — subscribes to active account
    // -------------------------------------------------------------------------

    @Test
    fun `init block subscribes to active account from repository`() {
        testScope.testScheduler.advanceUntilIdle()
        verify { accountRepository.getActiveAccount() }
    }

    // -------------------------------------------------------------------------
    // checkIfAlreadyUsed
    // -------------------------------------------------------------------------

    @Test
    fun `checkIfAlreadyUsed returns false when no current account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        // Re-create service so currentAccountId becomes null
        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.checkIfAlreadyUsed()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIfAlreadyUsed returns true when no assignedTo exists`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } returns emptyMap()
        testScope.testScheduler.advanceUntilIdle()

        val result = service.checkIfAlreadyUsed()
        assertThat(result).isTrue()
    }

    @Test
    fun `checkIfAlreadyUsed returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("fail")
        testScope.testScheduler.advanceUntilIdle()

        val result = service.checkIfAlreadyUsed()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkIntegrated
    // -------------------------------------------------------------------------

    @Test
    fun `checkIntegrated returns false when no current account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.checkIntegrated()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIntegrated returns false when account data has no assignedTo`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } returns emptyMap()
        testScope.testScheduler.advanceUntilIdle()

        val result = service.checkIntegrated()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIntegrated returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("fail")
        testScope.testScheduler.advanceUntilIdle()

        val result = service.checkIntegrated()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // syncAllData
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData returns false when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.syncAllData()
        assertThat(result).isFalse()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `syncAllData shows loader`() = runTest {
        // Will return false due to checkIfAlreadyUsed failing, but we can check loader is shown
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("fail")
        testScope.testScheduler.advanceUntilIdle()

        service.syncAllData()

        verify { dialogQueueService.showLoader(any()) }
    }

    @Test
    fun `syncAllData dismisses loader on exception`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("fail")
        testScope.testScheduler.advanceUntilIdle()

        service.syncAllData()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // syncWeightHistory
    // -------------------------------------------------------------------------

    @Test
    fun `syncWeightHistory shows confirmation dialog`() {
        service.syncWeightHistory()

        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // handleOnNewIntent — before load
    // -------------------------------------------------------------------------

    @Test
    fun `handleOnNewIntent does not crash when not loaded`() {
        // Service is not loaded (load() was never called), intent should be ignored
        service.handleOnNewIntent(null)
        // No exception thrown
    }

    // -------------------------------------------------------------------------
    // clearHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `clearHealthConnect returns false when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.clearHealthConnect()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkPermissionChange
    // -------------------------------------------------------------------------

    @Test
    fun `checkPermissionChange returns early when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        svc.checkPermissionChange()

        // Should not call getAccountByID when accountId is null
        coVerify(exactly = 0) { healthConnectRepository.getAccountByID(any()) }
    }

    // -------------------------------------------------------------------------
    // checkMultiDeviceConnection
    // -------------------------------------------------------------------------

    @Test
    fun `checkMultiDeviceConnection returns false when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.checkMultiDeviceConnection()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkMultipleDeviceIds
    // -------------------------------------------------------------------------

    @Test
    fun `checkMultipleDeviceIds returns false when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.checkMultipleDeviceIds(
            com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType.HEALTH_CONNECT,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultipleDeviceIds returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(any()) } throws RuntimeException("fail")
        testScope.testScheduler.advanceUntilIdle()

        val result = service.checkMultipleDeviceIds(
            com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType.HEALTH_CONNECT,
        )
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // healthConnectOutOfSync
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectOutOfSync returns false when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        val svc = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = testScope,
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = svc.healthConnectOutOfSync()
        assertThat(result).isFalse()
    }
}
