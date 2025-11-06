//
//  PermissionsService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import Foundation
import GGBluetoothSwiftPackage
import Combine

@MainActor
final class PermissionsService: PermissionsServiceProtocol, ObservableObject {
    // Shared singleton instance for global access. Prefer DI for new code when possible.
    static let shared = PermissionsService()

    // MARK: - Published Properties
    /// Latest permission status keyed by permission type. `nil` until first update from SDK.
    @Published private(set) var permissions: [GGPermissionType: GGPermissionState]? = nil

    /// Permission categories that are required based on the user’s connected devices.
    @Published private(set) var requiredCategories: Set<PermissionCategory> = []

    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var scaleService: ScaleService

    private var cancellables = Set<AnyCancellable>()

    // MARK: - Init
    private init() {
        // Compute the initial required permissions
        self.updateRequiredCategories(with: scaleService.scales)

        // Observe scale changes to keep required permissions up-to-date
        scaleService.$scales
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                self?.updateRequiredCategories(with: scales)
            }
            .store(in: &cancellables)
    }
    
    
    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
    }

    /// Updates a single permission entry and publishes the new dictionary.
    /// - Parameters:
    ///   - type: The permission type to update.
    ///   - state: The new state for the permission.
    func updatePermission(_ type: GGPermissionType, to state: GGPermissionState) {
        var current = self.permissions ?? [:]
        current[type] = state
        self.permissions = current
    }

    // MARK: - Permission Helper
    /// Requests or toggles the permission represented by `type` via the GG SDK and converts the raw result to `GGPermissionState`.
    /// Call this for any permission-related operation instead of the previous specialised helpers.
    /// - Parameter type: The permission type to request or enable.
    /// - Returns: The resulting `GGPermissionState`.
    @discardableResult
    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        let raw = await GGBluetoothSwiftPackage.shared.requestPermission(permissionType: type)
        return raw == GGPermissionState.ENABLED.rawValue ? .ENABLED : .DISABLED
    }
    
    // MARK: - Permission Dispatcher
    /// Centralised permission handler that returns the resulting `GGPermissionState`.
    /// - Parameter type: The permission type that should be handled.
    /// - Returns: The latest `GGPermissionState` for the given permission.
    @discardableResult
    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        switch type {
        case .notification:
            return await self.showNotificationDisabledAlert()
        case .bluetoothSwitch:
            return await self.showBluetoothDisabledAlert()
        case .bluetooth:
            return await self.showBluetoothAuthDisabledAlert()
        case .locationSwitch:
            return await self.showLocationDisabledAlert()
        case .location:
            return await self.showLocationAuthDisabledAlert()
        case .camera:
            return await self.showCameraDisabledAlert()
        case .wifiSwitch, .internet:
            return await self.showWifiDisabledAlert()
        }
    }
    
    /// Checks the current permission state for a given type.
    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        return permissions?[type]
    }
    
    func navigateToWifiSettings() {
        Task {
            await GGBluetoothSwiftPackage.shared.navigateToSettings(permissionType: .WIFI_SWITCH)
        }
    }

    // MARK: - Required Permission Helpers
    /// Updates `requiredCategories` based on the provided devices.
    private func updateRequiredCategories(with devices: [Device]) {
        var newRequired: Set<PermissionCategory> = []

        guard !devices.isEmpty else {
            requiredCategories = []
            return
        }

        for device in devices {
            let rawType = (device.bathScale?.scaleType ?? device.deviceType ?? "")
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

    /// Public accessor for the current set of required permission categories.
    func getRequiredPermissionList() -> Set<PermissionCategory> {
        return requiredCategories
    }

    // MARK: - Alert Builders
    private func showBluetoothDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.bluetoothDisabledTitle,
                message: AlertStrings.PermissionAlerts.bluetoothDisabledMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.exit, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.BLUETOOTH_SWITCH) ?? .DISABLED
                        continuation.resume(returning: current)
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    /// Presents an alert informing the user that Bluetooth authorisation is disabled and
    /// offers the option to open the permissions prompt.  The method suspends until the
    /// user makes a choice and then returns the latest `GGPermissionState` for Bluetooth.
    /// - Returns: The `GGPermissionState` after the user responds to the alert.
    private func showBluetoothAuthDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.bluetoothAuthDisabledTitle,
                message: AlertStrings.PermissionAlerts.bluetoothAuthDisabledMessage,
                buttons: [
                    // Cancel → just return the current state without requesting permissions
                    AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.BLUETOOTH) ?? .DISABLED
                        continuation.resume(returning: current)
                    },
                    // Permissions → trigger a permission request and return the resulting state
                    AlertButtonModel(title: CommonStrings.permissions, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task {
                            let newState = await self.permissionRequest(.BLUETOOTH)
                            continuation.resume(returning: newState)
                        }
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    private func showLocationDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.locationDisabledTitle,
                message: AlertStrings.PermissionAlerts.locationDisabledMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.exit, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.LOCATION_SWITCH) ?? .DISABLED
                        continuation.resume(returning: current)
                    },
                    AlertButtonModel(title: CommonStrings.why, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task {
                            await self.showLocationWhyAlert()
                            // After returning from the "Why" screen, re-show the disabled alert
                            // and only resolve when the user makes a final choice there.
                            let state = await self.showLocationDisabledAlert()
                            continuation.resume(returning: state)
                        }
                    },
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    private func showLocationAuthDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.locationAuthTitle,
                message: AlertStrings.PermissionAlerts.locationAuthMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.LOCATION) ?? .DISABLED
                        continuation.resume(returning: current)
                    },
                    AlertButtonModel(title: CommonStrings.permissions, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task {
                            let newState = await self.permissionRequest(.LOCATION)
                            continuation.resume(returning: newState)
                        }
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    private func showCameraDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.cameraDisabledTitle,
                message: AlertStrings.PermissionAlerts.cameraDisabledMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.cancel, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.CAMERA) ?? .DISABLED
                        continuation.resume(returning: current)
                    },
                    AlertButtonModel(title: CommonStrings.allow, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task {
                            let newState = await self.permissionRequest(.CAMERA)
                            continuation.resume(returning: newState)
                        }
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    private func showNotificationDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.notificationDisabledTitle,
                message: AlertStrings.PermissionAlerts.notificationDisabledMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.ignore, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.NOTIFICATION) ?? .DISABLED
                        continuation.resume(returning: current)
                    },
                    AlertButtonModel(title: CommonStrings.enable, type: .primary) { [weak self] _ in
                        guard let self else { return }
                        Task {
                            let newState = await self.permissionRequest(.NOTIFICATION)
                            continuation.resume(returning: newState)
                        }
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    /// Shows an alert explaining why location permission is required for Bluetooth device connections.
    private func showLocationWhyAlert() async {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.locationWhyTitle,
                message: AlertStrings.PermissionAlerts.locationWhyMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.back, type: .primary) { _ in
                        continuation.resume(returning: ())
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }

    /// Shows an alert informing the user that Wi-Fi is disabled and provides instructions to enable it.
    private func showWifiDisabledAlert() async -> GGPermissionState {
        await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: AlertStrings.PermissionAlerts.wifiDisabledTitle,
                message: AlertStrings.PermissionAlerts.wifiDisabledMessage,
                buttons: [
                    AlertButtonModel(title: CommonStrings.exit, type: .secondary) { [weak self] _ in
                        guard let self else { return }
                        let current = self.getPermissionState(.WIFI_SWITCH) ?? .DISABLED
                        continuation.resume(returning: current)
                    }
                ]
            )
            self.notificationService.showAlert(alert)
        }
    }
}
