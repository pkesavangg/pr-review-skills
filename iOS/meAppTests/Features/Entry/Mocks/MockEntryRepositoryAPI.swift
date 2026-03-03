import Foundation
@testable import meApp

final class MockEntryRepositoryAPI: EntryRepositoryAPIProtocol {
    var syncOperationError: Error?
    var fetchOperationsResult = BathScaleOperationListResponse(operations: [], timestamp: "2026-03-03T00:00:00Z")
    var fetchOperationsError: Error?
    var exportCsvResult = ExportResponse(sent: true)
    var exportCsvError: Error?

    private(set) var syncOperationCalls = 0
    private(set) var fetchOperationsCalls = 0
    private(set) var exportCsvCalls = 0

    private(set) var lastSyncedOperation: BathScaleOperationDTO?
    private(set) var lastFetchStartTimestamp: String?
    private(set) var lastExportCsvUseR4Endpoint: Bool?

    func syncOperation(operation: BathScaleOperationDTO) async throws {
        syncOperationCalls += 1
        lastSyncedOperation = operation
        if let syncOperationError { throw syncOperationError }
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
