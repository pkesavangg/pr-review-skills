import Foundation

/// Response envelope for the unified `GET /v3/entries/` read endpoint (MOB-385).
///
/// The endpoint serves two modes from one shape:
/// * **Cursor pagination** — `{ entries, nextCursor, hasMore }`. `nextCursor` is the
///   `entryTimestamp` of the last row; the client sends it back as `?cursor` for the next
///   page. `hasMore` is true when more rows exist beyond the returned page.
/// * **Sync (delta)** — `{ entries, timestamp }`. `timestamp` is the server clock captured
///   before the query; the client persists it and sends it as `?start` on the next sync.
///
/// `entries` are flat (server strips nulls), sharing the `UnifiedEntryResult` shape with the
/// `POST /v3/entries/` write response.
struct BathScaleOperationListResponse: Codable {
    let entries: [UnifiedEntryResult]
    let nextCursor: String?
    let hasMore: Bool?
    let timestamp: String?

    init(
        entries: [UnifiedEntryResult],
        nextCursor: String? = nil,
        hasMore: Bool? = nil,
        timestamp: String? = nil
    ) {
        self.entries = entries
        self.nextCursor = nextCursor
        self.hasMore = hasMore
        self.timestamp = timestamp
    }
}

extension BathScaleOperationListResponse {
    /// Legacy DTO projection consumed by the sync/merge engine, which predates the flat
    /// unified shape. Covers weight, BP, and baby (baby read wired in MOB-386).
    var operations: [BathScaleOperationDTO] {
        entries.map { $0.toOperationDTO() }
    }

    /// Convenience initializer for callers/tests that build the response from DTOs.
    init(operations: [BathScaleOperationDTO], timestamp: String) {
        self.init(
            entries: operations.map(UnifiedEntryResult.init(from:)),
            timestamp: timestamp
        )
    }
}
