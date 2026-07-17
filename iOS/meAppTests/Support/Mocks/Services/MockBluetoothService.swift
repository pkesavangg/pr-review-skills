import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockBluetoothService: BluetoothServiceProtocol {
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false
    var skipDevices: [String] = []
    var onOpenDeviceSetup: ((DeviceSnapshot, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?
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
    var updateFirmwareResult: Result<Void, BluetoothServiceError> = .failure(.notImplemented)
    var clearDataResult: Result<Void, BluetoothServiceError> = .failure(.notImplemented)

    private(set) var disconnectConnectedScalesCalls = 0
    private(set) var scanForPairingCalls = 0
    private(set) var resumeSmartScanCalls = 0
    private(set) var syncDevicesCalls = 0
    private(set) var handleWeightOnlyModeAlertDismissedCalls = 0
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
    private(set) var refreshScanProfileForNonR4ScalesCalls = 0
    private(set) var deleteR4ScalesCalls = 0
    private(set) var confirmSmartPairCalls = 0
    private(set) var deleteUserByTokenCalls = 0
    private(set) var getWifiListCalls = 0
    private(set) var setupWifiCalls = 0
    private(set) var cancelWifiCalls = 0
    private(set) var reapplySkipDevicesExcludingPairedCalls = 0
    private(set) var startLiveMeasurementCalls = 0
    private(set) var stopLiveMeasurementCalls = 0
    private(set) var updateFirmwareCalls = 0
    private(set) var clearDataCalls = 0
    private(set) var lastUpdateFirmwareBroadcastId: String?
    private(set) var lastUpdateFirmwareTimestamp: UInt32?
    private(set) var lastClearDataBroadcastId: String?
    private(set) var lastClearDataType: DeviceClearType?
    private(set) var lastConnectedWifiSSIDBroadcastId: String?
    private(set) var lastUpdateSettingBroadcastId: String?
    private(set) var lastUpdateSettings: [DeviceSetting] = []
    private(set) var lastUpdateAccountBroadcastId: String?
    private(set) var lastDeviceInfoBroadcastId: String?
    private(set) var lastWifiMacBroadcastId: String?
    private(set) var lastUserListBroadcastId: String?
    private(set) var lastWeightOnlyModeBroadcastId: String?
    private(set) var lastConfirmedPairDevice: Device?
    private(set) var lastConfirmedPairToken: String?
    private(set) var lastConfirmedPairDisplayName: String?
    private(set) var lastConfirmedPairUserNumber: Int?
    private(set) var lastDeleteUserBroadcastId: String?
    private(set) var lastDeleteUserToken: String?
    private(set) var lastDeleteUserDisconnect: Bool?
    private(set) var lastWifiListBroadcastId: String?
    private(set) var lastWifiSetupBroadcastId: String?
    private(set) var lastWifiSetupConfig: WifiConfig?
    private(set) var lastCancelWifiBroadcastId: String?
    private(set) var lastStartLiveMeasurementBroadcastId: String?
    private(set) var lastStopLiveMeasurementBroadcastId: String?
    private(set) var lastResumeClearOnlyPairing: Bool?
    private(set) var lastSyncedDevices: [DeviceSnapshot] = []

    let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    let newEntryReceivedSubject = PassthroughSubject<EntryNotification, Never>()
    let pendingScaleEntrySubject = PassthroughSubject<EntryNotification, Never>()
    let pendingBpmEntrySubject = PassthroughSubject<EntryNotification, Never>()
    let liveMeasurementSubject = PassthroughSubject<GGWeightEntry, Never>()

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { deviceDiscoveredSubject.eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { newEntryReceivedSubject.eraseToAnyPublisher() }
    var pendingScaleEntryPublisher: AnyPublisher<EntryNotification, Never> { pendingScaleEntrySubject.eraseToAnyPublisher() }
    var pendingBpmEntryPublisher: AnyPublisher<EntryNotification, Never> { pendingBpmEntrySubject.eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { liveMeasurementSubject.eraseToAnyPublisher() }

    let newBpmReadingReceivedSubject = PassthroughSubject<BpmMeasurement, Never>()
    var newBpmReadingReceivedPublisher: AnyPublisher<BpmMeasurement, Never> { newBpmReadingReceivedSubject.eraseToAnyPublisher() }

    private(set) var confirmPendingScaleEntryCalls = 0
    private(set) var discardPendingScaleEntryCalls = 0
    var confirmPendingScaleEntryError: Error?

    func initialize() {}
    func stopScan() {}
    func startBluetoothOperations() async {}
    func confirmPendingBpmEntry() async throws {}
    func discardPendingBpmEntry() {}
    func confirmPendingScaleEntry() async throws {
        confirmPendingScaleEntryCalls += 1
        if let error = confirmPendingScaleEntryError { throw error }
    }
    func discardPendingScaleEntry() { discardPendingScaleEntryCalls += 1 }
    func disconnectConnectedScales() async { disconnectConnectedScalesCalls += 1 }
    func reapplySkipDevicesExcludingPaired() { reapplySkipDevicesExcludingPairedCalls += 1 }
    func handleWeightOnlyModeAlertDismissed() { handleWeightOnlyModeAlertDismissedCalls += 1 }
    func clearDevices() {}
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {
        resumeSmartScanCalls += 1
        lastResumeClearOnlyPairing = clearOnlyPairing
    }
    func scanForPairing() { scanForPairingCalls += 1 }

    private(set) var scanForBpmCalls = 0
    private(set) var connectBpmCalls = 0
    private(set) var receiveBpmReadingCalls = 0
    private(set) var lastConnectBpmBroadcastId: String?
    private(set) var lastReceiveBpmBroadcastId: String?
    var connectBpmResult: Result<UserCreationResponse, BluetoothServiceError> = .success(.creationCompleted)
    /// When set, each call to connectBpm consumes the next result in the array.
    /// Falls back to connectBpmResult once the array is exhausted.
    var connectBpmResults: [Result<UserCreationResponse, BluetoothServiceError>] = []
    var receiveBpmReadingResult: Result<Void, BluetoothServiceError> = .success(())

    func scanForBpm() { scanForBpmCalls += 1 }
    func connectBpm(broadcastId: String, userNumber: Int, replaceUser: Bool, pairedSKUMonitors: [DeviceSnapshot]) async -> Result<UserCreationResponse, BluetoothServiceError> {
        connectBpmCalls += 1
        lastConnectBpmBroadcastId = broadcastId
        if !connectBpmResults.isEmpty {
            return connectBpmResults.removeFirst()
        }
        return connectBpmResult
    }
    func receiveBpmReading(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        receiveBpmReadingCalls += 1
        lastReceiveBpmBroadcastId = broadcastId
        return receiveBpmReadingResult
    }

    func resyncAndScan() async -> Result<Void, BluetoothServiceError> {
        resyncAndScanCalls += 1
        return resyncAndScanResult
    }
    func refreshScanProfileForNonR4Scales() async {
        refreshScanProfileForNonR4ScalesCalls += 1
    }
    func syncDevices(_ devices: [DeviceSnapshot]) {
        syncDevicesCalls += 1
        lastSyncedDevices = devices
    }
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> {
        confirmSmartPairCalls += 1
        lastConfirmedPairDevice = device
        lastConfirmedPairToken = token
        lastConfirmedPairDisplayName = displayName
        lastConfirmedPairUserNumber = userNumber
        return confirmSmartPairResult
    }
    func deleteDevice(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        deleteUserByTokenCalls += 1
        lastDeleteUserBroadcastId = broadcastId
        lastDeleteUserToken = token
        lastDeleteUserDisconnect = disconnect
        return deleteUserByTokenResult
    }
    func deleteCurrentUserFromScaleIfPossible(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        deleteCurrentUserFromScaleIfPossibleCalls += 1
        return deleteCurrentUserFromScaleIfPossibleResult
    }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> {
        disconnectDeviceCalls += 1
        return disconnectDeviceResult
    }
    func getWifiList(broadcastId: String) async -> Result<[WifiDetails], BluetoothServiceError> {
        getWifiListCalls += 1
        lastWifiListBroadcastId = broadcastId
        return getWifiListResult
    }
    func setupWifi(broadcastId: String, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> {
        setupWifiCalls += 1
        lastWifiSetupBroadcastId = broadcastId
        lastWifiSetupConfig = config
        return setupWifiResult
    }
    func cancelWifi(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        cancelWifiCalls += 1
        lastCancelWifiBroadcastId = broadcastId
        return cancelWifiResult
    }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        getConnectedWifiSSIDCalls += 1
        lastConnectedWifiSSIDBroadcastId = broadcastId
        return getConnectedWifiSSIDResult
    }
    func updateSetting(broadcastId: String, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> {
        updateSettingCalls += 1
        lastUpdateSettingBroadcastId = broadcastId
        lastUpdateSettings = settings
        return updateSettingResult
    }
    func updateFirmware(broadcastId: String, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> {
        updateFirmwareCalls += 1
        lastUpdateFirmwareBroadcastId = broadcastId
        lastUpdateFirmwareTimestamp = timestamp
        return updateFirmwareResult
    }
    func clearData(broadcastId: String, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> {
        clearDataCalls += 1
        lastClearDataBroadcastId = broadcastId
        lastClearDataType = dataType
        return clearDataResult
    }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> {
        updateUserProfileForR4ScalesCalls += 1
        return updateUserProfileForR4ScalesResult
    }
    func updateAccount(broadcastId: String) async -> Result<UserCreationResponse, BluetoothServiceError> {
        updateAccountCalls += 1
        lastUpdateAccountBroadcastId = broadcastId
        return updateAccountResult
    }
    func getDeviceInfo(broadcastId: String, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> {
        getDeviceInfoCalls += 1
        lastDeviceInfoBroadcastId = broadcastId
        return getDeviceInfoResult
    }
    func getWifiMacAddress(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        getWifiMacAddressCalls += 1
        lastWifiMacBroadcastId = broadcastId
        return getWifiMacAddressResult
    }
    func startLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        startLiveMeasurementCalls += 1
        lastStartLiveMeasurementBroadcastId = broadcastId
        return startLiveMeasurementResult
    }
    func stopLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        stopLiveMeasurementCalls += 1
        lastStopLiveMeasurementBroadcastId = broadcastId
        return stopLiveMeasurementResult
    }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(broadcastId: String, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> {
        getScaleUserListCalls += 1
        lastUserListBroadcastId = broadcastId
        return getScaleUserListResult
    }
    func getDeviceLogs(broadcastId: String) async -> Result<DeviceLogs, BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(broadcastId: String?) async -> Result<Void, BluetoothServiceError> {
        updateWeightOnlyModeCalls += 1
        lastWeightOnlyModeBroadcastId = broadcastId
        return updateWeightOnlyModeResult
    }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> {
        deleteR4ScalesCalls += 1
        return deleteR4ScalesResult
    }
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}
