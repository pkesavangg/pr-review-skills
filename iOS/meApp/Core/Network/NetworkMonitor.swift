//
//  NetworkMonitor.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Network
import Foundation
import Combine

// MARK: - Network Monitor
@MainActor
@Observable
final class NetworkMonitor {
    static let shared = NetworkMonitor()
    
    var isConnected = false
    var connectionType: NWInterface.InterfaceType?
    
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue.global(qos: .background)
    
    private init() {
        startMonitoring();
    }
    
    private func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.isConnected = path.status == .satisfied
                self?.connectionType = path.availableInterfaces.first?.type
            }
        }
        monitor.start(queue: queue)
    }
    
    func stopMonitoring() {
        monitor.cancel()
    }
}
