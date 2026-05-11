import Foundation

@MainActor
final class EntryRepositoryAPI: EntryRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func syncOperation(operation: BathScaleOperationDTO) async throws {
        _ = try await httpClient.send(
            .operationsR4(startTimestamp: nil),
            method: .post,
            body: operation,
            needsAuth: true
        ) as EmptyResponse
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
