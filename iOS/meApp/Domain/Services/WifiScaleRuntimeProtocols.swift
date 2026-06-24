import Foundation
import ggWifiScalePackage
import Network

@MainActor
protocol WifiNetworkStatusProviding {
    var isConnected: Bool { get }
    var connectionType: NWInterface.InterfaceType? { get }
}

@MainActor
protocol WifiInfoProviding {
    func currentSSID() async -> String?
    func currentBSSID() async -> String?
}

@MainActor
protocol WifiScaleSetupClientProtocol {
    func smartConnect(config: WifiScaleConfig) async throws
    func espSmartConnect(config: WifiScaleConfig) async throws
    func apMode(config: WifiScaleConfig) async throws
    func cancel()
}

@MainActor
struct NetworkMonitorStatusProvider: WifiNetworkStatusProviding {
    var isConnected: Bool { NetworkMonitor.shared.isConnected }
    var connectionType: NWInterface.InterfaceType? { NetworkMonitor.shared.connectionType }
}

@MainActor
struct SystemWifiInfoProvider: WifiInfoProviding {
    func currentSSID() async -> String? {
        await WifiInfoService.currentSSID()
    }

    func currentBSSID() async -> String? {
        await WifiInfoService.currentBSSID()
    }
}

@MainActor
final class BasicWifiScaleSetupClient: WifiScaleSetupClientProtocol {
    private let wifiScale = BasicWifiScale()

    func smartConnect(config: WifiScaleConfig) async throws {
        try await wifiScale.connect(config: config, mode: .smartConfig, timeout: 120)
    }

    func espSmartConnect(config: WifiScaleConfig) async throws {
        try await wifiScale.connect(config: config, mode: .esptouch, timeout: 60)
    }

    func apMode(config: WifiScaleConfig) async throws {
        try await wifiScale.connect(config: config, mode: .apMode, timeout: 90)
    }

    func cancel() {
        wifiScale.cancel()
    }
}
