import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockBluetoothService: BluetoothServiceProtocol {
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false
    var skipDevices: [String] = []
    var onOpenScaleSetup: ((Device, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?
    var resyncAndScanResult: Result<Void, BluetoothServiceError> = .success(())
    var deleteCurrentUserFromScaleIfPossibleResult: Result<UserDeletionResponse, BluetoothServiceError> = .failure(.notImplemented)
    var disconnectDeviceResult: Result<Void, BluetoothServiceError> = .success(())
    var getConnectedWifiSSIDResult: Result<String, BluetoothServiceError> = .failure(.notImplemented)
    var updateSettingResult: Result<Void, BluetoothServiceError> = .failure(.notImplemented)
    var updateAccountResult: Result<UserCreationResponse, BluetoothServiceError> = .failure(.notImplemented)
    var getDeviceInfoResult: Result<DeviceInfo, BluetoothServiceError> = .failure(.notImplemented)
    var getWifiMacAddressResult: Result<String, BluetoothServiceError> = .failure(.notImplemented)
    var getScaleUserListResult: Result<[DeviceUser], BluetoothServiceError> = .failure(.notImplemented)
    var updateWeightOnlyModeResult: Result<Void, BluetoothServiceError> = .failure(.notImplemented)
    var updateUserProfileForR4ScalesResult: Result<[String], BluetoothServiceError> = .failure(.notImplemented)
    var deleteR4ScalesResult: Result<Void, BluetoothServiceError> = .failure(.notImplemented)
    var confirmSmartPairResult: Result<UserCreationResponse, BluetoothServiceError> = .failure(.notImplemented)
    var deleteUserByTokenResult: Result<UserDeletionResponse, BluetoothServiceError> = .failure(.notImplemented)
    var getWifiListResult: Result<[WifiDetails], BluetoothServiceError> = .failure(.notImplemented)
    var setupWifiResult: Result<WifiSetupResponse, BluetoothServiceError> = .failure(.notImplemented)
    var cancelWifiResult: Result<Void, BluetoothServiceError> = .success(())
    var startLiveMeasurementResult: Result<Void, BluetoothServiceError> = .success(())
    var stopLiveMeasurementResult: Result<Void, BluetoothServiceError> = .success(())

    private(set) var disconnectConnectedScalesCalls = 0
    private(set) var scanForPairingCalls = 0
    private(set) var resyncAndScanCalls = 0
    private(set) var deleteCurrentUserFromScaleIfPossibleCalls = 0
    private(set) var disconnectDeviceCalls = 0
    private(set) var getConnectedWifiSSIDCalls = 0
    private(set) var updateSettingCalls = 0
    private(set) var updateAccountCalls = 0
    private(set) var getDeviceInfoCalls = 0
    private(set) var getWifiMacAddressCalls = 0
    private(set) var getScaleUserListCalls = 0
    private(set) var updateWeightOnlyModeCalls = 0
    private(set) var updateUserProfileForR4ScalesCalls = 0
    private(set) var deleteR4ScalesCalls = 0
    private(set) var confirmSmartPairCalls = 0
    private(set) var deleteUserByTokenCalls = 0
    private(set) var getWifiListCalls = 0
    private(set) var setupWifiCalls = 0
    private(set) var cancelWifiCalls = 0
    private(set) var startLiveMeasurementCalls = 0
    private(set) var stopLiveMeasurementCalls = 0
    private(set) var lastConnectedWifiSSIDBroadcastId: String?
    private(set) var lastUpdateSettingDevice: Device?
    private(set) var lastUpdateSettings: [DeviceSetting] = []
    private(set) var lastUpdateAccountDevice: Device?
    private(set) var lastUpdateAccountPreference: R4ScalePreference?
    private(set) var lastDeviceInfoDevice: Device?
    private(set) var lastWifiMacDevice: Device?
    private(set) var lastUserListDevice: Device?
    private(set) var lastWeightOnlyModeDevice: Device?
    private(set) var lastConfirmedPairDevice: Device?
    private(set) var lastConfirmedPairToken: String?
    private(set) var lastConfirmedPairDisplayName: String?
    private(set) var lastConfirmedPairUserNumber: Int?
    private(set) var lastDeleteUserBroadcastId: String?
    private(set) var lastDeleteUserToken: String?
    private(set) var lastDeleteUserDisconnect: Bool?
    private(set) var lastWifiListDevice: Device?
    private(set) var lastWifiSetupDevice: Device?
    private(set) var lastWifiSetupConfig: WifiConfig?
    private(set) var lastCancelWifiDevice: Device?
    private(set) var lastStartLiveMeasurementDevice: Device?
    private(set) var lastStopLiveMeasurementDevice: Device?

    let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    let newEntryReceivedSubject = PassthroughSubject<EntryNotification, Never>()
    let liveMeasurementSubject = PassthroughSubject<GGWeightEntry, Never>()

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { deviceDiscoveredSubject.eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { newEntryReceivedSubject.eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { liveMeasurementSubject.eraseToAnyPublisher() }

    func initialize() {}
    func stopScan() {}
    func startBluetoothOperations() async {}
    func disconnectConnectedScales() async { disconnectConnectedScalesCalls += 1 }
    func reapplySkipDevicesExcludingPaired() {}
    func handleWeightOnlyModeAlertDismissed() {}
    func clearDevices() {}
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {}
    func scanForPairing() { scanForPairingCalls += 1 }

    func resyncAndScan() async -> Result<Void, BluetoothServiceError> {
        resyncAndScanCalls += 1
        return resyncAndScanResult
    }
    func syncDevices(_ devices: [Device]) {}
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> {
        confirmSmartPairCalls += 1
        lastConfirmedPairDevice = device
        lastConfirmedPairToken = token
        lastConfirmedPairDisplayName = displayName
        lastConfirmedPairUserNumber = userNumber
        return confirmSmartPairResult
    }
    func deleteDevice(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        deleteUserByTokenCalls += 1
        lastDeleteUserBroadcastId = broadcastId
        lastDeleteUserToken = token
        lastDeleteUserDisconnect = disconnect
        return deleteUserByTokenResult
    }
    func deleteCurrentUserFromScaleIfPossible(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        deleteCurrentUserFromScaleIfPossibleCalls += 1
        return deleteCurrentUserFromScaleIfPossibleResult
    }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> {
        disconnectDeviceCalls += 1
        return disconnectDeviceResult
    }
    func getWifiList(for device: Device) async -> Result<[WifiDetails], BluetoothServiceError> {
        getWifiListCalls += 1
        lastWifiListDevice = device
        return getWifiListResult
    }
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> {
        setupWifiCalls += 1
        lastWifiSetupDevice = device
        lastWifiSetupConfig = config
        return setupWifiResult
    }
    func cancelWifi(on device: Device) async -> Result<Void, BluetoothServiceError> {
        cancelWifiCalls += 1
        lastCancelWifiDevice = device
        return cancelWifiResult
    }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        getConnectedWifiSSIDCalls += 1
        lastConnectedWifiSSIDBroadcastId = broadcastId
        return getConnectedWifiSSIDResult
    }
    func updateSetting(on device: Device, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> {
        updateSettingCalls += 1
        lastUpdateSettingDevice = device
        lastUpdateSettings = settings
        return updateSettingResult
    }
    func updateFirmware(on device: Device, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func clearData(on device: Device, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> {
        updateUserProfileForR4ScalesCalls += 1
        return updateUserProfileForR4ScalesResult
    }
    func updateAccount(on device: Device, preference: R4ScalePreference) async -> Result<UserCreationResponse, BluetoothServiceError> {
        updateAccountCalls += 1
        lastUpdateAccountDevice = device
        lastUpdateAccountPreference = preference
        return updateAccountResult
    }
    func getDeviceInfo(for device: Device, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> {
        getDeviceInfoCalls += 1
        lastDeviceInfoDevice = device
        return getDeviceInfoResult
    }
    func getWifiMacAddress(for device: Device) async -> Result<String, BluetoothServiceError> {
        getWifiMacAddressCalls += 1
        lastWifiMacDevice = device
        return getWifiMacAddressResult
    }
    func startLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> {
        startLiveMeasurementCalls += 1
        lastStartLiveMeasurementDevice = device
        return startLiveMeasurementResult
    }
    func stopLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> {
        stopLiveMeasurementCalls += 1
        lastStopLiveMeasurementDevice = device
        return stopLiveMeasurementResult
    }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(for device: Device, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> {
        getScaleUserListCalls += 1
        lastUserListDevice = device
        return getScaleUserListResult
    }
    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(on device: Device?) async -> Result<Void, BluetoothServiceError> {
        updateWeightOnlyModeCalls += 1
        lastWeightOnlyModeDevice = device
        return updateWeightOnlyModeResult
    }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> {
        deleteR4ScalesCalls += 1
        return deleteR4ScalesResult
    }
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}
