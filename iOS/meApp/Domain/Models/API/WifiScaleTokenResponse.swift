import Foundation

/// Response model for WiFi scale token API calls.
///
/// Represents the response from the `/account/scale` endpoint which returns
/// a token for WiFi scale operations.
struct WifiScaleTokenResponse: Codable {
    /// The WiFi scale token string.
    let token: String
} 
