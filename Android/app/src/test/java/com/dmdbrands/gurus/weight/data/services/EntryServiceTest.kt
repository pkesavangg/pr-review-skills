package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
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
    private val fakeEntry3: Entry = mockk(relaxed = true)

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
        every { accountRepository.getSyncTimeStamp() } returns flowOf("")
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
    fun `atomic batch failure does not crash and is swallowed for retry`() = runTest {
        coEvery { entryRepository.getUnSynced(testAccountId) } returns emptyList()
        coEvery { entryRepository.getOperationCount(testAccountId) } returns 0
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        every { accountRepository.getSyncTimeStamp() } returns flowOf("")
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
    // initializeGoalCardMonitoring — entry count >= 3
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring calls checkGoalCard when entries at least 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2, fakeEntry3)

        service.initializeGoalCardMonitoring(testAccountId)
        Thread.sleep(200)

        coVerify { goalService.checkGoalCard() }
    }

    @Test
    fun `initializeGoalCardMonitoring does not call checkGoalCard when entries less than 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2)

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
}
