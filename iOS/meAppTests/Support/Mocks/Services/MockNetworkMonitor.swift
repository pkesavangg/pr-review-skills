import Combine
import Foundation
@testable import meApp

@MainActor
final class MockNetworkMonitor: NetworkMonitoring {
    @Published var isConnected: Bool

    var isConnectedPublisher: AnyPublisher<Bool, Never> {
        $isConnected.eraseToAnyPublisher()
    }

    init(isConnected: Bool) {
        self.isConnected = isConnected
    }
}
