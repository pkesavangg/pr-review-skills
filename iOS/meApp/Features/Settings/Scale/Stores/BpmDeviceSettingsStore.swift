//
//  BpmDeviceSettingsStore.swift
//  meApp
//

import Combine
import SwiftData
import SwiftUI

@MainActor
final class BpmDeviceSettingsStore: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var permissionsService: PermissionsServiceProtocol
    private var cancellables = Set<AnyCancellable>()

    // Store the device ID for safe refetching from MainActor context
    private let deviceId: PersistentIdentifier
    private let deviceIdString: String

    // Cached device for fallback when model not found in context
    private var cachedDevice: Device?

    // Returns the cached device - use refreshDevice() to update from database
    var device: Device {
        if let cached = cachedDevice {
            return cached
        }
        logger.log(level: .error, tag: tag, message: "No cached device available")
        return Device(id: "", accountId: "", deviceName: "Error", deviceType: "")
    }

    @Published var isDeviceConnected: Bool = false

    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL?

    let disconnectableDeviceModelTypes: Set<DeviceSourceType> = [.btWifiR4, .bluetooth, .bluetoothScale, .lcbt, .lcbtScale]

    // Strings
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    private let alertLang = AlertStrings.self
    private let appConstants = AppConstants.self

    private let tag = "BpmDeviceSettingsStore"

    convenience init(device: Device) {
        self.init(
            device: device,
            notificationService: nil,
            deviceService: nil,
            bluetoothService: nil,
            logger: nil,
            permissionsService: nil
        )
    }

    init(
        device: Device,
        notificationService: NotificationHelperServiceProtocol?,
        deviceService: PairedDeviceServiceProtocol?,
        bluetoothService: BluetoothServiceProtocol?,
        logger: LoggerServiceProtocol?,
        permissionsService: PermissionsServiceProtocol?
    ) {
        self.deviceId = device.persistentModelID
        self.deviceIdString = device.id
        self.cachedDevice = device

        if let notificationService { self.notificationService = notificationService }
        if let deviceService { self.deviceService = deviceService }
        if let bluetoothService { self.bluetoothService = bluetoothService }
        if let logger { self.logger = logger }
        if let permissionsService { self.permissionsService = permissionsService }

        logger?.log(level: .debug, tag: tag, message: "BpmDeviceSettingsStore initialized for device: \(device.id)")

        refreshCachedValues()

        self.deviceService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                guard let self = self else { return }
                guard devices.contains(where: { $0.id == self.device.id }) else { return }
                self.refreshCachedValues()
            }
            .store(in: &cancellables)
    }

    /// Refreshes the device from the database
    func refreshDevice() {
        if let freshDevice: Device = PersistenceController.shared.context.registeredModel(for: deviceId) {
            cachedDevice = freshDevice
            return
        }

        let idToFind = deviceIdString
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { device in
                device.id == idToFind
            }
        )
        do {
            let results = try PersistenceController.shared.context.fetch(descriptor)
            if let freshDevice = results.first {
                cachedDevice = freshDevice
                return
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch device from store: \(error.localizedDescription)")
        }

        if cachedDevice != nil {
            logger.log(level: .debug, tag: tag, message: "Using existing cached device after refresh failed")
        }
    }

    private func refreshCachedValues() {
        refreshDevice()
        let isBluetoothOn = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        isDeviceConnected = (device.isConnected ?? false) && isBluetoothOn
    }

    func refreshDeviceData() {
        refreshCachedValues()
    }

    /// Opens the product guide/manual for the given SKU inside the in-app browser.
    func openProductGuide(for sku: String) {
        guard let url = URL(string: "\(appConstants.Product.baseURL)/\(sku)") else { return }
        productURL = url
        showProductBrowser = true
    }

    // MARK: - Delete Device
    func handleDeviceDelete(deviceId: String, onSuccess: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.RemoveDeviceAlert.title,
            message: alertLang.RemoveDeviceAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.RemoveDeviceAlert.removeButton, type: .danger) { _ in
                    Task { [weak self] in
                        guard let self = self else { return }
                        let success = await self.deleteDevice(deviceId: deviceId)
                        if success {
                            onSuccess()
                        }
                    }
                },
                AlertButtonModel(title: alertLang.RemoveDeviceAlert.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func deleteDevice(deviceId: String) async -> Bool {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.deletingScale))
        var isSuccess = false
        defer {
            Task { @MainActor in
                _ = await bluetoothService.resyncAndScan()
                bluetoothService.isSetupInProgress = false
            }
        }
        do {
            bluetoothService.isSetupInProgress = true
            if let broadcastId = device.broadcastIdString {
                let deletionTask = Task { @MainActor in
                    _ = await bluetoothService.deleteCurrentUserFromScaleIfPossible(broadcastId: broadcastId, disconnect: false)
                }
                try? await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.scaleDeletionGraceTimeoutNs))
                deletionTask.cancel()
                _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId)
            }
            // Use deleteSingleDeviceEntry to remove only THIS specific user's entry,
            // preserving other users' entries on the same physical BPM monitor.
            try await deviceService.deleteSingleDeviceEntry(deviceId)
            bluetoothService.isSetupInProgress = false
            await deviceService.syncAllScalesWithRemote()
            notificationService.showToast(ToastModel(title: ToastStrings.deleted, message: ToastStrings.scaleDeleted))
            isSuccess = true
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete device: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.errorDeletingScale, message: ToastStrings.restartApp))
        }
        notificationService.dismissLoader()
        return isSuccess
    }

    private func getDeviceModelType() -> DeviceSourceType? {
        guard let scaleType = device.bathScale?.scaleType else { return nil }
        return DeviceSourceType(rawValue: scaleType)
    }
}
