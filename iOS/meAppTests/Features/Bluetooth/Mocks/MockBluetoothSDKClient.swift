import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

struct SetupWifiInput {
    let broadcastId: String
    let ssid: String
    let password: String
}

@MainActor
final class MockBluetoothSDKClient: BluetoothSDKClient {
    var scanError: Error?
    var confirmPairError: Error?
    var updateAccountError: Error?
    var deleteUserError: Error?
    var getWifiListError: Error?
    var setupWifiError: Error?
    var getUsersError: Error?
    var getWifiMacAddressError: Error?
    var getDeviceInfoError: Error?
    var updateProfileError: Error?
    var startFirmwareUpdateError: Error?
    var updateSettingError: Error?
    var cancelWifiError: Error?
    var getConnectedWifiSSIDError: Error?
    var getDeviceLogsError: Error?
    var getMeasurementLiveDataError: Error?
    var clearDataError: Error?
    var startLiveMeasurementError: Error?
    var stopLiveMeasurementError: Error?

    var confirmPairResult: UserCreationResponseType = .CREATION_COMPLETED
    var updateAccountResult: UserCreationResponseType = .CREATION_COMPLETED
    var deleteUserResult: UserDeletionResponseType = .SUCCESS
    var getWifiListResult = decodeWifiResponse([
        ["macAddress": "AA:BB:CC", "ssid": "Home WiFi", "rssi": -44, "password": "pass"]
    ])
    var setupWifiResult = decodeWifiSetupResponse(wifiState: "CONNECTED", errorCode: nil)
    var getUsersResult = decodeScaleUsers([
        ["name": "Scale User", "token": "user-token", "lastActive": 42, "isBodyMetricsEnabled": true]
    ])
    var getWifiMacAddressResult = "11:22:33:44:55:66"
    var getDeviceInfoResult: GGDeviceDetails? = decodeDeviceDetails(
        deviceName: "Scale",
        broadcastId: "ABC123",
        modelNumber: "Model-R4",
        serialNumber: "Serial-123",
        firmwareRevision: "FW-1",
        hardwareRevision: "HW-1",
        softwareRevision: "SW-1",
        manufacturerName: "Weight Gurus",
        systemID: "SYS-1",
        wifiMacAddress: "11:22:33:44:55:66",
        protocolType: "R4"
    )
    var updateProfileResult = ["ABC123"]
    var getConnectedWifiSSIDResult = "Home WiFi"
    var getDeviceLogsResult = decodeDeviceLogs([
        ["macAddress": "AA:BB:CC", "log": "entry"]
    ])
    var getMeasurementLiveDataResult = "ok"
    var clearDataResult = "cleared"

    private(set) var scannedAppTypes: [GGAppType] = []
    private(set) var scannedProfiles: [GGBTUserProfile] = []
    private(set) var scanCallbacks: [GGBTScanCallback] = []
    private(set) var syncedDevices: [[GGBTDevice]] = []
    private(set) var confirmedDevices: [GGBTDevice] = []
    private(set) var deletedDevices: [(device: GGBTDevice, disconnect: Bool)] = []
    private(set) var wifiListDevices: [GGBTDevice] = []
    private(set) var setupWifiCalls: [(device: GGBTDevice, config: GGBTWifiConfig)] = []
    private(set) var setupWifiInputs: [SetupWifiInput] = []
    private(set) var cancelledWifiDevices: [GGBTDevice] = []
    private(set) var connectedWifiSSIDRequests: [GGBTDevice] = []
    private(set) var wifiMacAddressDevices: [GGBTDevice] = []
    private(set) var startLiveMeasurementDevices: [GGBTDevice] = []
    private(set) var stopLiveMeasurementDevices: [GGBTDevice] = []
    private(set) var updateSettingCalls: [(device: GGBTDevice, settings: [GGBTSetting])] = []
    private(set) var firmwareUpdateCalls: [(device: GGBTDevice, timestamp: UInt32)] = []
    private(set) var clearDataCalls: [(device: GGBTDevice, type: ClearDataType)] = []
    private(set) var updateProfileRequests: [GGBTUserProfile] = []
    private(set) var updatedAccountDevices: [GGBTDevice] = []
    private(set) var userListRequests: [GGBTDevice] = []
    private(set) var deviceInfoRequests: [GGBTDevice] = []
    private(set) var skippedDevices: [(broadcastId: String, considerForSession: Bool)] = []

    func scan(_ appType: GGAppType, _ profile: GGBTUserProfile, _ callback: @escaping GGBTScanCallback) {
        scannedAppTypes.append(appType)
        scannedProfiles.append(profile)
        scanCallbacks.append(callback)
        if let scanError {
            callback(.failure(scanError))
        }
    }

    func scanForPairing() {}

    func confirmPair(_ device: GGBTDevice, replaceUser: Bool, pairedSKUMonitors: [GGBTDevice]) async throws -> UserCreationResponseType {
        confirmedDevices.append(device)
        if let confirmPairError { throw confirmPairError }
        return confirmPairResult
    }

    func updateAccount(_ device: GGBTDevice) async throws -> UserCreationResponseType {
        updatedAccountDevices.append(device)
        if let updateAccountError { throw updateAccountError }
        return updateAccountResult
    }

    func deleteUser(_ device: GGBTDevice, canDisconnect: Bool) async throws -> UserDeletionResponseType {
        deletedDevices.append((device, canDisconnect))
        if let deleteUserError { throw deleteUserError }
        return deleteUserResult
    }

    func getWifiList(_ device: GGBTDevice) async throws -> GGWifiResponse<GGWifiDetails> {
        wifiListDevices.append(device)
        if let getWifiListError { throw getWifiListError }
        return getWifiListResult
    }

    func setupWifi(_ device: GGBTDevice, _ wifiConfig: GGBTWifiConfig) async throws -> GGWifiSetupResponse {
        setupWifiCalls.append((device, wifiConfig))
        var ssid = ""
        var password = ""
        for child in Mirror(reflecting: wifiConfig).children {
            guard let label = child.label, let value = child.value as? String else {
                continue
            }
            if label == "ssid" {
                ssid = value
            } else if label == "password" {
                password = value
            }
        }
        setupWifiInputs.append(SetupWifiInput(broadcastId: device.broadcastId, ssid: ssid, password: password))
        if let setupWifiError { throw setupWifiError }
        return setupWifiResult
    }

    func getUsers(_ device: GGBTDevice) async throws -> GGScaleUserResponse {
        userListRequests.append(device)
        if let getUsersError { throw getUsersError }
        return getUsersResult
    }

    func getWifiMacAddress(_ device: GGBTDevice) async throws -> String {
        wifiMacAddressDevices.append(device)
        if let getWifiMacAddressError { throw getWifiMacAddressError }
        return getWifiMacAddressResult
    }

    func getDeviceInfo(_ device: GGBTDevice) async throws -> GGDeviceDetails? {
        deviceInfoRequests.append(device)
        if let getDeviceInfoError { throw getDeviceInfoError }
        return getDeviceInfoResult
    }

    func updateProfile(profile: GGBTUserProfile) async throws -> [String] {
        updateProfileRequests.append(profile)
        if let updateProfileError { throw updateProfileError }
        return updateProfileResult
    }

    func startFirmwareUpdate(_ device: GGBTDevice, _ timestamp: UInt32) throws {
        firmwareUpdateCalls.append((device, timestamp))
        if let startFirmwareUpdateError { throw startFirmwareUpdateError }
    }

    func syncDevices(_ devices: [GGBTDevice]) {
        syncedDevices.append(devices)
    }

    func pauseScan() {}
    func stop() {}
    func resumeScan(_ clearOnlyPairing: Bool) {}
    func clearDevices() {}

    func updateSetting(_ device: GGBTDevice, _ settings: [GGBTSetting]) throws {
        updateSettingCalls.append((device, settings))
        if let updateSettingError { throw updateSettingError }
    }

    func disconnectDevice(_ broadcastId: String) {}

    func skipDevice(_ broadcastId: String, _ considerForSession: Bool) {
        skippedDevices.append((broadcastId, considerForSession))
    }

    func cancelWifi(_ device: GGBTDevice) throws {
        cancelledWifiDevices.append(device)
        if let cancelWifiError { throw cancelWifiError }
    }

    func getConnectedWifiSSID(_ device: GGBTDevice) async throws -> String {
        connectedWifiSSIDRequests.append(device)
        if let getConnectedWifiSSIDError { throw getConnectedWifiSSIDError }
        return getConnectedWifiSSIDResult
    }

    func getDeviceLogs(_ device: GGBTDevice) async throws -> GGDeviceLogResponse<DeviceLog> {
        if let getDeviceLogsError { throw getDeviceLogsError }
        return getDeviceLogsResult
    }

    func getMeasurementLiveData(_ device: GGBTDevice) async throws -> String {
        if let getMeasurementLiveDataError { throw getMeasurementLiveDataError }
        return getMeasurementLiveDataResult
    }

    func clearData(_ device: GGBTDevice, _ dataType: ClearDataType) async throws -> String {
        clearDataCalls.append((device, dataType))
        if let clearDataError { throw clearDataError }
        return clearDataResult
    }

    func startLiveMeasurement(_ device: GGBTDevice) throws {
        startLiveMeasurementDevices.append(device)
        if let startLiveMeasurementError { throw startLiveMeasurementError }
    }

    func stopLiveMeasurement(_ device: GGBTDevice) throws {
        stopLiveMeasurementDevices.append(device)
        if let stopLiveMeasurementError { throw stopLiveMeasurementError }
    }
}

private func decodeWifiResponse(_ wifi: [[String: Any]]) -> GGWifiResponse<GGWifiDetails> {
    decode([
        "wifi": wifi
    ], as: GGWifiResponse<GGWifiDetails>.self)
}

private func decodeScaleUsers(_ users: [[String: Any]]) -> GGScaleUserResponse {
    decode([
        "user": users
    ], as: GGScaleUserResponse.self)
}

private func decodeWifiSetupResponse(wifiState: String, errorCode: String?) -> GGWifiSetupResponse {
    var payload: [String: Any] = ["wifiState": wifiState]
    if let errorCode {
        payload["errorCode"] = errorCode
    }
    return decode(payload, as: GGWifiSetupResponse.self)
}

// swiftlint:disable:next function_parameter_count
private func decodeDeviceDetails(
    deviceName: String,
    broadcastId: String,
    modelNumber: String,
    serialNumber: String,
    firmwareRevision: String,
    hardwareRevision: String,
    softwareRevision: String,
    manufacturerName: String,
    systemID: String,
    wifiMacAddress: String,
    protocolType: String
) -> GGDeviceDetails {
    decode([
        "manufacturerName": manufacturerName,
        "modelNumber": modelNumber,
        "serialNumber": serialNumber,
        "firmwareRevision": firmwareRevision,
        "hardwareRevision": hardwareRevision,
        "softwareRevision": softwareRevision,
        "systemID": systemID,
        "deviceName": deviceName,
        "broadcastId": broadcastId,
        "broadcastIdString": broadcastId,
        "password": "00000000",
        "macAddress": "AA:BB:CC:DD:EE:FF",
        "wifiMacAddress": wifiMacAddress,
        "identifier": "identifier-\(broadcastId)",
        "protocolType": protocolType,
        "isWifiConfigured": true,
        "sessionImpedanceSwitchState": true,
        "impedanceSwitchState": true,
        "startAnimationState": true,
        "endAnimationState": false,
        "batteryLevel": 90,
        "userNumber": 2,
        "heartRateState": true
    ], as: GGDeviceDetails.self)
}

private func decodeDeviceLogs(_ logs: [[String: Any]]) -> GGDeviceLogResponse<DeviceLog> {
    decode([
        "logs": logs
    ], as: GGDeviceLogResponse<DeviceLog>.self)
}

private func decode<T: Decodable>(_ object: [String: Any], as type: T.Type) -> T {
    // swiftlint:disable:next force_try
    let data = try! JSONSerialization.data(withJSONObject: object)
    // swiftlint:disable:next force_try
    return try! JSONDecoder().decode(type, from: data)
}
