package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EntryRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var entryDao: EntryDao

    @MockK(relaxUnitFun = true)
    private lateinit var entryApi: EntryApi

    private lateinit var repository: EntryRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        repository = EntryRepository(entryDao, entryApi)
    }

    // ── insert(Entry) ──────────────────────────────────────────────────────────

    @Test
    fun `insert single entry with valid timestamp calls dao and returns id`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.insert(any<Entry>()) } returns 42L

        val result = repository.insert(entry)

        assertThat(result).isEqualTo(42L)
        coVerify { entryDao.insert(any<Entry>()) }
    }

    @Test
    fun `insert single entry with invalid timestamp skips dao and returns -1`() = runTest {
        val entry = buildScaleEntry(INVALID_TIMESTAMP)

        val result = repository.insert(entry)

        assertThat(result).isEqualTo(-1L)
        coVerify(exactly = 0) { entryDao.insert(any<Entry>()) }
    }

    @Test
    fun `insert single entry with empty timestamp skips dao and returns -1`() = runTest {
        val entry = buildScaleEntry("")

        val result = repository.insert(entry)

        assertThat(result).isEqualTo(-1L)
        coVerify(exactly = 0) { entryDao.insert(any<Entry>()) }
    }

    // ── insert(List<Entry>) ────────────────────────────────────────────────────

    @Test
    fun `insert list with all valid entries passes all to dao`() = runTest {
        val entries = listOf(buildScaleEntry(VALID_TIMESTAMP), buildBpmEntry(VALID_TIMESTAMP))

        repository.insert(entries)

        coVerify { entryDao.insert(match<List<Entry>> { it.size == 2 }) }
    }

    @Test
    fun `insert list filters invalid entries and passes only valid ones to dao`() = runTest {
        val valid = buildScaleEntry(VALID_TIMESTAMP)
        val invalid = buildScaleEntry(INVALID_TIMESTAMP)

        repository.insert(listOf(valid, invalid))

        coVerify { entryDao.insert(match<List<Entry>> { it.size == 1 }) }
    }

    @Test
    fun `insert list with all invalid entries passes empty list to dao`() = runTest {
        val entries = listOf(buildScaleEntry(INVALID_TIMESTAMP), buildBpmEntry(INVALID_TIMESTAMP))

        repository.insert(entries)

        coVerify { entryDao.insert(match<List<Entry>> { it.isEmpty() }) }
    }

    // ── update(Entry) ──────────────────────────────────────────────────────────

    @Test
    fun `update delegates to dao and returns row count`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.update(any<Entry>()) } returns 1L

        val result = repository.update(entry)

        assertThat(result).isEqualTo(1L)
        coVerify { entryDao.update(any<Entry>()) }
    }

    // ── delete(Entry) ──────────────────────────────────────────────────────────

    @Test
    fun `delete delegates to dao`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)

        repository.delete(entry)

        coVerify { entryDao.delete(entry) }
    }

    // ── deleteById ─────────────────────────────────────────────────────────────

    @Test
    fun `deleteById delegates to dao and returns affected row count`() = runTest {
        coEvery { entryDao.deleteById(99L) } returns 1

        val result = repository.deleteById(99L)

        assertThat(result).isEqualTo(1)
        coVerify { entryDao.deleteById(99L) }
    }

    // ── sendOperationToAPI ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `sendOperationToAPI throws IllegalArgumentException when operation is null`() = runTest {
        repository.sendOperationToAPI(null)
    }

    @Test
    fun `sendOperationToAPI calls entryApi with given operation`() = runTest {
        val operation = buildScaleApiEntry()
        coEvery { entryApi.sendOperation(operation) } returns operation

        repository.sendOperationToAPI(operation)

        coVerify { entryApi.sendOperation(operation) }
    }

    @Test(expected = RuntimeException::class)
    fun `sendOperationToAPI rethrows exception from api`() = runTest {
        val operation = buildScaleApiEntry()
        coEvery { entryApi.sendOperation(any()) } throws RuntimeException("network error")

        repository.sendOperationToAPI(operation)
    }

    // ── getOperationsFromAPI ───────────────────────────────────────────────────

    @Test
    fun `getOperationsFromAPI with non-blank timestamp calls getOperations`() = runTest {
        val response = OperationsResponse(emptyList(), "2024-01-15T00:00:00.000Z")
        coEvery { entryApi.getOperations("2024-01-15") } returns response

        val result = repository.getOperationsFromAPI("2024-01-15")

        assertThat(result).isEqualTo(response)
        coVerify { entryApi.getOperations("2024-01-15") }
        coVerify(exactly = 0) { entryApi.getAllOperations() }
    }

    @Test
    fun `getOperationsFromAPI with blank timestamp calls getAllOperations`() = runTest {
        val response = OperationsResponse(emptyList(), "2024-01-15T00:00:00.000Z")
        coEvery { entryApi.getAllOperations() } returns response

        val result = repository.getOperationsFromAPI("")

        assertThat(result).isEqualTo(response)
        coVerify { entryApi.getAllOperations() }
        coVerify(exactly = 0) { entryApi.getOperations(any()) }
    }

    @Test
    fun `getOperationsFromAPI with whitespace-only timestamp calls getAllOperations`() = runTest {
        val response = OperationsResponse(emptyList(), "2024-01-15T00:00:00.000Z")
        coEvery { entryApi.getAllOperations() } returns response

        val result = repository.getOperationsFromAPI("   ")

        assertThat(result).isEqualTo(response)
        coVerify { entryApi.getAllOperations() }
    }

    @Test
    fun `getOperationsFromAPI returns null when api throws exception`() = runTest {
        coEvery { entryApi.getAllOperations() } throws RuntimeException("error")

        val result = repository.getOperationsFromAPI("")

        assertThat(result).isNull()
    }

    // ── deleteAllEntriesForAccount ─────────────────────────────────────────────

    @Test
    fun `deleteAllEntriesForAccount emits no values`() = runTest {
        val flow = repository.deleteAllEntriesForAccount("account1")
        val values = flow.toList()
        assertThat(values).isEmpty()
    }

    // ── getEntryById ───────────────────────────────────────────────────────────

    @Test
    fun `getEntryById returns null when dao returns null`() = runTest {
        coEvery { entryDao.getEntryById(any()) } returns null

        val result = repository.getEntryById(1L)

        assertThat(result).isNull()
    }

    // ── getLatestEntry ─────────────────────────────────────────────────────────

    @Test
    fun `getLatestEntry maps null dao emission to null`() = runTest {
        every { entryDao.getLatestEntry("account1") } returns flowOf(null)

        val flow = repository.getLatestEntry("account1")
        val result = flow.toList()

        assertThat(result).containsExactly(null)
    }

    // ── getEntriesByAccount ────────────────────────────────────────────────────

    @Test
    fun `getEntriesByAccount returns empty list when dao returns empty`() = runTest {
        coEvery { entryDao.getEntriesByAccount(any()) } returns emptyList()

        val result = repository.getEntriesByAccount("account1")

        assertThat(result).isEmpty()
    }

    // ── getUnSynced ────────────────────────────────────────────────────────────

    @Test
    fun `getUnSynced returns empty list when dao returns empty`() = runTest {
        coEvery { entryDao.getUnSynced(any()) } returns emptyList()

        val result = repository.getUnSynced("account1")

        assertThat(result).isEmpty()
    }

    // ── incrementAttempts ──────────────────────────────────────────────────────

    @Test
    fun `incrementAttempts delegates to dao and returns updated count`() = runTest {
        coEvery { entryDao.incrementAttempts(5L) } returns 3

        val result = repository.incrementAttempts(5L)

        assertThat(result).isEqualTo(3)
        coVerify { entryDao.incrementAttempts(5L) }
    }

    // ── clearUnSynced ──────────────────────────────────────────────────────────

    @Test
    fun `clearUnSynced delegates to dao and returns cleared count`() = runTest {
        coEvery { entryDao.clearUnSynced("account1") } returns 7

        val result = repository.clearUnSynced("account1")

        assertThat(result).isEqualTo(7)
        coVerify { entryDao.clearUnSynced("account1") }
    }

    // ── getOperationCount ──────────────────────────────────────────────────────

    @Test
    fun `getOperationCount delegates to dao`() = runTest {
        coEvery { entryDao.getOperationCount("account1") } returns 15

        val result = repository.getOperationCount("account1")

        assertThat(result).isEqualTo(15)
    }

    // ── getTotalCount ──────────────────────────────────────────────────────────

    @Test
    fun `getTotalCount delegates to dao`() = runTest {
        coEvery { entryDao.getTotalCount("account1") } returns 100

        val result = repository.getTotalCount("account1")

        assertThat(result).isEqualTo(100)
    }

    // ── getLongestStreakCount ──────────────────────────────────────────────────

    @Test
    fun `getLongestStreakCount delegates to dao`() = runTest {
        coEvery { entryDao.getLongestStreakCount("account1") } returns 30

        val result = repository.getLongestStreakCount("account1")

        assertThat(result).isEqualTo(30)
    }

    // ── getStreakData ──────────────────────────────────────────────────────────

    @Test
    fun `getStreakData returns timestamps from dao`() = runTest {
        val timestamps = listOf("2024-01-15T10:00:00.000Z", "2024-01-14T09:00:00.000Z")
        coEvery { entryDao.getStreakData("account1") } returns timestamps

        val result = repository.getStreakData("account1")

        assertThat(result).isEqualTo(timestamps)
    }

    // ── getOldestEntry ─────────────────────────────────────────────────────────

    @Test
    fun `getOldestEntry returns null when dao returns null`() = runTest {
        coEvery { entryDao.getOldestEntry(any()) } returns null

        val result = repository.getOldestEntry("account1")

        assertThat(result).isNull()
    }

    // ── getEntriesInRange ──────────────────────────────────────────────────────

    @Test
    fun `getEntriesInRange returns empty list when dao flow emits empty list`() = runTest {
        every {
            entryDao.getEntriesInRange("account1", "2024-01-01", "2024-01-31")
        } returns flowOf(emptyList())

        val result = repository.getEntriesInRange("account1", "2024-01-01", "2024-01-31")

        assertThat(result).isEmpty()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    companion object {
        private const val VALID_TIMESTAMP = "2024-01-15T10:30:00.000Z"
        private const val INVALID_TIMESTAMP = "not-a-timestamp"
    }

    private fun buildScaleEntry(timestamp: String): ScaleEntry {
        val entryEntity = EntryEntity(
            id = 1L,
            accountId = "account1",
            entryTimestamp = timestamp,
            operationType = "create",
            deviceType = "scale",
            deviceId = "device1",
        )
        val bodyScaleEntry = BodyScaleEntryEntity(
            id = 1L,
            weight = 1650.0,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = null,
            source = "manual",
        )
        return ScaleEntry(entryEntity, ScaleEntryWithMetrics(bodyScaleEntry, null))
    }

    private fun buildBpmEntry(timestamp: String): BpmEntry {
        val entryEntity = EntryEntity(
            id = 2L,
            accountId = "account1",
            entryTimestamp = timestamp,
            operationType = "create",
            deviceType = "bpm",
            deviceId = "device2",
        )
        val bpmEntryEntity = BpmEntryEntity(
            id = 2L,
            systolic = 120,
            diastolic = 80,
            pulse = 70,
            meanArterial = "93.3",
            note = null,
        )
        return BpmEntry(entryEntity, bpmEntryEntity)
    }

    private fun buildScaleApiEntry(): ScaleApiEntry = ScaleApiEntry(
        operationType = "create",
        entryTimestamp = VALID_TIMESTAMP,
        serverTimestamp = null,
        weight = 1650,
        bodyFat = null,
        muscleMass = null,
        boneMass = null,
        water = null,
        bmi = null,
        source = "manual",
    )
}
