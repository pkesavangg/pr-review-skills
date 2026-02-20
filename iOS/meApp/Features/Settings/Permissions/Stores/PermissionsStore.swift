//
//  PermissionsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import Combine
import Foundation
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
    @Injector private var permissionsService: PermissionsService

    private var cancellables: Set<AnyCancellable> = []

    // MARK: - Init
    init() {
        
        // Update Bluetooth permissions
        updateBluetoothPermissions()
        
        permissionsService.$requiredCategories
            .receive(on: DispatchQueue.main)
            .sink { [weak self] categories in
                self?.requiredCategories = categories
            }
            .store(in: &cancellables)

        // Subscribe to real-time permission changes from PermissionsService
        // This ensures Bluetooth permissions are updated immediately when the user changes them
        // while already on the ScaleBluetoothScreen
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateBluetoothPermissions()
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
    
    deinit {
        cancellables.removeAll()
    }
}
