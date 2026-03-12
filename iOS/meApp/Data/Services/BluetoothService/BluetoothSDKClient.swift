import Foundation
import GGBluetoothSwiftPackage

@MainActor
protocol BluetoothSDKClient {
    func scan(_ appType: GGAppType, _ profile: GGBTUserProfile, _ callback: @escaping GGBTScanCallback)
    func scanForPairing()
    func confirmPair(_ device: GGBTDevice) async throws -> UserCreationResponseType
    func updateAccount(_ device: GGBTDevice) async throws -> UserCreationResponseType
    func deleteUser(_ device: GGBTDevice, canDisconnect: Bool) async throws -> UserDeletionResponseType
    func getWifiList(_ device: GGBTDevice) async throws -> GGWifiResponse<GGWifiDetails>
    func setupWifi(_ device: GGBTDevice, _ wifiConfig: GGBTWifiConfig) async throws -> GGWifiSetupResponse
    func getUsers(_ device: GGBTDevice) async throws -> GGScaleUserResponse
    func getWifiMacAddress(_ device: GGBTDevice) async throws -> String
    func getDeviceInfo(_ device: GGBTDevice) async throws -> GGDeviceDetails?
    func updateProfile(profile: GGBTUserProfile) async throws -> [String]
    func startFirmwareUpdate(_ device: GGBTDevice, _ timestamp: UInt32) throws
    func syncDevices(_ devices: [GGBTDevice])
    func pauseScan()
    func stop()
    func resumeScan(_ clearOnlyPairing: Bool)
    func clearDevices()
    func updateSetting(_ device: GGBTDevice, _ settings: [GGBTSetting]) throws
    func disconnectDevice(_ broadcastId: String)
    func skipDevice(_ broadcastId: String, _ considerForSession: Bool)
    func cancelWifi(_ device: GGBTDevice) throws
    func getConnectedWifiSSID(_ device: GGBTDevice) async throws -> String
    func getDeviceLogs(_ device: GGBTDevice) async throws -> GGDeviceLogResponse<DeviceLog>
    func getMeasurementLiveData(_ device: GGBTDevice) async throws -> String
    func clearData(_ device: GGBTDevice, _ dataType: ClearDataType) async throws -> String
    func startLiveMeasurement(_ device: GGBTDevice) throws
    func stopLiveMeasurement(_ device: GGBTDevice) throws
}

@MainActor
final class GGBluetoothSDKClient: BluetoothSDKClient {
    private let sdk: GGBluetoothSwiftPackage

    init(sdk: GGBluetoothSwiftPackage = .shared) {
        self.sdk = sdk
    }

    func scan(_ appType: GGAppType, _ profile: GGBTUserProfile, _ callback: @escaping GGBTScanCallback) {
        sdk.scan(appType, profile, callback)
    }

    func scanForPairing() {
        sdk.scanForPairing()
    }

    func confirmPair(_ device: GGBTDevice) async throws -> UserCreationResponseType {
        await sdk.confirmPair(device)
    }

    func updateAccount(_ device: GGBTDevice) async throws -> UserCreationResponseType {
        await sdk.updateAccount(device)
    }

    func deleteUser(_ device: GGBTDevice, canDisconnect: Bool) async throws -> UserDeletionResponseType {
        await sdk.deleteUser(device, canDisconnect: canDisconnect)
    }

    func getWifiList(_ device: GGBTDevice) async throws -> GGWifiResponse<GGWifiDetails> {
        await sdk.getWifiList(device)
    }

    func setupWifi(_ device: GGBTDevice, _ wifiConfig: GGBTWifiConfig) async throws -> GGWifiSetupResponse {
        await sdk.setupWifi(device, wifiConfig)
    }

    func getUsers(_ device: GGBTDevice) async throws -> GGScaleUserResponse {
        await sdk.getUsers(device)
    }

    func getWifiMacAddress(_ device: GGBTDevice) async throws -> String {
        await sdk.getWifiMacAddress(device)
    }

    func getDeviceInfo(_ device: GGBTDevice) async throws -> GGDeviceDetails? {
        await sdk.getDeviceInfo(device)
    }

    func updateProfile(profile: GGBTUserProfile) async throws -> [String] {
        await sdk.updateProfile(profile: profile)
    }

    func startFirmwareUpdate(_ device: GGBTDevice, _ timestamp: UInt32) throws {
        sdk.startFirmwareUpdate(device, timestamp)
    }

    func syncDevices(_ devices: [GGBTDevice]) {
        sdk.syncDevices(devices)
    }

    func pauseScan() {
        sdk.pauseScan()
    }

    func stop() {
        sdk.stop()
    }

    func resumeScan(_ clearOnlyPairing: Bool) {
        sdk.resumeScan(clearOnlyPairing)
    }

    func clearDevices() {
        sdk.clearDevices()
    }

    func updateSetting(_ device: GGBTDevice, _ settings: [GGBTSetting]) throws {
        sdk.updateSetting(device, settings)
    }

    func disconnectDevice(_ broadcastId: String) {
        sdk.disconnectDevice(broadcastId)
    }

    func skipDevice(_ broadcastId: String, _ considerForSession: Bool) {
        sdk.skipDevice(broadcastId, considerForSession)
    }

    func cancelWifi(_ device: GGBTDevice) throws {
        sdk.cancelWifi(device)
    }

    func getConnectedWifiSSID(_ device: GGBTDevice) async throws -> String {
        await sdk.getConnectedWifiSSID(device)
    }

    func getDeviceLogs(_ device: GGBTDevice) async throws -> GGDeviceLogResponse<DeviceLog> {
        await sdk.getDeviceLogs(device)
    }

    func getMeasurementLiveData(_ device: GGBTDevice) async throws -> String {
        await sdk.getMeasurementLiveData(device)
    }

    func clearData(_ device: GGBTDevice, _ dataType: ClearDataType) async throws -> String {
        await sdk.clearData(device, dataType)
    }

    func startLiveMeasurement(_ device: GGBTDevice) throws {
        sdk.startLiveMeasurement(device)
    }

    func stopLiveMeasurement(_ device: GGBTDevice) throws {
        sdk.stopLiveMeasurement(device)
    }
}
