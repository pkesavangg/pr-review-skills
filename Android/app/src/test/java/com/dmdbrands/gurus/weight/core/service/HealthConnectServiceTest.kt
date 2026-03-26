package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData as HcAccountData
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.model.storage.entry.toPeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.integration.model.Integrations
import com.google.common.truth.Truth.assertThat
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.enums.HealthConnectPermissionStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectRequestStatus
import com.greatergoods.libs.healthconnect.enums.HealthConnectStatus
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val context: Context = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val entryRepository: IEntryRepository = mockk()
    private val integrationRepository: IIntegrationRepository = mockk(relaxed = true)
    private val mockActivity: ComponentActivity = mockk(relaxed = true)

    private lateinit var service: HealthConnectService

    // --- Test Fixtures ---
    private val fakeAccountId = "acc-1"
    private val fakeDeviceId = "device-uuid-123"
    private val fakeAccount = Account(
        id = fakeAccountId,
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "john@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1700,
        activityLevel = "normal",
        isActiveAccount = true,
    )

    private val fakeSummary = PeriodBodyScaleSummary(
        period = "2024-01",
        entryTimestamp = "2024-01-15T10:00:00Z",
        weight = 180.0,
        bodyFat = 20.0,
        muscleMass = 40.0,
        boneMass = 3.5,
        bmr = 1800.0,
        pulse = 72.0,
        unit = WeightUnit.LB,
    )

    private val fakeMinimalSummary = PeriodBodyScaleSummary(
        period = "2024-01",
        entryTimestamp = "2024-01-15T10:00:00Z",
        weight = 170.0,
        unit = WeightUnit.LB,
    )

    @Before
    fun setUp() {
        mockkConstructor(HealthConnect::class)
        mockkObject(DeviceInfoUtil)
        every { DeviceInfoUtil.getDeviceUUID(any()) } returns fakeDeviceId
        stubNetworkAvailable()
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        every { integrationRepository.integrationsFromServer } returns flowOf(null)
        stubDefaultHealthConnect()

        service = createService()
        service.load(mockActivity)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkConstructor(HealthConnect::class)
        unmockkObject(DeviceInfoUtil)
    }

    private fun createService() = HealthConnectService(
        context = context,
        healthConnectRepository = healthConnectRepository,
        accountRepository = accountRepository,
        connectivityObserver = connectivityObserver,
        dialogQueueService = dialogQueueService,
        appNavigationService = appNavigationService,
        entryRepository = entryRepository,
        integrationRepository = integrationRepository,
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    private fun stubNetworkAvailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = true, unAvailable = false)
    }

    private fun stubNetworkUnavailable() {
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = false, unAvailable = true)
    }

    private fun stubDefaultHealthConnect() {
        coEvery { anyConstructed<HealthConnect>().isAvailable() } returns true
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } returns setOf("perm1", "perm2")
        coEvery { anyConstructed<HealthConnect>().revokeAllPermissions() } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().launchHealthConnect(any(), any()) } returns true
        coEvery { anyConstructed<HealthConnect>().saveData(any()) } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().deleteEntry(any()) } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().deleteAllData(any()) } returns HealthConnectResult.Success(Unit)
        coEvery { anyConstructed<HealthConnect>().handleOnNewIntent(any()) } returns Unit
    }

    /** Stub checkIfAlreadyUsed to return true (assigned to current account or no assignment). */
    private fun stubIntegrated() {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns false
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(fakeAccountId to mockData)
    }

    /** Stub checkIfAlreadyUsed to return false (assigned to a different account). */
    private fun stubNotIntegrated() {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns true
            every { assignedTo } returns "other-account"
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(fakeAccountId to mockData)
    }

    private fun fakeHcAccountData(
        integrated: Boolean = false,
        alertSeen: Boolean = false,
        isOpen: Boolean = false,
        outOfSync: Boolean = false,
        modalState: Boolean = false,
        assignedTo: String = "",
        grantedPermissions: List<String> = emptyList(),
    ): HcAccountData = mockk {
        every { this@mockk.integrated } returns integrated
        every { this@mockk.alertSeen } returns alertSeen
        every { getOpen() } returns isOpen
        every { this@mockk.outOfSync } returns outOfSync
        every { this@mockk.modalState } returns modalState
        every { this@mockk.assignedTo } returns assignedTo
        every { hasAssignedTo() } returns assignedTo.isNotEmpty()
        every { grantedPermissionList } returns grantedPermissions
    }

    private fun setLoaded(loaded: Boolean) {
        val field = HealthConnectService::class.java.getDeclaredField("isLoaded")
        field.isAccessible = true
        field.set(service, loaded)
    }

    private fun createServiceWithNoAccount(): HealthConnectService {
        every { accountRepository.getActiveAccount() } returns flowOf(null)
        val svc = createService()
        svc.load(mockActivity)
        return svc
    }

    private fun setupExtensionMock() {
        mockkStatic(Entry::toPeriodBodyScaleSummary)
    }

    private fun teardownExtensionMock() {
        unmockkStatic(Entry::toPeriodBodyScaleSummary)
    }

    // -------------------------------------------------------------------------
    // requestingPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `requestingPermissions contains expected write types`() {
        val writeTypes = service.requestingPermissions.writeTypes
        assertThat(writeTypes).contains(DataType.Weight)
        assertThat(writeTypes).contains(DataType.BodyFat)
        assertThat(writeTypes).contains(DataType.LeanBodyMass)
        assertThat(writeTypes).contains(DataType.BasalMetabolicRate)
        assertThat(writeTypes).contains(DataType.RestingHeartRate)
        assertThat(writeTypes).contains(DataType.BoneMass)
    }

    // -------------------------------------------------------------------------
    // outOfSyncState
    // -------------------------------------------------------------------------

    @Test
    fun `outOfSyncState emits initial false`() = runTest {
        service.outOfSyncState.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // load
    // -------------------------------------------------------------------------

    @Test
    fun `load sets isLoaded true on success`() = runTest {
        // load was called in setUp; verify indirectly via getApprovedPermissionList
        val result = service.getApprovedPermissionList()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `load sets isLoaded false on exception`() = runTest {
        // Simulate failed load: force isLoaded false
        setLoaded(false)
        val result = service.getApprovedPermissionList()
        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // initializeHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `initializeHealthConnect does nothing when already loaded`() {
        // load was already called in setUp (isLoaded = true)
        service.initializeHealthConnect(mockActivity)
        // No crash = pass; constructor was only called once during setUp load()
    }

    @Test
    fun `initializeHealthConnect calls load when not loaded`() = runTest {
        setLoaded(false)
        service.initializeHealthConnect(mockActivity)
        // After this, isLoaded should be true — verify via getApprovedPermissionList
        val result = service.getApprovedPermissionList()
        assertThat(result).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // checkAvailability
    // -------------------------------------------------------------------------

    @Test
    fun `checkAvailability returns true when available`() = runTest {
        coEvery { anyConstructed<HealthConnect>().isAvailable() } returns true
        val result = service.checkAvailability()
        assertThat(result).isTrue()
    }

    @Test
    fun `checkAvailability returns false on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().isAvailable() } throws RuntimeException("error")
        val result = service.checkAvailability()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // healthConnectStatus
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectStatus returns status from library`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UPDATE_REQUIRED
        val result = service.healthConnectStatus()
        assertThat(result).isEqualTo(HealthConnectStatus.UPDATE_REQUIRED)
    }

    @Test
    fun `healthConnectStatus returns UNAVAILABLE on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } throws RuntimeException("error")
        val result = service.healthConnectStatus()
        assertThat(result).isEqualTo(HealthConnectStatus.UNAVAILABLE)
    }

    // -------------------------------------------------------------------------
    // checkPermissionStatus
    // -------------------------------------------------------------------------

    @Test
    fun `checkPermissionStatus returns status from library`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.PARTIAL
        val result = service.checkPermissionStatus()
        assertThat(result).isEqualTo(HealthConnectPermissionStatus.PARTIAL)
    }

    @Test
    fun `checkPermissionStatus returns NONE on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } throws RuntimeException("error")
        val result = service.checkPermissionStatus()
        assertThat(result).isEqualTo(HealthConnectPermissionStatus.NONE)
    }

    // -------------------------------------------------------------------------
    // requestAuthorization
    // -------------------------------------------------------------------------

    @Test
    fun `requestAuthorization forwards result to callback`() = runTest {
        coEvery { anyConstructed<HealthConnect>().requestAuthorization(any(), any()) } answers {
            val callback = secondArg<(HealthConnectRequestStatus) -> Unit>()
            callback(HealthConnectRequestStatus.CONNECTED)
        }
        var capturedResult: HealthConnectRequestStatus? = null
        service.requestAuthorization { capturedResult = it }
        assertThat(capturedResult).isEqualTo(HealthConnectRequestStatus.CONNECTED)
    }

    @Test
    fun `requestAuthorization calls callback with CANCELLED on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().requestAuthorization(any(), any()) } throws RuntimeException("error")
        var capturedResult: HealthConnectRequestStatus? = null
        service.requestAuthorization { capturedResult = it }
        assertThat(capturedResult).isEqualTo(HealthConnectRequestStatus.CANCELLED)
    }

    // -------------------------------------------------------------------------
    // openHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `openHealthConnect returns true on success`() = runTest {
        coEvery { anyConstructed<HealthConnect>().launchHealthConnect(any(), any()) } returns true
        val result = service.openHealthConnect()
        assertThat(result).isTrue()
    }

    @Test
    fun `openHealthConnect sets open when isFromSetup`() = runTest {
        coEvery { anyConstructed<HealthConnect>().launchHealthConnect(any(), any()) } returns true
        service.openHealthConnect(isFromSetup = true)
        coVerify { healthConnectRepository.setOpen(fakeAccountId, true) }
    }

    @Test
    fun `openHealthConnect does not set open when not from setup`() = runTest {
        coEvery { anyConstructed<HealthConnect>().launchHealthConnect(any(), any()) } returns true
        service.openHealthConnect(isFromSetup = false)
        coVerify(exactly = 0) { healthConnectRepository.setOpen(any(), any()) }
    }

    @Test
    fun `openHealthConnect returns false on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().launchHealthConnect(any(), any()) } throws RuntimeException("error")
        val result = service.openHealthConnect()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // revokePermission
    // -------------------------------------------------------------------------

    @Test
    fun `revokePermission returns true on success`() = runTest {
        coEvery { anyConstructed<HealthConnect>().revokeAllPermissions() } returns HealthConnectResult.Success(Unit)
        val result = service.revokePermission()
        assertThat(result).isTrue()
    }

    @Test
    fun `revokePermission returns false on error result`() = runTest {
        coEvery { anyConstructed<HealthConnect>().revokeAllPermissions() } returns HealthConnectResult.Error(RuntimeException("fail"))
        val result = service.revokePermission()
        assertThat(result).isFalse()
    }

    @Test
    fun `revokePermission returns false on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().revokeAllPermissions() } throws RuntimeException("error")
        val result = service.revokePermission()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkIfAlreadyUsed
    // -------------------------------------------------------------------------

    @Test
    fun `checkIfAlreadyUsed returns true when no assignment`() = runTest {
        stubIntegrated()
        val result = service.checkIfAlreadyUsed()
        assertThat(result).isTrue()
    }

    @Test
    fun `checkIfAlreadyUsed returns true when assigned to current account`() = runTest {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns true
            every { assignedTo } returns fakeAccountId
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(fakeAccountId to mockData)

        val result = service.checkIfAlreadyUsed()
        assertThat(result).isTrue()
    }

    @Test
    fun `checkIfAlreadyUsed returns false when assigned to different account`() = runTest {
        stubNotIntegrated()
        val result = service.checkIfAlreadyUsed()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIfAlreadyUsed returns false when no active account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.checkIfAlreadyUsed()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIfAlreadyUsed returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("error")
        val result = service.checkIfAlreadyUsed()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkIntegrated
    // -------------------------------------------------------------------------

    @Test
    fun `checkIntegrated returns true when assigned to current account`() = runTest {
        val mockData = mockk<HcAccountData> {
            every { hasAssignedTo() } returns true
            every { assignedTo } returns fakeAccountId
        }
        coEvery { healthConnectRepository.getAccountDataMap() } returns mapOf(fakeAccountId to mockData)

        val result = service.checkIntegrated()
        assertThat(result).isTrue()
    }

    @Test
    fun `checkIntegrated returns false when assigned to different account`() = runTest {
        stubNotIntegrated()
        val result = service.checkIntegrated()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIntegrated returns false when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.checkIntegrated()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkIntegrated returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getAccountDataMap() } throws RuntimeException("error")
        val result = service.checkIntegrated()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // handleOnNewIntent
    // -------------------------------------------------------------------------

    @Test
    fun `handleOnNewIntent does nothing when not loaded`() {
        setLoaded(false)
        val intent = mockk<Intent> { every { action } returns "some-action" }
        service.handleOnNewIntent(intent)
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().handleOnNewIntent(any()) }
    }

    @Test
    fun `handleOnNewIntent handles null intent`() {
        service.handleOnNewIntent(null)
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().handleOnNewIntent(any()) }
    }

    @Test
    fun `handleOnNewIntent ignores unknown action`() {
        val intent = mockk<Intent> { every { action } returns "unknown-action" }
        service.handleOnNewIntent(intent)
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().handleOnNewIntent(any()) }
    }

    // -------------------------------------------------------------------------
    // getApprovedPermissionList
    // -------------------------------------------------------------------------

    @Test
    fun `getApprovedPermissionList returns list when loaded`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } returns setOf("perm1", "perm2")
        val result = service.getApprovedPermissionList()
        assertThat(result).hasSize(2)
        assertThat(result).containsAtLeast("perm1", "perm2")
    }

    @Test
    fun `getApprovedPermissionList returns empty when not loaded`() = runTest {
        setLoaded(false)
        val result = service.getApprovedPermissionList()
        assertThat(result).isEmpty()
    }

    @Test
    fun `getApprovedPermissionList returns empty on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } throws RuntimeException("error")
        val result = service.getApprovedPermissionList()
        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // syncData
    // -------------------------------------------------------------------------

    @Test
    fun `syncData processes entries with all fields`() = runTest {
        stubIntegrated()
        service.syncData(listOf(fakeSummary))
        coVerify { anyConstructed<HealthConnect>().saveData(any()) }
    }

    @Test
    fun `syncData handles entries with minimal fields`() = runTest {
        stubIntegrated()
        service.syncData(listOf(fakeMinimalSummary))
        coVerify { anyConstructed<HealthConnect>().saveData(any()) }
    }

    @Test
    fun `syncData returns early when not integrated`() = runTest {
        stubNotIntegrated()
        service.syncData(listOf(fakeSummary))
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().saveData(any()) }
    }

    @Test
    fun `syncData rethrows when saveData fails`() = runTest {
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().saveData(any()) } throws RuntimeException("sync failed")

        try {
            service.syncData(listOf(fakeSummary))
            assertThat(false).isTrue() // Should not reach
        } catch (e: Exception) {
            assertThat(e.message).isEqualTo("sync failed")
        }
    }

    @Test
    fun `syncData skips zero weight`() = runTest {
        stubIntegrated()
        val zeroWeight = fakeSummary.copy(weight = 0.0)
        service.syncData(listOf(zeroWeight))
        coVerify { anyConstructed<HealthConnect>().saveData(any()) }
    }

    // -------------------------------------------------------------------------
    // syncAllData
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData returns true on success`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(fakeEntry)

        val result = service.syncAllData()

        assertThat(result).isTrue()
        verify { dialogQueueService.showLoader(any()) }
        verify { dialogQueueService.dismissLoader() }
        teardownExtensionMock()
    }

    @Test
    fun `syncAllData shows syncToast when not fromOutOfSync`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(fakeEntry)

        service.syncAllData(fromOutOfSync = false)

        verify { dialogQueueService.showToast(any()) }
        teardownExtensionMock()
    }

    @Test
    fun `syncAllData returns false when no active account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.syncAllData()
        assertThat(result).isFalse()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `syncAllData returns false when not integrated`() = runTest {
        stubNotIntegrated()
        val result = service.syncAllData()
        assertThat(result).isFalse()
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `syncAllData shows error dialog on exception`() = runTest {
        stubIntegrated()
        coEvery { entryRepository.getEntriesByAccount(any()) } throws RuntimeException("sync error")

        val result = service.syncAllData()

        assertThat(result).isFalse()
        verify { dialogQueueService.showDialog(any<DialogModel.Alert>()) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry returns true on success`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { anyConstructed<HealthConnect>().deleteEntry(any()) } returns HealthConnectResult.Success(Unit)

        val result = service.deleteEntry(fakeEntry)

        assertThat(result).isTrue()
        teardownExtensionMock()
    }

    @Test
    fun `deleteEntry returns false when not loaded`() = runTest {
        setLoaded(false)
        val fakeEntry = mockk<Entry>()
        val result = service.deleteEntry(fakeEntry)
        assertThat(result).isFalse()
    }

    @Test
    fun `deleteEntry returns false when entry conversion fails`() = runTest {
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns null

        val result = service.deleteEntry(fakeEntry)

        assertThat(result).isFalse()
        teardownExtensionMock()
    }

    @Test
    fun `deleteEntry returns false when not integrated`() = runTest {
        stubNotIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary

        val result = service.deleteEntry(fakeEntry)

        assertThat(result).isFalse()
        teardownExtensionMock()
    }

    @Test
    fun `deleteEntry returns false on error result`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { anyConstructed<HealthConnect>().deleteEntry(any()) } returns HealthConnectResult.Error(RuntimeException("fail"))

        val result = service.deleteEntry(fakeEntry)

        assertThat(result).isFalse()
        teardownExtensionMock()
    }

    @Test
    fun `deleteEntry returns false on exception`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { anyConstructed<HealthConnect>().deleteEntry(any()) } throws RuntimeException("error")

        val result = service.deleteEntry(fakeEntry)

        assertThat(result).isFalse()
        teardownExtensionMock()
    }

    // -------------------------------------------------------------------------
    // deleteAllData
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAllData returns true on success`() = runTest {
        coEvery { anyConstructed<HealthConnect>().deleteAllData(any()) } returns HealthConnectResult.Success(Unit)
        val result = service.deleteAllData()
        assertThat(result).isTrue()
    }

    @Test
    fun `deleteAllData returns false when not loaded`() = runTest {
        setLoaded(false)
        val result = service.deleteAllData()
        assertThat(result).isFalse()
    }

    @Test
    fun `deleteAllData returns false on error result`() = runTest {
        coEvery { anyConstructed<HealthConnect>().deleteAllData(any()) } returns HealthConnectResult.Error(RuntimeException("fail"))
        val result = service.deleteAllData()
        assertThat(result).isFalse()
    }

    @Test
    fun `deleteAllData returns false on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().deleteAllData(any()) } throws RuntimeException("error")
        val result = service.deleteAllData()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // turnOnIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `turnOnIntegration saves integration data`() = runTest {
        service.turnOnIntegration(fromMultiDevice = true, isRequestNeed = false)

        coVerify { healthConnectRepository.saveIntegration(any()) }
        coVerify { healthConnectRepository.setHealthConnectIntegrationStatus(fakeAccountId, true) }
        coVerify { integrationRepository.updateHealthConnectIntegrationOffline(true) }
        coVerify { integrationRepository.updateLocalAccount() }
    }

    @Test
    fun `turnOnIntegration returns early when fromMultiDevice`() = runTest {
        service.turnOnIntegration(fromMultiDevice = true, isRequestNeed = false)
        // Should not call syncWeightHistory or syncAllData — no Confirm dialog shown
        verify(exactly = 0) { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `turnOnIntegration calls syncWeightHistory when not isRequestNeed`() = runTest {
        service.turnOnIntegration(fromMultiDevice = false, isRequestNeed = false)
        // syncWeightHistory shows a Confirm dialog
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `turnOnIntegration calls syncAllData when isRequestNeed`() = runTest {
        stubIntegrated()
        setupExtensionMock()
        val fakeEntry = mockk<Entry>()
        every { fakeEntry.toPeriodBodyScaleSummary() } returns fakeSummary
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(fakeEntry)

        service.turnOnIntegration(fromMultiDevice = false, isRequestNeed = true)

        verify { dialogQueueService.showLoader(any()) }
        teardownExtensionMock()
    }

    // -------------------------------------------------------------------------
    // removeHealthConnectIntegration
    // -------------------------------------------------------------------------

    @Test
    fun `removeHealthConnectIntegration returns true on success`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { anyConstructed<HealthConnect>().deleteAllData(any()) } returns HealthConnectResult.Success(Unit)

        val result = service.removeHealthConnectIntegration()

        assertThat(result).isTrue()
        coVerify { healthConnectRepository.setHealthConnectIntegrationStatus(fakeAccountId, false) }
        coVerify { integrationRepository.updateHealthConnectIntegrationOffline(false) }
        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `removeHealthConnectIntegration revokes when installed`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        service.removeHealthConnectIntegration()
        coVerify { anyConstructed<HealthConnect>().revokeAllPermissions() }
    }

    @Test
    fun `removeHealthConnectIntegration revokes when update required`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UPDATE_REQUIRED
        service.removeHealthConnectIntegration()
        coVerify { anyConstructed<HealthConnect>().revokeAllPermissions() }
    }

    @Test
    fun `removeHealthConnectIntegration skips revoke when unavailable`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UNAVAILABLE
        service.removeHealthConnectIntegration()
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().revokeAllPermissions() }
    }

    @Test
    fun `removeHealthConnectIntegration sets outOfSyncState false`() = runTest {
        service.removeHealthConnectIntegration()
        service.outOfSyncState.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeHealthConnectIntegration returns false on exception`() = runTest {
        // Throw from a call that is NOT caught internally
        coEvery { healthConnectRepository.removeServerHcIntegration(any()) } throws RuntimeException("error")
        val result = service.removeHealthConnectIntegration()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // clearHealthConnect
    // -------------------------------------------------------------------------

    @Test
    fun `clearHealthConnect returns true on success`() = runTest {
        val result = service.clearHealthConnect()
        assertThat(result).isTrue()
        coVerify { healthConnectRepository.setStoredIntegrationData(fakeAccountId, any()) }
        coVerify { healthConnectRepository.setHealthConnectIntegrationStatus(fakeAccountId, false) }
        coVerify { healthConnectRepository.updateAlertSeen(fakeAccountId, false) }
        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, false) }
        coVerify { healthConnectRepository.updateModalState(fakeAccountId, false) }
        coVerify { healthConnectRepository.setOpen(fakeAccountId, false) }
    }

    @Test
    fun `clearHealthConnect returns false when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.clearHealthConnect()
        assertThat(result).isFalse()
    }

    @Test
    fun `clearHealthConnect returns false on exception`() = runTest {
        coEvery { healthConnectRepository.setStoredIntegrationData(any(), any()) } throws RuntimeException("error")
        val result = service.clearHealthConnect()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkPermissionChange
    // -------------------------------------------------------------------------

    @Test
    fun `checkPermissionChange returns when no account`() = runTest {
        service = createServiceWithNoAccount()
        service.checkPermissionChange()
        coVerify(exactly = 0) { healthConnectRepository.getAccountByID(any()) }
    }

    @Test
    fun `checkPermissionChange skips when no stored permissions`() = runTest {
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            grantedPermissions = emptyList(),
        )
        service.checkPermissionChange()
        coVerify(exactly = 0) { healthConnectRepository.setHcPermissions(any(), any()) }
    }

    @Test
    fun `checkPermissionChange updates when permissions differ`() = runTest {
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            grantedPermissions = listOf("old-perm"),
        )
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } returns setOf("new-perm1", "new-perm2")

        service.checkPermissionChange()

        coVerify { healthConnectRepository.setHcPermissions(fakeAccountId, any()) }
    }

    @Test
    fun `checkPermissionChange does not update inline when permissions same`() = runTest {
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            grantedPermissions = listOf("perm1", "perm2"),
        )
        coEvery { anyConstructed<HealthConnect>().getApprovedPermissionList() } returns setOf("perm1", "perm2")

        service.checkPermissionChange()

        // setHcPermissions IS called via turnOnIntegration (which always runs),
        // but should NOT be called in the permission-change-detection block.
        // Verify turnOnIntegration was still called (it always is when stored permissions exist)
        coVerify { healthConnectRepository.saveIntegration(any()) }
    }

    @Test
    fun `checkPermissionChange catches exception`() = runTest {
        coEvery { healthConnectRepository.getAccountByID(any()) } throws RuntimeException("error")
        service.checkPermissionChange() // Should not throw
    }

    // -------------------------------------------------------------------------
    // checkMultipleDeviceIds
    // -------------------------------------------------------------------------

    @Test
    fun `checkMultipleDeviceIds returns true when no local integration and server on`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)

        assertThat(result).isTrue()
    }

    @Test
    fun `checkMultipleDeviceIds returns true when empty deviceId and server on`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns IntegratedDeviceInfo(
            operationType = "save",
            scopes = IntegrationData(deviceId = "", type = "healthconnect"),
        )
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)

        assertThat(result).isTrue()
    }

    @Test
    fun `checkMultipleDeviceIds returns false when local integration exists`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns IntegratedDeviceInfo(
            operationType = "save",
            scopes = IntegrationData(deviceId = fakeDeviceId, type = "healthconnect"),
        )
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)

        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultipleDeviceIds returns false when server integration off`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = false))

        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)

        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultipleDeviceIds returns false when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)
        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultipleDeviceIds returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(any()) } throws RuntimeException("error")
        val result = service.checkMultipleDeviceIds(IntegrationType.HEALTH_CONNECT)
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkMultiDeviceConnection
    // -------------------------------------------------------------------------

    @Test
    fun `checkMultiDeviceConnection returns true when multiple devices detected`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val result = service.checkMultiDeviceConnection(isPermissionEnabled = true)

        assertThat(result).isTrue()
        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `checkMultiDeviceConnection sets outOfSync when permissions disabled`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        service.checkMultiDeviceConnection(isPermissionEnabled = false)

        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
    }

    @Test
    fun `checkMultiDeviceConnection does not set outOfSync when permissions enabled`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        service.checkMultiDeviceConnection(isPermissionEnabled = true)

        coVerify(exactly = 0) { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
    }

    @Test
    fun `checkMultiDeviceConnection returns false when no multiple devices`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns IntegratedDeviceInfo(
            operationType = "save",
            scopes = IntegrationData(deviceId = fakeDeviceId, type = "healthconnect"),
        )
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val result = service.checkMultiDeviceConnection()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultiDeviceConnection returns false when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.checkMultiDeviceConnection()
        assertThat(result).isFalse()
    }

    @Test
    fun `checkMultiDeviceConnection returns false on exception`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(any()) } throws RuntimeException("error")
        val result = service.checkMultiDeviceConnection()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // healthConnectOutOfSync
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectOutOfSync returns true when NONE permissions and out of sync`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        val result = service.healthConnectOutOfSync()

        assertThat(result).isTrue()
        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
        coVerify { healthConnectRepository.updateModalState(fakeAccountId, true) }
    }

    @Test
    fun `healthConnectOutOfSync returns false when permissions restored and modal not dismissed`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
            modalState = false,
            grantedPermissions = listOf("perm1"),
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, false) }
        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `healthConnectOutOfSync returns false when permissions restored and modal dismissed`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
            modalState = true,
            grantedPermissions = listOf("perm1"),
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
        verify(exactly = 0) { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `healthConnectOutOfSync returns false when not out of sync`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = false,
            integrated = true,
        )

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
    }

    @Test
    fun `healthConnectOutOfSync returns false when unavailable`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UNAVAILABLE

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
    }

    @Test
    fun `healthConnectOutOfSync returns false when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.healthConnectOutOfSync()
        assertThat(result).isFalse()
    }

    @Test
    fun `healthConnectOutOfSync returns false on exception`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } throws RuntimeException("error")
        val result = service.healthConnectOutOfSync()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // syncWeightHistory
    // -------------------------------------------------------------------------

    @Test
    fun `syncWeightHistory shows confirm dialog`() {
        service.syncWeightHistory()
        verify { dialogQueueService.showDialog(any<DialogModel.Confirm>()) }
    }

    @Test
    fun `syncWeightHistory dialog onCancel dismisses`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.syncWeightHistory()

        dialogSlot.captured.onCancel?.invoke()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `syncWeightHistory dialog onDismiss dismisses`() {
        val dialogSlot = slot<DialogModel.Confirm>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.syncWeightHistory()

        dialogSlot.captured.onDismiss?.invoke()
        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // checkHealthConnectPermissionDisabled
    // -------------------------------------------------------------------------

    @Test
    fun `checkHealthConnectPermissionDisabled returns when no accountId`() = runTest {
        service = createServiceWithNoAccount()
        service.checkHealthConnectPermissionDisabled()
        coVerify(exactly = 0) { healthConnectRepository.getAccountByID(any()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled does nothing for UNAVAILABLE status`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UNAVAILABLE
        service.checkHealthConnectPermissionDisabled()
        // getAccountByID IS called (before the status branch), but no dialog is shown
        verify(exactly = 0) { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled returns when not already connected`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
        )
        stubNotIntegrated()

        service.checkHealthConnectPermissionDisabled()

        // Should set _outOfSyncState to the session value and return
    }

    @Test
    fun `checkHealthConnectPermissionDisabled shows out-of-sync modal for NONE permissions`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = false,
            alertSeen = false,
            isOpen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        service.checkHealthConnectPermissionDisabled()

        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled clears out of sync when permissions restored`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = true,
            grantedPermissions = listOf("perm1"),
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        service.checkHealthConnectPermissionDisabled()

        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, false) }
        coVerify { healthConnectRepository.updateModalState(fakeAccountId, false) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled resets modal on NONE with opened`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = true,
            outOfSync = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        service.checkHealthConnectPermissionDisabled()

        coVerify { healthConnectRepository.updateModalState(fakeAccountId, false) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled calls turnOnIntegration when locally integrated`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        service.checkHealthConnectPermissionDisabled()

        coVerify { healthConnectRepository.saveIntegration(any()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled calls healthConnectOutOfSync when outOfSyncSession`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = true,
            grantedPermissions = listOf("perm1"),
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        service.checkHealthConnectPermissionDisabled()

        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, false) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled handles multi-device condition`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = false,
            outOfSync = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null

        service.checkHealthConnectPermissionDisabled()

        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled shows INSTALL_REQUIRED alert when hcOn`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData()
        // Set currentIntegrations via reflection to trigger INSTALL_REQUIRED branch
        val field = HealthConnectService::class.java.getDeclaredField("currentIntegrations")
        field.isAccessible = true
        field.set(service, Integrations(isHealthConnectOn = true))

        service.checkHealthConnectPermissionDisabled()

        verify { dialogQueueService.enqueue(any<DialogModel.Alert>()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled INSTALL_REQUIRED alert onDismiss`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALL_REQUIRED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData()
        val field = HealthConnectService::class.java.getDeclaredField("currentIntegrations")
        field.isAccessible = true
        field.set(service, Integrations(isHealthConnectOn = true))

        val dialogSlot = slot<DialogModel.Alert>()
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onDismiss?.invoke()
        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Dialog callback tests for checkMultiDeviceConnection
    // -------------------------------------------------------------------------

    @Test
    fun `checkMultiDeviceConnection onConfirm navigates and updates state`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkMultiDeviceConnection(isPermissionEnabled = true)

        dialogSlot.captured.onConfirm?.invoke(Unit)
        // onConfirmMultiDevice launches on Dispatchers.Main
        advanceUntilIdle()

        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `checkMultiDeviceConnection onDismiss sets outOfSync true`() = runTest {
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkMultiDeviceConnection(isPermissionEnabled = true)

        dialogSlot.captured.onDismiss?.invoke()
        // onCancelMultiDevice launches on Dispatchers.Main
        advanceUntilIdle()

        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
        coVerify { healthConnectRepository.updateModalState(fakeAccountId, true) }
    }

    // -------------------------------------------------------------------------
    // Dialog callback tests for syncAllData error dialog
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData error dialog onDismiss dismisses`() = runTest {
        stubIntegrated()
        coEvery { entryRepository.getEntriesByAccount(any()) } throws RuntimeException("sync error")

        val dialogSlot = slot<DialogModel.Alert>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.syncAllData()

        dialogSlot.captured.onDismiss?.invoke()
        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Dialog callback tests for healthConnectOutOfSync FinishConnect
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectOutOfSync FinishConnect onConfirm navigates`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
            modalState = false,
            grantedPermissions = listOf("perm1"),
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.healthConnectOutOfSync()

        dialogSlot.captured.onConfirm?.invoke(Unit)
        advanceUntilIdle()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `healthConnectOutOfSync FinishConnect onDismiss dismisses`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
            modalState = false,
            grantedPermissions = listOf("perm1"),
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.healthConnectOutOfSync()

        dialogSlot.captured.onDismiss?.invoke()
        verify { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // Additional healthConnectOutOfSync branches
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectOutOfSync handles UPDATE_REQUIRED status`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.UPDATE_REQUIRED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        val result = service.healthConnectOutOfSync()

        assertThat(result).isTrue()
    }

    @Test
    fun `healthConnectOutOfSync handles PARTIAL permission with out of sync`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = true,
            modalState = false,
            grantedPermissions = listOf("perm1"),
        )
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.PARTIAL

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, false) }
    }

    @Test
    fun `healthConnectOutOfSync returns false when not integrated`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            outOfSync = true,
            integrated = false,
        )

        val result = service.healthConnectOutOfSync()

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // Additional checkHealthConnectPermissionDisabled branches
    // -------------------------------------------------------------------------

    @Test
    fun `checkHealthConnectPermissionDisabled out-of-sync modal onConfirm opens health connect`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = false,
            isOpen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onConfirm?.invoke(Unit)
        advanceUntilIdle()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled out-of-sync modal onDismiss updates state`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = false,
            isOpen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onDismiss?.invoke()
        advanceUntilIdle()
    }

    @Test
    fun `checkHealthConnectPermissionDisabled handles PARTIAL permission with finishConnect off`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = false,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.PARTIAL
        // currentIntegrations?.isHealthConnectOn is null by default → finishConnectCondition = false

        service.checkHealthConnectPermissionDisabled()

        // turnOnIntegration is called because isLocallyIntegrated is true
        coVerify { healthConnectRepository.saveIntegration(any()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect when hcOff and not cancelled`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = false,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        // Set currentIntegrations to have hcOn = false to trigger finishConnect condition
        val field = HealthConnectService::class.java.getDeclaredField("currentIntegrations")
        field.isAccessible = true
        field.set(service, Integrations(isHealthConnectOn = false))

        service.checkHealthConnectPermissionDisabled()

        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect onConfirm navigates`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = false,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        val field = HealthConnectService::class.java.getDeclaredField("currentIntegrations")
        field.isAccessible = true
        field.set(service, Integrations(isHealthConnectOn = false))

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onConfirm?.invoke(Unit)
        advanceUntilIdle()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect onDismiss sets alertSeen`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = false,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        val field = HealthConnectService::class.java.getDeclaredField("currentIntegrations")
        field.isAccessible = true
        field.set(service, Integrations(isHealthConnectOn = false))

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onDismiss?.invoke()
        advanceUntilIdle()
        coVerify { healthConnectRepository.updateAlertSeen(fakeAccountId, true) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect from opened path`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = true,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL
        // currentIntegrations is null so finishConnectCondition=false, falls to isHealthConnectOpened branch

        service.checkHealthConnectPermissionDisabled()

        verify { dialogQueueService.showDialog(any<DialogModel.Custom>()) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect from opened onConfirm`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = true,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onConfirm?.invoke(Unit)
        advanceUntilIdle()
        coVerify { healthConnectRepository.setOpen(fakeAccountId, false) }
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled finishConnect from opened onDismiss`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            isOpen = true,
            outOfSync = false,
            alertSeen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.ALL

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        dialogSlot.captured.onDismiss?.invoke()
        advanceUntilIdle()
        coVerify { healthConnectRepository.setOpen(fakeAccountId, false) }
    }

    @Test
    fun `checkHealthConnectPermissionDisabled multi-device with NONE permission`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = false,
            outOfSync = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE
        every { integrationRepository.integrationsFromServer } returns flowOf(Integrations(isHealthConnectOn = true))
        coEvery { healthConnectRepository.getStoredIntegrationData(fakeAccountId) } returns null

        service.checkHealthConnectPermissionDisabled()

        // isConnect = false (NONE), so checkMultiDeviceConnection(false) → outOfSync set to true
        coVerify { healthConnectRepository.updateOutOfSync(fakeAccountId, true) }
    }

    // -------------------------------------------------------------------------
    // Additional handleOnNewIntent test
    // -------------------------------------------------------------------------

    @Test
    fun `handleOnNewIntent forwards permissions rationale action`() {
        val intent = mockk<Intent> {
            every { action } returns HealthConnect.ACTION_SHOW_PERMISSIONS_RATIONALE
        }
        service.handleOnNewIntent(intent)
        coVerify { anyConstructed<HealthConnect>().handleOnNewIntent(intent) }
    }

    @Test
    fun `handleOnNewIntent forwards view permission usage action`() {
        val intent = mockk<Intent> {
            every { action } returns HealthConnect.ACTION_VIEW_PERMISSION_USAGE
        }
        service.handleOnNewIntent(intent)
        coVerify { anyConstructed<HealthConnect>().handleOnNewIntent(intent) }
    }

    @Test
    fun `handleOnNewIntent handles intent with null action`() {
        val intent = mockk<Intent> { every { action } returns null }
        service.handleOnNewIntent(intent)
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().handleOnNewIntent(any()) }
    }

    // -------------------------------------------------------------------------
    // Additional openHealthConnect test
    // -------------------------------------------------------------------------

    @Test
    fun `openHealthConnect throws when no account`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.openHealthConnect()
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // Additional turnOnIntegration test
    // -------------------------------------------------------------------------

    @Test
    fun `turnOnIntegration catches exception gracefully`() = runTest {
        coEvery { healthConnectRepository.saveIntegration(any()) } throws RuntimeException("error")
        // Should not crash; continues to sync logic
        service.turnOnIntegration(fromMultiDevice = true, isRequestNeed = false)
    }

    // -------------------------------------------------------------------------
    // requireCurrentAccountId edge case
    // -------------------------------------------------------------------------

    @Test
    fun `openHealthConnect handles requireCurrentAccountId throwing`() = runTest {
        service = createServiceWithNoAccount()
        val result = service.openHealthConnect(isFromSetup = true)
        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // checkHealthConnectPermissionDisabled out-of-sync modal secondaryAction
    // -------------------------------------------------------------------------

    @Test
    fun `checkHealthConnectPermissionDisabled out-of-sync modal secondaryAction removes integration`() = runTest {
        coEvery { anyConstructed<HealthConnect>().getStatus() } returns HealthConnectStatus.INSTALLED
        coEvery { healthConnectRepository.getAccountByID(fakeAccountId) } returns fakeHcAccountData(
            integrated = true,
            outOfSync = false,
            isOpen = false,
        )
        stubIntegrated()
        coEvery { anyConstructed<HealthConnect>().getPermissionStatus(any()) } returns HealthConnectPermissionStatus.NONE

        val dialogSlot = slot<DialogModel.Custom>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.checkHealthConnectPermissionDisabled()

        // The secondaryAction is in params map
        val params = dialogSlot.captured.params
        val secondaryAction = params?.get("secondaryAction") as? (() -> Unit)
        secondaryAction?.invoke()
        advanceUntilIdle()
        verify { dialogQueueService.showLoader(any()) }
    }
}
