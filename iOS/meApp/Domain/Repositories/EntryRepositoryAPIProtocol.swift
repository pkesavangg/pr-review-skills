import Foundation

/// Protocol for interacting with the remote Entry API.
///
/// The write path uses the unified `POST /v3/entries/` endpoint (MOB-384). Read
/// and CSV export still target the legacy `/operation/r4` endpoints until iOS 2b.
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
