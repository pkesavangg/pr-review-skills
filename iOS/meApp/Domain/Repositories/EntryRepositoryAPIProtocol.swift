import Foundation

/// Protocol for interacting with the remote Operation/Entry API (e.g., /operation/r4 endpoints).
protocol EntryRepositoryAPIProtocol {
    /// Syncs new and deleted entries with the backend via the operation API.
    /// - Parameters:
    ///   - operations: Operations to sync.
    func syncOperation(operation: BathScaleOperationDTO) async throws

    /// Fetches all operations (entries) for the given account from the backend, optionally since a given timestamp or count.
    /// - Parameters:
    ///   - startTimestamp: The timestamp to fetch operations since (optional).
    /// - Returns: An array of Entry objects from the backend.
    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse

    /// Exports all operations as a CSV file.
    /// - Parameters:
    /// - useR4Endpoint: Whether to use the R4 endpoint for export.
    /// - Returns: Data representing the CSV file.
    func exportCsv(useR4Endpoint: Bool) async throws -> ExportResponse
}
