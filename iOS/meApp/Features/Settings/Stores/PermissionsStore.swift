//
//  PermissionsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import Foundation
import Combine
import SwiftUI

@MainActor
final class PermissionsStore: ObservableObject {
    // MARK: - Published outputs
    /// `requiredCategories` that are *mandatory* – used for red status icons.
    @Published private(set) var requiredCategories: Set<PermissionCategory> = []
    
    /// Bluetooth authorization status
    @Published private(set) var isBluetoothAuthorized: Bool = false
    
    /// Bluetooth switch status
    @Published private(set) var isBluetoothOn: Bool = false

    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService
    @Injector private var permissionsService: PermissionsService

    private var cancellables: Set<AnyCancellable> = []

    // MARK: - Init
    init() {
        // Derive the initial value from the current scales array
        updatePermissionSets(with: scaleService.scales)
        
        // Update Bluetooth permissions
        updateBluetoothPermissions()

        // Continue to observe for subsequent scale changes
        scaleService.$scales
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                self?.updatePermissionSets(with: devices)
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Public Methods
    
    /// Updates Bluetooth permission status
    func updateBluetoothPermissions() {
        isBluetoothAuthorized = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        isBluetoothOn = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }
    
    /// Handles Bluetooth authorization permission request
    func handleBluetoothAuthorization() async {
        await permissionsService.handlePermission(.bluetooth)
        updateBluetoothPermissions()
    }
    
    /// Handles Bluetooth switch permission request
    func handleBluetoothSwitch() async {
        await permissionsService.handlePermission(.bluetoothSwitch)
        updateBluetoothPermissions()
    }
    
    /// Handles Bluetooth authorization tap - called from view
    func handleBluetoothAuthorizationTap() {
        Task {
            await handleBluetoothAuthorization()
        }
    }
    
    /// Handles Bluetooth switch tap - called from view
    func handleBluetoothSwitchTap() {
        Task {
            await handleBluetoothSwitch()
        }
    }

    // MARK: - Internal helpers
    /// Updates the set of required permission categories based on the provided devices.
    /// This method analyzes each device's scale or connection type to determine which
    /// permissions (e.g., Bluetooth, Camera, Notifications) are required for proper functionality.
    /// The resulting `requiredCategories` set is used to visually indicate which permissions
    /// are mandatory (e.g., using red status icons) in the `PermissionListView`.
    ///
    /// - Parameter devices: An array of `Device` instances whose capabilities dictate required permissions.
    private func updatePermissionSets(with devices: [Device]) {
        // Reset
        var newRequired: Set<PermissionCategory> = []

        guard !devices.isEmpty else {
            requiredCategories = []
            return
        }

        for device in devices {
            let rawType = (device.bathScale?.scaleType ?? device.deviceType ?? "").lowercased()
            guard let scaleType = ScaleSourceType(rawValue: rawType) else { continue }

            switch scaleType {
            case .wifi, .espTouchWifi:
                newRequired.insert(.notifications)

            case .bluetooth, .lcbt, .lcbtScale, .bluetoothScale:
                newRequired.insert(.bluetooth)

            case .appsync, .appsyncScale:
                newRequired.insert(.camera)

            case .btWifiR4:
                newRequired.formUnion([.bluetooth, .notifications])
            }
        }

        requiredCategories = newRequired
    }
} 
