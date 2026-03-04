import Foundation
@testable import meApp

@MainActor
final class MockNetworkMonitor: NetworkMonitoring {
    var isConnected: Bool

    init(isConnected: Bool) {
        self.isConnected = isConnected
    }
}
