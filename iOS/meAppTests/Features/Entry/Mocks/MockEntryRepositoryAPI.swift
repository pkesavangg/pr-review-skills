import Foundation
@testable import meApp

final class MockEntryRepositoryAPI: EntryRepositoryAPIProtocol {
    var submitEntriesError: Error?
    var submitEntriesResult = UnifiedEntryResponse(entries: [], timestamp: "2026-03-03T00:00:00Z")
    var fetchOperationsResult = BathScaleOperationListResponse(operations: [], timestamp: "2026-03-03T00:00:00Z")
    var fetchOperationsError: Error?
    var exportCsvResult = ExportResponse(sent: true)
    var exportCsvError: Error?

    private(set) var submitEntriesCalls = 0
    private(set) var fetchOperationsCalls = 0
    private(set) var exportCsvCalls = 0

    private(set) var lastSubmittedEntries: [UnifiedEntryRequest]?
    private(set) var lastFetchStartTimestamp: String?
    private(set) var lastExportCsvUseR4Endpoint: Bool?

    /// Convenience accessor for tests asserting on the single entry submitted in a batch-of-one push.
    var lastSubmittedEntry: UnifiedEntryRequest? { lastSubmittedEntries?.first }

    @discardableResult
    func submitEntries(_ entries: [UnifiedEntryRequest]) async throws -> UnifiedEntryResponse {
        submitEntriesCalls += 1
        lastSubmittedEntries = entries
        if let submitEntriesError { throw submitEntriesError }
        return submitEntriesResult
    }

    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse {
        fetchOperationsCalls += 1
        lastFetchStartTimestamp = startTimestamp
        if let fetchOperationsError { throw fetchOperationsError }
        return fetchOperationsResult
    }

    func exportCsv(useR4Endpoint: Bool) async throws -> ExportResponse {
        exportCsvCalls += 1
        lastExportCsvUseR4Endpoint = useR4Endpoint
        if let exportCsvError { throw exportCsvError }
        return exportCsvResult
    }

}
