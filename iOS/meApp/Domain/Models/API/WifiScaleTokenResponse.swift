import Foundation

/// Response model for WiFi scale token API calls.
///
/// Represents the response from the `/account/scale` endpoint which returns
/// a token for WiFi scale operations.
struct WifiScaleTokenResponse: Codable {
    /// The WiFi scale token string.
    let token: String
    
    /// Initializes a WifiScaleTokenResponse.
    /// - Parameter token: The WiFi scale token string.
    init(token: String) {
        self.token = token
    }
} 