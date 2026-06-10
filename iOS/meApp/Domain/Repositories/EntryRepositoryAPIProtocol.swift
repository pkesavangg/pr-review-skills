import Foundation

/// Protocol for interacting with the remote Entry API.
///
/// As of MOB-385 the read, sync, and CSV-export paths all target the unified
/// `/v3/entries/` endpoints. The legacy `/operation/r4` endpoints remain on the server
/// for old apps but are no longer called from here.
protocol EntryRepositoryAPIProtocol {
    /// Submits a batch of entries to the unified `POST /v3/entries/` endpoint.
    ///
    /// The request body is a raw array supporting mixed categories (weight + BP)
    /// processed as a single atomic batch — if any entry fails validation, the
    /// whole request fails. A single entry is simply an array of one.
    /// - Parameter entries: The entries to create or delete.
    /// - Returns: The unified response containing the persisted entries and server timestamp.
    @discardableResult
    func submitEntries(_ entries: [UnifiedEntryRequest]) async throws -> UnifiedEntryResponse

    /// Reads entries from the unified `GET /v3/entries/` endpoint.
    ///
    /// Supports both server modes from one call:
    /// * **Sync mode** — pass `start`; the server returns every entry with
    ///   `serverTimestamp > start` (no limit) plus a fresh `timestamp` for the next sync.
    /// * **Cursor pagination** — pass `cursor` (and optionally `limit`); the server returns
    ///   up to `limit` entries with `entryTimestamp < cursor` plus `nextCursor`/`hasMore`.
    ///
    /// If both `start` and `cursor` are supplied the server prioritizes `cursor`.
    /// - Parameters:
    ///   - start: ISO timestamp for sync mode (entries since `serverTimestamp > start`).
    ///   - cursor: ISO timestamp for pagination (entries where `entryTimestamp < cursor`).
    ///   - limit: Page size for cursor mode (server default 20, max 100).
    ///   - category: Optional product filter (`weight`/`bp`/`baby`); omit for all products.
    ///   - babyId: Optional baby filter (only meaningful with `category == "baby"`).
    /// - Returns: The unified list response (flat entries + cursor/sync metadata).
    func fetchEntries(
        start: String?,
        cursor: String?,
        limit: Int?,
        category: String?,
        babyId: String?
    ) async throws -> BathScaleOperationListResponse

    /// Exports entries as CSV via the unified `GET /v3/entries/csv` endpoint.
    /// - Parameter request: The export parameters (category, download vs email, utcOffset, …).
    /// - Returns: The export response. In email mode this carries `sent: true`.
    @discardableResult
    func exportEntriesCSV(_ request: EntriesCSVRequest) async throws -> ExportResponse
}

extension EntryRepositoryAPIProtocol {
    /// Convenience overload for callers that don't need a baby filter (weight/BP/sync paths).
    func fetchEntries(
        start: String?,
        cursor: String?,
        limit: Int?,
        category: String?
    ) async throws -> BathScaleOperationListResponse {
        try await fetchEntries(start: start, cursor: cursor, limit: limit, category: category, babyId: nil)
    }
}
