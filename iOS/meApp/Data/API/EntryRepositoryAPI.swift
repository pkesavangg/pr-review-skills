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

    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse {
        // GET /operation/r4?start=timestamp
        let response: BathScaleOperationListResponse = try await httpClient.get(
            .operationsR4(startTimestamp: startTimestamp),
            needsAuth: true
        )
        return response
    }

    func exportCsv(useR4Endpoint: Bool) async throws -> ExportResponse {
        let utcOffset = DateTimeTools.getUTCOffset()
        let endPoint: Endpoint = useR4Endpoint
            ? .operationsR4CSV(utcOffset: utcOffset, download: false)
            : .operationsCSV(utcOffset: utcOffset, download: false)
        let response: ExportResponse = try await httpClient.get(
            endPoint,
            needsAuth: true
        )
        return response
    }

}
