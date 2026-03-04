import Foundation

@MainActor
protocol NetworkMonitoring {
    var isConnected: Bool { get }
    func getCurrentConnectionStatus() -> Bool
    func verifyNetworkAvailability(baseURL: String) async -> Bool
}

extension NetworkMonitoring {
    func getCurrentConnectionStatus() -> Bool {
        isConnected
    }

    func verifyNetworkAvailability(baseURL: String) async -> Bool {
        getCurrentConnectionStatus()
    }
}

extension NetworkMonitor: NetworkMonitoring {}
