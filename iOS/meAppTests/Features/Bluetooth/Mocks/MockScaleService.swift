import Combine
import Foundation
@testable import meApp

@MainActor
final class MockScaleService: ScaleServiceProtocol {
    @Published var scales: [Device] = []
    var scalesPublisher: AnyPublisher<[Device], Never> { $scales.eraseToAnyPublisher() }
    var attachedPreferences: [String: R4ScalePreference] = [:]

    var updateAllScalesStatusError: Error?
    var syncDevicesError: Error?
    var createDeviceError: Error?
    var getDevicesError: Error?
    var updateScalePreferenceError: Error?
    var updateScalePreferenceErrorsByCall: [Int: Error] = [:]
    var deleteDeviceError: Error?
    var createR4ScaleError: Error?
    var fetchAttachedPreferenceResult: R4ScalePreference?

    private(set) var updateConnectedDevicesCalls = 0
    private(set) var updateConnectedDeviceWifiStatusCalls = 0
    private(set) var updateConnectedDeviceWeightOnlyModeCalls = 0
    private(set) var syncDevicesCalls = 0
    private(set) var createDeviceCalls = 0
    private(set) var createBluetoothScaleCalls = 0
    private(set) var createA6ScaleCalls = 0
    private(set) var createR4ScaleCalls = 0
    private(set) var deleteDeviceCalls = 0
    private(set) var pushLocalChangesToServerCalls = 0
    private(set) var syncAllScalesWithRemoteCalls = 0
    private(set) var updateScalePreferenceCalls = 0
    private(set) var updateScalePreferenceFromDTOCalls = 0
    private(set) var updateAllScalesStatusCalls = 0
    private(set) var lastUpdatedScalePreference: R4ScalePreference?
    private(set) var lastDeletedDeviceId: String?
    private(set) var lastDeletedShowToast: Bool?
    private(set) var lastUpdatedScalePreferenceDeviceId: String?
    private(set) var lastUpdatedScalePreferenceDTO: R4ScalePreferenceDTO?
    private(set) var lastCreatedDevice: Device?
    private(set) var callSequence: [String] = []
    private(set) var lastCreatedBluetoothScale: Device?
    private(set) var lastCreatedA6Scale: Device?
    private(set) var lastCreatedR4Scale: Device?
    private(set) var lastCreateR4ScaleSkipDuplicateCheck: Bool?
    var createA6ScaleError: Error?

    func clearAllData() async {}
    func getDevices() async throws -> [Device] {
        if let getDevicesError {
            throw getDevicesError
        }
        return scales
    }
    func getConnectedDevices() async -> [String: Any] { [:] }

    func updateConnectedDevices(device: Any, isConnected: Bool) async {
        updateConnectedDevicesCalls += 1
        callSequence.append("updateConnectedDevices")
    }

    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {
        updateConnectedDeviceWifiStatusCalls += 1
        callSequence.append("updateConnectedDeviceWifiStatus")
    }

    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {
        updateConnectedDeviceWeightOnlyModeCalls += 1
        callSequence.append("updateConnectedDeviceWeightOnlyMode")
    }

    func syncDevices(tempDevice: Device?) async throws {
        syncDevicesCalls += 1
        callSequence.append("syncDevices")
        if let syncDevicesError { throw syncDevicesError }
    }

    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device {
        createDeviceCalls += 1
        callSequence.append("createDevice")
        lastCreatedDevice = device
        if let createDeviceError { throw createDeviceError }
        return device
    }

    func createBluetoothScale(
        device: Device,
        sku: String?,
        userNumber: String,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool,
        deviceType: DeviceType = .scale
    ) async throws -> Device {
        createBluetoothScaleCalls += 1
        if let createDeviceError { throw createDeviceError }

        device.accountId = accountId
        device.sku = sku
        device.userNumber = userNumber
        device.metaData = deviceMetadata
        device.bathScale = device.bathScale ?? BathScale(scaleType: ScaleSourceType.bluetooth.rawValue, bodyComp: false)
        lastCreatedBluetoothScale = device
        return device
    }

    func createA6Scale(
        device: Device,
        sku: String?,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device {
        createA6ScaleCalls += 1
        if let createA6ScaleError { throw createA6ScaleError }
        device.accountId = accountId
        device.sku = sku
        device.metaData = deviceMetadata
        lastCreatedA6Scale = device
        return device
    }

    func createR4Scale(
        scaleId: String,
        accountId: String,
        displayName: String,
        token: String,
        mac: String?,
        broadcastIdString: String?,
        broadcastId: Int64?,
        sku: String?,
        deviceName: String?,
        wifiMac: String? = nil,
        deviceMetadata: DeviceMetaData? = nil,
        isWifiConfigured: Bool = false,
        isConnected: Bool = false,
        skipDuplicateCheck: Bool = false
    ) async throws -> Device {
        createR4ScaleCalls += 1
        lastCreateR4ScaleSkipDuplicateCheck = skipDuplicateCheck
        if let createR4ScaleError { throw createR4ScaleError }

        let device = Device(
            id: scaleId,
            accountId: accountId,
            nickname: "AccuCheck Verve Smart Scale",
            sku: sku,
            mac: mac,
            deviceName: deviceName,
            deviceType: DeviceType.scale.rawValue,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            userNumber: "0",
            createdAt: "2026-03-03T00:00:00Z",
            isConnected: isConnected,
            wifiMac: wifiMac,
            isWifiConfigured: isWifiConfigured,
            token: token,
            metaData: deviceMetadata
        )
        device.bathScale = BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true)
        device.r4ScalePreference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(scaleId: scaleId, displayName: displayName),
            scaleId: scaleId
        )
        lastCreatedR4Scale = device
        return device
    }

    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        throw UnexpectedCallError.methodCalled("editDevice")
    }

    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        deleteDeviceCalls += 1
        lastDeletedDeviceId = deviceId
        lastDeletedShowToast = showToast
        if let deleteDeviceError { throw deleteDeviceError }
    }

    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {
        updateScalePreferenceCalls += 1
        lastUpdatedScalePreference = preference
        let callError = updateScalePreferenceErrorsByCall[updateScalePreferenceCalls]
        if let callError { throw callError }
        if let updateScalePreferenceError { throw updateScalePreferenceError }
    }
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {
        updateScalePreferenceFromDTOCalls += 1
        lastUpdatedScalePreferenceDeviceId = deviceId
        lastUpdatedScalePreferenceDTO = dto
        if let updateScalePreferenceError { throw updateScalePreferenceError }
    }

    func updateAllScalesStatus(_ scales: [Device]?) async throws {
        updateAllScalesStatusCalls += 1
        callSequence.append("updateAllScalesStatus")
        if let updateAllScalesStatusError { throw updateAllScalesStatusError }
    }

    func createScaleInLocal(_ device: Device) async throws -> Device {
        device
    }

    func syncAllScalesWithRemote() async { syncAllScalesWithRemoteCalls += 1 }
    func pushLocalChangesToServer() async { pushLocalChangesToServerCalls += 1 }
    func getDevice(by deviceId: String) async throws -> Device? { scales.first { $0.id == deviceId } }
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { attachedPreferences[id] ?? fetchAttachedPreferenceResult }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { attachedPreferences[id] ?? fetchAttachedPreferenceResult }
}
