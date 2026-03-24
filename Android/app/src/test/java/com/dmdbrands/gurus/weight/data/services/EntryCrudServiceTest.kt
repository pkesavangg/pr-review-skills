package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.Entry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAnalyticsService
import com.dmdbrands.gurus.weight.domain.services.IEntrySyncService
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryCrudServiceTest {

    // --- Mocks ---
    private val syncService: IEntrySyncService = mockk(relaxed = true)
    private val analyticsService: IAnalyticsService = mockk(relaxed = true)

    private lateinit var service: EntryCrudService

    // --- Test fixtures ---
    private val testAccountId = "acc-123"
    private val fakeEntry: Entry = mockk(relaxed = true)
    private val fakeEntry2: Entry = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit

        // Default: fakeEntry returns itself on updateEntry
        every { fakeEntry.updateEntry(any()) } returns fakeEntry
        every { fakeEntry2.updateEntry(any()) } returns fakeEntry2

        service = EntryCrudService(syncService, analyticsService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // addEntry (single) — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single calls syncService syncOperations`() = runTest {
        service.addEntry(fakeEntry, testAccountId)

        coVerify { syncService.syncOperations(testAccountId, any()) }
    }

    @Test
    fun `addEntry single logs analytics event`() = runTest {
        service.addEntry(fakeEntry, testAccountId)

        verify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // addEntry (single) — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single returns early when accountId is null`() = runTest {
        service.addEntry(fakeEntry, null)

        coVerify(exactly = 0) { syncService.syncOperations(any(), any()) }
        verify(exactly = 0) { analyticsService.logEvent(any()) }
    }

    // -------------------------------------------------------------------------
    // addEntry (single) — ScaleEntry converts weight
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry single with ScaleEntry calls syncOperations`() = runTest {
        val scaleEntry: ScaleEntry = mockk(relaxed = true)
        every { scaleEntry.updateEntry(any()) } returns scaleEntry

        service.addEntry(scaleEntry, testAccountId)

        coVerify { syncService.syncOperations(testAccountId, any()) }
        verify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // addEntry (list) — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list calls syncService syncOperations`() = runTest {
        val entries = listOf(fakeEntry, fakeEntry2)

        service.addEntry(entries, testAccountId)

        coVerify { syncService.syncOperations(testAccountId, any()) }
    }

    @Test
    fun `addEntry list logs analytics event`() = runTest {
        val entries = listOf(fakeEntry)

        service.addEntry(entries, testAccountId)

        verify { analyticsService.logEvent(IAnalyticsService.Events.WEIGHT_ENTRY_CREATED) }
    }

    // -------------------------------------------------------------------------
    // addEntry (list) — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list returns early when accountId is null`() = runTest {
        service.addEntry(listOf(fakeEntry), null)

        coVerify(exactly = 0) { syncService.syncOperations(any(), any()) }
        verify(exactly = 0) { analyticsService.logEvent(any()) }
    }

    // -------------------------------------------------------------------------
    // addEntry (list) — empty list
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry empty list still calls syncOperations`() = runTest {
        service.addEntry(emptyList(), testAccountId)

        coVerify { syncService.syncOperations(testAccountId, any()) }
    }

    // -------------------------------------------------------------------------
    // addEntry (list) — error handling
    // -------------------------------------------------------------------------

    @Test
    fun `addEntry list handles exception gracefully`() = runTest {
        coEvery { syncService.syncOperations(any(), any()) } throws RuntimeException("Sync error")

        service.addEntry(listOf(fakeEntry), testAccountId)

        verify { AppLog.e("EntryCrudService", match { it.contains("Error saving new entries") }, any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry calls syncOperations with delete ops`() = runTest {
        service.deleteEntry(fakeEntry, testAccountId)

        coVerify { syncService.syncOperations(testAccountId, emptyList(), any()) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry — null accountId
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry returns early when accountId is null`() = runTest {
        service.deleteEntry(fakeEntry, null)

        coVerify(exactly = 0) { syncService.syncOperations(any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // deleteEntry — error handling
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry handles exception gracefully`() = runTest {
        coEvery { syncService.syncOperations(any(), any(), any()) } throws RuntimeException("Delete error")

        service.deleteEntry(fakeEntry, testAccountId)

        verify { AppLog.e("EntryCrudService", match { it.contains("Error deleting entry") }, any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // interface conformance
    // -------------------------------------------------------------------------

    @Test
    fun `service implements IEntryCrudService`() {
        assertThat(service).isInstanceOf(com.dmdbrands.gurus.weight.domain.services.IEntryCrudService::class.java)
    }
}
