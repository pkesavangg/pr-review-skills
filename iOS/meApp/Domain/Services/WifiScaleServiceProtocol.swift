import Foundation

/// Protocol defining the service interface for managing WiFi scale operations.
///
/// This protocol defines the contract for WiFi scale business logic operations,
/// including token management and other WiFi scale related functionality.
@MainActor
protocol WifiScaleServiceProtocol {
    /// Fetches the scale token for WiFi scale operations.
    /// - Parameter r: Optional parameter for the scale token request.
    /// - Returns: A WifiScaleTokenResponse containing the scale token.
    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse
} 