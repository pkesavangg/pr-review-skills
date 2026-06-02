import Foundation

@MainActor
final class EntryRepositoryAPI: EntryRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    @discardableResult
    func submitEntries(_ entries: [UnifiedEntryRequest]) async throws -> UnifiedEntryResponse {
        // POST /v3/entries/ — raw array body, mixed categories, atomic batch.
        let response: UnifiedEntryResponse = try await httpClient.send(
            .submitEntries,
            method: .post,
            body: entries,
            needsAuth: true
        )
        return response
    }

    func fetchEntries(
        start: String?,
        cursor: String?,
        limit: Int?,
        category: String?
    ) async throws -> BathScaleOperationListResponse {
        // GET /v3/entries/ — sync mode (?start=) or cursor pagination (?cursor=&limit=).
        let response: BathScaleOperationListResponse = try await httpClient.get(
            .entries(start: start, cursor: cursor, limit: limit, category: category),
            needsAuth: true
        )
        return response
    }

    @discardableResult
    func exportEntriesCSV(_ request: EntriesCSVRequest) async throws -> ExportResponse {
        // GET /v3/entries/csv — download streams a file, otherwise the report is emailed.
        let response: ExportResponse = try await httpClient.get(
            .entriesCSV(
                category: request.category,
                babyId: request.babyId,
                download: request.download,
                utcOffset: request.utcOffset,
                entryType: request.entryType
            ),
            needsAuth: true
        )
        return response
    }

}
