package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.WeightProgress
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryAggregationService
import com.dmdbrands.gurus.weight.domain.services.IEntryCrudService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val crudService: IEntryCrudService = mockk(relaxed = true)
    private val syncService: IEntrySyncService = mockk(relaxed = true)
    private val aggregationService: IEntryAggregationService = mockk(relaxed = true)
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk()
    private val goalService: IGoalService = mockk(relaxed = true)

    private lateinit var service: EntryService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"
    private val fakeAccount: Account = mockk {
        every { initialWeight } returns 180.0
    }
    private val fakeEntry: Entry = mockk(relaxed = true)
    private val fakeEntry2: Entry = mockk(relaxed = true)
    private val fakeEntry3: Entry = mockk(relaxed = true)

    private val lastUpdatedFlow = MutableStateFlow<Long?>(null)
    private val isUpdatingFlow = MutableStateFlow(false)
    private val emptyEntriesFlow = MutableStateFlow<List<Entry>>(emptyList())

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit

        every { syncService.isUpdating } returns isUpdatingFlow
        every { syncService.lastUpdated } returns lastUpdatedFlow
        every { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
        every { entryRepository.getEntriesByOperationType(any(), any()) } returns emptyEntriesFlow

        service = EntryService(
            crudService = crudService,
            syncService = syncService,
            aggregationService = aggregationService,
            entryRepository = entryRepository,
            accountRepository = accountRepository,
            goalService = goalService,
            appScope = TestScope(UnconfinedTestDispatcher()),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // StateFlow delegation — isEmpty, isUpdating, lastUpdated, latestEntry, etc.
    // -------------------------------------------------------------------------

    @Test
    fun `isEmpty defaults to false`() {
        assertThat(service.isEmpty.value).isFalse()
    }

    @Test
    fun `isUpdating delegates to syncService`() {
        assertThat(service.isUpdating).isSameInstanceAs(isUpdatingFlow)
    }

    @Test
    fun `lastUpdated delegates to syncService`() {
        assertThat(service.lastUpdated).isSameInstanceAs(lastUpdatedFlow)
    }

    @Test
    fun `latestEntry delegates to aggregationService`() {
        val flow = MutableStateFlow<Entry?>(null)
        every { aggregationService.latestEntry } returns flow
        assertThat(service.latestEntry).isSameInstanceAs(flow)
    }

    @Test
    fun `last7Days delegates to aggregationService`() {
        val flow = MutableStateFlow<List<Entry>>(emptyList())
        every { aggregationService.last7Days } returns flow
        assertThat(service.last7Days).isSameInstanceAs(flow)
    }

    @Test
    fun `last30Days delegates to aggregationService`() {
        val flow = MutableStateFlow<List<Entry>>(emptyList())
        every { aggregationService.last30Days } returns flow
        assertThat(service.last30Days).isSameInstanceAs(flow)
    }

    @Test
    fun `progress delegates to aggregationService`() {
        val flow = flowOf(mockk<WeightProgress>())
        every { aggregationService.progress } returns flow
        assertThat(service.progress).isSameInstanceAs(flow)
    }

    // -------------------------------------------------------------------------
    // updateAllData — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData clears data and sets account id on aggregation service`() = runTest {
        service.updateAllData(testAccountId)

        verify { aggregationService.setAccountId(testAccountId, 180.0) }
    }

    @Test
    fun `updateAllData calls syncOperations with account id`() = runTest {
        service.updateAllData(testAccountId)

        coVerify { syncService.syncOperations(testAccountId) }
    }

    @Test
    fun `updateAllData starts data collection`() = runTest {
        service.updateAllData(testAccountId)

        verify { aggregationService.startDataCollection(testAccountId) }
    }

    @Test
    fun `updateAllData collects entries by operation type`() = runTest {
        service.updateAllData(testAccountId)

        verify { entryRepository.getEntriesByOperationType(testAccountId, "create") }
    }

    // -------------------------------------------------------------------------
    // updateAllData — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData returns early when accountId is null`() = runTest {
        service.updateAllData(null)

        verify(exactly = 0) { aggregationService.setAccountId(any(), any()) }
        coVerify(exactly = 0) { syncService.syncOperations(any()) }
    }

    // -------------------------------------------------------------------------
    // updateAllData — account fetch exception
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData sets null initialWeight when account is null`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        service.updateAllData(testAccountId)

        // initialWeight will be null since account is null
        verify { aggregationService.setAccountId(testAccountId, null) }
    }

    @Test
    fun `updateAllData handles account repository exception gracefully`() = runTest {
        coEvery { accountRepository.getActiveAccount() } throws RuntimeException("DB error")

        service.updateAllData(testAccountId)

        // Should still proceed — exception is caught
        verify { aggregationService.setAccountId(testAccountId, null) }
        coVerify { syncService.syncOperations(testAccountId) }
    }

    @Test
    fun `updateAllData logs error when account fetch fails`() = runTest {
        coEvery { accountRepository.getActiveAccount() } throws RuntimeException("DB error")

        service.updateAllData(testAccountId)

        verify { AppLog.e("EntryService", match { it.contains("Error updating account flows") }, any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // updateAllData — isEmpty flow updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData sets isEmpty true when entries list is empty`() = runTest {
        val entriesFlow = MutableStateFlow<List<Entry>>(emptyList())
        every { entryRepository.getEntriesByOperationType(testAccountId, "create") } returns entriesFlow

        service.updateAllData(testAccountId)
        Thread.sleep(200)

        assertThat(service.isEmpty.value).isTrue()
    }

    @Test
    fun `updateAllData sets isEmpty false when entries list is non-empty`() = runTest {
        val entriesFlow = MutableStateFlow(listOf(fakeEntry))
        every { entryRepository.getEntriesByOperationType(testAccountId, "create") } returns entriesFlow

        service.updateAllData(testAccountId)
        Thread.sleep(200)

        assertThat(service.isEmpty.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateAllData — calls clearAllData first
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData resets syncService before syncing`() = runTest {
        service.updateAllData(testAccountId)

        verify { syncService.reset() }
    }

    @Test
    fun `updateAllData clears aggregation flows before starting`() = runTest {
        service.updateAllData(testAccountId)

        verify { aggregationService.clearFlows() }
    }

    // -------------------------------------------------------------------------
    // clearAllData
    // -------------------------------------------------------------------------

    @Test
    fun `clearAllData resets isEmpty to false`() {
        service.clearAllData()

        assertThat(service.isEmpty.value).isFalse()
    }

    @Test
    fun `clearAllData calls syncService reset`() {
        service.clearAllData()

        verify { syncService.reset() }
    }

    @Test
    fun `clearAllData calls aggregationService clearFlows`() {
        service.clearAllData()

        verify { aggregationService.clearFlows() }
    }

    // -------------------------------------------------------------------------
    // addEntry — single entry
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single delegates to crudService with current accountId`() = runTest {
        service.updateAllData(testAccountId)

        service.addEntry(fakeEntry)

        coVerify { crudService.addEntry(fakeEntry, testAccountId) }
    }

    @Test
    fun `addEntry single passes null accountId when not initialized`() = runTest {
        service.addEntry(fakeEntry)

        coVerify { crudService.addEntry(fakeEntry, null) }
    }

    // -------------------------------------------------------------------------
    // addEntry — list of entries
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list delegates to crudService with current accountId`() = runTest {
        service.updateAllData(testAccountId)
        val entries = listOf(fakeEntry, fakeEntry2)

        service.addEntry(entries)

        coVerify { crudService.addEntry(entries, testAccountId) }
    }

    @Test
    fun `addEntry list passes null accountId when not initialized`() = runTest {
        val entries = listOf(fakeEntry)

        service.addEntry(entries)

        coVerify { crudService.addEntry(entries, null) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry delegates to crudService with current accountId`() = runTest {
        service.updateAllData(testAccountId)

        service.deleteEntry(fakeEntry)

        coVerify { crudService.deleteEntry(fakeEntry, testAccountId) }
    }

    @Test
    fun `deleteEntry passes null accountId when not initialized`() = runTest {
        service.deleteEntry(fakeEntry)

        coVerify { crudService.deleteEntry(fakeEntry, null) }
    }

    // -------------------------------------------------------------------------
    // syncOperations
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations delegates to syncService with current accountId`() = runTest {
        service.updateAllData(testAccountId)
        val newEntries = listOf(fakeEntry)
        val deleteOps = listOf(fakeEntry2)

        service.syncOperations(newEntries, deleteOps)

        coVerify { syncService.syncOperations(testAccountId, newEntries, deleteOps) }
    }

    @Test
    fun `syncOperations returns early when accountId is null`() = runTest {
        val newEntries = listOf(fakeEntry)
        val deleteOps = listOf(fakeEntry2)

        service.syncOperations(newEntries, deleteOps)

        // syncService.syncOperations is called in updateAllData setup, so verify only the specific call
        coVerify(exactly = 0) { syncService.syncOperations(any(), newEntries, deleteOps) }
    }

    @Test
    fun `syncOperations with empty lists delegates correctly`() = runTest {
        service.updateAllData(testAccountId)

        service.syncOperations(emptyList(), emptyList())

        coVerify { syncService.syncOperations(testAccountId, emptyList(), emptyList()) }
    }

    // -------------------------------------------------------------------------
    // refreshEntryData
    // -------------------------------------------------------------------------

    @Test
    fun `refreshEntryData delegates to aggregationService`() = runTest {
        service.refreshEntryData()

        coVerify { aggregationService.refreshEntryData() }
    }

    // -------------------------------------------------------------------------
    // getMonthlyAverage
    // -------------------------------------------------------------------------

    @Test
    fun `getMonthlyAverage delegates to aggregationService`() = runTest {
        val expectedFlow = flowOf(listOf(mockk<HistoryMonth>()))
        coEvery { aggregationService.getMonthlyAverage(testAccountId) } returns expectedFlow

        val result = service.getMonthlyAverage(testAccountId)

        assertThat(result).isSameInstanceAs(expectedFlow)
    }

    // -------------------------------------------------------------------------
    // monthDetails
    // -------------------------------------------------------------------------

    @Test
    fun `monthDetails delegates to aggregationService`() = runTest {
        val expectedFlow = flowOf(listOf(fakeEntry))
        coEvery { aggregationService.monthDetails("2024-01") } returns expectedFlow

        val result = service.monthDetails("2024-01")

        assertThat(result).isSameInstanceAs(expectedFlow)
    }

    // -------------------------------------------------------------------------
    // getEntriesByDeviceType
    // -------------------------------------------------------------------------

    @Test
    fun `getEntriesByDeviceType delegates to entryRepository`() {
        val expectedFlow = flowOf(listOf(fakeEntry))
        every { entryRepository.getEntriesByDeviceType(testAccountId, "scale") } returns expectedFlow

        val result = service.getEntriesByDeviceType(testAccountId, "scale")

        assertThat(result).isSameInstanceAs(expectedFlow)
    }

    // -------------------------------------------------------------------------
    // initializeGoalCardMonitoring — entry count >= 3
    // -------------------------------------------------------------------------

    @Test
    fun `initializeGoalCardMonitoring calls checkGoalCard when entries at least 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2, fakeEntry3)

        service.initializeGoalCardMonitoring(testAccountId)
        // Trigger the lastUpdated flow to emit so the collect block runs
        lastUpdatedFlow.value = System.currentTimeMillis()
        Thread.sleep(200)

        coVerify { goalService.checkGoalCard() }
    }

    @Test
    fun `initializeGoalCardMonitoring does not call checkGoalCard when entries less than 3`() = runTest {
        coEvery { entryRepository.getEntriesByAccount(testAccountId, false) } returns listOf(fakeEntry, fakeEntry2)

        service.initializeGoalCardMonitoring(testAccountId)
        lastUpdatedFlow.value = System.currentTimeMillis()
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
        lastUpdatedFlow.value = System.currentTimeMillis()
        Thread.sleep(200)

        verify { AppLog.e("EntryService", match { it.contains("Error checking entries for goal card") }, any<String>()) }
    }

    // -------------------------------------------------------------------------
    // updateAllData — clears accountId then sets new one
    // -------------------------------------------------------------------------

    @Test
    fun `updateAllData with different accountId updates correctly`() = runTest {
        service.updateAllData(testAccountId)
        service.updateAllData("acc-456")

        verify { aggregationService.setAccountId("acc-456", 180.0) }
        coVerify { syncService.syncOperations("acc-456") }
    }

    @Test
    fun `addEntry uses latest accountId after updateAllData`() = runTest {
        service.updateAllData(testAccountId)
        service.updateAllData("acc-456")

        service.addEntry(fakeEntry)

        coVerify { crudService.addEntry(fakeEntry, "acc-456") }
    }

    // -------------------------------------------------------------------------
    // clearAllData resets accountId
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry passes null after clearAllData`() = runTest {
        service.updateAllData(testAccountId)
        service.clearAllData()

        service.addEntry(fakeEntry)

        coVerify { crudService.addEntry(fakeEntry, null) }
    }

    @Test
    fun `syncOperations returns early after clearAllData`() = runTest {
        service.updateAllData(testAccountId)
        service.clearAllData()

        service.syncOperations(listOf(fakeEntry), emptyList())

        // Should not call syncService with any accountId+entries combo after clear
        coVerify(exactly = 0) { syncService.syncOperations(any(), listOf(fakeEntry), emptyList()) }
    }

    // -------------------------------------------------------------------------
    // monthlyBodyScaleAverages and related delegations
    // -------------------------------------------------------------------------

    @Test
    fun `monthlyBodyScaleAverages delegates to aggregationService`() {
        val flow = MutableStateFlow(emptyList<PeriodBodyScaleSummary>())
        every { aggregationService.monthlyBodyScaleAverages } returns flow
        assertThat(service.monthlyBodyScaleAverages).isSameInstanceAs(flow)
    }

    @Test
    fun `monthlyBodyScaleLatest delegates to aggregationService`() {
        val flow = MutableStateFlow(emptyList<PeriodBodyScaleSummary>())
        every { aggregationService.monthlyBodyScaleLatest } returns flow
        assertThat(service.monthlyBodyScaleLatest).isSameInstanceAs(flow)
    }

    @Test
    fun `daywiseBodyScaleAverages delegates to aggregationService`() {
        val flow = MutableStateFlow(emptyList<PeriodBodyScaleSummary>())
        every { aggregationService.daywiseBodyScaleAverages } returns flow
        assertThat(service.daywiseBodyScaleAverages).isSameInstanceAs(flow)
    }

    @Test
    fun `daywiseBodyScaleLatest delegates to aggregationService`() {
        val flow = MutableStateFlow(emptyList<PeriodBodyScaleSummary>())
        every { aggregationService.daywiseBodyScaleLatest } returns flow
        assertThat(service.daywiseBodyScaleLatest).isSameInstanceAs(flow)
    }

    @Test
    fun `monthlyAverage delegates to aggregationService`() {
        val flow = MutableStateFlow(emptyList<HistoryMonth>())
        every { aggregationService.monthlyAverage } returns flow
        assertThat(service.monthlyAverage).isSameInstanceAs(flow)
    }
}
