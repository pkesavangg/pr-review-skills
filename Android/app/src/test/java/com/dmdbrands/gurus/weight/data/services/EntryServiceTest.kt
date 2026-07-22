package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesSyncResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IBabyProfileRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryServiceTest {

    // --- Mocks ---
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk()
    private val babyProfileRepository: IBabyProfileRepository = mockk(relaxed = true)
    private val goalService: IGoalService = mockk(relaxed = true)
    private val healthConnectService: IHealthConnectService = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)

    private lateinit var service: EntryService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"
    private val fakeAccount: Account = mockk {
        every { initialWeight } returns 180.0
    }
    private val fakeEntry: Entry = mockk(relaxed = true)
    private val fakeEntry2: Entry = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.d(any(), any(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.w(any<String>(), any<String>()) } returns Unit
        every { AppLog.i(any<String>(), any<String>()) } returns Unit
        every { AppLog.v(any<String>(), any<String>()) } returns Unit

        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)

        service = EntryService(
            entryRepository = entryRepository,
            accountRepository = accountRepository,
            babyProfileRepository = babyProfileRepository,
            goalService = goalService,
            healthConnectService = healthConnectService,
            healthConnectRepository = healthConnectRepository,
            analyticsService = analyticsService,
            appScope = TestScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // isUpdating
    // -------------------------------------------------------------------------

    @Test
    fun `isUpdating defaults to false`() {
        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateAllData — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData calls syncOperations with account id`() = runTest {
        service.updateAllData(testAccountId)

        coVerify { entryRepository.getUnSynced(testAccountId) }
    }

    // -------------------------------------------------------------------------
    // updateAllData — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData returns early when accountId is null`() = runTest {
        service.updateAllData(null)

        coVerify(exactly = 0) { entryRepository.getUnSynced(any()) }
    }

    // -------------------------------------------------------------------------
    // addEntry — single entry
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single entry triggers sync`() = runTest {
        service.updateAllData(testAccountId)

        service.addEntry(fakeEntry)

        coVerify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // addEntry — list of entries
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list triggers sync`() = runTest {
        service.updateAllData(testAccountId)
        val entries = listOf(fakeEntry, fakeEntry2)

        service.addEntry(entries)

        coVerify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // syncOperations
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations returns early when accountId is null`() = runTest {
        val newEntries = listOf(fakeEntry)
        val deleteOps = listOf(fakeEntry2)

        service.syncOperations(newEntries, deleteOps)

        // Should not call entryRepository since accountId is null
        coVerify(exactly = 0) { entryRepository.getUnSynced(any()) }
    }

    // -------------------------------------------------------------------------
    // Unified atomic batch send (MOB-379)
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry sends a single mixed weight+bp batch to entries endpoint`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        val captured = mutableListOf<List<UnifiedEntryRequest>>()
        coEvery { entryRepository.sendBatchToAPI(capture(captured)) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")

        service.updateAllData(testAccountId)
        service.addEntry(listOf(realScaleEntry(), realBpmEntry()))

        val batch = captured.last()
        assertThat(batch).hasSize(2)
        assertThat(batch.map { it.category }).containsExactly("weight", "bp")
        coVerify { entryRepository.sendBatchToAPI(any()) }
    }

    @Test
    fun `addEntry syncs a created BP entry to Health Connect despite lowercase create op`() = runTest {
        // Regression (MOB-1498): BP ops carry lowercase "create"; the case-sensitive gate used to
        // skip tryLocalIntegration for them, so BP never reached Health Connect while weight did.
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")
        coEvery { healthConnectService.checkIntegrated() } returns true

        service.updateAllData(testAccountId)
        service.addEntry(listOf(realBpmEntry()))

        coVerify {
            healthConnectService.syncEntries(
                match { entries -> entries.any { it is com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry } },
            )
        }
    }

    @Test
    fun `addEntry sends a baby weight entry to the entries endpoint`() = runTest {
        // Manual baby entries go through addEntry (syncing path), unlike addBabyEntry (local-only).
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        val captured = mutableListOf<List<UnifiedEntryRequest>>()
        coEvery { entryRepository.sendBatchToAPI(capture(captured)) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")

        service.updateAllData(testAccountId)
        service.addEntry(babyEntry())

        val batch = captured.last()
        assertThat(batch.map { it.category }).containsExactly("baby")
        assertThat(batch.first().entryType).isEqualTo("weight")
        assertThat(batch.first().babyWeightDecigrams).isEqualTo(40_000)
        coVerify { entryRepository.sendBatchToAPI(any()) }
    }

    @Test
    fun `successful batch persists source rows as synced even when server echo is empty`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { entryRepository.getEntryById(any()) } returns null
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { accountRepository.updateSyncTimeStamp(any()) } returns Unit
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")

        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        // P1 fix: the source row is written back as isSynced = true even though the server
        // echoed no entries, so it is not re-POSTed (and duplicated) on the next sync.
        coVerify { entryRepository.insert(match<Entry> { it.entry.isSynced }) }
    }

    @Test
    fun `sync with null server timestamp does not crash and skips cursor update`() = runTest {
        // MOB-591: the server may return a null sync timestamp (e.g. first sync with empty start).
        // It must not be written through to the proto store (NPE) — the cursor update is skipped.
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { entryRepository.getEntryById(any()) } returns null
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { accountRepository.updateSyncTimeStamp(any()) } returns Unit
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = null)
        coEvery { entryRepository.getEntriesSync(any(), any()) } returns
            EntriesSyncResponse(entries = emptyList(), timestamp = null)

        // Should not throw.
        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        // Null timestamp is never written to the sync cursor.
        coVerify(exactly = 0) { accountRepository.updateSyncTimeStamp(any()) }
    }

    @Test
    fun `atomic batch failure does not crash and is swallowed for retry`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } throws RuntimeException("batch failed")

        // Should not throw — the whole batch is left unsynced for the next retry.
        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        coVerify { entryRepository.sendBatchToAPI(any()) }
    }

    private fun realScaleEntry() = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry(
        entry = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity(
            accountId = testAccountId,
            entryTimestamp = "2024-01-01T10:00:00.000Z",
            operationType = "create",
            deviceType = "scale",
            deviceId = "d1",
        ),
        scale = com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics(
            scaleEntry = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity(
                id = 0L, weight = 750.0, bodyFat = null, muscleMass = null, water = null, bmi = null, source = "manual",
            ),
            scaleEntryMetric = null,
        ),
    )

    private fun realBpmEntry() = com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry(
        entry = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity(
            accountId = testAccountId,
            entryTimestamp = "2024-01-01T11:00:00.000Z",
            operationType = "create",
            deviceType = "bpm",
            deviceId = "d2",
        ),
        bpmEntry = com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity(
            id = 0L, systolic = 120, diastolic = 80, pulse = 72, meanArterial = "93", note = null,
        ),
    )

    // -------------------------------------------------------------------------
    // addBabyEntry / deleteBabyEntry — synced baby persistence (POST /v3/entries, §2.16)
    // -------------------------------------------------------------------------

    private fun babyEntry(babyId: String = "baby-1") = BabyEntry(
        entry = EntryEntity(
            id = 0L,
            accountId = "",
            entryTimestamp = "2026-06-10T10:00:00.000Z",
            operationType = "create",
            deviceType = "baby",
            deviceId = "dev-1",
        ),
        babyEntry = BabyEntryEntity(id = 0L, babyId = babyId, babyWeightDecigrams = 40_000),
    )

    @Test
    fun `addBabyEntry returns -1 and does not insert when accountId is null`() = runTest {
        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(-1)
        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `addBabyEntry persists under active account as unsynced create and returns new id`() = runTest {
        service.updateAllData(testAccountId)
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        val captured = slot<Entry>()
        coEvery { entryRepository.insert(capture(captured)) } returns 99L

        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(99L)
        // Stamped with the active (parent) account and queued for sync (isSynced = false).
        assertThat(captured.captured.entry.accountId).isEqualTo(testAccountId)
        assertThat(captured.captured.entry.isSynced).isFalse()
        assertThat(captured.captured.entry.operationType).isEqualTo(OperationType.CREATE.name)
    }

    @Test
    fun `addBabyEntry pushes the reading to the entries endpoint as category baby`() = runTest {
        // The 2.0 unified API carries baby entries, so an assigned reading now syncs (§2.16).
        coEvery { entryRepository.insert(any<Entry>()) } returns 99L
        // The freshly-inserted unsynced row is what the sync loop picks up and POSTs.
        coEvery { entryRepository.getUnSynced(testAccountId) } returns listOf(babyEntry())
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        val captured = mutableListOf<List<UnifiedEntryRequest>>()
        coEvery { entryRepository.sendBatchToAPI(capture(captured)) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")

        service.updateAllData(testAccountId)
        service.addBabyEntry(babyEntry())

        val batch = captured.last()
        assertThat(batch.map { it.category }).contains("baby")
        coVerify { entryRepository.sendBatchToAPI(any()) }
    }

    @Test
    fun `addBabyEntry returns -1 and logs when insert fails`() = runTest {
        service.updateAllData(testAccountId)
        coEvery { entryRepository.insert(any<Entry>()) } throws RuntimeException("DB error")

        val result = service.addBabyEntry(babyEntry())

        assertThat(result).isEqualTo(-1)
        verify { AppLog.e("EntryService", match { it.contains("Error saving baby entry") }, any<Throwable>()) }
    }

    @Test
    fun `deleteBabyEntry syncs a delete op for the row to the entries endpoint`() = runTest {
        coEvery { entryRepository.getEntryById(99L) } returns babyEntry()
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        val captured = mutableListOf<List<UnifiedEntryRequest>>()
        coEvery { entryRepository.sendBatchToAPI(capture(captured)) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-01T00:00:00.000Z")

        service.updateAllData(testAccountId)
        service.deleteBabyEntry(99L)

        coVerify { entryRepository.getEntryById(99L) }
        val batch = captured.last()
        assertThat(batch.map { it.category }).contains("baby")
        assertThat(batch.first().operationType).isEqualTo("delete")
    }

    @Test
    fun `deleteBabyEntry is a no-op when the row no longer exists`() = runTest {
        service.updateAllData(testAccountId)
        coEvery { entryRepository.getEntryById(99L) } returns null

        service.deleteBabyEntry(99L)

        coVerify(exactly = 0) { entryRepository.sendBatchToAPI(any()) }
    }

    @Test
    fun `sync refreshes baby profiles before pulling entries down`() = runTest {
        // Baby profiles must be present locally before incoming baby entries can satisfy
        // their FK, so the read-down pull refreshes them first (MOB-598).
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")

        service.updateAllData(testAccountId)

        coVerify { babyProfileRepository.refresh(testAccountId) }
    }

    // -------------------------------------------------------------------------
    // initializeGoalCardMonitoring — entry count >= 3
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring calls checkGoalCard when weight entries at least 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns
            listOf(realScaleEntry(), realScaleEntry(), realScaleEntry())

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify { goalService.checkGoalCard() }
    }

    @Test
    fun `initializeGoalCardMonitoring does not call checkGoalCard when weight entries less than 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns
            listOf(realScaleEntry(), realScaleEntry())

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify(exactly = 0) { goalService.checkGoalCard() }
    }

    @Test
    fun `initializeGoalCardMonitoring counts only weight entries, not BP or baby`() = runTest {
        // 2 weight + 1 BP + 1 baby → only 2 weight readings, below the 3-weight threshold.
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns
            listOf(realScaleEntry(), realScaleEntry(), realBpmEntry(), babyEntry())

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify(exactly = 0) { goalService.checkGoalCard() }
    }

    // -------------------------------------------------------------------------
    // initializeGoalCardMonitoring — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring handles exception gracefully`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } throws RuntimeException("DB error")

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        verify { AppLog.e("EntryService", match { it.contains("Error checking entries for goal card") }, any<String>()) }
    }

    // -------------------------------------------------------------------------
    // Unified sync GET failure — persists placeholders for retry (MOB-380)
    // -------------------------------------------------------------------------

    @Test
    fun `unified sync GET failure persists placeholders and does not crash`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 2
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("2024-01-01T00:00:00.000Z")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-02T00:00:00.000Z")
        // The unified GET delta pull throws — the catch block must persist placeholders.
        coEvery { entryRepository.getEntriesSync(any(), any()) } throws RuntimeException("GET failed")

        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        verify { AppLog.e("EntryService", match { it.contains("Unified sync GET failed") }, any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // tryLocalIntegration — Health Connect sync for CREATE scale entries
    // -------------------------------------------------------------------------

    @Test
    fun `create scale entry syncs to Health Connect when integration enabled`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-02T00:00:00.000Z")
        coEvery { entryRepository.getEntriesSync(any(), any()) } returns
            EntriesSyncResponse(entries = emptyList(), timestamp = null)
        coEvery { healthConnectService.checkIntegrated() } returns true

        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        coVerify { healthConnectService.syncData(any()) }
        coVerify { healthConnectRepository.syncEntry(any()) }
    }

    @Test
    fun `edit scale entry mirrors the edited reading to Health Connect (MOB-1173)`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-02T00:00:00.000Z")
        coEvery { entryRepository.getEntriesSync(any(), any()) } returns
            EntriesSyncResponse(entries = emptyList(), timestamp = null)
        coEvery { healthConnectService.checkIntegrated() } returns true

        service.updateAllData(testAccountId)
        service.editEntry(realScaleEntry())

        // Edit no longer leaves HC stale: the edited reading is pushed to Health Connect too.
        coVerify { healthConnectService.syncData(any()) }
        coVerify { healthConnectRepository.syncEntry(any()) }
    }

    @Test
    fun `create scale entry skips Health Connect when integration disabled`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-02T00:00:00.000Z")
        coEvery { entryRepository.getEntriesSync(any(), any()) } returns
            EntriesSyncResponse(entries = emptyList(), timestamp = null)
        coEvery { healthConnectService.checkIntegrated() } returns false

        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        coVerify(exactly = 0) { healthConnectService.syncData(any()) }
    }

    @Test
    fun `Health Connect sync failure is swallowed and does not crash sync`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("")
        coEvery { entryRepository.sendBatchToAPI(any()) } returns
            UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-02T00:00:00.000Z")
        coEvery { entryRepository.getEntriesSync(any(), any()) } returns
            EntriesSyncResponse(entries = emptyList(), timestamp = null)
        coEvery { healthConnectService.checkIntegrated() } returns true
        coEvery { healthConnectService.syncData(any()) } throws RuntimeException("HC error")

        // Non-critical — must not throw.
        service.updateAllData(testAccountId)
        service.addEntry(realScaleEntry())

        verify { AppLog.e("EntryService", match { it.contains("Error syncing to Health Connect") }, any<Throwable>()) }
    }
}
