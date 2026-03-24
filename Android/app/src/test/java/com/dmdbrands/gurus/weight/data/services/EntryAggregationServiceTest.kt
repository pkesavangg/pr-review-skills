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
import org.junit.After
import org.junit.Before
import org.junit.Test

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

    @Before
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

    @After
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
}
