import Foundation

@MainActor
protocol NetworkMonitoring {
    var isConnected: Bool { get }
}

extension NetworkMonitor: NetworkMonitoring {}
