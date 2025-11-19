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
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()
    
    @Published var isConnected = true
    @Published var connectionType: NWInterface.InterfaceType?
    
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue.global(qos: .background)
    private var previousWifiState: Bool? = nil
    
    private init() {
        startMonitoring()
    }
    
    private func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                let wasWifi = self.previousWifiState ?? (self.connectionType == .wifi)
                self.isConnected = path.status == .satisfied
                self.connectionType = path.availableInterfaces.first?.type
                
                // Check if WiFi interface is available
                let isWifi = path.availableInterfaces.contains { $0.type == .wifi }
                let isWifiActive = self.connectionType == .wifi && self.isConnected
                
                // Check if WiFi status changed
                if wasWifi != isWifi {
                    self.previousWifiState = isWifi
                }
            }
        }
        monitor.start(queue: queue)
    }
    
    func stopMonitoring() {
        monitor.cancel()
    }
}
