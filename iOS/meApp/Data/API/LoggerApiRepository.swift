import Foundation


final class LoggerApiRepository: LoggerApiRepositoryProtocol {
    private let httpClient = HTTPClient.shared

    /// Sends logs to the support/log endpoint
    func sendLogs(_ logsPayload: LogsPayload) async throws {
        _ = try await httpClient.send(.log, method: .post, body: logsPayload, needsAuth: true) as String
    }
}
