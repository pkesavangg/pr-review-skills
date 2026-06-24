import Foundation
@testable import meApp

enum WifiScaleTestError: Error, Equatable {
    case apiFailed
    case noNetwork
    case setupFailed
}

enum WifiScaleTestFixtures {
    static func makeTokenResponse(token: String = "wifi-token-123") -> WifiScaleTokenResponse {
        WifiScaleTokenResponse(token: token)
    }

    static func makeSetupInfo(
        ssid: String? = "Home Wifi",
        bssid: String? = "aa:bb:cc:dd:ee:ff",
        password: String? = "secret123",
        userNumber: Int? = 7,
        token: String? = "setup-token"
    ) -> WifiSetupInfo {
        WifiSetupInfo(
            ssid: ssid,
            bssid: bssid,
            password: password,
            userNumber: userNumber,
            token: token
        )
    }
}
