import Foundation

struct BTScaleData: Codable {
    /// The display name of the scale
    let name: String?
    /// The broadcast ID of the scale (as string)
    let broadcastId: String?
    /// The protocol type used by the scale (e.g., Bluetooth, WiFi)
    let protocolType: String?
    /// The unique peripheral identifier for the scale
    let peripheralIdentifier: String?
    /// The MAC address of the scale
    let mac: String?
    /// The password for the scale, if any
    let password: String?
    /// The user number associated with the scale
    let userNumber: Int?
    /// Whether the scale is currently connected
    let isConnected: Bool?
    /// The R4 scale preference data
    let preference: R4ScalePreferenceDTO?
    /// Any additional information received with the scale data
    let additionalInfo: [String: AnyCodable]?
}
