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
    var onOpenDeviceSetup: ((DeviceSnapshot, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { Empty().eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var pendingScaleEntryPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var pendingBpmEntryPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { Empty().eraseToAnyPublisher() }
    var newBpmReadingReceivedPublisher: AnyPublisher<BpmMeasurement, Never> { Empty().eraseToAnyPublisher() }

    var getDeviceLogsResult: Result<DeviceLogs, BluetoothServiceError> = .success(DeviceLogs(logs: []))
    private(set) var getDeviceLogsCalls = 0
    private(set) var lastGetDeviceLogsBroadcastId: String?

    func getDeviceLogs(broadcastId: String) async -> Result<DeviceLogs, BluetoothServiceError> {
        getDeviceLogsCalls += 1
        lastGetDeviceLogsBroadcastId = broadcastId
        return getDeviceLogsResult
    }

    func initialize() {}
    func stopScan() {}
    func startBluetoothOperations() async {}
    func confirmPendingScaleEntry() async throws {}
    func discardPendingScaleEntry() {}
    func confirmPendingBpmEntry() async throws {}
    func discardPendingBpmEntry() {}
    func disconnectConnectedScales() async {}
    func reapplySkipDevicesExcludingPaired() {}
    func handleWeightOnlyModeAlertDismissed() {}
    func clearDevices() {}
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {}
    func scanForPairing() {}
    func scanForBpm() {}
    func connectBpm(broadcastId: String, userNumber: Int, replaceUser: Bool, pairedSKUMonitors: [DeviceSnapshot]) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func receiveBpmReading(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func resyncAndScan() async -> Result<Void, BluetoothServiceError> { .success(()) }
    func refreshScanProfileForNonR4Scales() async {}
    func syncDevices(_ devices: [DeviceSnapshot]) {}
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteDevice(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteCurrentUserFromScaleIfPossible(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiList(broadcastId: String) async -> Result<[WifiDetails], BluetoothServiceError> { .failure(.notImplemented) }
    func setupWifi(broadcastId: String, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func cancelWifi(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func updateSetting(broadcastId: String, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateFirmware(broadcastId: String, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func clearData(broadcastId: String, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> { .failure(.notImplemented) }
    func updateAccount(broadcastId: String) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceInfo(broadcastId: String, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiMacAddress(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func startLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func stopLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(broadcastId: String, skipConnectionCheck: Bool, sku: String?) async -> Result<[DeviceUser], BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(broadcastId: String?) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}
