//
//  NetworkMonitor.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Combine
import Foundation
import Network

// MARK: - Network Monitor
@MainActor
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()
    
    @Published private(set) var isConnected = false
    @Published private(set) var connectionType: NWInterface.InterfaceType?

    var isConnectedPublisher: AnyPublisher<Bool, Never> {
        $isConnected.eraseToAnyPublisher()
    }
    
    private let monitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue.global(qos: .utility)
    private var previousConnectionState: Bool?
    private var isMonitoring = false
    
    private init() {
        let currentPath = monitor.currentPath
        let initialStatus = currentPath.status == .satisfied
        self.isConnected = initialStatus
        self.connectionType = initialStatus ? currentPath.availableInterfaces.first?.type : nil
        self.previousConnectionState = initialStatus
        startMonitoring()
    }
    
    deinit {
        monitor.cancel()
    }
    
    private func startMonitoring() {
        guard !isMonitoring else { return }
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.handlePathUpdate(path)
            }
        }
        monitor.start(queue: monitorQueue)
        isMonitoring = true
    }
    
    nonisolated func stopMonitoring() {
        monitor.cancel()
    }
    
    private func handlePathUpdate(_ path: NWPath) {
        let newConnectionStatus = path.status == .satisfied
        let newConnectionType = path.availableInterfaces.first?.type
        let statusChanged = previousConnectionState == nil || previousConnectionState != newConnectionStatus
        
        if statusChanged {
            previousConnectionState = newConnectionStatus
            isConnected = newConnectionStatus
            connectionType = newConnectionStatus ? newConnectionType : nil
        } else if isConnected && connectionType != newConnectionType {
            connectionType = newConnectionType
        }
    }
    
    func getCurrentConnectionStatus() -> Bool {
        return monitor.currentPath.status == .satisfied
    }
    
    /// Checks real internet availability by pinging the base URL.
    /// - Returns: `true` if reachable, otherwise `false`       
    func verifyNetworkAvailability(baseURL: String) async -> Bool {
        guard getCurrentConnectionStatus(),
              let url = URL(string: baseURL) else {
            return false
        }

        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.timeoutInterval = 5
        request.cachePolicy = .reloadIgnoringLocalCacheData

        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse) != nil
        } catch {
            return false
        }
    }
}
