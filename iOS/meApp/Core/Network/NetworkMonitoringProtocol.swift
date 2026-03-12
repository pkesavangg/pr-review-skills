import Combine
import Foundation

@MainActor
protocol NetworkMonitoring {
    var isConnected: Bool { get }
    var isConnectedPublisher: AnyPublisher<Bool, Never> { get }
    func getCurrentConnectionStatus() -> Bool
    func verifyNetworkAvailability(baseURL: String) async -> Bool
}

extension NetworkMonitoring {
    var isConnectedPublisher: AnyPublisher<Bool, Never> {
        Just(isConnected).eraseToAnyPublisher()
    }

    func getCurrentConnectionStatus() -> Bool {
        isConnected
    }

    func verifyNetworkAvailability(baseURL: String) async -> Bool {
        getCurrentConnectionStatus()
    }
}

extension NetworkMonitor: NetworkMonitoring {}
