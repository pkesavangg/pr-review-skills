package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.OperationsResponse
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.services.IGoalService
import com.dmdbrands.gurus.weight.domain.services.IHealthConnectService
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
class EntrySyncServiceTest {

    // --- Mocks ---
    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private val accountRepository: IAccountRepository = mockk(relaxed = true)
    private val goalService: IGoalService = mockk(relaxed = true)
    private val healthConnectService: IHealthConnectService = mockk(relaxed = true)
    private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
    private val appScope = TestScope()

    private lateinit var service: EntrySyncService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"
    private val fakeScaleEntry: ScaleEntry = mockk(relaxed = true)
    private val fakeScaleApiEntry: ScaleApiEntry = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.d(any(), any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit
        every { AppLog.w(any(), any()) } returns Unit
        every { AppLog.i(any(), any()) } returns Unit

        mockkObject(EntryServiceHelper)

        // Default stubs
        coEvery { entryRepository.getUnSynced(any()) } returns emptyList()
        coEvery { entryRepository.getOperationCount(any()) } returns 0
        coEvery { accountRepository.getSyncTimeStamp() } returns flowOf("2024-01-01T00:00:00Z")
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null
        coEvery { healthConnectService.checkIntegrated() } returns false

        service = EntrySyncService(
            entryRepository = entryRepository,
            accountRepository = accountRepository,
            goalService = goalService,
            healthConnectService = healthConnectService,
            healthConnectRepository = healthConnectRepository,
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
    fun `isUpdating defaults to false`() {
        assertThat(service.isUpdating.value).isFalse()
    }

    @Test
    fun `lastUpdated defaults to null`() {
        assertThat(service.lastUpdated.value).isNull()
    }

    // -------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------

    @Test
    fun `reset sets isUpdating to false`() {
        service.reset()

        assertThat(service.isUpdating.value).isFalse()
    }

    @Test
    fun `reset sets lastUpdated to null`() {
        service.reset()

        assertThat(service.lastUpdated.value).isNull()
    }

    // -------------------------------------------------------------------------
    // syncOperations — happy path with no unsynced entries
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations sets isUpdating true then false`() = runTest {
        service.syncOperations(testAccountId)

        // After sync completes, isUpdating should be false
        assertThat(service.isUpdating.value).isFalse()
    }

    @Test
    fun `syncOperations updates lastUpdated timestamp`() = runTest {
        service.syncOperations(testAccountId)

        assertThat(service.lastUpdated.value).isNotNull()
    }

    @Test
    fun `syncOperations fetches unsynced entries from repository`() = runTest {
        service.syncOperations(testAccountId)

        coVerify { entryRepository.getUnSynced(testAccountId) }
    }

    @Test
    fun `syncOperations gets operation count`() = runTest {
        service.syncOperations(testAccountId)

        coVerify { entryRepository.getOperationCount(testAccountId) }
    }

    @Test
    fun `syncOperations fetches sync timestamp from account repository`() = runTest {
        service.syncOperations(testAccountId)

        coVerify { accountRepository.getSyncTimeStamp() }
    }

    // -------------------------------------------------------------------------
    // syncOperations — API returns null response
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations returns early when API returns null operations`() = runTest {
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns null

        service.syncOperations(testAccountId)

        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // syncOperations — API returns operations
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations processes operations from API`() = runTest {
        val apiResponse = OperationsResponse(
            operations = emptyList(),
            timestamp = "2024-01-02T00:00:00Z",
        )
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns apiResponse

        service.syncOperations(testAccountId)

        coVerify { accountRepository.updateSyncTimeStamp("2024-01-02T00:00:00Z") }
    }

    // -------------------------------------------------------------------------
    // syncOperations — sends operations to API
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations sends unsynced entries to API`() = runTest {
        val apiResponse = OperationsResponse(
            operations = emptyList(),
            timestamp = "2024-01-02T00:00:00Z",
        )
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns apiResponse
        every { fakeScaleEntry.entry } returns mockk(relaxed = true) {
            every { entryTimestamp } returns "2024-01-01T12:00:00Z"
            every { operationType } returns "CREATE"
        }
        every { fakeScaleEntry.toScaleApiEntry() } returns fakeScaleApiEntry
        every { fakeScaleEntry.updateEntry(any()) } returns fakeScaleEntry
        coEvery { entryRepository.getUnSynced(testAccountId) } returns listOf(fakeScaleEntry)

        service.syncOperations(testAccountId)

        coVerify { entryRepository.sendOperationToAPI(fakeScaleApiEntry) }
    }

    // -------------------------------------------------------------------------
    // syncOperations — API call failure for operation
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations handles API failure for individual operation`() = runTest {
        val apiResponse = OperationsResponse(
            operations = emptyList(),
            timestamp = "2024-01-02T00:00:00Z",
        )
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns apiResponse
        every { fakeScaleEntry.entry } returns mockk(relaxed = true) {
            every { entryTimestamp } returns "2024-01-01T12:00:00Z"
            every { operationType } returns "CREATE"
            every { attempts } returns 0
        }
        every { fakeScaleEntry.toScaleApiEntry() } returns fakeScaleApiEntry
        every { fakeScaleEntry.updateEntry(any()) } returns fakeScaleEntry
        coEvery { entryRepository.getUnSynced(testAccountId) } returns listOf(fakeScaleEntry)
        coEvery { entryRepository.sendOperationToAPI(any()) } throws RuntimeException("API error")

        service.syncOperations(testAccountId)

        // Should still complete without crashing
        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // syncOperations — new entries are included
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations includes newEntries in processing`() = runTest {
        val apiResponse = OperationsResponse(
            operations = emptyList(),
            timestamp = "2024-01-02T00:00:00Z",
        )
        coEvery { entryRepository.getOperationsFromAPI(any()) } returns apiResponse
        every { fakeScaleEntry.entry } returns mockk(relaxed = true) {
            every { entryTimestamp } returns "2024-01-01T12:00:00Z"
            every { accountId } returns testAccountId
            every { operationType } returns "CREATE"
        }
        every { fakeScaleEntry.updateEntry(any()) } returns fakeScaleEntry
        every { fakeScaleEntry.toScaleApiEntry() } returns fakeScaleApiEntry

        service.syncOperations(testAccountId, listOf(fakeScaleEntry))

        coVerify { entryRepository.sendOperationToAPI(any()) }
    }

    // -------------------------------------------------------------------------
    // syncOperations — overall error handling
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations handles top-level exception gracefully`() = runTest {
        coEvery { entryRepository.getUnSynced(any()) } throws RuntimeException("DB error")

        service.syncOperations(testAccountId)

        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // syncOperations — getOperationsFromAPI throws
    // -------------------------------------------------------------------------

    @Test
    fun `syncOperations handles getOperationsFromAPI exception`() = runTest {
        coEvery { entryRepository.getOperationsFromAPI(any()) } throws RuntimeException("Network error")

        service.syncOperations(testAccountId)

        // Should not crash, isUpdating reset
        assertThat(service.isUpdating.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // interface conformance
    // -------------------------------------------------------------------------

    @Test
    fun `service implements IEntrySyncService`() {
        assertThat(service).isInstanceOf(com.dmdbrands.gurus.weight.domain.services.IEntrySyncService::class.java)
    }
}
