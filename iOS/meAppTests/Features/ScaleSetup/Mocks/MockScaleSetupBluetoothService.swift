//
//  MockScaleSetupBluetoothService.swift
//  meAppTests
//

import Foundation
import Combine
@testable import meApp

@MainActor
final class MockScaleSetupBluetoothService: BluetoothServiceProtocol {

    // MARK: - State
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false

    // MARK: - Publishers
    private let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> {
        deviceDiscoveredSubject.eraseToAnyPublisher()
    }

    /// Test helper: emit a device-discovery event to subscribers.
    func emitDiscovery(_ event: DeviceDiscoveryEvent) {
        deviceDiscoveredSubject.send(event)
    }

    private let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> {
        deviceInfoUpdatedSubject.eraseToAnyPublisher()
    }

    private let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> {
        showWeightOnlyModeAlertSubject.eraseToAnyPublisher()
    }

    private let newEntrySubject = PassthroughSubject<EntryNotification, Never>()
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> {
        newEntrySubject.eraseToAnyPublisher()
    }

    private let firmwareUpdateSubject = PassthroughSubject<FirmwareUpdateStatus, Never>()
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> {
        firmwareUpdateSubject.eraseToAnyPublisher()
    }

    // MARK: - Lifecycle
    func initialize() {}
    func stopScan() {}
    func clearDevices() {}

    // MARK: - Scanning & Pairing
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {}
    func scanForPairing() {}

    // MARK: - Device Synchronisation
    func resyncAndScan() async -> Result<Void, BluetoothServiceError> { .success(()) }
    func syncDevices(_ devices: [Device]) {}

    // MARK: - Device CRUD
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .success(device) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> {
        .failure(.notImplemented)
    }
    func deleteDevice(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteCurrentUserFromScaleIfPossible(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> { .success(()) }

    // MARK: - WiFi
    func getWifiList(for device: Device) async -> Result<[WifiDetails], BluetoothServiceError> { .success([]) }
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func cancelWifi(on device: Device) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> { .success("") }

    // MARK: - Settings & Firmware
    func updateSetting(on device: Device, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func updateFirmware(on device: Device, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func clearData(on device: Device, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .success(()) }

    // MARK: - Profile / Account
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> { .success([]) }
    func updateAccount(on device: Device, preference: R4ScalePreference) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }

    // MARK: - Device Information
    func getDeviceInfo(for device: Device, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiMacAddress(for device: Device) async -> Result<String, BluetoothServiceError> { .success("") }
    func startLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func stopLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(for device: Device, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> { .success([]) }
    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> { .failure(.notImplemented) }

    // MARK: - Alerts & Utility
    func updateWeightOnlyMode(on device: Device?) async -> Result<Void, BluetoothServiceError> { .success(()) }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> { .success(()) }
    func reapplySkipDevicesExcludingPaired() {}
    func disconnectConnectedScales() async {}
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}
