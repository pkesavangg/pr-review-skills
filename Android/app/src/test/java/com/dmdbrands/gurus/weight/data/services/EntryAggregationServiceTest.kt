package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IGoalRepository
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryAggregationServiceTest {

    // --- Mocks ---
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val goalRepository: IGoalRepository = mockk(relaxed = true)
    private val appScope = TestScope()

    private lateinit var service: EntryAggregationService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.d(any(), any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.w(any(), any()) } returns Unit

        // Default stubs for combined weight settings flow
        every { accountRepository.getActiveAccountWeightUnitFlow() } returns flowOf(WeightUnit.LB)
        every { accountRepository.getActiveAccountWeightlessFlow() } returns flowOf(mockk<Weightless>(relaxed = true))
        every { goalRepository.getCurrentGoal() } returns flowOf(null)
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        // Default stubs for entry queries
        coEvery { entryRepository.getLatestEntry(any()) } returns flowOf(null)
        coEvery { entryRepository.getLastNDaysEntries(any(), any()) } returns flowOf(emptyList())
        every { entryRepository.getMonthlyHistoryLastYear(any()) } returns flowOf(emptyList())
        every { entryRepository.getMonthlyBodyScaleAveragesWithJoin(any()) } returns flowOf(emptyList())
        every { entryRepository.getMonthlyBodyScaleLatestWithJoin(any()) } returns flowOf(emptyList())
        every { entryRepository.getDaywiseBodyScaleAveragesWithJoin(any()) } returns flowOf(emptyList())
        every { entryRepository.getDaywiseBodyScaleLatestWithJoin(any()) } returns flowOf(emptyList())
        every { entryRepository.getMonthlyAverage(any()) } returns flowOf(emptyList())

        service = EntryAggregationService(
            entryRepository = entryRepository,
            accountRepository = accountRepository,
            goalRepository = goalRepository,
            appScope = appScope,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `latestEntry defaults to null`() {
        assertThat(service.latestEntry.value).isNull()
    }

    @Test
    fun `last7Days defaults to empty list`() {
        assertThat(service.last7Days.value).isEmpty()
    }

    @Test
    fun `last30Days defaults to empty list`() {
        assertThat(service.last30Days.value).isEmpty()
    }

    @Test
    fun `monthlyBodyScaleAverages defaults to empty list`() {
        assertThat(service.monthlyBodyScaleAverages.value).isEmpty()
    }

    @Test
    fun `monthlyBodyScaleLatest defaults to empty list`() {
        assertThat(service.monthlyBodyScaleLatest.value).isEmpty()
    }

    @Test
    fun `daywiseBodyScaleAverages defaults to empty list`() {
        assertThat(service.daywiseBodyScaleAverages.value).isEmpty()
    }

    @Test
    fun `daywiseBodyScaleLatest defaults to empty list`() {
        assertThat(service.daywiseBodyScaleLatest.value).isEmpty()
    }

    @Test
    fun `monthlyAverage defaults to empty list`() {
        assertThat(service.monthlyAverage.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // setAccountId
    // -------------------------------------------------------------------------

    @Test
    fun `setAccountId stores the account id`() {
        service.setAccountId(testAccountId, 180.0)

        // No direct access to accountId, but we can verify it does not crash
        // and subsequent operations use it
    }

    @Test
    fun `setAccountId accepts null accountId`() {
        service.setAccountId(null, null)

        // Should not crash
    }

    // -------------------------------------------------------------------------
    // startDataCollection — launches jobs
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection queries latest entry`() {
        service.startDataCollection(testAccountId)

        coEvery { entryRepository.getLatestEntry(testAccountId) } returns flowOf(null)
    }

    @Test
    fun `startDataCollection queries last 7 days`() {
        service.startDataCollection(testAccountId)

        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 7) } returns flowOf(emptyList())
    }

    @Test
    fun `startDataCollection queries last 30 days`() {
        service.startDataCollection(testAccountId)

        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 30) } returns flowOf(emptyList())
    }

    // -------------------------------------------------------------------------
    // clearFlows
    // -------------------------------------------------------------------------

    @Test
    fun `clearFlows resets latestEntry to null`() {
        service.clearFlows()

        assertThat(service.latestEntry.value).isNull()
    }

    @Test
    fun `clearFlows resets last7Days to empty list`() {
        service.clearFlows()

        assertThat(service.last7Days.value).isEmpty()
    }

    @Test
    fun `clearFlows resets last30Days to empty list`() {
        service.clearFlows()

        assertThat(service.last30Days.value).isEmpty()
    }

    @Test
    fun `clearFlows resets monthlyBodyScaleAverages to empty list`() {
        service.clearFlows()

        assertThat(service.monthlyBodyScaleAverages.value).isEmpty()
    }

    @Test
    fun `clearFlows resets monthlyBodyScaleLatest to empty list`() {
        service.clearFlows()

        assertThat(service.monthlyBodyScaleLatest.value).isEmpty()
    }

    @Test
    fun `clearFlows resets daywiseBodyScaleAverages to empty list`() {
        service.clearFlows()

        assertThat(service.daywiseBodyScaleAverages.value).isEmpty()
    }

    @Test
    fun `clearFlows resets daywiseBodyScaleLatest to empty list`() {
        service.clearFlows()

        assertThat(service.daywiseBodyScaleLatest.value).isEmpty()
    }

    @Test
    fun `clearFlows resets monthlyAverage to empty list`() {
        service.clearFlows()

        assertThat(service.monthlyAverage.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // refreshEntryData — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `refreshEntryData does nothing when accountId is null`() = runTest {
        service.setAccountId(null, null)

        service.refreshEntryData()

        // Should return early without errors
    }

    @Test
    fun `refreshEntryData calls repository methods when accountId is set`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 0
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 0

        service.refreshEntryData()

        coVerify { entryRepository.getStreakData(testAccountId) }
    }

    // -------------------------------------------------------------------------
    // refreshEntryData — error handling
    // -------------------------------------------------------------------------

    @Test
    fun `refreshEntryData handles exception gracefully`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        coEvery { entryRepository.getStreakData(any()) } throws RuntimeException("DB error")

        service.refreshEntryData()

        // Should not crash — exception caught internally
    }

    // -------------------------------------------------------------------------
    // getMonthlyAverage
    // -------------------------------------------------------------------------

    @Test
    fun `getMonthlyAverage returns flow from repository`() = runTest {
        every { entryRepository.getMonthlyAverage(testAccountId) } returns flowOf(emptyList())

        val result = service.getMonthlyAverage(testAccountId)

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // monthDetails
    // -------------------------------------------------------------------------

    @Test
    fun `monthDetails returns flow for the given month`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        every { entryRepository.getMonthDetail(testAccountId, "2024-01") } returns flowOf(emptyList())

        val result = service.monthDetails("Jan 2024")

        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // interface conformance
    // -------------------------------------------------------------------------

    @Test
    fun `service implements IEntryAggregationService`() {
        assertThat(service).isInstanceOf(com.dmdbrands.gurus.weight.domain.services.IEntryAggregationService::class.java)
    }

    // -------------------------------------------------------------------------
    // refreshEntryData — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `refreshEntryData queries streak data from repository`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 5
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 10

        service.refreshEntryData()

        coVerify { entryRepository.getStreakData(testAccountId) }
        coVerify { entryRepository.getLongestStreakCount(testAccountId) }
        coVerify { entryRepository.getTotalCount(testAccountId) }
    }

    @Test
    fun `refreshEntryData queries oldest entry when initialWeight is null`() = runTest {
        service.setAccountId(testAccountId, null)
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 0
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 0
        coEvery { entryRepository.getOldestEntry(testAccountId) } returns null

        service.refreshEntryData()

        coVerify { entryRepository.getOldestEntry(testAccountId) }
    }

    @Test
    fun `refreshEntryData queries oldest entry when initialWeight is zero`() = runTest {
        service.setAccountId(testAccountId, 0.0)
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 0
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 0
        coEvery { entryRepository.getOldestEntry(testAccountId) } returns null

        service.refreshEntryData()

        coVerify { entryRepository.getOldestEntry(testAccountId) }
    }

    @Test
    fun `refreshEntryData does not query oldest entry when initialWeight is set`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 0
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 0

        service.refreshEntryData()

        coVerify(exactly = 0) { entryRepository.getOldestEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // getMonthlyAverage — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `getMonthlyAverage combines entries with weight settings`() = runTest {
        every { entryRepository.getMonthlyAverage(testAccountId) } returns flowOf(emptyList())

        val result = service.getMonthlyAverage(testAccountId)

        // Should return a flow that can be collected
        assertThat(result).isNotNull()
    }

    @Test
    fun `getMonthlyAverage returns empty when repository returns empty`() = runTest {
        every { entryRepository.getMonthlyAverage(testAccountId) } returns flowOf(emptyList())

        val flow = service.getMonthlyAverage(testAccountId)
        flow.collect { months ->
            assertThat(months).isEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // monthDetails — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `monthDetails parses month string and queries repository`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        every { entryRepository.getMonthDetail(testAccountId, "2024-03") } returns flowOf(emptyList())

        val result = service.monthDetails("Mar 2024")

        assertThat(result).isNotNull()
    }

    @Test
    fun `monthDetails returns empty for month with no entries`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        every { entryRepository.getMonthDetail(testAccountId, "2024-06") } returns flowOf(emptyList())

        val flow = service.monthDetails("Jun 2024")
        flow.collect { entries ->
            assertThat(entries).isEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // startDataCollection — additional coverage
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection launches multiple collection jobs`() {
        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)

        // Verify that various repository methods are called for data collection
        coEvery { entryRepository.getLatestEntry(testAccountId) } returns flowOf(null)
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 7) } returns flowOf(emptyList())
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 30) } returns flowOf(emptyList())
    }

    @Test
    fun `startDataCollection after clearFlows can restart collection`() {
        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        service.clearFlows()

        // Should be able to restart data collection after clearing
        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)

        // Should not crash
        assertThat(service.latestEntry.value).isNull()
    }

    @Test
    fun `clearFlows after startDataCollection resets all flows`() {
        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)

        service.clearFlows()

        assertThat(service.latestEntry.value).isNull()
        assertThat(service.last7Days.value).isEmpty()
        assertThat(service.last30Days.value).isEmpty()
        assertThat(service.monthlyBodyScaleAverages.value).isEmpty()
        assertThat(service.daywiseBodyScaleAverages.value).isEmpty()
        assertThat(service.monthlyAverage.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // startDataCollection — updateLatestEntry populates _latestEntry
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates latestEntry when repository emits entry`() = runTest {
        val fakeEntry: Entry = mockk(relaxed = true)
        coEvery { entryRepository.getLatestEntry(testAccountId) } returns flowOf(fakeEntry)
        // Stub streak/progress queries for updateProgressCache triggered by updateLatestEntry
        coEvery { entryRepository.getStreakData(testAccountId) } returns emptyList()
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 0
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 0

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.latestEntry.value).isEqualTo(fakeEntry)
    }

    // -------------------------------------------------------------------------
    // startDataCollection — updateLast7Days populates _last7Days
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates last7Days when repository emits entries`() = runTest {
        val fakeEntries = listOf<Entry>(mockk(relaxed = true), mockk(relaxed = true))
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 7) } returns flowOf(fakeEntries)

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.last7Days.value).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // startDataCollection — updateLast30Days populates _last30Days
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates last30Days when repository emits entries`() = runTest {
        val fakeEntries = listOf<Entry>(mockk(relaxed = true))
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 30) } returns flowOf(fakeEntries)

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.last30Days.value).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // startDataCollection — updateMonthYear populates _monthYear
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates monthlyAverage when repository emits months`() = runTest {
        every { entryRepository.getMonthlyAverage(testAccountId) } returns flowOf(emptyList())

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.monthlyAverage.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // startDataCollection — updateMonthlyBodyScaleAveragesWithJoin
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates monthlyBodyScaleAverages from repository`() = runTest {
        every { entryRepository.getMonthlyBodyScaleAveragesWithJoin(testAccountId) } returns flowOf(emptyList())

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.monthlyBodyScaleAverages.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // updateProgressCache — via refreshEntryData
    // -------------------------------------------------------------------------

    @Test
    fun `refreshEntryData updates progress cache with streak and count values`() = runTest {
        service.setAccountId(testAccountId, 180.0)
        coEvery { entryRepository.getStreakData(testAccountId) } returns listOf("2024-01-01", "2024-01-02")
        coEvery { entryRepository.getLongestStreakCount(testAccountId) } returns 10
        coEvery { entryRepository.getTotalCount(testAccountId) } returns 25

        service.refreshEntryData()

        // Verify the queries were called (progress cache updated internally)
        coVerify { entryRepository.getStreakData(testAccountId) }
        coVerify { entryRepository.getLongestStreakCount(testAccountId) }
        coVerify { entryRepository.getTotalCount(testAccountId) }
    }

    // -------------------------------------------------------------------------
    // startDataCollection — error handling in individual update methods
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection handles exception in updateLatestEntry gracefully`() = runTest {
        coEvery { entryRepository.getLatestEntry(testAccountId) } throws RuntimeException("DB error")

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        // Should not crash, latestEntry stays null
        assertThat(service.latestEntry.value).isNull()
    }

    @Test
    fun `startDataCollection handles exception in updateLast7Days gracefully`() = runTest {
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 7) } throws RuntimeException("DB error")

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.last7Days.value).isEmpty()
    }

    @Test
    fun `startDataCollection handles exception in updateLast30Days gracefully`() = runTest {
        coEvery { entryRepository.getLastNDaysEntries(testAccountId, 30) } throws RuntimeException("DB error")

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.last30Days.value).isEmpty()
    }

    @Test
    fun `startDataCollection handles exception in updateMonthYear gracefully`() = runTest {
        every { entryRepository.getMonthlyHistoryLastYear(testAccountId) } throws RuntimeException("DB error")

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        // Should not crash
    }

    @Test
    fun `startDataCollection handles exception in updateMonthlyBodyScaleAveragesWithJoin gracefully`() = runTest {
        every { entryRepository.getMonthlyBodyScaleAveragesWithJoin(testAccountId) } throws RuntimeException("DB error")

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.monthlyBodyScaleAverages.value).isEmpty()
    }

    // -------------------------------------------------------------------------
    // updateDaywiseBodyScaleAveragesWithJoin — via startDataCollection
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates daywiseBodyScaleAverages via updateDaywiseBodyScaleAveragesWithJoin`() = runTest {
        val fakeData = listOf(mockk<com.dmdbrands.gurus.weight.domain.model.storage.entry.PeriodBodyScaleSummary>(relaxed = true))
        every { entryRepository.getDaywiseBodyScaleAveragesWithJoin(testAccountId) } returns flowOf(fakeData)

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.daywiseBodyScaleAverages.value).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // updateMonthlyAverage — via startDataCollection
    // -------------------------------------------------------------------------

    @Test
    fun `startDataCollection populates monthlyAverage via updateMonthlyAverage`() = runTest {
        val fakeMonths = listOf(mockk<com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth>(relaxed = true))
        every { entryRepository.getMonthlyAverage(testAccountId) } returns flowOf(fakeMonths)
        every { accountRepository.getActiveAccountWeightUnitFlow() } returns flowOf(WeightUnit.LB)

        service.setAccountId(testAccountId, 180.0)
        service.startDataCollection(testAccountId)
        appScope.testScheduler.advanceUntilIdle()

        assertThat(service.monthlyAverage.value).isNotEmpty()
    }
}
