import Foundation

/// Protocol defining the service interface for managing WiFi scale operations.
///
/// This protocol defines the contract for WiFi scale business logic operations,
/// including token management and other WiFi scale related functionality.
@MainActor
protocol WifiScaleServiceProtocol {
    /// Fetches the scale token for WiFi scale operations.
    /// - Parameter request: Optional parameter for the scale token request.
    /// - Returns: A WifiScaleTokenResponse containing the scale token.
    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse

    /// Returns information about currently connected Wi-Fi network and permission state.
    func getConnectedWifiInfo() async -> WifiStatus

    /// Stops any ongoing Wi-Fi setup operation.
    func stop() async

    /// Starts SmartConfig flow.
    func smartConnect(_ info: WifiSetupInfo) async throws

    /// Starts ESP Touch flow.
    func espSmartConnect(_ info: WifiSetupInfo) async throws

    /// Starts AP mode flow.
    func apMode(_ info: WifiSetupInfo) async throws
} 
