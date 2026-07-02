package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.google.common.truth.Truth.assertThat
import com.greatergoods.libs.healthconnect.HealthConnect
import com.greatergoods.libs.healthconnect.enums.DataType
import com.greatergoods.libs.healthconnect.model.HealthConnectData
import com.greatergoods.libs.healthconnect.model.HealthConnectResult
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectBpSyncTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

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

    private val fakeAccountId = "acc-1"
    private val fakeAccount = Account(
        id = fakeAccountId,
        firstName = "Test",
        lastName = "User",
        dob = "1990-01-01",
        email = "test@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1700,
        activityLevel = "normal",
        isActiveAccount = true,
    )

    @BeforeEach
    fun setUp() {
        mockkConstructor(HealthConnect::class)
        mockkObject(DeviceInfoUtil)
        HealthConnectTestHelper.stubDeviceInfo()
        every {
            connectivityObserver.getCurrentNetworkState()
        } returns NetworkState(available = true, unAvailable = false)
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        every { integrationRepository.integrationsFromServer } returns flowOf(null)
        HealthConnectTestHelper.stubDefaultHealthConnect()

        service = HealthConnectService(
            context = context,
            healthConnectRepository = healthConnectRepository,
            accountRepository = accountRepository,
            connectivityObserver = connectivityObserver,
            dialogQueueService = dialogQueueService,
            appNavigationService = appNavigationService,
            entryRepository = entryRepository,
            integrationRepository = integrationRepository,
            appScope = CoroutineScope(mainDispatcherRule.dispatcher),
        )
        service.load(mockActivity)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkConstructor(HealthConnect::class)
        unmockkObject(DeviceInfoUtil)
    }

    private fun stubIntegrated() {
        HealthConnectTestHelper.stubIntegrated(healthConnectRepository, fakeAccountId)
    }

    private fun stubNotIntegrated() {
        HealthConnectTestHelper.stubNotIntegrated(healthConnectRepository, fakeAccountId)
    }

    // -------------------------------------------------------------------------
    // requestingPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `requestingPermissions includes BloodPressure write type`() {
        val writeTypes = service.requestingPermissions.writeTypes
        assertThat(writeTypes).contains(DataType.BloodPressure)
    }

    // -------------------------------------------------------------------------
    // syncAllData — BP entries
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData calls saveData with BloodPressure data when BpmEntry present`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)

        service.syncAllData()

        coVerify { anyConstructed<HealthConnect>().saveData(match { list ->
            list.any { it.type == DataType.BloodPressure }
        }) }
    }

    @Test
    fun `syncAllData passes correct systolic and diastolic values to HC library`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 130, diastolic = 85)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.syncAllData()

        val bpData = slot.captured.firstOrNull { it.type == DataType.BloodPressure }
        assertThat(bpData).isNotNull()
        assertThat(bpData!!.bloodPressure?.systolic).isEqualTo(130.0)
        assertThat(bpData.bloodPressure?.diastolic).isEqualTo(85.0)
    }

    @Test
    fun `syncAllData does not call saveData for BP when no BpmEntry instances`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns emptyList()

        service.syncAllData()

        coVerify(exactly = 0) { anyConstructed<HealthConnect>().saveData(match { list ->
            list.any { it.type == DataType.BloodPressure }
        }) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry — BpmEntry
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry with BpmEntry returns true on success`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry()

        val result = service.deleteEntry(bpmEntry)

        assertThat(result).isTrue()
    }

    @Test
    fun `deleteEntry with BpmEntry calls HC library with BloodPressure data type`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().deleteEntry(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.deleteEntry(bpmEntry)

        assertThat(slot.captured.any { it.type == DataType.BloodPressure }).isTrue()
        val bpData = slot.captured.first { it.type == DataType.BloodPressure }
        assertThat(bpData.bloodPressure?.systolic).isEqualTo(120.0)
        assertThat(bpData.bloodPressure?.diastolic).isEqualTo(80.0)
    }

    @Test
    fun `deleteEntry with BpmEntry returns false when not integrated`() = runTest(mainDispatcherRule.scheduler) {
        stubNotIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry()

        val result = service.deleteEntry(bpmEntry)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().deleteEntry(any()) }
    }

    @Test
    fun `deleteEntry with BpmEntry returns false when all BPM fields are non-positive`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        // Pulse is now synced separately as RestingHeartRate, so an "all-invalid" BPM entry
        // requires systolic=0, diastolic=0, AND pulse=0 to truly produce an empty delete payload.
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 0, diastolic = 0, pulse = 0)

        val result = service.deleteEntry(bpmEntry)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().deleteEntry(any()) }
    }

    @Test
    fun `syncAllData passes Instant parsed from entryTimestamp to HC library`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val timestamp = "2024-06-15T08:30:00.000Z"
        val bpmEntry = TestFixtures.aBpmEntry(entryTimestamp = timestamp)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.syncAllData()

        val bpData = slot.captured.firstOrNull { it.type == DataType.BloodPressure }
        assertThat(bpData).isNotNull()
        assertThat(bpData!!.timeStamp).isEqualTo(java.time.Instant.parse(timestamp))
    }

    @Test
    fun `deleteEntry with BpmEntry returns false on malformed entryTimestamp`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(entryTimestamp = "not-a-timestamp")

        val result = service.deleteEntry(bpmEntry)

        assertThat(result).isFalse()
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().deleteEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // syncAllData failure contract (I3)
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData surfaces HC saveData failure as a false result and dialog (no silent success)`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)
        coEvery { anyConstructed<HealthConnect>().saveData(any()) } throws RuntimeException("HC save failed")

        val result = service.syncAllData()

        assertThat(result).isFalse()
        // Prove the catch path was reached: user-facing dialog must fire.
        // Without this, a regression that swallows the exception and returns false
        // through a different branch would still pass.
        coVerify { dialogQueueService.showDialog(any<DialogModel.Alert>()) }
    }

    // -------------------------------------------------------------------------
    // I1 — single saveData call combining body-scale + BPM (no partial-success risk)
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData makes a single saveData call with both ScaleEntry and BpmEntry records combined`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val scaleEntry = TestFixtures.aWeightEntry(weight = 75.0)
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80, pulse = 70)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(scaleEntry, bpmEntry)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.syncAllData()

        // Single saveData call — not two — to avoid partial-success state.
        coVerify(exactly = 1) { anyConstructed<HealthConnect>().saveData(any()) }
        // Combined payload contains both body-scale and BPM types.
        assertThat(slot.captured.any { it.type == DataType.Weight }).isTrue()
        assertThat(slot.captured.any { it.type == DataType.BloodPressure }).isTrue()
    }

    // -------------------------------------------------------------------------
    // Pulse sync via DataType.RestingHeartRate (BPM-source pulse)
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData adds RestingHeartRate record when BpmEntry pulse is positive`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80, pulse = 72)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.syncAllData()

        val hr = slot.captured.firstOrNull { it.type == DataType.RestingHeartRate }
        assertThat(hr).isNotNull()
        assertThat(hr!!.value).isEqualTo(72.0)
    }

    @Test
    fun `syncAllData omits RestingHeartRate record when BpmEntry pulse is zero`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80, pulse = 0)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(bpmEntry)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.syncAllData()

        assertThat(slot.captured.any { it.type == DataType.RestingHeartRate }).isFalse()
        // BP record still present.
        assertThat(slot.captured.any { it.type == DataType.BloodPressure }).isTrue()
    }

    @Test
    fun `deleteEntry with BpmEntry includes RestingHeartRate when pulse is positive`() = runTest(mainDispatcherRule.scheduler) {
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 120, diastolic = 80, pulse = 72)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().deleteEntry(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.deleteEntry(bpmEntry)

        assertThat(slot.captured.any { it.type == DataType.RestingHeartRate }).isTrue()
        val hr = slot.captured.first { it.type == DataType.RestingHeartRate }
        assertThat(hr.value).isEqualTo(72.0)
    }

    @Test
    fun `deleteEntry with BpmEntry returns true with only pulse when BP readings are zero`() = runTest(mainDispatcherRule.scheduler) {
        // Symmetric with sync: if pulse synced (because > 0) but BP didn't (because = 0),
        // delete must remove the pulse record; failing to do so causes HC state drift.
        stubIntegrated()
        val bpmEntry = TestFixtures.aBpmEntry(systolic = 0, diastolic = 0, pulse = 72)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().deleteEntry(capture(slot)) } returns HealthConnectResult.Success(Unit)

        val result = service.deleteEntry(bpmEntry)

        assertThat(result).isTrue()
        assertThat(slot.captured.any { it.type == DataType.RestingHeartRate }).isTrue()
        assertThat(slot.captured.any { it.type == DataType.BloodPressure }).isFalse()
    }

    // -------------------------------------------------------------------------
    // C1 fix — LeanBodyMass derived from bodyFat (not muscleMass) on delete
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry with ScaleEntry sends LeanBodyMass derived from bodyFat (not muscleMass)`() = runTest(mainDispatcherRule.scheduler) {
        // Pre-fix: delete used (muscleMass × weight) / 100 → 28.0
        // Post-fix: delete uses weight × (1 - bodyFat/100) → 60.0 (matches sync formula)
        // The two values are different by design so a regression to the old formula will fail this test.
        stubIntegrated()
        val scaleEntry = TestFixtures.aWeightEntry(weight = 80.0, bodyFat = 25.0, muscleMass = 35.0)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().deleteEntry(capture(slot)) } returns HealthConnectResult.Success(Unit)

        service.deleteEntry(scaleEntry)

        val lbm = slot.captured.firstOrNull { it.type == DataType.LeanBodyMass }
        assertThat(lbm).isNotNull()
        // Sync formula: 80 * (1 - 25/100) = 60.0
        assertThat(lbm!!.value).isEqualTo(60.0)
        // Sanity-check it is NOT the old muscleMass-based value (35 * 80 / 100 = 28.0).
        assertThat(lbm.value).isNotEqualTo(28.0)
    }

    // -------------------------------------------------------------------------
    // parseTimestampOrNull — skip-and-continue keeps valid entries flowing
    // -------------------------------------------------------------------------

    @Test
    fun `syncAllData returns false and shows dialog when entries existed but all were filtered out`() = runTest(mainDispatcherRule.scheduler) {
        // Pin the contract: if the user has entries but every one is unsyncable
        // (malformed timestamp, all-zero values), do NOT show the success toast —
        // surface the same error dialog as a sync exception so the user knows nothing
        // reached Health Connect.
        stubIntegrated()
        val allBadTimestamp = TestFixtures.aBpmEntry(entryTimestamp = "not-a-timestamp")
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(allBadTimestamp)

        val result = service.syncAllData()

        assertThat(result).isFalse()
        coVerify { dialogQueueService.showDialog(any<DialogModel.Alert>()) }
        coVerify(exactly = 0) { anyConstructed<HealthConnect>().saveData(any()) }
    }

    @Test
    fun `syncAllData skips entries with malformed timestamps but still syncs valid ones`() = runTest(mainDispatcherRule.scheduler) {
        // A future regression to a raw Instant.parse() would make ONE bad row abort the
        // entire sync. This test pins the skip-and-continue contract.
        stubIntegrated()
        val validBpm = TestFixtures.aBpmEntry(entryTimestamp = "2024-06-15T08:30:00.000Z", systolic = 120, diastolic = 80)
        val malformedBpm = TestFixtures.aBpmEntry(entryTimestamp = "not-a-timestamp", systolic = 130, diastolic = 85)
        coEvery { entryRepository.getEntriesByAccount(fakeAccountId) } returns listOf(validBpm, malformedBpm)
        val slot = slot<List<HealthConnectData>>()
        coEvery { anyConstructed<HealthConnect>().saveData(capture(slot)) } returns HealthConnectResult.Success(Unit)

        val result = service.syncAllData()

        assertThat(result).isTrue()
        // Valid BPM record made it through.
        val captured = slot.captured.filter { it.type == DataType.BloodPressure }
        assertThat(captured).hasSize(1)
        assertThat(captured.first().bloodPressure?.systolic).isEqualTo(120.0)
    }
}
