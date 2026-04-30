import Foundation

struct BTScaleData: Codable {
    /// The display name of the scale
    var name: String?
    /// The broadcast ID of the scale (as string)
    var broadcastId: String?
    /// The protocol type used by the scale (e.g., Bluetooth, WiFi)
    var protocolType: String?
    /// The unique peripheral identifier for the scale
    var peripheralIdentifier: String?
    /// The MAC address of the scale
    var mac: String?
    /// The password for the scale, if any
    var password: String?
    /// The user number associated with the scale
    var userNumber: Int?
    /// Whether the scale is currently connected
    var isConnected: Bool?
    /// The R4 scale preference data
    var preference: R4ScalePreferenceDTO?
    /// Any additional information received with the scale data
    var additionalInfo: [String: AnyCodable]?

    init(name: String? = nil,
         broadcastId: String? = nil,
         protocolType: String? = nil,
         peripheralIdentifier: String? = nil,
         mac: String? = nil,
         password: String? = nil,
         userNumber: Int? = nil,
         isConnected: Bool? = nil,
         preference: R4ScalePreferenceDTO? = nil,
         additionalInfo: [String: AnyCodable]? = nil) {
        self.name = name
        self.broadcastId = broadcastId
        self.protocolType = protocolType
        self.peripheralIdentifier = peripheralIdentifier
        self.mac = mac
        self.password = password
        self.userNumber = userNumber
        self.isConnected = isConnected
        self.preference = preference
        self.additionalInfo = additionalInfo
    }
}
