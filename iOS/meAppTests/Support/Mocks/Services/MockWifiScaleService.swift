import Foundation
@testable import meApp

@MainActor
final class MockWifiScaleService: WifiPairedDeviceServiceProtocol {
    var getScaleTokenResult: Result<WifiScaleTokenResponse, Error> = .success(
        WifiScaleTokenResponse(token: "scale-token")
    )
    var connectedWifiInfo: WifiStatus = WifiStatus(status: .connected, locationStatus: .ENABLED, ssid: "Home", bssid: "AA:BB")

    private(set) var getScaleTokenCalls = 0
    private(set) var lastScaleTokenRequest: String?
    private(set) var getConnectedWifiInfoCalls = 0
    private(set) var stopCalls = 0
    private(set) var smartConnectCalls = 0
    private(set) var espSmartConnectCalls = 0
    private(set) var apModeCalls = 0
    private(set) var lastSmartConnectInfo: WifiSetupInfo?
    private(set) var lastEspSmartConnectInfo: WifiSetupInfo?
    private(set) var lastApModeInfo: WifiSetupInfo?

    var smartConnectResult: Result<Void, Error> = .success(())
    var espSmartConnectResult: Result<Void, Error> = .success(())
    var apModeResult: Result<Void, Error> = .success(())

    func getScaleToken(request: String?) async throws -> WifiScaleTokenResponse {
        getScaleTokenCalls += 1
        lastScaleTokenRequest = request
        return try getScaleTokenResult.get()
    }

    func getConnectedWifiInfo() async -> WifiStatus {
        getConnectedWifiInfoCalls += 1
        return connectedWifiInfo
    }

    func stop() async {
        stopCalls += 1
    }

    func smartConnect(_ info: WifiSetupInfo) async throws {
        smartConnectCalls += 1
        lastSmartConnectInfo = info
        try smartConnectResult.get()
    }

    func espSmartConnect(_ info: WifiSetupInfo) async throws {
        espSmartConnectCalls += 1
        lastEspSmartConnectInfo = info
        try espSmartConnectResult.get()
    }

    func apMode(_ info: WifiSetupInfo) async throws {
        apModeCalls += 1
        lastApModeInfo = info
        try apModeResult.get()
    }
}
