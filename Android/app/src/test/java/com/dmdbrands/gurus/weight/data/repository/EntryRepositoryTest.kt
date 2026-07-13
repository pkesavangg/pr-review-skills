package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.EntryApi
import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.data.storage.db.dao.EntryDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.ActiveEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesCursorResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesSyncResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryRequest
import com.dmdbrands.gurus.weight.domain.model.api.entry.UnifiedEntryResponse
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BabyEntryEntity
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BabyEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedActiveEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.PopulatedEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class EntryRepositoryTest {

    @MockK(relaxUnitFun = true)
    private lateinit var entryDao: EntryDao

    @MockK(relaxUnitFun = true)
    private lateinit var entryApi: EntryApi

    private lateinit var repository: EntryRepository

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        repository = EntryRepository(entryDao, entryApi)
    }

    // ── insert(Entry) ──────────────────────────────────────────────────────────

    @Test
    fun `insert single entry with valid timestamp calls dao and returns id`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNote(any(), any()) } returns null
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
        coEvery { entryDao.getStoredScaleNotes(any()) } returns emptyList()

        repository.insert(entries)

        coVerify { entryDao.insert(match<List<Entry>> { it.size == 2 }) }
    }

    @Test
    fun `insert list filters invalid entries and passes only valid ones to dao`() = runTest {
        val valid = buildScaleEntry(VALID_TIMESTAMP)
        val invalid = buildScaleEntry(INVALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNotes(any()) } returns emptyList()

        repository.insert(listOf(valid, invalid))

        coVerify { entryDao.insert(match<List<Entry>> { it.size == 1 }) }
    }

    @Test
    fun `insert list with all invalid entries passes empty list to dao`() = runTest {
        val entries = listOf(buildScaleEntry(INVALID_TIMESTAMP), buildBpmEntry(INVALID_TIMESTAMP))
        coEvery { entryDao.getStoredScaleNotes(any()) } returns emptyList()

        repository.insert(entries)

        coVerify { entryDao.insert(match<List<Entry>> { it.isEmpty() }) }
    }

    // ── update(Entry) ──────────────────────────────────────────────────────────

    @Test
    fun `update delegates to dao and returns row count`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNote(any(), any()) } returns null
        coEvery { entryDao.update(any<Entry>()) } returns 1L

        val result = repository.update(entry)

        assertThat(result).isEqualTo(1L)
        coVerify { entryDao.update(any<Entry>()) }
    }

    @Test
    fun `update preserves existing local scale note when incoming note is blank`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNote(any(), any()) } returns "local note"
        coEvery { entryDao.update(any<Entry>()) } returns 1L

        repository.update(entry)

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

    @Test
    fun `sendOperationToAPI throws IllegalArgumentException when operation is null`() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.sendOperationToAPI(null) }
    }

    @Test
    fun `sendOperationToAPI calls entryApi with given operation`() = runTest {
        val operation = buildScaleApiEntry()
        coEvery { entryApi.sendOperation(operation) } returns operation

        repository.sendOperationToAPI(operation)

        coVerify { entryApi.sendOperation(operation) }
    }

    @Test
    fun `sendOperationToAPI rethrows exception from api`() = runTest {
        val operation = buildScaleApiEntry()
        coEvery { entryApi.sendOperation(any()) } throws RuntimeException("network error")

        assertFailsWith<RuntimeException> { repository.sendOperationToAPI(operation) }
    }

    // ── sendBatchToAPI ─────────────────────────────────────────────────────────

    @Test
    fun `sendBatchToAPI posts entries and returns response`() = runTest {
        val requests = listOf(
            UnifiedEntryRequest(category = "weight", operationType = "create", entryTimestamp = "2024-01-15T00:00:00.000Z", weight = 750),
        )
        val response = UnifiedEntryResponse(entries = emptyList(), timestamp = "2024-01-15T00:00:01.000Z")
        coEvery { entryApi.postEntries(requests) } returns response

        val result = repository.sendBatchToAPI(requests)

        assertThat(result).isEqualTo(response)
        coVerify { entryApi.postEntries(requests) }
    }

    @Test
    fun `sendBatchToAPI rethrows exception from api (atomic failure)`() = runTest {
        coEvery { entryApi.postEntries(any()) } throws RuntimeException("batch failed")

        assertFailsWith<RuntimeException> {
            repository.sendBatchToAPI(
                listOf(UnifiedEntryRequest(category = "bp", operationType = "create", entryTimestamp = "t", systolic = 120, diastolic = 80, pulse = 72)),
            )
        }
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
        val flow = repository.deleteAllEntriesForAccount(ACCOUNT_ID)
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

    @Test
    fun `getEntryById returns mapped entry when dao returns populated entry`() = runTest {
        coEvery { entryDao.getEntryById(1L) } returns buildPopulatedEntry()

        val result = repository.getEntryById(1L)

        assertThat(result).isNotNull()
    }

    // ── getEntriesByAccount ────────────────────────────────────────────────────

    @Test
    fun `getEntriesByAccount returns empty list when dao returns empty`() = runTest {
        coEvery { entryDao.getEntriesByAccount(any()) } returns emptyList()

        val result = repository.getEntriesByAccount(ACCOUNT_ID)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getEntriesByAccount returns mapped entries from dao`() = runTest {
        coEvery { entryDao.getEntriesByAccount(ACCOUNT_ID) } returns listOf(buildPopulatedActiveEntry())

        val result = repository.getEntriesByAccount(ACCOUNT_ID)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `getEntriesByAccount with convertToDisplay maps entries`() = runTest {
        coEvery { entryDao.getEntriesByAccount(ACCOUNT_ID) } returns listOf(buildPopulatedActiveEntry())

        val result = repository.getEntriesByAccount(ACCOUNT_ID, convertToDisplay = true)

        assertThat(result).hasSize(1)
    }

    // ── getUnSynced ────────────────────────────────────────────────────────────

    @Test
    fun `getUnSynced returns empty list when dao returns empty`() = runTest {
        coEvery { entryDao.getUnSynced(any()) } returns emptyList()

        val result = repository.getUnSynced(ACCOUNT_ID)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getUnSynced returns mapped entries from dao`() = runTest {
        coEvery { entryDao.getUnSynced(ACCOUNT_ID) } returns listOf(buildPopulatedEntry())

        val result = repository.getUnSynced(ACCOUNT_ID)

        assertThat(result).hasSize(1)
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
        coEvery { entryDao.clearUnSynced(ACCOUNT_ID) } returns 7

        val result = repository.clearUnSynced(ACCOUNT_ID)

        assertThat(result).isEqualTo(7)
        coVerify { entryDao.clearUnSynced(ACCOUNT_ID) }
    }

    // ── getOperationCount ──────────────────────────────────────────────────────

    @Test
    fun `getOperationCount delegates to dao`() = runTest {
        coEvery { entryDao.getOperationCount(ACCOUNT_ID) } returns 15

        val result = repository.getOperationCount(ACCOUNT_ID)

        assertThat(result).isEqualTo(15)
    }

    // ── getFailedOperations ────────────────────────────────────────────────────

    @Test
    fun `getFailedOperations returns empty list when dao returns empty`() = runTest {
        coEvery { entryDao.getFailedOperations(ACCOUNT_ID, 3) } returns emptyList()

        val result = repository.getFailedOperations(ACCOUNT_ID, 3)

        assertThat(result).isEmpty()
        coVerify { entryDao.getFailedOperations(ACCOUNT_ID, 3) }
    }

    @Test
    fun `getFailedOperations returns mapped entries from dao`() = runTest {
        coEvery { entryDao.getFailedOperations(ACCOUNT_ID, 3) } returns listOf(buildPopulatedEntry())

        val result = repository.getFailedOperations(ACCOUNT_ID, 3)

        assertThat(result).hasSize(1)
    }

    // ── updateNote ─────────────────────────────────────────────────────────────

    @Test
    fun `updateNote on ScaleEntry calls updateScaleNote`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)

        repository.updateNote(entry, "weighed in")

        coVerify { entryDao.updateScaleNote(entry.entry.id, "weighed in") }
        coVerify(exactly = 0) { entryDao.updateBpmNote(any(), any()) }
    }

    @Test
    fun `updateNote on BpmEntry calls updateBpmNote`() = runTest {
        val entry = buildBpmEntry(VALID_TIMESTAMP)

        repository.updateNote(entry, "after walk")

        coVerify { entryDao.updateBpmNote(entry.entry.id, "after walk") }
        coVerify(exactly = 0) { entryDao.updateScaleNote(any(), any()) }
    }

    @Test
    fun `updateNote on BabyEntry calls updateBabyNote`() = runTest {
        val entry = buildBabyEntry(VALID_TIMESTAMP)

        repository.updateNote(entry, "fussy")

        coVerify { entryDao.updateBabyNote(entry.entry.id, "fussy") }
    }

    @Test
    fun `updateNote with null note clears the note column`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)

        repository.updateNote(entry, null)

        coVerify { entryDao.updateScaleNote(entry.entry.id, null) }
    }

    // ── insert(List) note preservation merge ───────────────────────────────────

    @Test
    fun `insert list preserves stored note for scale entry with blank note`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP) // note is null
        coEvery { entryDao.getStoredScaleNotes(ACCOUNT_ID) } returns
            listOf(com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceNoteRow(VALID_TIMESTAMP, "stored note"))

        repository.insert(listOf(entry))

        coVerify { entryDao.getStoredScaleNotes(ACCOUNT_ID) }
        coVerify { entryDao.insert(match<List<Entry>> { it.size == 1 }) }
    }

    @Test
    fun `insert list ignores blank stored note rows`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNotes(ACCOUNT_ID) } returns
            listOf(com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceNoteRow(VALID_TIMESTAMP, "  "))

        repository.insert(listOf(entry))

        coVerify { entryDao.insert(match<List<Entry>> { it.size == 1 }) }
    }

    // ── insert(single) note preservation ───────────────────────────────────────

    @Test
    fun `insert single scale entry restores stored note when incoming note is blank`() = runTest {
        val entry = buildScaleEntry(VALID_TIMESTAMP)
        coEvery { entryDao.getStoredScaleNote(ACCOUNT_ID, VALID_TIMESTAMP) } returns "kept note"
        coEvery { entryDao.insert(any<Entry>()) } returns 1L

        repository.insert(entry)

        coVerify { entryDao.getStoredScaleNote(ACCOUNT_ID, VALID_TIMESTAMP) }
        coVerify { entryDao.insert(any<Entry>()) }
    }

    // ── getEntriesSync ─────────────────────────────────────────────────────────

    @Test
    fun `getEntriesSync returns response from api`() = runTest {
        val response = EntriesSyncResponse(entries = emptyList(), timestamp = "2024-01-15T00:00:00.000Z")
        coEvery { entryApi.getEntriesSync("2024-01-15", "weight") } returns response

        val result = repository.getEntriesSync("2024-01-15", "weight")

        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `getEntriesSync rethrows on api failure`() = runTest {
        coEvery { entryApi.getEntriesSync(any(), any()) } throws RuntimeException("sync failed")

        assertFailsWith<RuntimeException> { repository.getEntriesSync("start", null) }
    }

    // ── getEntriesPage ─────────────────────────────────────────────────────────

    @Test
    fun `getEntriesPage returns response from api`() = runTest {
        val response = EntriesCursorResponse(entries = emptyList(), nextCursor = null, hasMore = false)
        coEvery { entryApi.getEntriesPage("cursor1", 20, "weight") } returns response

        val result = repository.getEntriesPage("cursor1", 20, "weight")

        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `getEntriesPage rethrows on api failure`() = runTest {
        coEvery { entryApi.getEntriesPage(any(), any(), any()) } throws RuntimeException("page failed")

        assertFailsWith<RuntimeException> { repository.getEntriesPage(null, 20, null) }
    }

    // ── exportEntriesCsv ───────────────────────────────────────────────────────

    @Test
    fun `exportEntriesCsv returns body when response successful`() = runTest {
        val body = okhttp3.ResponseBody.create(null, "csv data")
        coEvery { entryApi.exportEntriesCsv(any(), any(), any(), any()) } returns retrofit2.Response.success(body)

        val result = repository.exportEntriesCsv("weight", download = true, utcOffset = 0)

        assertThat(result).isNotNull()
    }

    @Test
    fun `exportEntriesCsv throws HttpException when response unsuccessful`() = runTest {
        val errorBody = okhttp3.ResponseBody.create(null, "err")
        coEvery { entryApi.exportEntriesCsv(any(), any(), any(), any()) } returns
            retrofit2.Response.error(500, errorBody)

        // A non-2xx must surface as a failure so the email export path cannot report
        // "sent" on a rejected request; it previously collapsed to a null body.
        val ex = assertFailsWith<retrofit2.HttpException> {
            repository.exportEntriesCsv("weight", download = false, utcOffset = 5)
        }
        assertThat(ex.code()).isEqualTo(500)
    }

    @Test
    fun `exportEntriesCsv rethrows on api failure`() = runTest {
        coEvery { entryApi.exportEntriesCsv(any(), any(), any(), any()) } throws RuntimeException("csv failed")

        assertFailsWith<RuntimeException> { repository.exportEntriesCsv(null, download = true, utcOffset = 0) }
    }

    @Test
    fun `exportEntriesCsv forwards category and babyId to the api`() = runTest {
        val body = okhttp3.ResponseBody.create(null, "csv data")
        coEvery { entryApi.exportEntriesCsv(any(), any(), any(), any()) } returns retrofit2.Response.success(body)

        repository.exportEntriesCsv(category = "baby", babyId = "baby-1", download = false, utcOffset = 0)

        coVerify {
            entryApi.exportEntriesCsv(
                category = "baby",
                babyId = "baby-1",
                download = null,
                utcOffset = 0,
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    companion object {
        private const val VALID_TIMESTAMP = "2024-01-15T10:30:00.000Z"
        private const val INVALID_TIMESTAMP = "not-a-timestamp"
        private const val ACCOUNT_ID = "account1"
        private const val DEVICE_TYPE_SCALE = "scale"
        private const val OPERATION_TYPE_CREATE = "create"
    }

    private fun buildPopulatedEntry(): PopulatedEntry {
        val entryEntity = EntryEntity(
            id = 1L,
            accountId = ACCOUNT_ID,
            entryTimestamp = VALID_TIMESTAMP,
            operationType = OPERATION_TYPE_CREATE,
            deviceType = DEVICE_TYPE_SCALE,
            deviceId = "device1",
        )
        return PopulatedEntry(
            entry = entryEntity,
            bpmEntry = null,
            scaleEntry = BodyScaleEntryEntity(id = 1L, weight = 1650.0, bodyFat = null, muscleMass = null, water = null, bmi = null, source = "manual"),
            scaleEntryMetric = null,
            babyEntry = null,
        )
    }

    private fun buildPopulatedActiveEntry(): PopulatedActiveEntry {
        val entryEntity = ActiveEntryEntity(
            id = 1L,
            accountId = ACCOUNT_ID,
            entryTimestamp = VALID_TIMESTAMP,
            serverTimestamp = null,
            opTimestamp = null,
            operationType = OPERATION_TYPE_CREATE,
            deviceType = DEVICE_TYPE_SCALE,
            deviceId = "device1",
        )
        return PopulatedActiveEntry(
            entry = entryEntity,
            bpmEntry = null,
            scaleEntry = BodyScaleEntryEntity(id = 1L, weight = 1650.0, bodyFat = null, muscleMass = null, water = null, bmi = null, source = "manual"),
            scaleEntryMetric = null,
            babyEntry = null,
        )
    }

    private fun buildScaleEntry(timestamp: String): ScaleEntry {
        val entryEntity = EntryEntity(
            id = 1L,
            accountId = ACCOUNT_ID,
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
            accountId = ACCOUNT_ID,
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

    private fun buildBabyEntry(timestamp: String): BabyEntry {
        val entryEntity = EntryEntity(
            id = 3L,
            accountId = ACCOUNT_ID,
            entryTimestamp = timestamp,
            operationType = "create",
            deviceType = "baby",
            deviceId = "device3",
        )
        val babyEntryEntity = BabyEntryEntity(
            id = 3L,
            babyId = "baby1",
            babyWeightDecigrams = 3500,
            babyLengthMillimeters = 500,
            entryNote = null,
            entryType = "weight",
        )
        return BabyEntry(entryEntity, babyEntryEntity)
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
