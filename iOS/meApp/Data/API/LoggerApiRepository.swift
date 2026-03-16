import Foundation

@MainActor
final class LoggerApiRepository: LoggerApiRepositoryProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol = HTTPClient.shared) {
        self.httpClient = httpClient
    }

    /// Sends logs to the support/log endpoint
    func sendLogs(_ logsPayload: LogsPayload) async throws {
        _ = try await httpClient.send(.log, method: .post, body: logsPayload, needsAuth: true) as String
    }
}
