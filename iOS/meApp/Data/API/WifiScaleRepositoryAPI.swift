import Foundation

@MainActor
final class WifiScaleRepositoryAPI: WifiScaleRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared

    func getScaleToken(r: String?) async throws -> WifiScaleTokenResponse {
        return try await httpClient.get(.wifiScale(r: r), needsAuth: true)
    }
} 