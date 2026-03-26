import Foundation

@MainActor
final class WifiScaleRepositoryAPI: WifiScaleRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        return try await httpClient.get(.wifiScale(request: request), needsAuth: true)
    }
}
