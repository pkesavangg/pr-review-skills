import Foundation

/// Protocol for abstracting WiFi scale API operations.
///
/// This protocol defines the contract for interacting with the WiFi scale API endpoints,
/// including fetching scale tokens and other WiFi scale related operations.
@MainActor
protocol WifiScaleRepositoryAPIProtocol {
    /// Fetches the scale token from the backend API.
    /// - Parameter r: Optional parameter for the scale token request.
    /// - Returns: A WifiScaleTokenResponse containing the scale token.
    func getScaleToken(r: String?) async throws -> WifiScaleTokenResponse
} 