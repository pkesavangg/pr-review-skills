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
    @Injector private var permissionsService: PermissionsService
    @Injector private var logger: LoggerService

    private var cancellables: Set<AnyCancellable> = []
    private let tag = "PermissionsStore"

    // MARK: - Init
    init() {
        
        // Update Bluetooth permissions
        updateBluetoothPermissions()
        
        permissionsService.$requiredCategories
            .receive(on: DispatchQueue.main)
            .sink { [weak self] categories in
                self?.requiredCategories = categories
                self?.logger.log(level: .info, tag: self?.tag ?? "PermissionsStore", message: "Required categories updated. categories=\(categories.map { String(describing: $0) })")
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
        logger.log(level: .info, tag: tag, message: "Bluetooth permission states refreshed. authorized=\(isBluetoothAuthorized), switchOn=\(isBluetoothOn)")
    }
    
    /// Handles Bluetooth authorization permission request
    func handleBluetoothAuthorization() async {
        logger.log(level: .info, tag: tag, message: "Handling Bluetooth authorization permission")
        await permissionsService.handlePermission(.bluetooth)
        updateBluetoothPermissions()
    }
    
    /// Handles Bluetooth switch permission request
    func handleBluetoothSwitch() async {
        logger.log(level: .info, tag: tag, message: "Handling Bluetooth switch permission")
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
