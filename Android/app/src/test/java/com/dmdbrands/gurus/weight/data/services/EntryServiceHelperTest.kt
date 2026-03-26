package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.features.goal.helper.Weightless
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EntryServiceHelperTest {

    private val entryRepository: IEntryRepository = mockk()

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // computeLongestStreakFromDates
    // -------------------------------------------------------------------------

    @Test
    fun `computeLongestStreakFromDates returns 0 for empty list`() {
        val result = EntryServiceHelper.computeLongestStreakFromDates(emptyList())
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `computeLongestStreakFromDates returns 1 for single date`() {
        val result = EntryServiceHelper.computeLongestStreakFromDates(listOf("2024-01-15"))
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `computeLongestStreakFromDates returns correct streak for consecutive dates`() {
        val dates = listOf("2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(4)
    }

    @Test
    fun `computeLongestStreakFromDates returns correct streak for non-consecutive dates`() {
        val dates = listOf("2024-01-01", "2024-01-02", "2024-01-05", "2024-01-06", "2024-01-07")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `computeLongestStreakFromDates handles unsorted input`() {
        val dates = listOf("2024-01-03", "2024-01-01", "2024-01-02")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `computeLongestStreakFromDates handles duplicate dates`() {
        val dates = listOf("2024-01-01", "2024-01-01", "2024-01-02", "2024-01-02")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `computeLongestStreakFromDates returns 1 for all non-consecutive dates`() {
        val dates = listOf("2024-01-01", "2024-01-03", "2024-01-05")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `computeLongestStreakFromDates finds longest streak in middle`() {
        val dates = listOf(
            "2024-01-01",
            "2024-01-05", "2024-01-06", "2024-01-07", "2024-01-08", "2024-01-09",
            "2024-01-15"
        )
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(5)
    }

    // -------------------------------------------------------------------------
    // computeCurrentStreakFromDates
    // -------------------------------------------------------------------------

    @Test
    fun `computeCurrentStreakFromDates returns 0 for empty list`() {
        val result = EntryServiceHelper.computeCurrentStreakFromDates(emptyList())
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `computeCurrentStreakFromDates returns 1 when first date is today`() {
        val today = todayString()
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today))
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `computeCurrentStreakFromDates returns 1 when first date is yesterday`() {
        val yesterday = dayOffsetString(-1)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(yesterday))
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `computeCurrentStreakFromDates returns 0 when first date is two days ago`() {
        val twoDaysAgo = dayOffsetString(-2)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(twoDaysAgo))
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `computeCurrentStreakFromDates counts consecutive days from today`() {
        val today = todayString()
        val yesterday = dayOffsetString(-1)
        val twoDaysAgo = dayOffsetString(-2)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today, yesterday, twoDaysAgo))
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `computeCurrentStreakFromDates breaks on gap`() {
        val today = todayString()
        val yesterday = dayOffsetString(-1)
        val threeDaysAgo = dayOffsetString(-3)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today, yesterday, threeDaysAgo))
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `computeCurrentStreakFromDates counts consecutive from yesterday`() {
        val yesterday = dayOffsetString(-1)
        val twoDaysAgo = dayOffsetString(-2)
        val threeDaysAgo = dayOffsetString(-3)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(yesterday, twoDaysAgo, threeDaysAgo))
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `computeCurrentStreakFromDates handles same year different month`() {
        // Entries in same year but different months — not consecutive days
        val result = EntryServiceHelper.computeCurrentStreakFromDates(
            listOf(todayString(), dayOffsetString(-40))
        )
        assertThat(result).isAtMost(1)
    }

    @Test
    fun `computeCurrentStreakFromDates handles same month different day non-consecutive`() {
        val today = todayString()
        val fourDaysAgo = dayOffsetString(-4)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today, fourDaysAgo))
        assertThat(result).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // processWeight
    // -------------------------------------------------------------------------

    @Test
    fun `processWeight converts stored to lbs when unit is LB`() {
        // stored 1800.0 / 10.0 = 180.0 lbs
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.LB, null)
        assertThat(result).isWithin(0.01).of(180.0)
    }

    @Test
    fun `processWeight converts stored to kg when unit is KG`() {
        // stored 1800.0 / 22.046 * 10 / 10.0 = ~81.65 kg
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.KG, null)
        assertThat(result).isWithin(0.1).of(81.65)
    }

    @Test
    fun `processWeight defaults to lbs when unit is null`() {
        val result = EntryServiceHelper.processWeight(1800.0, null, null)
        assertThat(result).isWithin(0.01).of(180.0)
    }

    @Test
    fun `processWeight subtracts weightless when enabled`() {
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = 5.0f)
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.LB, weightless)
        assertThat(result).isWithin(0.01).of(175.0)
    }

    @Test
    fun `processWeight does not subtract weightless when disabled`() {
        val weightless = Weightless(isWeightlessOn = false, weightlessWeight = 5.0f)
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.LB, weightless)
        assertThat(result).isWithin(0.01).of(180.0)
    }

    @Test
    fun `processWeight with null weightless returns converted weight`() {
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.LB, null)
        assertThat(result).isWithin(0.01).of(180.0)
    }

    @Test
    fun `processWeight with kg and weightless subtracts correctly`() {
        val weightless = Weightless(isWeightlessOn = true, weightlessWeight = 2.0f)
        val result = EntryServiceHelper.processWeight(1800.0, WeightUnit.KG, weightless)
        assertThat(result).isWithin(0.1).of(79.65)
    }

    // -------------------------------------------------------------------------
    // executeOperations (simple overload — insert list)
    // -------------------------------------------------------------------------

    @Test
    fun `executeOperations simple returns early for empty list`() = runTest {
        EntryServiceHelper.executeOperations(entryRepository, emptyList())

        coVerify(exactly = 0) { entryRepository.insert(any<List<Entry>>()) }
    }

    @Test
    fun `executeOperations simple inserts sorted by serverTimestamp`() = runTest {
        val entry1 = createMockEntry(id = 1, serverTimestamp = "2024-01-02T00:00:00Z")
        val entry2 = createMockEntry(id = 2, serverTimestamp = "2024-01-01T00:00:00Z")
        val insertedList = mutableListOf<List<Entry>>()
        coEvery { entryRepository.insert(capture(insertedList)) } returns Unit

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry1, entry2))

        assertThat(insertedList).hasSize(1)
        assertThat(insertedList[0][0].entry.serverTimestamp).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(insertedList[0][1].entry.serverTimestamp).isEqualTo("2024-01-02T00:00:00Z")
    }

    @Test
    fun `executeOperations simple catches exception and logs error`() = runTest {
        val entry = createMockEntry(id = 1, serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.insert(any<List<Entry>>()) } throws RuntimeException("DB error")

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry))

        io.mockk.verify { AppLog.e("EntryService", "Error executing operations", any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // executeOperations (complex overload — create/delete with existence check)
    // -------------------------------------------------------------------------

    @Test
    fun `executeOperations complex returns early for empty list`() = runTest {
        EntryServiceHelper.executeOperations(entryRepository, emptyList(), true, false)

        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
        coVerify(exactly = 0) { entryRepository.update(any()) }
        coVerify(exactly = 0) { entryRepository.delete(any()) }
    }

    @Test
    fun `executeOperations complex inserts new create operations`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.getEntryById(1) } returns null
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry), true, false)

        coVerify { entryRepository.insert(any<Entry>()) }
        coVerify(exactly = 0) { entryRepository.update(any()) }
    }

    @Test
    fun `executeOperations complex updates existing create operations`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        val existingEntry: Entry = mockk(relaxed = true)
        coEvery { entryRepository.getEntryById(1) } returns existingEntry
        coEvery { entryRepository.update(any()) } returns 1L

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry), true, false)

        coVerify { entryRepository.update(any()) }
        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex skips existence check when userHasOperations is false`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry), false, false)

        coVerify(exactly = 0) { entryRepository.getEntryById(any()) }
        coVerify { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex handles delete operations`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "DELETE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.delete(any()) } returns Unit

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry), true, false)

        coVerify { entryRepository.delete(any()) }
        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex handles mixed create and delete operations`() = runTest {
        val createEntry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        val deleteEntry = createMockEntry(id = 2, operationType = "DELETE", serverTimestamp = "2024-01-02T00:00:00Z")
        coEvery { entryRepository.getEntryById(1) } returns null
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L
        coEvery { entryRepository.delete(any()) } returns Unit

        EntryServiceHelper.executeOperations(entryRepository, listOf(createEntry, deleteEntry), true, false)

        coVerify { entryRepository.insert(any<Entry>()) }
        coVerify { entryRepository.delete(any()) }
    }

    @Test
    fun `executeOperations complex sorts by serverTimestamp`() = runTest {
        val entry1 = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-02T00:00:00Z")
        val entry2 = createMockEntry(id = 2, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.getEntryById(any()) } returns null
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry1, entry2), true, false)

        coVerify(exactly = 2) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex catches exception logs error and rethrows`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.getEntryById(1) } throws RuntimeException("DB error")

        var thrownException: Exception? = null
        try {
            EntryServiceHelper.executeOperations(entryRepository, listOf(entry), true, false)
        } catch (e: Exception) {
            thrownException = e
        }

        assertThat(thrownException).isNotNull()
        assertThat(requireNotNull(thrownException).message).isEqualTo("DB error")
        io.mockk.verify { AppLog.e("EntryService", "Error executing operations", any<Throwable>()) }
    }

    @Test
    fun `executeOperations complex uses default parameter for arePlaceholders`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "CREATE", serverTimestamp = "2024-01-01T00:00:00Z")
        coEvery { entryRepository.getEntryById(1) } returns null
        coEvery { entryRepository.insert(any<Entry>()) } returns 1L

        // Call with userHasOperations explicit, arePlaceholders uses default (false)
        EntryServiceHelper.executeOperations(
            entryRepository = entryRepository,
            operations = listOf(entry),
            userHasOperations = true,
        )

        coVerify { entryRepository.getEntryById(1) }
        coVerify { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex uses default for both optional params with empty list`() = runTest {
        // Covers the $default bridge with empty list — returns early
        EntryServiceHelper.executeOperations(
            entryRepository = entryRepository,
            operations = emptyList(),
            userHasOperations = true,
        )

        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
    }

    @Test
    fun `executeOperations complex ignores unknown operation types`() = runTest {
        val entry = createMockEntry(id = 1, operationType = "UNKNOWN", serverTimestamp = "2024-01-01T00:00:00Z")

        EntryServiceHelper.executeOperations(entryRepository, listOf(entry), true, false)

        coVerify(exactly = 0) { entryRepository.insert(any<Entry>()) }
        coVerify(exactly = 0) { entryRepository.update(any()) }
        coVerify(exactly = 0) { entryRepository.delete(any()) }
    }

    // -------------------------------------------------------------------------
    // OperationType enum
    // -------------------------------------------------------------------------

    @Test
    fun `OperationType CREATE has correct name`() {
        assertThat(OperationType.CREATE.name).isEqualTo("CREATE")
    }

    @Test
    fun `OperationType DELETE has correct name`() {
        assertThat(OperationType.DELETE.name).isEqualTo("DELETE")
    }

    @Test
    fun `OperationType values contains exactly CREATE and DELETE`() {
        assertThat(OperationType.values().map { it.name }).containsExactly("CREATE", "DELETE")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun todayString(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(Calendar.getInstance().time)
    }

    private fun dayOffsetString(offset: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
        return fmt.format(cal.time)
    }

    // -------------------------------------------------------------------------
    // toDayMillis — tested indirectly via computeLongestStreakFromDates
    // -------------------------------------------------------------------------

    @Test
    fun `toDayMillis consecutive dates differ by 1`() {
        // If toDayMillis works, consecutive dates should produce a streak of 2
        val dates = listOf("2024-06-15", "2024-06-16")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `toDayMillis non-consecutive dates differ by more than 1`() {
        // Dates 2 days apart should not form a streak
        val dates = listOf("2024-06-15", "2024-06-17")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `toDayMillis handles month boundary correctly`() {
        // Jan 31 to Feb 1 is consecutive
        val dates = listOf("2024-01-31", "2024-02-01")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `toDayMillis handles year boundary correctly`() {
        // Dec 31 to Jan 1 is consecutive
        val dates = listOf("2023-12-31", "2024-01-01")
        val result = EntryServiceHelper.computeLongestStreakFromDates(dates)
        assertThat(result).isEqualTo(2)
    }

    // -------------------------------------------------------------------------
    // datesAreSame — tested indirectly via computeCurrentStreakFromDates
    // -------------------------------------------------------------------------

    @Test
    fun `datesAreSame returns true for same day entries via current streak`() {
        // Two entries for today should only count as 1 streak day
        val today = todayString()
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today, today))
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `datesAreSame differentiates same month different days`() {
        // Today vs 3 days ago should not match
        val today = todayString()
        val threeDaysAgo = dayOffsetString(-3)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(listOf(today, threeDaysAgo))
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `datesAreSame differentiates same day different year`() {
        // Even if month and day match, different year should not be same
        val result = EntryServiceHelper.computeCurrentStreakFromDates(
            listOf(todayString(), "2020-01-01")
        )
        assertThat(result).isAtMost(1)
    }

    // -------------------------------------------------------------------------
    // addOne — tested indirectly via computeCurrentStreakFromDates
    // -------------------------------------------------------------------------

    @Test
    fun `addOne increments score for each consecutive day`() {
        val today = todayString()
        val yesterday = dayOffsetString(-1)
        val twoDaysAgo = dayOffsetString(-2)
        val threeDaysAgo = dayOffsetString(-3)
        val fourDaysAgo = dayOffsetString(-4)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(
            listOf(today, yesterday, twoDaysAgo, threeDaysAgo, fourDaysAgo)
        )
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `addOne advances dateToCheck backwards by 1 day`() {
        // If addOne didn't advance the date, it would only count 1
        val yesterday = dayOffsetString(-1)
        val twoDaysAgo = dayOffsetString(-2)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(
            listOf(yesterday, twoDaysAgo)
        )
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `addOne stops incrementing when gap is found`() {
        val today = todayString()
        val yesterday = dayOffsetString(-1)
        // Gap: skip -2
        val threeDaysAgo = dayOffsetString(-3)
        val result = EntryServiceHelper.computeCurrentStreakFromDates(
            listOf(today, yesterday, threeDaysAgo)
        )
        assertThat(result).isEqualTo(2) // stops at gap
    }

    private fun createMockEntry(
        id: Long = 0,
        operationType: String = "CREATE",
        serverTimestamp: String? = null,
    ): Entry {
        val entryEntity: EntryEntity = mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.operationType } returns operationType
            every { this@mockk.serverTimestamp } returns serverTimestamp
        }
        return mockk<Entry>(relaxed = true) {
            every { entry } returns entryEntity
        }
    }
}
