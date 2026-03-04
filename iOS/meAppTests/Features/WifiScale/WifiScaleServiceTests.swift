import Foundation
import Network
import Testing
@testable import meApp

@MainActor
struct WifiScaleServiceTests {
    @Test("getScaleToken success: returns token and forwards request")
    func getScaleTokenSuccess() async throws {
        let deps = makeDependencies()
        deps.api.getScaleTokenResult = .success(WifiScaleTestFixtures.makeTokenResponse(token: "token-a"))
        let sut = makeSUT(deps)

        let result = try await sut.getScaleToken(request: "req-1")

        #expect(result.token == "token-a")
        #expect(deps.api.getScaleTokenCalls == 1)
        #expect(deps.api.lastRequest == "req-1")
    }

    @Test("getScaleToken failure: rethrows API error")
    func getScaleTokenFailure() async {
        let deps = makeDependencies()
        deps.api.getScaleTokenResult = .failure(WifiScaleTestError.apiFailed)
        let sut = makeSUT(deps)

        do {
            _ = try await sut.getScaleToken(request: nil)
            Issue.record("Expected getScaleToken to throw")
        } catch {
            #expect(error as? WifiScaleTestError == .apiFailed)
        }

        #expect(deps.api.getScaleTokenCalls == 1)
    }

    @Test("getConnectedWifiInfo no network: status disabled and empty network details")
    func getConnectedWifiInfoNoNetwork() async {
        let deps = makeDependencies(network: .init(isConnected: false, connectionType: nil))
        deps.permissions.permissionStates[.LOCATION_SWITCH] = .ENABLED
        let sut = makeSUT(deps)

        let info = await sut.getConnectedWifiInfo()

        #expect(info.status == .disabled)
        #expect(info.locationStatus == .ENABLED)
        #expect(info.ssid == "")
        #expect(info.bssid == "")
        #expect(deps.wifiInfo.currentSSIDCalls == 0)
        #expect(deps.wifiInfo.currentBSSIDCalls == 0)
    }

    @Test("getConnectedWifiInfo wifi enabled with missing location permission: returns enabled without SSID")
    func getConnectedWifiInfoEnabledWithoutLocationPermission() async {
        let deps = makeDependencies(network: .init(isConnected: true, connectionType: .wifi))
        deps.permissions.permissionStates[.LOCATION_SWITCH] = .DISABLED
        deps.permissions.permissionStates[.LOCATION] = .ENABLED
        deps.wifiInfo.ssid = "Home"
        deps.wifiInfo.bssid = "aa:bb"
        let sut = makeSUT(deps)

        let info = await sut.getConnectedWifiInfo()

        #expect(info.status == .enabled)
        #expect(info.locationStatus == .DISABLED)
        #expect(info.ssid == "")
        #expect(info.bssid == "")
        #expect(deps.wifiInfo.currentSSIDCalls == 0)
        #expect(deps.wifiInfo.currentBSSIDCalls == 0)
    }

    @Test("getConnectedWifiInfo location granted and SSID/BSSID available: returns connected")
    func getConnectedWifiInfoConnected() async {
        let deps = makeDependencies(network: .init(isConnected: true, connectionType: .wifi))
        deps.permissions.permissionStates[.LOCATION_SWITCH] = .ENABLED
        deps.permissions.permissionStates[.LOCATION] = .ENABLED
        deps.wifiInfo.ssid = "Home"
        deps.wifiInfo.bssid = "aa:bb:cc"
        let sut = makeSUT(deps)

        let info = await sut.getConnectedWifiInfo()

        #expect(info.status == .connected)
        #expect(info.locationStatus == .ENABLED)
        #expect(info.ssid == "Home")
        #expect(info.bssid == "aa:bb:cc")
        #expect(deps.wifiInfo.currentSSIDCalls == 1)
        #expect(deps.wifiInfo.currentBSSIDCalls == 1)
    }

    @Test("getConnectedWifiInfo granted permission but unknown ssid: remains enabled")
    func getConnectedWifiInfoUnknownSSID() async {
        let deps = makeDependencies(network: .init(isConnected: true, connectionType: .wifi))
        deps.permissions.permissionStates[.LOCATION_SWITCH] = .ENABLED
        deps.permissions.permissionStates[.LOCATION] = .ENABLED
        deps.wifiInfo.ssid = "<unknown ssid>"
        deps.wifiInfo.bssid = "11:22"
        let sut = makeSUT(deps)

        let info = await sut.getConnectedWifiInfo()

        #expect(info.status == .enabled)
        #expect(info.ssid == "")
        #expect(info.bssid == "11:22")
    }

    @Test("getConnectedWifiInfo location switch missing: defaults to NOT_REQUESTED")
    func getConnectedWifiInfoLocationDefault() async {
        let deps = makeDependencies(network: .init(isConnected: false, connectionType: nil))
        let sut = makeSUT(deps)

        let info = await sut.getConnectedWifiInfo()

        #expect(info.locationStatus == .NOT_REQUESTED)
    }

    @Test("smartConnect success: builds config and calls setup client")
    func smartConnectSuccess() async throws {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        try await sut.smartConnect(WifiScaleTestFixtures.makeSetupInfo())

        #expect(deps.setupClient.smartConnectCalls == 1)
        #expect(deps.setupClient.lastSmartConfig?.ssid == "Home Wifi")
        #expect(deps.setupClient.lastSmartConfig?.bssid == "aa:bb:cc:dd:ee:ff")
        #expect(deps.setupClient.lastSmartConfig?.password == "secret123")
        #expect(deps.setupClient.lastSmartConfig?.token == "setup-token")
        #expect(deps.setupClient.lastSmartConfig?.userNumber == 7)
    }

    @Test("smartConnect failure: rethrows setup error")
    func smartConnectFailure() async {
        let deps = makeDependencies()
        deps.setupClient.smartConnectResult = .failure(WifiScaleTestError.setupFailed)
        let sut = makeSUT(deps)

        do {
            try await sut.smartConnect(WifiScaleTestFixtures.makeSetupInfo())
            Issue.record("Expected smartConnect to throw")
        } catch {
            #expect(error as? WifiScaleTestError == .setupFailed)
        }

        #expect(deps.setupClient.smartConnectCalls == 1)
    }

    @Test("espSmartConnect success: calls setup client")
    func espSmartConnectSuccess() async throws {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        try await sut.espSmartConnect(WifiScaleTestFixtures.makeSetupInfo(ssid: "Office"))

        #expect(deps.setupClient.espSmartConnectCalls == 1)
        #expect(deps.setupClient.lastEspConfig?.ssid == "Office")
    }

    @Test("apMode success: calls setup client")
    func apModeSuccess() async throws {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        try await sut.apMode(WifiScaleTestFixtures.makeSetupInfo(token: "ap-token"))

        #expect(deps.setupClient.apModeCalls == 1)
        #expect(deps.setupClient.lastApConfig?.token == "ap-token")
    }

    @Test("apMode failure: rethrows setup error")
    func apModeFailure() async {
        let deps = makeDependencies()
        deps.setupClient.apModeResult = .failure(WifiScaleTestError.noNetwork)
        let sut = makeSUT(deps)

        do {
            try await sut.apMode(WifiScaleTestFixtures.makeSetupInfo())
            Issue.record("Expected apMode to throw")
        } catch {
            #expect(error as? WifiScaleTestError == .noNetwork)
        }

        #expect(deps.setupClient.apModeCalls == 1)
    }

    @Test("stop: cancels ongoing setup")
    func stopCancelsSetup() async {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        await sut.stop()

        #expect(deps.setupClient.cancelCalls == 1)
    }

    @Test("setup methods with nil info fields: fallback defaults are applied")
    func setupDefaultsWhenInfoMissing() async throws {
        let deps = makeDependencies()
        let sut = makeSUT(deps)

        try await sut.smartConnect(WifiScaleTestFixtures.makeSetupInfo(ssid: nil, bssid: nil, password: nil, userNumber: nil, token: nil))

        #expect(deps.setupClient.lastSmartConfig?.ssid == "")
        #expect(deps.setupClient.lastSmartConfig?.bssid == "")
        #expect(deps.setupClient.lastSmartConfig?.password == "")
        #expect(deps.setupClient.lastSmartConfig?.token == "")
        #expect(deps.setupClient.lastSmartConfig?.userNumber == 0)
    }

    private func makeDependencies(network: MockWifiNetworkStatusProvider = .init(isConnected: true, connectionType: .wifi)) -> WifiScaleTestDependencies {
        let permissions = MockWifiPermissionsService()
        return WifiScaleTestDependencies(
            api: MockWifiScaleRepositoryAPI(),
            permissions: permissions,
            network: network,
            wifiInfo: MockWifiInfoProvider(),
            setupClient: MockWifiScaleSetupClient(),
            logger: MockLoggerService()
        )
    }

    private func makeSUT(_ deps: WifiScaleTestDependencies) -> WifiScaleService {
        WifiScaleService(
            apiRepo: deps.api,
            logger: deps.logger,
            setupClient: deps.setupClient,
            networkProvider: deps.network,
            wifiInfoProvider: deps.wifiInfo,
            permissionsService: deps.permissions
        )
    }
}

private struct WifiScaleTestDependencies {
    let api: MockWifiScaleRepositoryAPI
    let permissions: MockWifiPermissionsService
    let network: MockWifiNetworkStatusProvider
    let wifiInfo: MockWifiInfoProvider
    let setupClient: MockWifiScaleSetupClient
    let logger: MockLoggerService
}
