import Foundation

@MainActor
final class WifiScaleRepositoryAPI: WifiScaleRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        return try await httpClient.get(.wifiScale(request: request), needsAuth: true)
    }
}
