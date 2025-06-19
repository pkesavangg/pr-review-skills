import Foundation

final class EntryRepositoryAPI: EntryRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared

    func syncOperations(operations: [BathScaleOperationDTO]) async throws {
        let operations = operations.map { $0.toAPIRequest() }
        for operation in operations {
            _ = try await httpClient.send(
                .operationsR4(startTimestamp: nil),
                method: .post,
                body: operation,
                needsAuth: true
                
            ) as EmptyResponse
        }
    }

    func fetchOperations(startTimestamp: String?) async throws -> BathScaleOperationListResponse {
        // GET /operation/r4?start=timestamp
        let response: BathScaleOperationListResponse = try await httpClient.get(
            .operationsR4(startTimestamp: startTimestamp),
            needsAuth: true
        )
        return response
    }
}
