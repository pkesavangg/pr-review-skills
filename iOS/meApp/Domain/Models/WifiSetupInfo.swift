import Foundation

/// Container describing Wi-Fi setup parameters used by smart-connect operations.
/// This is a Swift counterpart of the `WifiSetupInfo` 
/// All properties are optional to allow flexible construction depending on the
/// current `WifiSetupType` (first / join / change / espTouchWifi).
public struct WifiSetupInfo: Sendable, Equatable {
    public let ssid: String?
    public let bssid: String?
    public let password: String?
    public let userNumber: Int?
    public let token: String?

    public init(ssid: String? = nil,
                bssid: String? = nil,
                password: String? = nil,
                userNumber: Int? = nil,
                token: String? = nil) {
        self.ssid = ssid
        self.bssid = bssid
        self.password = password
        self.userNumber = userNumber
        self.token = token
    }
} 
