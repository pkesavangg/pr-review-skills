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
            DispatchQueue.main.async {
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
}
