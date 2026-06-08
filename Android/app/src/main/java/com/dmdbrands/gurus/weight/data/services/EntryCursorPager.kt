package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.api.entry.toDomainEntries
import com.dmdbrands.gurus.weight.domain.repository.IEntryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backfills all historical entries into Room by chaining cursor pages from
 * `GET /v3/entries/?cursor=&limit=` (MOB-380).
 *
 * The caller holds the Room-backed flow; UI remains reactive without any change.
 * A non-advancing cursor (tie-break guard) stops the loop to prevent infinite runs.
 */
@Singleton
class EntryCursorPager @Inject constructor(
    private val entryRepository: IEntryRepository,
) {

    companion object {
        private const val TAG = "EntryCursorPager"
        private const val PAGE_SIZE = 50
        private const val MAX_PAGES = 500
    }

    /**
     * Pages all history into Room for [accountId], optionally filtered by [category].
     * Each page is upserted immediately so the UI updates progressively.
     *
     * @param onPage optional callback after each page (e.g. for progress updates).
     */
    suspend fun backfill(
        accountId: String,
        category: String? = null,
        onPage: (suspend (pageIndex: Int, count: Int) -> Unit)? = null,
    ) {
        AppLog.d(TAG, "backfill start — accountId=$accountId category=$category")
        var cursor: String? = null
        var pageIndex = 0

        while (pageIndex < MAX_PAGES) {
            val response = try {
                entryRepository.getEntriesPage(cursor = cursor, limit = PAGE_SIZE, category = category)
            } catch (e: Exception) {
                AppLog.e(TAG, "backfill page $pageIndex failed — stopping", e)
                break
            }

            val entries = response.entries.toDomainEntries(accountId)
            if (entries.isNotEmpty()) {
                com.dmdbrands.gurus.weight.data.services.EntryServiceHelper.executeOperations(entryRepository, entries)
            }

            onPage?.invoke(pageIndex, entries.size)
            AppLog.d(TAG, "backfill page $pageIndex: ${entries.size} entries, hasMore=${response.hasMore}")

            if (!response.hasMore || response.nextCursor == null) break

            // Tie-break guard: a server that echoes back the cursor it was just given
            // (nextCursor == the cursor used for this request) is not advancing — stop
            // to avoid looping until MAX_PAGES. On the first page cursor is null, so a
            // non-null nextCursor always advances past it.
            if (response.nextCursor == cursor) {
                AppLog.w(TAG, "backfill: cursor did not advance ($cursor) — stopping to avoid infinite loop")
                break
            }

            cursor = response.nextCursor
            pageIndex++
        }

        AppLog.d(TAG, "backfill complete — $pageIndex pages processed")
    }
}
