import Foundation
@testable import meApp

final class MockEntryRepositoryAPI: EntryRepositoryAPIProtocol {
    var submitEntriesError: Error?
    var submitEntriesResult = UnifiedEntryResponse(entries: [], timestamp: "2026-03-03T00:00:00Z")
    var fetchEntriesResult = BathScaleOperationListResponse(operations: [], timestamp: "2026-03-03T00:00:00Z")
    var fetchEntriesError: Error?
    var exportCsvResult = ExportResponse(sent: true)
    var exportCsvError: Error?

    private(set) var submitEntriesCalls = 0
    private(set) var fetchEntriesCalls = 0
    private(set) var exportCsvCalls = 0

    private(set) var lastSubmittedEntries: [UnifiedEntryRequest]?
    private(set) var lastFetchStart: String?
    private(set) var lastFetchCursor: String?
    private(set) var lastFetchLimit: Int?
    private(set) var lastFetchCategory: String?
    private(set) var lastFetchBabyId: String?
    private(set) var lastExportCsvRequest: EntriesCSVRequest?

    /// Convenience accessor for tests asserting on the single entry submitted in a batch-of-one push.
    var lastSubmittedEntry: UnifiedEntryRequest? { lastSubmittedEntries?.first }

    @discardableResult
    func submitEntries(_ entries: [UnifiedEntryRequest]) async throws -> UnifiedEntryResponse {
        submitEntriesCalls += 1
        lastSubmittedEntries = entries
        if let submitEntriesError { throw submitEntriesError }
        return submitEntriesResult
    }

    func fetchEntries(
        start: String?,
        cursor: String?,
        limit: Int?,
        category: String?,
        babyId: String?
    ) async throws -> BathScaleOperationListResponse {
        fetchEntriesCalls += 1
        lastFetchStart = start
        lastFetchCursor = cursor
        lastFetchLimit = limit
        lastFetchCategory = category
        lastFetchBabyId = babyId
        if let fetchEntriesError { throw fetchEntriesError }
        return fetchEntriesResult
    }

    @discardableResult
    func exportEntriesCSV(_ request: EntriesCSVRequest) async throws -> ExportResponse {
        exportCsvCalls += 1
        lastExportCsvRequest = request
        if let exportCsvError { throw exportCsvError }
        return exportCsvResult
    }

}
