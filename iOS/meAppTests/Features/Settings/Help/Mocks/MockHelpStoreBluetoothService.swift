//
//  MockHelpStoreBluetoothService.swift
//  meAppTests
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockHelpStoreBluetoothService: BluetoothServiceProtocol {
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false
    var skipDevices: [String] = []
    var onOpenScaleSetup: ((Device, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { Empty().eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { Empty().eraseToAnyPublisher() }

    var getDeviceLogsResult: Result<DeviceLogs, BluetoothServiceError> = .success(DeviceLogs(logs: []))
    private(set) var getDeviceLogsCalls = 0
    private(set) var lastGetDeviceLogsDevice: Device?

    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> {
        getDeviceLogsCalls += 1
        lastGetDeviceLogsDevice = device
        return getDeviceLogsResult
    }

    func initialize() {}
    func stopScan() {}
    func startBluetoothOperations() async {}
    func disconnectConnectedScales() async {}
    func reapplySkipDevicesExcludingPaired() {}
    func handleWeightOnlyModeAlertDismissed() {}
    func clearDevices() {}
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {}
    func scanForPairing() {}
    func resyncAndScan() async -> Result<Void, BluetoothServiceError> { .success(()) }
    func syncDevices(_ devices: [Device]) {}
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteDevice(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteCurrentUserFromScaleIfPossible(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiList(for device: Device) async -> Result<[WifiDetails], BluetoothServiceError> { .failure(.notImplemented) }
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func cancelWifi(on device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func updateSetting(on device: Device, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateFirmware(on device: Device, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func clearData(on device: Device, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> { .failure(.notImplemented) }
    func updateAccount(on device: Device, preference: R4ScalePreference) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceInfo(for device: Device, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiMacAddress(for device: Device) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func startLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func stopLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(for device: Device, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(on device: Device?) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}
