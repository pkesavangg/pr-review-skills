import Foundation
@testable import meApp

@MainActor
final class MockWifiScaleService: WifiScaleServiceProtocol {
    var getScaleTokenResult: Result<WifiScaleTokenResponse, Error> = .success(
        WifiScaleTokenResponse(token: "scale-token")
    )
    var connectedWifiInfo: WifiStatus = WifiStatus(status: .connected, locationStatus: .ENABLED, ssid: "Home", bssid: "AA:BB")

    private(set) var getScaleTokenCalls = 0
    private(set) var lastScaleTokenRequest: String?
    private(set) var getConnectedWifiInfoCalls = 0

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        getScaleTokenCalls += 1
        lastScaleTokenRequest = request
        return try getScaleTokenResult.get()
    }

    func getConnectedWifiInfo() async -> WifiStatus {
        getConnectedWifiInfoCalls += 1
        return connectedWifiInfo
    }
}
