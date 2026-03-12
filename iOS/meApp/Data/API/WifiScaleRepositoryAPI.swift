import Foundation

@MainActor
final class WifiScaleRepositoryAPI: WifiScaleRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol = HTTPClient.shared) {
        self.httpClient = httpClient
    }

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        return try await httpClient.get(.wifiScale(request: request), needsAuth: true)
    }
}
