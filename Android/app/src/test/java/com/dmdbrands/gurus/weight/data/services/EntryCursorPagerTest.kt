package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.entry.EntriesCursorResponse
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryCursorPagerTest {

    private val entryRepository: IEntryRepository = mockk(relaxed = true)
    private lateinit var pager: EntryCursorPager

    private companion object {
        const val ACCOUNT_ID = "acc-1"
    }

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.w(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        pager = EntryCursorPager(entryRepository)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `backfill walks pages until hasMore is false`() = runTest {
        coEvery { entryRepository.getEntriesPage(cursor = null, limit = any(), category = any()) } returns
            EntriesCursorResponse(entries = emptyList(), nextCursor = "c1", hasMore = true)
        coEvery { entryRepository.getEntriesPage(cursor = "c1", limit = any(), category = any()) } returns
            EntriesCursorResponse(entries = emptyList(), nextCursor = null, hasMore = false)

        pager.backfill(ACCOUNT_ID)

        coVerify(exactly = 1) { entryRepository.getEntriesPage(cursor = null, limit = any(), category = any()) }
        coVerify(exactly = 1) { entryRepository.getEntriesPage(cursor = "c1", limit = any(), category = any()) }
    }

    @Test
    fun `backfill stops when the cursor does not advance`() = runTest {
        // Server keeps echoing the same nextCursor with hasMore=true. The tie-break guard
        // must catch this on the page whose request cursor equals the returned nextCursor,
        // rather than looping all the way to MAX_PAGES (500).
        coEvery { entryRepository.getEntriesPage(cursor = any(), limit = any(), category = any()) } returns
            EntriesCursorResponse(entries = emptyList(), nextCursor = "stuck", hasMore = true)

        pager.backfill(ACCOUNT_ID)

        // Page 0 (cursor=null) advances to "stuck"; page 1 (cursor="stuck") sees
        // nextCursor=="stuck"==cursor and stops. Two fetches, no runaway loop.
        coVerify(exactly = 2) { entryRepository.getEntriesPage(cursor = any(), limit = any(), category = any()) }
    }

    @Test
    fun `backfill stops on a page fetch failure without throwing`() = runTest {
        coEvery { entryRepository.getEntriesPage(cursor = any(), limit = any(), category = any()) } throws
            RuntimeException("network down")

        // Should not propagate — the loop logs and breaks.
        pager.backfill(ACCOUNT_ID)

        coVerify(exactly = 1) { entryRepository.getEntriesPage(cursor = any(), limit = any(), category = any()) }
    }
}
